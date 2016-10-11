package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.BigDecimal;
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
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.WalletJournal;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;
import enterprises.orbital.evexmlapi.shared.IWalletJournalEntry;

public class CorporationWalletJournalSync extends AbstractCorporationSync {
  protected static final Logger log                 = Logger.getLogger(CorporationWalletJournalSync.class.getName());

  // Subject to change, see EVE API docs
  public static final int       MAX_RECORD_DOWNLOAD = 2560;

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getWalletJournalStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setWalletJournalStatus(status);
    tracker.setWalletJournalDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setWalletJournalExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getWalletJournalExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof WalletJournal;
    WalletJournal api = (WalletJournal) item;

    if (WalletJournal.get(accountKey, time, api.getAccountKey(), api.getRefID()) != null) {
      // This item already exists. We don't need to check if it's changed because journal entries are immutable.
      return true;
    }

    // This is the first time we've seen this item so create it.
    api.setup(accountKey, time);
    super.commit(time, tracker, container, accountKey, api);

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICorporationAPI corpRequest)
    throws IOException {
    // Retrieve accounts to query.
    Collection<IAccountBalance> accounts = corpRequest.requestAccountBalances();
    if (corpRequest.isError()) return null;
    Map<Integer, Collection<IWalletJournalEntry>> recordMap = new HashMap<Integer, Collection<IWalletJournalEntry>>();

    // The EVE api docs are a little sketchy here. This is how things seem to work right now. First, the journal API provides at most one month of data. So no
    // matter what, you can't scan backwards further than that. Second, the journal API limits the maximum number of entries that will be returned until the
    // cache timer expires. That maximum is currently 2560.
    //
    // When you call the API without a "fromID", you'll get some set of transactions which may or may not be the latest (usually not for characters with lots of
    // transactions). Now you have a choice: if you call the API without a "fromID" again you will "walk forward" and get the next set of transactions moving
    // closer to the latest transaction. If you specify a "fromID", you will "walk backward" and get older transactions. If you're already at the most recent
    // transactions, then calling without a "fromID" may give you exactly the same transactions again. That is, walking forward does not result in an empty list
    // when you've completed the walk.

    // No great solution here. Our strategy is as follows:
    //
    // 1) Walk forward counting the number of entries we add (since we know the max is 2560).
    //
    // 2) Once we exhaust walking forward, if we think we can still retrieve new transactions, then walk backward starting from the first refID we saw when we
    // walked forward.
    //
    // We may also need to provide a convenience method on CorporationWalletJournal which allows the caller to attempt to fill in gaps in transactions.

    for (IAccountBalance nextAccount : accounts) {
      Collection<IWalletJournalEntry> allRecords = new ArrayList<IWalletJournalEntry>();
      recordMap.put(nextAccount.getAccountKey(), allRecords);

      // Start walk
      Collection<IWalletJournalEntry> records = corpRequest.requestWalletJournalEntries(nextAccount.getAccountKey());
      long minRefID = Long.MAX_VALUE;
      int recordCount = 0;

      // Walk forward. The API doesn't automatically terminate walk forward by sending empty rows. Instead, we'll need to detect that we've already seen the
      // highest refID and terminate that way.
      long localMaxRefID = Long.MIN_VALUE;
      long lastMaxRefID = 0;
      long walkForwardCount = 0;

      if (corpRequest.isError()) return null;

      while (records != null && records.size() > 0 && (localMaxRefID != lastMaxRefID) && recordCount < MAX_RECORD_DOWNLOAD) {
        lastMaxRefID = localMaxRefID;
        localMaxRefID = Long.MIN_VALUE;

        for (IWalletJournalEntry next : records) {
          // Track the min, max and record count
          if (next.getRefID() < minRefID) {
            minRefID = next.getRefID();
          }
          if (next.getRefID() > localMaxRefID) {
            localMaxRefID = next.getRefID();
          }

          recordCount++;
          walkForwardCount++;
          allRecords.add(next);

        }

        // walk forward
        records = corpRequest.requestWalletJournalEntries(nextAccount.getAccountKey());

        if (corpRequest.isError()) return null;
      }

      if (localMaxRefID == lastMaxRefID) {
        log.fine("Terminated forward walk due to max ID reached");
      }

      log.fine("Walked forward and discovered " + walkForwardCount + " entries");

      // Now attempt to walk backward
      long walkBackwardCount = 0;
      if (recordCount < MAX_RECORD_DOWNLOAD) {
        log.fine("Start backward walk from ref " + minRefID);
        records = corpRequest.requestWalletJournalEntries(nextAccount.getAccountKey(), minRefID);
        if (corpRequest.isError()) return null;

        while (records.size() > 0 && recordCount < MAX_RECORD_DOWNLOAD) {

          for (IWalletJournalEntry next : records) {
            // Setup next min.
            if (next.getRefID() < minRefID) {
              minRefID = next.getRefID();
            }

            recordCount++;
            walkBackwardCount++;
            allRecords.add(next);
          }

          // Walk backward
          records = corpRequest.requestWalletJournalEntries(nextAccount.getAccountKey(), minRefID);

          if (corpRequest.isError()) return null;
        }

        log.fine("Walked backward and discovered " + walkBackwardCount + " entries");
      } else {
        log.fine("Backward walk skipped due to max records reached");
      }
    }

    log.fine("Corp wallet journal record retrieval complete.");
    return recordMap;
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Map<Integer, Collection<IWalletJournalEntry>> recordMap = (Map<Integer, Collection<IWalletJournalEntry>>) data;

    for (Entry<Integer, Collection<IWalletJournalEntry>> nextRecord : recordMap.entrySet()) {
      int accountKey = nextRecord.getKey();
      Collection<IWalletJournalEntry> allRecords = nextRecord.getValue();
      for (IWalletJournalEntry next : allRecords) {

        // Populate record
        WalletJournal newRecord = new WalletJournal(
            accountKey, next.getRefID(), ModelUtil.safeConvertDate(next.getDate()), next.getRefTypeID(), next.getOwnerName1(), next.getOwnerID1(),
            next.getOwnerName2(), next.getOwnerID2(), next.getArgName1(), next.getArgID1(), next.getAmount().setScale(2, RoundingMode.HALF_UP),
            next.getBalance().setScale(2, RoundingMode.HALF_UP), next.getReason(), next.getTaxReceiverID(),
            next.getTaxAmount() != null ? next.getTaxAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        updates.add(newRecord);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationWalletJournalSync syncher = new CorporationWalletJournalSync();

  public static SyncStatus syncCorporationWalletJournal(
                                                        long time,
                                                        SynchronizedEveAccount syncAccount,
                                                        SynchronizerUtil syncUtil,
                                                        ICorporationAPI corpRequest) {
    SyncStatus result = syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationWalletJournal");
    return result;
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationWalletJournal", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationWalletJournal", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
