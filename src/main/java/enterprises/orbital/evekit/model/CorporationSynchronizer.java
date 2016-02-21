package enterprises.orbital.evekit.model;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.sync.CorporationAccountBalanceSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationAssetsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationBlueprintsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationBookmarksSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationContactListSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationContainerLogSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationContractBidsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationContractItemsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationContractsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationCustomsOfficesSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationFacWarStatsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationFacilitiesSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationIndustryJobsHistorySync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationIndustryJobsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationKillLogSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationMarketOrderSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationMedalsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationMemberMedalsSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationMemberSecurityLogSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationMemberSecuritySync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationMemberTrackingSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationOutpostListSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationOutpostServiceDetailSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationShareholdersSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationSheetSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationStandingSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationStarbaseDetailSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationStarbaseListSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationTitlesSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationWalletJournalSync;
import enterprises.orbital.evekit.model.corporation.sync.CorporationWalletTransactionSync;
import enterprises.orbital.evexmlapi.IEveXmlApi;
import enterprises.orbital.evexmlapi.act.IAPIKeyInfo;
import enterprises.orbital.evexmlapi.act.IAccountAPI;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;

/**
 * Synchronize a corporation account.
 */
public class CorporationSynchronizer extends AbstractSynchronizer {
  protected static final Logger log = Logger.getLogger(CorporationSynchronizer.class.getName());

  // Class which encapsulates sync state execution
  public static interface CorpStateHandler extends AbstractSynchronizer.StateHandler {
    public SyncStatus sync(long syncTime, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest, IAccountAPI acctRequest);
  }

  // Support synchronization features (see bottom of file for initialization)
  public static final Map<SynchronizationState, CorpStateHandler> supportedFeatures = new HashMap<SynchronizationState, CorpStateHandler>();

  /**
   * {@inheritDoc}
   */
  @Override
  public void synchronize(SynchronizedEveAccount syncAccount) throws IOException, URISyntaxException {
    log.fine("Starting sync: " + syncAccount);
    // Steps:
    // 1) Verify the API key is not expired. Synchronization is skipped if the key is expired.
    IEveXmlApi apiHandle = getApiHandle();
    IAccountAPI acctRequest = apiHandle.getAccountAPIService(syncAccount.getEveKey(), syncAccount.getEveVCode());
    ICorporationAPI corpRequest = apiHandle.getCorporationAPIService(syncAccount.getEveKey(), syncAccount.getEveVCode(), syncAccount.getEveCharacterID());
    IAPIKeyInfo keyInfo = getKeyInfo(acctRequest);
    if (!verifyNotExpired(keyInfo)) return;
    // 2) Verify the account is active. Synchronization is skipped if the account is not active or marked for delete.
    if (!verifyActiveAndNotDeleted(syncAccount)) return;
    // 3) Verify the account is not stuck. If an unfinished synchronizer exists but has been open for too long. Then it is finished immediately.
    if (!verifyTrackerNotStuck(syncAccount)) return;
    // 4) Verify enough time has passed since the last synchronization. If not enough time has passed, then synchronization is skipped.
    if (!verifyTrackerSeparation(syncAccount)) return;
    // 5) Create a new tracker if it's time for this account to be synched.
    CorporationSyncTracker tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);
    if (tracker.getSyncStart() <= 0) {
      // Tracker isn't started yet so start it.
      tracker.setSyncStart(OrbitalProperties.getCurrentTime());
      tracker = SyncTracker.updateTracker(tracker);
    }
    // Make sure a containing Corporation exists as well.
    Corporation.getOrCreateCorporation(syncAccount);
    // 6) Synch the current account until we complete the synch or encounter an unrecoverable error.
    SynchronizerUtil syncUtil = new SynchronizerUtil();
    // Compute states that should be skipped according to system properties.
    Set<SynchronizationState> excluded = getExcludedStates();
    // Set syncTime to the start of the current tracker
    long syncTime = tracker.getSyncStart();
    // Determine starting syncState
    SynchronizationState state = tracker.trackerComplete(supportedFeatures.keySet());
    // Process state
    SyncStatus lastStatus = SyncStatus.DONE;
    do {
      try {
        // If we have a handler for this state, then invoke it.
        CorpStateHandler handler = supportedFeatures.get(state);
        if (handler != null) {
          if (excluded.contains(state)) {
            // Not sync'ing this state, record it.
            lastStatus = handler.exclude(syncAccount, syncUtil);
          } else if (!state.isAllowed(keyInfo.getAccessMask())) {
            // Key doesn't allow this state, record it.
            lastStatus = handler.notAllowed(syncAccount, syncUtil);
          } else {
            // Attempt to sync this state
            lastStatus = handler.sync(syncTime, syncAccount, syncUtil, corpRequest, acctRequest);
          }
        } else {
          // For now, since we likely don't have all the states handled we'll
          // do nothing here. In the future, this should complain about an
          // unknown state.
          log.warning("No handler for state " + state + ".  This sync will eventually time out if this state is expected to be handled!");
          lastStatus = SyncStatus.DONE;
        }
      } catch (Throwable e) {
        // Trap any errors which escape and treat the current step as a failure. We'll log the failure but otherwise not retry the current step.
        log.log(Level.SEVERE, "Current step " + state + " will be marked as failed and not retried", e);
        lastStatus = SyncStatus.DONE;
        tracker = CorporationSyncTracker.getUnfinishedTracker(syncAccount);
        if (tracker != null) {
          // Mark the state as failed
          tracker.setState(state, SyncTracker.SyncState.SYNC_ERROR, "Internal error.  Contact the admin for more information");
          SyncTracker.updateTracker(tracker);
        }
      }
      // Prep for next step or exit.
      tracker = CorporationSyncTracker.getUnfinishedTracker(syncAccount);
      state = tracker == null ? null : tracker.trackerComplete(supportedFeatures.keySet());
    } while (state != null && lastStatus == SyncStatus.DONE);
    // Check if we can finish this tracker.
    if (tracker != null && tracker.trackerComplete(supportedFeatures.keySet()) == null) {
      log.fine("Tracker done, marking as finished");
      SyncTracker.finishTracker(tracker);
    }
  }

  // Initialize support features
  static {
    supportedFeatures.put(SynchronizationState.SYNC_CORP_ACCOUNTBALANCE, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationAccountBalanceSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationAccountBalanceSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationAccountBalanceSync.syncAccountBalance(syncTime, syncAccount, syncUtil, corpRequest);

      }

    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_ASSETLIST, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationAssetsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationAssetsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationAssetsSync.syncAssets(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CORPSHEET, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationSheetSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationSheetSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationSheetSync.syncCorporationSheet(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CONTACTLIST, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContactListSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContactListSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationContactListSync.syncContactList(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_BLUEPRINTS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationBlueprintsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationBlueprintsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationBlueprintsSync.syncCorporationBlueprints(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_BOOKMARKS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationBookmarksSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationBookmarksSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationBookmarksSync.syncBookmarks(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CONTRACTS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContractsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContractsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationContractsSync.syncCorporationContracts(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CONTRACTITEMS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContractItemsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContractItemsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationContractItemsSync.syncCorporationContractItems(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CONTRACTBIDS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContractBidsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContractBidsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationContractBidsSync.syncCorporationContractBids(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CUSTOMSOFFICE, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationCustomsOfficesSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationCustomsOfficesSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationCustomsOfficesSync.syncCustomsOffices(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_FACILITIES, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationFacilitiesSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationFacilitiesSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationFacilitiesSync.syncFacilities(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_FACWARSTATS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationFacWarStatsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationFacWarStatsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationFacWarStatsSync.syncFacWarStats(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_INDUSTRYJOBS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationIndustryJobsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationIndustryJobsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationIndustryJobsSync.syncCorporationIndustryJobs(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_INDUSTRYJOBSHISTORY, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationIndustryJobsHistorySync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationIndustryJobsHistorySync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationIndustryJobsHistorySync.syncCorporationIndustryJobsHistory(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_KILLLOG, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationKillLogSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationKillLogSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationKillLogSync.syncCorporationKillLog(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_MARKETORDERS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMarketOrderSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMarketOrderSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationMarketOrderSync.syncCorporationMarketOrders(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_MEMBERMEDALS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberMedalsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberMedalsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationMemberMedalsSync.syncCorporationMemberMedals(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_STANDINGS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationStandingSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationStandingSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationStandingSync.syncStanding(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_WALLETJOURNAL, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationWalletJournalSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationWalletJournalSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationWalletJournalSync.syncCorporationWalletJournal(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_WALLETTRANSACTIONS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationWalletTransactionSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationWalletTransactionSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationWalletTransactionSync.syncCorporationWalletTransaction(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_SECURITY, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberSecuritySync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberSecuritySync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationMemberSecuritySync.syncCorporationMemberSecurity(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CONTAINERLOG, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContainerLogSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationContainerLogSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationContainerLogSync.syncCorporationContainerLog(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_MEMBERSECURITYLOG, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberSecurityLogSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberSecurityLogSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationMemberSecurityLogSync.syncCorporationMemberSecurityLog(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_MEMBERTRACKING, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberTrackingSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMemberTrackingSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationMemberTrackingSync.syncCorporationMemberTracking(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CORPMEDALS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMedalsSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationMedalsSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationMedalsSync.syncCorporationMedals(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_OUTPOSTLIST, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationOutpostListSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationOutpostListSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationOutpostListSync.syncOutpostList(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_OUTPOSTDETAIL, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationOutpostServiceDetailSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationOutpostServiceDetailSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationOutpostServiceDetailSync.syncOutpostServiceDetail(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_SHAREHOLDERS, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationShareholdersSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationShareholdersSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationShareholdersSync.syncShareholders(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_STARBASELIST, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationStarbaseListSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationStarbaseListSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationStarbaseListSync.syncStarbaseList(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_STARBASEDETAIL, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationStarbaseDetailSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationStarbaseDetailSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationStarbaseDetailSync.syncStarbaseDetail(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CORP_CORPTITLES, new CorpStateHandler() {
      @Override
      public SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationTitlesSync.exclude(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
        return CorporationTitlesSync.notAllowed(syncAccount, syncUtil);

      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICorporationAPI corpRequest,
                             IAccountAPI acctRequest) {
        return CorporationTitlesSync.syncCorporationTitles(syncTime, syncAccount, syncUtil, corpRequest);

      }
    });

  }

}
