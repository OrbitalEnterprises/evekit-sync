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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationFacilitiesSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdFacilities200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationFacilitiesSync.class.getName());

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

  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdFacilities200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCorporationsCorporationIdFacilities200Ok>> result = apiInstance.getCorporationsCorporationIdFacilitiesWithHttpInfo(
        (int) account.getEveCorporationID(),
        null,
        null,
        accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdFacilities200Ok>> data,
                                   List<CachedData> updates) throws IOException {

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
