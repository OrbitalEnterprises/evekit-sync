package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.AccountBalance;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;

public class CharacterAccountBalanceSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterAccountBalanceSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getAccountBalanceStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getAccountBalanceExpiry();
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncTracker.SyncState status, String detail) {
    tracker.setAccountBalanceStatus(status);
    tracker.setAccountBalanceDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setAccountBalanceExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof AccountBalance;

    AccountBalance api = (AccountBalance) item;
    AccountBalance existing = AccountBalance.get(accountKey, time, api.getAccountID());

    if (existing != null) {
      // Existing, evolve if changed
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
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestAccountBalance();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    IAccountBalance balanceResult = (IAccountBalance) data;
    updates.add(new AccountBalance(balanceResult.getAccountID(), balanceResult.getAccountKey(), balanceResult.getBalance().setScale(2, RoundingMode.HALF_UP)));
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterAccountBalanceSync syncher = new CharacterAccountBalanceSync();

  public static SyncStatus syncAccountBalance(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterAccountBalance");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAccountBalance", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAccountBalance", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
