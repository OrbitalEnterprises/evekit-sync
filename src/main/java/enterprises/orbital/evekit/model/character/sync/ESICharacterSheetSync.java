package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheet;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterSheetSync extends AbstractESIAccountSync<GetCharactersCharacterIdOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterSheetSync.class.getName());

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
  protected ESIAccountServerResult<GetCharactersCharacterIdOk> getServerData(ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdOk> result = apiInstance.getCharactersCharacterIdWithHttpInfo((int) account.getEveCharacterID(), null, null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()), result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdOk> data,
                                   List<CachedData> updates) throws IOException {
    updates.add(new CharacterSheet(account.getEveCharacterID(),
                                   data.getData().getName(),
                                   data.getData().getCorporationId(),
                                   data.getData().getRaceId(),
                                   data.getData().getBirthday().getMillis(),
                                   data.getData().getBloodlineId(),
                                   nullSafeInteger(data.getData().getAncestryId(), 0),
                                   data.getData().getGender().toString(),
                                   nullSafeInteger(data.getData().getAllianceId(), 0),
                                   nullSafeInteger(data.getData().getFactionId(), 0),
                                   data.getData().getDescription(),
                                   nullSafeFloat(data.getData().getSecurityStatus(), 0F)));
  }


}
