package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.AccountBalance;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterWalletBalanceSync extends AbstractESIAccountSync<Double> {
  protected static final Logger log = Logger.getLogger(ESICharacterWalletBalanceSync.class.getName());

  private CachedCharacterWalletBalance cacheUpdate;

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
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<Double> data,
                                   List<CachedData> updates) throws IOException {
    AccountBalance newBalance = new AccountBalance(1, BigDecimal.valueOf(data.getData())
                                                .setScale(2, RoundingMode.HALF_UP));

    // Check against cache, if it exists
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CHAR_WALLET_BALANCE);
    cacheUpdate = ref != null ? (CachedCharacterWalletBalance) ref.get() : null;
    if (cacheUpdate == null) {
      // No cache yet, populate from latest stored balance
      cacheUpdate = new CachedCharacterWalletBalance();
      cacheUpdate.cachedBalance = AccountBalance.get(account, time, 1);
    }

    if (cacheUpdate.cachedBalance == null || !cacheUpdate.cachedBalance.equivalent(newBalance)) {
      updates.add(newBalance);
      cacheUpdate.cachedBalance = newBalance;
      cacheMiss();
    } else {
      cacheHit();
    }
  }

  @Override
  protected void commitComplete() {
    // Update the character sheet cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CHAR_WALLET_BALANCE, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCharacterWalletBalance implements ModelCacheData {
    AccountBalance cachedBalance;
  }
}
