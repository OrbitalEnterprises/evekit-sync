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
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdLoyaltyPoints200Ok>> result = apiInstance.getCharactersCharacterIdLoyaltyPointsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        accessToken(),
        null,
        null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdLoyaltyPoints200Ok>> data,
                                   List<CachedData> updates) throws IOException {

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
