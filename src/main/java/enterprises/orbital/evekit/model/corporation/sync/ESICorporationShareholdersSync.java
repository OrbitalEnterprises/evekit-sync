package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdShareholders200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Shareholder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationShareholdersSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdShareholders200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationShareholdersSync.class.getName());

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  public ESICorporationShareholdersSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_SHAREHOLDERS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Shareholder;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = Shareholder.get(account, time, ((Shareholder) item).getShareholderID());
    }
    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdShareholders200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    try {
      Pair<Long, List<GetCorporationsCorporationIdShareholders200Ok>> result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdShareholdersWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });

      long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
      return new ESIAccountServerResult<>(expiry, result.getRight());
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        cacheHit();
        return new ESIAccountServerResult<>(expiry, null);
      } else {
        // Any other error will be rethrown.
        throw e;
      }

    }
  }

  @SuppressWarnings({"Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdShareholders200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData() == null)
      // 403 - nothing to do
      return;

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(1);

    // Compute and check hash
    List<Shareholder> retrievedShareholders = new ArrayList<>();
    for (GetCorporationsCorporationIdShareholders200Ok next : data.getData()) {
      retrievedShareholders.add(new Shareholder(next.getShareholderId(),
                                  next.getShareholderType()
                                      .toString(),
                                  next.getShareCount()));
    }
    retrievedShareholders.sort(Comparator.comparingInt(Shareholder::getShareholderID));
    String hash = CachedData.dataHashHelper(retrievedShareholders.toArray());

    if (cachedHash[0] == null || !cachedHash[0].equals(hash)) {
      cacheMiss();
      cachedHash[0] = hash;
      updates.addAll(retrievedShareholders);

      // Keep track of seen shareholders
      Set<Integer> seenShareholders = retrievedShareholders.stream().map(Shareholder::getShareholderID).collect(
          Collectors.toSet());

      // Check for shareholders that no longer exist and schedule for EOL
      for (Shareholder existing : retrieveAll(time,
                                              (long contid, AttributeSelector at) -> Shareholder.accessQuery(
                                                  account, contid,
                                                  1000,
                                                  false, at,
                                                  ANY_SELECTOR,
                                                  ANY_SELECTOR,
                                                  ANY_SELECTOR))) {
        if (!seenShareholders.contains(existing.getShareholderID())) {
          existing.evolve(null, time);
          updates.add(existing);
        }
      }
    } else {
      cacheHit();
    }

    // Save new hash
    currentETag = String.join("|", cachedHash);

  }

}
