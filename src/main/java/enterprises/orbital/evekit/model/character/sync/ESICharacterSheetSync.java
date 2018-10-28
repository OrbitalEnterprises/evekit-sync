package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheet;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterSheetSync extends AbstractESIAccountSync<GetCharactersCharacterIdOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterSheetSync.class.getName());
  private long corporationID;
  private String corporationName;
  private CachedCharacterSheet cacheUpdate;

  public ESICharacterSheetSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_SHEET;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterSheet;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CharacterSheet.get(account, time);
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdOk> result = apiInstance.getCharactersCharacterIdWithHttpInfo(
        (int) account.getEveCharacterID(), null, null);
    checkCommonProblems(result);

    // Also cache corporation ID and name in case these changed
    corporationID = result.getData().getCorporationId();
    ApiResponse<GetCorporationsCorporationIdOk> corpResult = cp.getCorporationApi().getCorporationsCorporationIdWithHttpInfo(
        (int) corporationID,
        null,
        null);
    checkCommonProblems(corpResult);
    corporationName = corpResult.getData().getName();

    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdOk> data,
                                   List<CachedData> updates) throws IOException {
    cacheUpdate = null;

    // Add update for processing
    CharacterSheet newSheet = new CharacterSheet(account.getEveCharacterID(),
                                                                 data.getData()
                                                                     .getName(),
                                                                 data.getData()
                                                                     .getCorporationId(),
                                                                 data.getData()
                                                                     .getRaceId(),
                                                                 data.getData()
                                                                     .getBirthday()
                                                                     .getMillis(),
                                                                 data.getData()
                                                                     .getBloodlineId(),
                                                                 nullSafeInteger(data.getData()
                                                                                     .getAncestryId(), 0),
                                                                 data.getData()
                                                                     .getGender()
                                                                     .toString(),
                                                                 nullSafeInteger(data.getData()
                                                                                     .getAllianceId(), 0),
                                                                 nullSafeInteger(data.getData()
                                                                                     .getFactionId(), 0),
                                                                 data.getData()
                                                                     .getDescription(),
                                                                 nullSafeFloat(data.getData()
                                                                                   .getSecurityStatus(), 0F));

    // Check whether the corporation has changed.  If so, update the SynchronizedEveAccount
    if (account.getEveCorporationID() != corporationID) {
      // Update account which includes updating the corporation name
      account.setEveCorporationID(corporationID);
      account.setEveCorporationName(corporationName);
      account = SynchronizedEveAccount.update(account);
    }

    // Only queue update if something has changed.
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CHAR_SHEET);
    @SuppressWarnings("ConstantConditions")
    CharacterSheet existing = ref != null ? ((CachedCharacterSheet) ref.get()).cachedData : null;
    if (existing == null || !existing.equivalent(newSheet)) {
      updates.add(newSheet);
      cacheUpdate = new CachedCharacterSheet(newSheet);
      if (existing != null) cacheMiss();
    } else {
      cacheHit();
    }
  }

  @Override
  protected void commitComplete() {
    // Update the character sheet cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CHAR_SHEET, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCharacterSheet implements ModelCacheData {
    CharacterSheet cachedData;

    CachedCharacterSheet(CharacterSheet source) {
      cachedData = new CharacterSheet(source.getCharacterID(),
                                                   source.getName(),
                                                   source.getCorporationID(),
                                                   source.getRaceID(),
                                                   source.getDoB(),
                                                   source.getBloodlineID(),
                                                   source.getAncestryID(),
                                                   source.getGender(),
                                                   source.getAllianceID(),
                                                   source.getFactionID(),
                                                   source.getDescription(),
                                                   source.getSecurityStatus());
    }
  }

}
