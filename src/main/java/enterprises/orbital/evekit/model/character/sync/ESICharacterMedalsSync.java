package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMedals200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMedalsGraphic;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterMedal;
import enterprises.orbital.evekit.model.character.CharacterMedalGraphic;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterMedalsSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdMedals200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterMedalsSync.class.getName());

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

  public ESICharacterMedalsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_MEDALS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CharacterMedal) || (item instanceof CharacterMedalGraphic);
    // Medals are immutable so we can skip the update if they already exist
    if (item instanceof CharacterMedal &&
        CharacterMedal.get(account, time, ((CharacterMedal) item).getMedalID(),
                           ((CharacterMedal) item).getIssued()) != null) {
      return;
    }
    if (item instanceof CharacterMedalGraphic &&
        CharacterMedalGraphic.get(account, time,
                                  ((CharacterMedalGraphic) item).getMedalID(),
                                  ((CharacterMedalGraphic) item).getIssued(),
                                  ((CharacterMedalGraphic) item).getPart(),
                                  ((CharacterMedalGraphic) item).getLayer()) != null) {
      return;
    }

    evolveOrAdd(time, null, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdMedals200Ok>> getServerData(
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
      ApiResponse<List<GetCharactersCharacterIdMedals200Ok>> result = apiInstance.getCharactersCharacterIdMedalsWithHttpInfo(
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
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdMedals200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    for (GetCharactersCharacterIdMedals200Ok next : data.getData()) {
      updates.add(new CharacterMedal(next.getDescription(),
                                     next.getMedalId(),
                                     next.getTitle(),
                                     next.getCorporationId(),
                                     next.getDate()
                                         .getMillis(),
                                     next.getIssuerId(),
                                     next.getReason(),
                                     next.getStatus()
                                         .toString()));
      for (GetCharactersCharacterIdMedalsGraphic grf : next.getGraphics()) {
        updates.add(new CharacterMedalGraphic(next.getMedalId(),
                                              next.getDate()
                                                  .getMillis(),
                                              grf.getPart(),
                                              grf.getLayer(),
                                              grf.getGraphic(),
                                              nullSafeInteger(grf.getColor(), 0)));
      }
    }

  }

}
