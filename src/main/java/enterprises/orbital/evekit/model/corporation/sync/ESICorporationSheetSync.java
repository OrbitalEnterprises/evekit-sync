package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdIconsOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CorporationSheet;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICorporationSheetSync extends AbstractESIAccountSync<ESICorporationSheetSync.CorporationData> {
  protected static final Logger log = Logger.getLogger(ESICorporationSheetSync.class.getName());

  static class CorporationData {
    GetCorporationsCorporationIdOk sheet;
    GetCorporationsCorporationIdIconsOk icons;
  }

  public ESICorporationSheetSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_SHEET;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CorporationSheet;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CorporationSheet.get(account, time);
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<CorporationData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationData data = new CorporationData();
    CorporationApi apiInstance = cp.getCorporationApi();

    long expiry;
    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<GetCorporationsCorporationIdOk> result = apiInstance.getCorporationsCorporationIdWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null);
      checkCommonProblems(result);
      data.sheet = result.getData();
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<GetCorporationsCorporationIdIconsOk> result = apiInstance.getCorporationsCorporationIdIconsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null);
      checkCommonProblems(result);
      data.icons = result.getData();
      expiry = Math.max(expiry, extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<CorporationData> data,
                                   List<CachedData> updates) throws IOException {
    updates.add(new CorporationSheet(nullSafeInteger(data.getData().sheet.getAllianceId(), 0),
                                     data.getData().sheet.getCeoId(),
                                     account.getEveCorporationID(),
                                     data.getData().sheet.getName(),
                                     data.getData().sheet.getDescription(),
                                     data.getData().sheet.getMemberCount(),
                                     nullSafeLong(data.getData().sheet.getShares(), 0),
                                     nullSafeInteger(data.getData().sheet.getHomeStationId(), 0),
                                     data.getData().sheet.getTaxRate(),
                                     data.getData().sheet.getTicker(),
                                     data.getData().sheet.getUrl(),
                                     nullSafeDateTime(data.getData().sheet.getDateFounded(),
                                                      new DateTime(new Date(0))).getMillis(),
                                     data.getData().sheet.getCreatorId(),
                                     nullSafeInteger(data.getData().sheet.getFactionId(), 0),
                                     data.getData().icons.getPx64x64(),
                                     data.getData().icons.getPx128x128(),
                                     data.getData().icons.getPx256x256(),
                                     data.getData().sheet.getWarEligible()));
  }


}
