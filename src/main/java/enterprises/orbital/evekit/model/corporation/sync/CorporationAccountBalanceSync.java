package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.AccountBalance;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;

public class CorporationAccountBalanceSync extends AbstractCorporationSync {
  protected static final Logger log = Logger.getLogger(CorporationAccountBalanceSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getAccountBalanceStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getAccountBalanceExpiry();
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncTracker.SyncState status, String detail) {
    tracker.setAccountBalanceStatus(status);
    tracker.setAccountBalanceDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setAccountBalanceExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof AccountBalance;

    AccountBalance api = (AccountBalance) item;
    AccountBalance existing = AccountBalance.get(accountKey, time, api.getAccountID());

    if (existing != null) {
      if (!existing.equivalent(api)) {
        // Evolve
        existing.evolve(api, time);
        super.commit(time, tracker, container, accountKey, existing);
        super.commit(time, tracker, container, accountKey, api);
      }
    } else {
      // New entity
      api.setup(accountKey, time);
      super.commit(time, tracker, container, accountKey, api);
    }

    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestAccountBalances();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {

    @SuppressWarnings("unchecked")
    Collection<IAccountBalance> balances = (Collection<IAccountBalance>) data;

    for (IAccountBalance next : balances) {
      updates.add(new AccountBalance(next.getAccountID(), next.getAccountKey(), next.getBalance().setScale(2, RoundingMode.HALF_UP)));
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationAccountBalanceSync syncher = new CorporationAccountBalanceSync();

  public static SyncStatus syncAccountBalance(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationAccountBalance");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationAccountBalance", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationAccountBalance", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
