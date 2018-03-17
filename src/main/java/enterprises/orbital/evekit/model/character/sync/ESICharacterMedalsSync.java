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
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdMedals200Ok>> result = apiInstance.getCharactersCharacterIdMedalsWithHttpInfo(
        (int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdMedals200Ok>> data,
                                   List<CachedData> updates) throws IOException {
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
