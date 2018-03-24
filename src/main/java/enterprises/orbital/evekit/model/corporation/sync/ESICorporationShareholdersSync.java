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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationShareholdersSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdShareholders200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationShareholdersSync.class.getName());

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

  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdShareholders200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    Pair<Long, List<GetCorporationsCorporationIdShareholders200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdShareholdersWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });

    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    return new ESIAccountServerResult<>(expiry, result.getRight());
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdShareholders200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    // Keep track of seen shareholders
    Set<Integer> seenShareholders = new HashSet<>();

    // Process shareholders
    for (GetCorporationsCorporationIdShareholders200Ok next : data.getData()) {
      updates.add(new Shareholder(next.getShareholderId(),
                                  next.getShareholderType()
                                      .toString(),
                                  next.getShareCount()));
      seenShareholders.add(next.getShareholderId());
    }

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
  }

}
