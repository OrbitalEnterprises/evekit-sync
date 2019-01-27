package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWallets200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.AccountBalance;
import enterprises.orbital.evekit.model.common.Blueprint;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICorporationWalletBalanceSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdWallets200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationWalletBalanceSync.class.getName());

  private CachedCorporationWalletBalance cacheUpdate;

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

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdWallets200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    ESIThrottle.throttle(endpoint().name(), account);
    try {
      ApiResponse<List<GetCorporationsCorporationIdWallets200Ok>> result = apiInstance.getCorporationsCorporationIdWalletsWithHttpInfo(
          (int) account.getEveCorporationID(), null, null, accessToken());
      checkCommonProblems(result);
      return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                          result.getData());
    } catch (ApiException e) {
      final String errTrap = "Character does not have required role";
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        return new ESIAccountServerResult<>(expiry, Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdWallets200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    List<AccountBalance> toAdd = new ArrayList<>();
    for (GetCorporationsCorporationIdWallets200Ok balance : data.getData()) {
      toAdd.add(new AccountBalance(balance.getDivision(), BigDecimal.valueOf(balance.getBalance())
                                                                      .setScale(2, RoundingMode.HALF_UP)));
    }

    // Check against any existing cache
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CORP_WALLET_BALANCE);
    cacheUpdate = ref != null ? (CachedCorporationWalletBalance) ref.get() : null;
    if (cacheUpdate == null) {
      // If we don't have a cache yet, then create one from all active stored balances
      cacheInit();
      cacheUpdate = new CachedCorporationWalletBalance();
      for (AccountBalance next : retrieveAll(time,
                                        (long contid, AttributeSelector at) -> AccountBalance.accessQuery(account, contid,
                                                                                                     1000,
                                                                                                     false, at,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR))) {
        cacheUpdate.addBalance(next);
      }
    }

    // Now process latest balances.  Only update if something changed.
    for (AccountBalance next : toAdd) {
      if (cacheUpdate.cachedBalance.containsKey(next.getDivision())) {
        if (!cacheUpdate.cachedBalance.get(next.getDivision()).equivalent(next)) {
          // Data changed, add update and update cache
          cacheMiss();
          updates.add(next);
          cacheUpdate.addBalance(next);
        } else {
          // Nothing changed, cached value is still correct
          cacheHit();
        }
      } else {
        // New, add it and save in the cache
        cacheMiss();
        updates.add(next);
        cacheUpdate.addBalance(next);
      }
    }

  }

  @Override
  protected void commitComplete() {
    // Update the character sheet cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CORP_WALLET_BALANCE, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCorporationWalletBalance implements ModelCacheData {
    Map<Integer, AccountBalance> cachedBalance = new HashMap<>();

    void addBalance(AccountBalance b) {
      cachedBalance.put(b.getDivision(), b);
    }
  }

}
