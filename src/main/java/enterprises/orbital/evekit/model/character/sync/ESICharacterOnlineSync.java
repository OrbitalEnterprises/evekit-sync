package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.LocationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOnlineOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterOnline;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterOnlineSync extends AbstractESIAccountSync<GetCharactersCharacterIdOnlineOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterOnlineSync.class.getName());

  public ESICharacterOnlineSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_ONLINE;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterOnline;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CharacterOnline.get(account, time);
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdOnlineOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    LocationApi apiInstance = cp.getLocationApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdOnlineOk> result = apiInstance.getCharactersCharacterIdOnlineWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdOnlineOk> data,
                                   List<CachedData> updates) throws IOException {
    updates.add(new CharacterOnline(data.getData()
                                        .getOnline(),
                                    nullSafeDateTime(data.getData()
                                                         .getLastLogin(), new DateTime(new Date(0L))).getMillis(),
                                    nullSafeDateTime(data.getData()
                                                         .getLastLogout(), new DateTime(new Date(0L))).getMillis(),
                                    nullSafeInteger(data.getData()
                                                        .getLogins(), 0)));
  }


}
