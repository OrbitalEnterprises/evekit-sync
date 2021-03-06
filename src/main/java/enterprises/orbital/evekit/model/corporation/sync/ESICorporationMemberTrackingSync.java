package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMembertracking200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.MemberLimit;
import enterprises.orbital.evekit.model.corporation.MemberTracking;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationMemberTrackingSync extends AbstractESIAccountSync<ESICorporationMemberTrackingSync.MemberData> {
  protected static final Logger log = Logger.getLogger(ESICorporationMemberTrackingSync.class.getName());

  class MemberData {
    int limit;
    List<GetCorporationsCorporationIdMembertracking200Ok> members;
  }

  public ESICorporationMemberTrackingSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_TRACK_MEMBERS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof MemberLimit) || (item instanceof MemberTracking);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof MemberLimit)
        existing = MemberLimit.get(account, time);
      else
        existing = MemberTracking.get(account, time, ((MemberTracking) item).getCharacterID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<MemberData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    MemberData data = new MemberData();
    CorporationApi apiInstance = cp.getCorporationApi();

    long expiry;
    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<Integer> result = apiInstance.getCorporationsCorporationIdMembersLimitWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          accessToken());
      checkCommonProblems(result);
      data.limit = result.getData();
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCorporationsCorporationIdMembertracking200Ok>> result = apiInstance.getCorporationsCorporationIdMembertrackingWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          accessToken());
      checkCommonProblems(result);
      data.members = result.getData();
      expiry = Math.max(expiry, extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<MemberData> data,
                                   List<CachedData> updates) throws IOException {

    // Store limit update first
    updates.add(new MemberLimit(data.getData().limit));

    // Now process members.  Keep track of members we've seen.
    Set<Integer> seenMembers = new HashSet<>();
    for (GetCorporationsCorporationIdMembertracking200Ok next : data.getData().members) {
      updates.add(new MemberTracking(next.getCharacterId(),
                                     nullSafeInteger(next.getBaseId(), 0),
                                     nullSafeLong(next.getLocationId(), 0),
                                     nullSafeDateTime(next.getLogoffDate(), new DateTime(new Date(0))).getMillis(),
                                     nullSafeDateTime(next.getLogonDate(), new DateTime(new Date(0))).getMillis(),
                                     nullSafeInteger(next.getShipTypeId(), 0),
                                     nullSafeDateTime(next.getStartDate(), new DateTime(new Date(0))).getMillis()));
      seenMembers.add(next.getCharacterId());
    }

    // Check for members that no longer exist and schedule for EOL
    for (MemberTracking existing : retrieveAll(time,
                                               (long contid, AttributeSelector at) -> MemberTracking.accessQuery(
                                                   account, contid,
                                                   1000,
                                                   false, at,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR))) {
      if (!seenMembers.contains(existing.getCharacterID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
