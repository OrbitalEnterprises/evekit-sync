package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.AccountBalance;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterWalletBalanceSync extends AbstractESIAccountSync<Double> {
  protected static final Logger log = Logger.getLogger(ESICharacterWalletBalanceSync.class.getName());

  public ESICharacterWalletBalanceSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_WALLET_BALANCE;
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
  protected ESIAccountServerResult<Double> getServerData(ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<Double> result = apiInstance.getCharactersCharacterIdWalletWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<Double> data,
                                   List<CachedData> updates) throws IOException {
    updates.add(new AccountBalance(1, BigDecimal.valueOf(data.getData())
                                                .setScale(2, RoundingMode.HALF_UP)));
  }


}
