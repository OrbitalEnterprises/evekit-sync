package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.WalletTransaction;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;
import enterprises.orbital.evexmlapi.shared.IWalletTransaction;

public class CorporationWalletTransactionSync extends AbstractCorporationSync {
  protected static final Logger log                 = Logger.getLogger(CorporationWalletTransactionSync.class.getName());

  // Subject to change, see EVE API docs
  public static final int       MAX_RECORD_DOWNLOAD = 2560;

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getWalletTransactionsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setWalletTransactionsStatus(status);
    tracker.setWalletTransactionsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setWalletTransactionsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getWalletTransactionsExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
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
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    Collection<IAccountBalance> accounts = corpRequest.requestAccountBalances();
    if (corpRequest.isError()) return null;
    Map<Integer, Collection<IWalletTransaction>> recordMap = new HashMap<Integer, Collection<IWalletTransaction>>();

    for (IAccountBalance nextAccount : accounts) {
      Collection<IWalletTransaction> allRecords = new ArrayList<IWalletTransaction>();
      recordMap.put(nextAccount.getAccountKey(), allRecords);

      // See CorporationWalletJournalSync for details on the processing method below.
      Collection<IWalletTransaction> records = corpRequest.requestWalletTransactions(nextAccount.getAccountKey());
      long minTransactionID = Long.MAX_VALUE;
      int recordCount = 0;

      // Walk forward. The API doesn't automatically terminate walk forward by sending empty rows. Instead, we'll need to detect that we've already seen the
      // highest transactionID and terminate that way.
      long localMaxTransactionID = Long.MIN_VALUE;
      long lastMaxTransactionID = 0;
      long walkForwardCount = 0;

      if (corpRequest.isError()) return null;

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
        records = corpRequest.requestWalletTransactions(nextAccount.getAccountKey());

        if (corpRequest.isError()) return null;
      }

      if (localMaxTransactionID == lastMaxTransactionID) {
        log.fine("Terminated forward walk due to max ID reached");
      }

      log.fine("Walked forward and discovered " + walkForwardCount + " entries");

      // Now attempt to walk backward
      long walkBackwardCount = 0;
      if (recordCount < MAX_RECORD_DOWNLOAD) {
        log.fine("Start backward walk from ref " + minTransactionID);
        records = corpRequest.requestWalletTransactions(nextAccount.getAccountKey(), minTransactionID);

        if (corpRequest.isError()) return null;

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
          records = corpRequest.requestWalletTransactions(nextAccount.getAccountKey(), minTransactionID);

          if (corpRequest.isError()) return null;
        }

        log.fine("Walked backward and populated " + walkBackwardCount + " entries");
      } else {
        log.fine("Backward walk skipped due to max records reached");
      }
    }

    log.fine("Corp wallet transaction record retrieval complete.");
    return recordMap;
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Map<Integer, Collection<IWalletTransaction>> recordMap = (Map<Integer, Collection<IWalletTransaction>>) data;

    for (Entry<Integer, Collection<IWalletTransaction>> nextRecord : recordMap.entrySet()) {

      int accountKey = nextRecord.getKey();
      Collection<IWalletTransaction> allRecords = nextRecord.getValue();
      for (IWalletTransaction next : allRecords) {

        // Populate record
        WalletTransaction newRecord = new WalletTransaction(
            accountKey, next.getTransactionID(), next.getTransactionDateTime().getTime(), (int) next.getQuantity(), next.getTypeName(), (int) next.getTypeID(),
            next.getPrice().setScale(2, RoundingMode.HALF_UP), next.getClientID(), next.getClientName(), (int) next.getStationID(), next.getStationName(),
            next.getTransactionType(), next.getTransactionFor(), next.getJournalTransactionID());
        updates.add(newRecord);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationWalletTransactionSync syncher = new CorporationWalletTransactionSync();

  public static SyncStatus syncCorporationWalletTransaction(
                                                            long time,
                                                            SynchronizedEveAccount syncAccount,
                                                            SynchronizerUtil syncUtil,
                                                            ICorporationAPI corpRequest) {
    SyncStatus result = syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationWalletTransaction");
    return result;
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationWalletTransaction", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationWalletTransaction", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
