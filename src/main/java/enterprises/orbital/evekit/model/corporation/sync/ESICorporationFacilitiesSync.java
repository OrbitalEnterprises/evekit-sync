package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdFacilities200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Facility;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICorporationFacilitiesSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdFacilities200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationFacilitiesSync.class.getName());

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  public ESICorporationFacilitiesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_FACILITIES;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Facility;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = Facility.get(account, time, ((Facility) item).getFacilityID());
    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdFacilities200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    // Check whether we have an ETAG to send for the skills call
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    try {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCorporationsCorporationIdFacilities200Ok>> result = apiInstance.getCorporationsCorporationIdFacilitiesWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          currentETag,
          accessToken());
      checkCommonProblems(result);
      cacheMiss();
      currentETag = extractETag(result, null);
      return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                          result.getData());
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        cacheHit();
        return new ESIAccountServerResult<>(expiry, Collections.emptyList());
      } else if (e.getCode() == 304) {
        // ETag hit
        cacheHit();
        currentETag = extractETag(e, null);
        return new ESIAccountServerResult<>(extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay()), null);
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }
  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdFacilities200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    Set<Long> seenFacilities = new HashSet<>();

    for (GetCorporationsCorporationIdFacilities200Ok next : data.getData()) {
      updates.add(new Facility(next.getFacilityId(),
                               next.getTypeId(),
                               next.getSystemId()));
      seenFacilities.add(next.getFacilityId());
    }

    for (Facility existing : retrieveAll(time,
                                         (long contid, AttributeSelector at) -> Facility.accessQuery(
                                             account, contid,
                                             1000,
                                             false, at,
                                             ANY_SELECTOR,
                                             ANY_SELECTOR,
                                             ANY_SELECTOR))) {
      if (!seenFacilities.contains(existing.getFacilityID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

  }

}
