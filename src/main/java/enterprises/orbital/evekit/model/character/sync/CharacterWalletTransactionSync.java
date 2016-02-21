package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.WalletTransaction;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IWalletTransaction;

public class CharacterWalletTransactionSync extends AbstractCharacterSync {
  protected static final Logger log                 = Logger.getLogger(CharacterWalletTransactionSync.class.getName());

  // Subject to change, see EVE API docs
  public static final int       MAX_RECORD_DOWNLOAD = 2560;

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getWalletTransactionsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setWalletTransactionsStatus(status);
    tracker.setWalletTransactionsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setWalletTransactionsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getWalletTransactionsExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof WalletTransaction;

    WalletTransaction api = (WalletTransaction) item;

    if (WalletTransaction.get(accountKey, time, api.getTransactionID()) != null) {
      // This item already exists. We don't need to check if it's changed because wallet transactions are immutable.
      return true;
    }

    // This is the first time we've seen this item so create it
    api.setup(accountKey, time);
    super.commit(time, tracker, container, accountKey, api);

    return true;
  }

  @Override
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    Collection<IWalletTransaction> allRecords = new ArrayList<IWalletTransaction>();

    // See CharacterWalletJournalSync for details on the processing method below.
    Collection<IWalletTransaction> records = charRequest.requestWalletTransactions();
    long minTransactionID = Long.MAX_VALUE;
    int recordCount = 0;

    // Walk forward. The API doesn't automatically terminate walk forward by sending empty rows. Instead, we'll need to detect that we've already seen the
    // highest transactionID and terminate that way.
    long localMaxTransactionID = Long.MIN_VALUE;
    long lastMaxTransactionID = 0;
    long walkForwardCount = 0;

    if (charRequest.isError()) return null;

    while (records != null && records.size() > 0 && (localMaxTransactionID != lastMaxTransactionID) && recordCount < MAX_RECORD_DOWNLOAD) {
      lastMaxTransactionID = localMaxTransactionID;
      localMaxTransactionID = Long.MIN_VALUE;

      for (IWalletTransaction next : records) {
        // Track the min, max and record count
        if (next.getTransactionID() < minTransactionID) {
          minTransactionID = next.getTransactionID();
        }
        if (next.getTransactionID() > localMaxTransactionID) {
          localMaxTransactionID = next.getTransactionID();
        }

        recordCount++;
        walkForwardCount++;
        allRecords.add(next);

      }

      // walk forward
      records = charRequest.requestWalletTransactions();

      if (charRequest.isError()) return null;
    }

    if (localMaxTransactionID == lastMaxTransactionID) {
      log.fine("Terminated forward walk due to max ID reached");
    }

    log.fine("Walked forward and discovered " + walkForwardCount + " entries");

    // Now attempt to walk backward
    long walkBackwardCount = 0;
    if (recordCount < MAX_RECORD_DOWNLOAD) {
      log.fine("Start backward walk from ref " + minTransactionID);
      records = charRequest.requestWalletTransactions(minTransactionID);

      if (charRequest.isError()) return null;

      while (records.size() > 0 && recordCount < MAX_RECORD_DOWNLOAD) {

        for (IWalletTransaction next : records) {
          // Setup next min.
          if (next.getTransactionID() < minTransactionID) {
            minTransactionID = next.getTransactionID();
          }

          recordCount++;
          walkBackwardCount++;
          allRecords.add(next);

        }

        // Walk backward
        records = charRequest.requestWalletTransactions(minTransactionID);

        if (charRequest.isError()) return null;
      }

      log.fine("Walked backward and populated " + walkBackwardCount + " entries");
    } else {
      log.fine("Backward walk skipped due to max records reached");
    }

    return allRecords;
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IWalletTransaction> allRecords = (Collection<IWalletTransaction>) data;

    for (IWalletTransaction next : allRecords) {
      // Populate record
      WalletTransaction newRecord = new WalletTransaction(
          1000, next.getTransactionID(), next.getTransactionDateTime().getTime(), (int) next.getQuantity(), next.getTypeName(), (int) next.getTypeID(),
          next.getPrice().setScale(2, RoundingMode.HALF_UP), next.getClientID(), next.getClientName(), (int) next.getStationID(), next.getStationName(),
          next.getTransactionType(), next.getTransactionFor(), next.getJournalTransactionID());
      updates.add(newRecord);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterWalletTransactionSync syncher = new CharacterWalletTransactionSync();

  public static SyncStatus syncCharacterWalletTransaction(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    SyncStatus result = syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterWalletTransaction");
    return result;
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterWalletTransaction", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterWalletTransaction", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
