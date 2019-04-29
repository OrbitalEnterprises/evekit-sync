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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterTitlesSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdTitles200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterTitlesSync.class.getName());

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

    // Check whether we have an ETAG to send for the skills call
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    try {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCharactersCharacterIdTitles200Ok>> result = apiInstance.getCharactersCharacterIdTitlesWithHttpInfo(
          (int) account.getEveCharacterID(), null, currentETag, accessToken());
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
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdTitles200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    // Queue updates for all current titles and record seen
    Set<Integer> seenTitles = new HashSet<>();
    for (GetCharactersCharacterIdTitles200Ok next : data.getData()) {
      updates.add(new CharacterTitle(nullSafeInteger(next.getTitleId(), 0),
                                     next.getName()));
      seenTitles.add(next.getTitleId());
    }

    // Remove any titles that no longer exist
    for (CharacterTitle next : CachedData.retrieveAll(time,
                                                      (contid, at) -> CharacterTitle.accessQuery(account, contid,
                                                                                                 1000, false, at,
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any()))) {
      if (!seenTitles.contains(next.getTitleID())) {
        next.evolve(null, time);
        updates.add(next);
      }
    }
  }

}
