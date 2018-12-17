package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdTitles200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterTitle;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ESICharacterTitlesSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdTitles200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterTitlesSync.class.getName());

  private CachedCharacterTitles cacheUpdate;

  public ESICharacterTitlesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_TITLES;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterTitle;
    CachedData existing = CharacterTitle.get(account, time, ((CharacterTitle) item).getTitleID());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdTitles200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdTitles200Ok>> result = apiInstance.getCharactersCharacterIdTitlesWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdTitles200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    Map<Integer, CharacterTitle> seenTitles = new HashMap<>();
    for (GetCharactersCharacterIdTitles200Ok next : data.getData()) {
      CharacterTitle nextTitle = new CharacterTitle(nullSafeInteger(next.getTitleId(), 0),
                                     next.getName());
      seenTitles.put(nextTitle.getTitleID(), nextTitle);
    }

    // Only queue update if something has changed.
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CHAR_TITLES);
    cacheUpdate = ref != null ? (CachedCharacterTitles) ref.get() : null;
    if (cacheUpdate == null) {
      // No cache yet, populate from latest character title list
      cacheInit();
      cacheUpdate = new CachedCharacterTitles();
          for (CharacterTitle stored : CachedData.retrieveAll(time,
                                                        (contid, at) -> CharacterTitle.accessQuery(account, contid,
                                                                                                   1000, false, at,
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any()))) {
            cacheUpdate.titleMap.put(stored.getTitleID(), stored);
          }
    }

    // Three cases:
    // 1) titles which no longer exist should be removed
    // 2) titles which have changed should be updated
    // 3) titles which are new should be added

    for (Integer titleID : cacheUpdate.titleMap.keySet()) {
      if (!seenTitles.containsKey(titleID)) {
        // Updated changed title
        CharacterTitle toRemove = cacheUpdate.titleMap.get(titleID);
        toRemove.evolve(null, time);
        updates.add(toRemove);
      } else {
        // check for and update changed title
        CharacterTitle existing = cacheUpdate.titleMap.get(titleID);
        CharacterTitle update = seenTitles.get(titleID);
        if (!existing.equivalent(update)) {
          updates.add(update);
          cacheMiss();
        } else {
          cacheHit();
        }
      }
    }

    for (Integer titleID : seenTitles.keySet()) {
      if (!cacheUpdate.titleMap.containsKey(titleID)) {
        // New title, add
        updates.add(seenTitles.get(titleID));
        cacheMiss();
      }
    }

    // Regardless, the cache will become the latest tiles when we're done.
    cacheUpdate.titleMap = seenTitles;
  }

  @Override
  protected void commitComplete() {
    // Update the character titles cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CHAR_TITLES, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCharacterTitles implements ModelCacheData {
    Map<Integer, CharacterTitle> titleMap = new HashMap<>();
  }


}
