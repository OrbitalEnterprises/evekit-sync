package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.LocationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdShipOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterShip;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterShipSync extends AbstractESIAccountSync<GetCharactersCharacterIdShipOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterShipSync.class.getName());

  private CachedCharacterShip cacheUpdate;

  public ESICharacterShipSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_SHIP_TYPE;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterShip;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CharacterShip.get(account, time);
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdShipOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    LocationApi apiInstance = cp.getLocationApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdShipOk> result = apiInstance.getCharactersCharacterIdShipWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdShipOk> data,
                                   List<CachedData> updates) throws IOException {
    CharacterShip shipUpdate = new CharacterShip(data.getData()
                                                     .getShipTypeId(),
                                                 data.getData()
                                                     .getShipItemId(),
                                                 data.getData()
                                                     .getShipName());

    // Only queue update if something has changed.
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CHAR_SHIP_TYPE);
    cacheUpdate = ref != null ? (CachedCharacterShip) ref.get() : null;
    if (cacheUpdate == null) {
      // No cache yet, populate from latest character ship
      cacheInit();
      cacheUpdate = new CachedCharacterShip();
      cacheUpdate.cachedData = CharacterShip.get(account, time);
    }

    if (cacheUpdate.cachedData == null || !cacheUpdate.cachedData.equivalent(shipUpdate)) {
      updates.add(shipUpdate);
      cacheUpdate.cachedData = shipUpdate;
      cacheMiss();
    } else {
      cacheHit();
    }

  }

  @Override
  protected void commitComplete() {
    // Update the character ship cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CHAR_SHIP_TYPE, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCharacterShip implements ModelCacheData {
    CharacterShip cachedData;
  }

}
