package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.LocationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdLocationOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterLocation;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterLocationSync extends AbstractESIAccountSync<GetCharactersCharacterIdLocationOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterLocationSync.class.getName());

  private CachedCharacterLocation cacheUpdate;

  public ESICharacterLocationSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_LOCATION;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterLocation;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CharacterLocation.get(account, time);
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdLocationOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    LocationApi apiInstance = cp.getLocationApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdLocationOk> result = apiInstance.getCharactersCharacterIdLocationWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdLocationOk> data,
                                   List<CachedData> updates) throws IOException {
    CharacterLocation locationUpdate = new CharacterLocation(data.getData()
                                                                 .getSolarSystemId(),
                                                             nullSafeInteger(data.getData()
                                                                                 .getStationId(), 0),
                                                             nullSafeLong(data.getData()
                                                                              .getStructureId(), 0L));

    // Only queue update if something has changed.
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CHAR_LOCATION);
    cacheUpdate = ref != null ? (CachedCharacterLocation) ref.get() : null;
    if (cacheUpdate == null) {
      // No cache yet, populate from latest character location
      cacheInit();
      cacheUpdate = new CachedCharacterLocation();
      cacheUpdate.cachedData = CharacterLocation.get(account, time);
    }

    if (cacheUpdate.cachedData == null || !cacheUpdate.cachedData.equivalent(locationUpdate)) {
      updates.add(locationUpdate);
      cacheUpdate.cachedData = locationUpdate;
      cacheMiss();
    } else {
      cacheHit();
    }

  }

  @Override
  protected void commitComplete() {
    // Update the character location cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CHAR_LOCATION, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCharacterLocation implements ModelCacheData {
    CharacterLocation cachedData;
  }

}
