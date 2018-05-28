package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWallets200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.AccountBalance;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

public class ESICorporationWalletBalanceSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdWallets200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationWalletBalanceSync.class.getName());

  public ESICorporationWalletBalanceSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_WALLET_BALANCE;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof AccountBalance;
    AccountBalance api = (AccountBalance) item;
    AccountBalance existing = AccountBalance.get(account, time, api.getDivision());
    evolveOrAdd(time, existing, api);
  }

  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdWallets200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCorporationsCorporationIdWallets200Ok>> result = apiInstance.getCorporationsCorporationIdWalletsWithHttpInfo(
        (int) account.getEveCorporationID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdWallets200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    for (GetCorporationsCorporationIdWallets200Ok balance : data.getData()) {
      updates.add(new AccountBalance(balance.getDivision(), BigDecimal.valueOf(balance.getBalance())
                                                                      .setScale(2, RoundingMode.HALF_UP)));
    }
  }


}
