package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.LoyaltyApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdLoyaltyPoints200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.LoyaltyPoints;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterLoyaltyPointsSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdLoyaltyPoints200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterLoyaltyPointsSync.class.getName());

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

  public ESICharacterLoyaltyPointsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_LOYALTY;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof LoyaltyPoints;

    CachedData existing = LoyaltyPoints.get(account, time, ((LoyaltyPoints) item).getCorporationID());

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdLoyaltyPoints200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    LoyaltyApi apiInstance = cp.getLoyaltyApi();

    // Check whether we have an ETAG to send for the skills call
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    try {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCharactersCharacterIdLoyaltyPoints200Ok>> result = apiInstance.getCharactersCharacterIdLoyaltyPointsWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          currentETag,
          accessToken());
      checkCommonProblems(result);
      cacheMiss();
      currentETag = extractETag(result, null);
      return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                          result.getData());
    } catch (ApiException e) {
      // Trap 304 which indicates there are no changes from the last call
      // Anything else is rethrown.
      if (e.getCode() != 304) throw e;
      cacheHit();
      currentETag = extractETag(e, null);
      return new ESIAccountServerResult<>(extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay()), null);
    }

  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdLoyaltyPoints200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    Map<Integer, LoyaltyPoints> existingLPMap = CachedData.retrieveAll(time,
                                                                       (contid, at) -> LoyaltyPoints.accessQuery(
                                                                           account,
                                                                           contid, 1000,
                                                                           false, at,
                                                                           AttributeSelector.any(),
                                                                           AttributeSelector.any()))
                                                          .stream()
                                                          .map(x -> new AbstractMap.SimpleEntry<>(x.getCorporationID(),
                                                                                                  x))
                                                          .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey,
                                                                                    AbstractMap.SimpleEntry::getValue));

    Set<Integer> seenLPs = new HashSet<>();

    for (GetCharactersCharacterIdLoyaltyPoints200Ok next : data.getData()) {
      LoyaltyPoints nextLP = new LoyaltyPoints(next.getCorporationId(),
                                               next.getLoyaltyPoints());
      // Only update if there is a change to reduce DB contention
      if (!existingLPMap.containsKey(nextLP.getCorporationID()) ||
          !nextLP.equivalent(existingLPMap.get(nextLP.getCorporationID())))
        updates.add(nextLP);
      seenLPs.add(next.getCorporationId());
    }

    // Check for deleted LPs
    for (LoyaltyPoints stored : existingLPMap.values()) {
      if (!seenLPs.contains(stored.getCorporationID())) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

  }

}
