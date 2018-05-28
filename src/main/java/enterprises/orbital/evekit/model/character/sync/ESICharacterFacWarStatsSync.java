package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.FactionWarfareApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdFwStatsOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.FacWarStats;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterFacWarStatsSync extends AbstractESIAccountSync<GetCharactersCharacterIdFwStatsOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterFacWarStatsSync.class.getName());

  public ESICharacterFacWarStatsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_FACTION_WAR;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof FacWarStats;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = FacWarStats.get(account, time);
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdFwStatsOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    FactionWarfareApi apiInstance = cp.getFactionWarfareApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdFwStatsOk> result = apiInstance.getCharactersCharacterIdFwStatsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdFwStatsOk> data,
                                   List<CachedData> updates) throws IOException {
    updates.add(new FacWarStats(nullSafeInteger(data.getData()
                                                    .getCurrentRank(), 0),
                                nullSafeDateTime(data.getData()
                                                     .getEnlistedOn(), new DateTime(new Date(0))).getMillis(),
                                nullSafeInteger(data.getData()
                                                    .getFactionId(), 0),
                                nullSafeInteger(data.getData()
                                                    .getHighestRank(), 0),
                                data.getData()
                                    .getKills()
                                    .getLastWeek(),
                                data.getData()
                                    .getKills()
                                    .getTotal(),
                                data.getData()
                                    .getKills()
                                    .getYesterday(),
                                0,
                                data.getData()
                                    .getVictoryPoints()
                                    .getLastWeek(),
                                data.getData()
                                    .getVictoryPoints()
                                    .getTotal(),
                                data.getData()
                                    .getVictoryPoints()
                                    .getYesterday()));
  }


}
