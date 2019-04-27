package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdFatigueOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheetJump;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterSheetJumpSync extends AbstractESIAccountSync<GetCharactersCharacterIdFatigueOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterSheetJumpSync.class.getName());

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

  public ESICharacterSheetJumpSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_FATIGUE;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterSheetJump;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CharacterSheetJump.get(account, time);
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdFatigueOk> getServerData(
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
      ApiResponse<GetCharactersCharacterIdFatigueOk> result = apiInstance.getCharactersCharacterIdFatigueWithHttpInfo(
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
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdFatigueOk> data,
                                   List<CachedData> updates) throws IOException {
    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    updates.add(new CharacterSheetJump(nullSafeDateTime(data.getData()
                                                            .getLastJumpDate(), new DateTime(new Date(0))).getMillis(),
                                       nullSafeDateTime(data.getData()
                                                            .getJumpFatigueExpireDate(),
                                                        new DateTime(new Date(0))).getMillis(),
                                       nullSafeDateTime(data.getData()
                                                            .getLastUpdateDate(),
                                                        new DateTime(new Date(0))).getMillis()));
  }


}
