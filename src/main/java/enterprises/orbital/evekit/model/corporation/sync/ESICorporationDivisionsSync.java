package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsHangarHangar;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsWalletWallet;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Division;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class ESICorporationDivisionsSync extends AbstractESIAccountSync<GetCorporationsCorporationIdDivisionsOk> {
  protected static final Logger log = Logger.getLogger(ESICorporationDivisionsSync.class.getName());

  public ESICorporationDivisionsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_DIVISIONS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Division;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = Division.get(account, time, ((Division) item).isWallet(), ((Division) item).getDivision());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCorporationsCorporationIdDivisionsOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCorporationsCorporationIdDivisionsOk> result = apiInstance.getCorporationsCorporationIdDivisionsWithHttpInfo(
        (int) account.getEveCorporationID(),
        null,
        null,
        accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCorporationsCorporationIdDivisionsOk> data,
                                   List<CachedData> updates) throws IOException {
    // Update wallet divisions
    for (GetCorporationsCorporationIdDivisionsHangarHangar next : data.getData()
                                                                      .getHangar()) {
      updates.add(new Division(false,
                               nullSafeInteger(next.getDivision(), 0),
                               next.getName()));
    }

    // Update hangar divisions
    for (GetCorporationsCorporationIdDivisionsWalletWallet next : data.getData()
                                                                      .getWallet()) {
      updates.add(new Division(true,
                               nullSafeInteger(next.getDivision(), 0),
                               next.getName()));
    }
  }

}
