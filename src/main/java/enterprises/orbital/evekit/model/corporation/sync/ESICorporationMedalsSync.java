package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMedals200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMedalsIssued200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CorporationMedal;
import enterprises.orbital.evekit.model.corporation.CorporationMemberMedal;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationMedalsSync extends AbstractESIAccountSync<ESICorporationMedalsSync.MedalsData> {
  protected static final Logger log = Logger.getLogger(ESICorporationMedalsSync.class.getName());

  class MedalsData {
    List<GetCorporationsCorporationIdMedals200Ok> medals;
    List<GetCorporationsCorporationIdMedalsIssued200Ok> issued;
  }

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

  public ESICorporationMedalsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_MEDALS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CorporationMedal) || (item instanceof CorporationMemberMedal);
    CachedData existing = null;

    if (item instanceof CorporationMedal && item.getLifeStart() == 0) {
      // Only CorporationMedals can be modified.  If this is an update, then check
      // for an existing CorporationMedal.
      existing = CorporationMedal.get(account, time, ((CorporationMedal) item).getMedalID());
    } else if (item instanceof CorporationMemberMedal) {
      // CorporationMemberMedals can NOT be modified.  If this awarded medal already exists,
      // then we can skip this update.  Otherwise, we're creating the awarded medal for the
      // first time.
      CorporationMemberMedal m = (CorporationMemberMedal) item;
      if (CorporationMemberMedal.get(account, time, m.getMedalID(), m.getCharacterID(), m.getIssued()) != null)
        return;
    }

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<MedalsData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    MedalsData data = new MedalsData();
    CorporationApi apiInstance = cp.getCorporationApi();

    Pair<Long, List<GetCorporationsCorporationIdMedals200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdMedalsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          page,
          accessToken());
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.medals = result.getRight();

    long bkExpiry;
    try {
      Pair<Long, List<GetCorporationsCorporationIdMedalsIssued200Ok>> bkResult = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdMedalsIssuedWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
      bkExpiry = bkResult.getLeft() > 0 ? bkResult.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
      data.issued = bkResult.getRight();
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        bkExpiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        data.issued = null;
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }

    return new ESIAccountServerResult<>(Math.max(expiry, bkExpiry), data);
  }

  @SuppressWarnings({"Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<MedalsData> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(2);

    // Check hash for medals
    List<CorporationMedal> retrievedMedals = new ArrayList<>();
    for (GetCorporationsCorporationIdMedals200Ok next : data.getData().medals) {
      retrievedMedals.add(new CorporationMedal(next.getMedalId(),
                                       next.getDescription(),
                                       next.getTitle(),
                                       next.getCreatedAt()
                                           .getMillis(),
                                       next.getCreatorId()));
    }
    retrievedMedals.sort(Comparator.comparingInt(CorporationMedal::getMedalID));
    String medalHash = CachedData.dataHashHelper(retrievedMedals.toArray());

    if (cachedHash[0] == null || !cachedHash[0].equals(medalHash)) {
      cacheMiss();
      cachedHash[0] = medalHash;
      updates.addAll(retrievedMedals);

      Set<Integer> seenMedals = retrievedMedals.stream().map(CorporationMedal::getMedalID).collect(Collectors.toSet());

      // Check for medals that no longer exist and schedule for EOL
      for (CorporationMedal existing : retrieveAll(time,
                                                   (long contid, AttributeSelector at) -> CorporationMedal.accessQuery(
                                                       account, contid,
                                                       1000,
                                                       false, at,
                                                       ANY_SELECTOR,
                                                       ANY_SELECTOR,
                                                       ANY_SELECTOR,
                                                       ANY_SELECTOR,
                                                       ANY_SELECTOR))) {
        if (!seenMedals.contains(existing.getMedalID())) {
          existing.evolve(null, time);
          updates.add(existing);
        }
      }
    } else {
      cacheHit();
    }

    // Check hash for issued
    if (data.getData().issued != null) {
      List<CorporationMemberMedal> issuedRetrieved = new ArrayList<>();
      for (GetCorporationsCorporationIdMedalsIssued200Ok next : data.getData().issued) {
        issuedRetrieved.add(new CorporationMemberMedal(next.getMedalId(),
                                               next.getCharacterId(),
                                               next.getIssuedAt()
                                                   .getMillis(),
                                               next.getIssuerId(),
                                               next.getReason(),
                                               next.getStatus()
                                                   .toString()));
      }
      issuedRetrieved.sort(Comparator.comparingLong(CorporationMemberMedal::getIssued));
      String issuedHash = CachedData.dataHashHelper(issuedRetrieved.toArray());

      if (cachedHash[1] == null || !cachedHash[1].equals(issuedHash)) {
        cacheMiss();
        cachedHash[1] = issuedHash;
        updates.addAll(issuedRetrieved);
      } else {
        cacheHit();
      }

    } else {
      cacheHit();
    }

    // Save new hash
    currentETag = String.join("|", cachedHash);
  }

}
