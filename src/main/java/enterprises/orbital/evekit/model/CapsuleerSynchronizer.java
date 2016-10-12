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
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.sync.CharacterAccountBalanceSync;
import enterprises.orbital.evekit.model.character.sync.CharacterAccountStatusSync;
import enterprises.orbital.evekit.model.character.sync.CharacterAssetsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterBlueprintsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterBookmarksSync;
import enterprises.orbital.evekit.model.character.sync.CharacterCalendarEventAttendeeSync;
import enterprises.orbital.evekit.model.character.sync.CharacterChatChannelsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterContactListSync;
import enterprises.orbital.evekit.model.character.sync.CharacterContactNotificationSync;
import enterprises.orbital.evekit.model.character.sync.CharacterContractBidsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterContractItemsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterContractsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterFacWarStatsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterIndustryJobsHistorySync;
import enterprises.orbital.evekit.model.character.sync.CharacterIndustryJobsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterKillLogSync;
import enterprises.orbital.evekit.model.character.sync.CharacterLocationsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterMailMessageBodiesSync;
import enterprises.orbital.evekit.model.character.sync.CharacterMailMessageSync;
import enterprises.orbital.evekit.model.character.sync.CharacterMailingListSync;
import enterprises.orbital.evekit.model.character.sync.CharacterMarketOrderSync;
import enterprises.orbital.evekit.model.character.sync.CharacterMedalSync;
import enterprises.orbital.evekit.model.character.sync.CharacterNotificationSync;
import enterprises.orbital.evekit.model.character.sync.CharacterNotificationTextSync;
import enterprises.orbital.evekit.model.character.sync.CharacterPlanetaryColoniesSync;
import enterprises.orbital.evekit.model.character.sync.CharacterResearchAgentSync;
import enterprises.orbital.evekit.model.character.sync.CharacterSheetSync;
import enterprises.orbital.evekit.model.character.sync.CharacterSkillInQueueSync;
import enterprises.orbital.evekit.model.character.sync.CharacterSkillInTrainingSync;
import enterprises.orbital.evekit.model.character.sync.CharacterSkillsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterStandingSync;
import enterprises.orbital.evekit.model.character.sync.CharacterUpcomingCalendarEventsSync;
import enterprises.orbital.evekit.model.character.sync.CharacterWalletJournalSync;
import enterprises.orbital.evekit.model.character.sync.CharacterWalletTransactionSync;
import enterprises.orbital.evekit.model.character.sync.PartialCharacterSheetSync;
import enterprises.orbital.evexmlapi.IEveXmlApi;
import enterprises.orbital.evexmlapi.act.IAPIKeyInfo;
import enterprises.orbital.evexmlapi.act.IAccountAPI;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;

/**
 * Synchronize a capsuleer account.
 */
public class CapsuleerSynchronizer extends AbstractSynchronizer {
  protected static final Logger log = Logger.getLogger(CapsuleerSynchronizer.class.getName());

  // Class which encapsulates sync state execution
  public static interface CharStateHandler extends AbstractSynchronizer.StateHandler {
    public SyncStatus sync(
                           long syncTime,
                           SynchronizedEveAccount syncAccount,
                           SynchronizerUtil syncUtil,
                           ICharacterAPI charRequest,
                           IAccountAPI acctRequest);
  }

  // Support synchronization features (see bottom of file for initialization)
  public static final Map<SynchronizationState, CharStateHandler> supportedFeatures = new HashMap<SynchronizationState, CharStateHandler>();

  /**
   * {@inheritDoc}
   */
  @Override
  public void synchronize(
                          SynchronizedEveAccount syncAccount)
    throws IOException, URISyntaxException {
    log.fine("Starting sync: " + syncAccount);
    // Steps:
    // 1) Verify the API key is not expired. Synchronization is skipped if the key is expired.
    IEveXmlApi apiHandle = getApiHandle();
    IAccountAPI acctRequest = apiHandle.getAccountAPIService(syncAccount.getEveKey(), syncAccount.getEveVCode());
    ICharacterAPI charRequest = apiHandle.getCharacterAPIService(syncAccount.getEveKey(), syncAccount.getEveVCode(), syncAccount.getEveCharacterID());
    IAPIKeyInfo keyInfo = getKeyInfo(acctRequest);
    if (!verifyNotExpired(keyInfo)) return;
    // 2) Verify the account is active. Synchronization is skipped if the account is not active or marked for delete.
    if (!verifyActiveAndNotDeleted(syncAccount)) return;
    // 3) Verify the account is not stuck. If an unfinished synchronizer exists but has been open for too long. Then it is finished immediately.
    if (!verifyTrackerNotStuck(syncAccount)) return;
    // 4) Verify enough time has passed since the last synchronization. If not enough time has passed, then synchronization is skipped.
    if (!verifyTrackerSeparation(syncAccount)) return;
    // 5) Create a new tracker if it's time for this account to be synched.
    CapsuleerSyncTracker tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);
    if (tracker.getSyncStart() <= 0) {
      // Tracker isn't started yet so start it.
      tracker.setSyncStart(OrbitalProperties.getCurrentTime());
      tracker = SyncTracker.updateTracker(tracker);
    }
    // Make sure a containing Capsuleer exists as well.
    Capsuleer.getOrCreateCapsuleer(syncAccount);
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
        CharStateHandler handler = supportedFeatures.get(state);
        if (handler != null) {
          if (excluded.contains(state)) {
            // Not sync'ing this state, record it.
            lastStatus = handler.exclude(syncAccount, syncUtil);
          } else if (!state.isAllowed(keyInfo.getAccessMask())) {
            // Key doesn't allow this state, record it.
            lastStatus = handler.notAllowed(syncAccount, syncUtil);
          } else {
            // Attempt to sync this state
            lastStatus = handler.sync(syncTime, syncAccount, syncUtil, charRequest, acctRequest);
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
        tracker = CapsuleerSyncTracker.getUnfinishedTracker(syncAccount);
        if (tracker != null) {
          // Mark the state as failed
          tracker.setState(state, SyncTracker.SyncState.SYNC_ERROR, "Internal error.  Contact the admin for more information");
          SyncTracker.updateTracker(tracker);
        }
      }
      // Prep for next step or exit.
      tracker = CapsuleerSyncTracker.getUnfinishedTracker(syncAccount);
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
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_ACCOUNTSTATUS, new CharStateHandler() {

      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterAccountStatusSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterAccountStatusSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterAccountStatusSync.syncAccountStatus(syncTime, syncAccount, syncUtil, charRequest, acctRequest);
      }

    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_ACCOUNTBALANCE, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterAccountBalanceSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterAccountBalanceSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterAccountBalanceSync.syncAccountBalance(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_SKILLINTRAINING, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterSkillInTrainingSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterSkillInTrainingSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterSkillInTrainingSync.syncSkillInTraining(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CHARACTERSHEET, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterSheetSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterSheetSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterSheetSync.syncCharacterSheet(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_PARTIALCHARACTERSHEET, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return PartialCharacterSheetSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return PartialCharacterSheetSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return PartialCharacterSheetSync.syncCharacterSheet(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CHATCHANNELS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterChatChannelsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterChatChannelsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterChatChannelsSync.syncChatChannels(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_ASSETLIST, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterAssetsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterAssetsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterAssetsSync.syncAssets(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_UPCOMINGCALENDAREVENTS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterUpcomingCalendarEventsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterUpcomingCalendarEventsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterUpcomingCalendarEventsSync.syncUpcomingCalendarEvents(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CALENDAREVENTATTENDEES, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterCalendarEventAttendeeSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterCalendarEventAttendeeSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterCalendarEventAttendeeSync.syncCalendarEventAttendees(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CONTACTLIST, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterContactListSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterContactListSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterContactListSync.syncContactList(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CONTACTNOTIFICATIONS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterContactNotificationSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterContactNotificationSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterContactNotificationSync.syncCharacterContactNotifications(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_BLUEPRINTS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterBlueprintsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterBlueprintsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterBlueprintsSync.syncCharacterBlueprints(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_BOOKMARKS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterBookmarksSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterBookmarksSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterBookmarksSync.syncBookmarks(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CONTRACTS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterContractsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterContractsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterContractsSync.syncCharacterContracts(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CONTRACTITEMS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterContractItemsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterContractItemsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterContractItemsSync.syncCharacterContractItems(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_CONTRACTBIDS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterContractBidsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterContractBidsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterContractBidsSync.syncCharacterContractBids(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_FACWARSTATS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterFacWarStatsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterFacWarStatsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterFacWarStatsSync.syncFacWarStats(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_INDUSTRYJOBS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterIndustryJobsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterIndustryJobsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterIndustryJobsSync.syncCharacterIndustryJobs(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_INDUSTRYJOBSHISTORY, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterIndustryJobsHistorySync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterIndustryJobsHistorySync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterIndustryJobsHistorySync.syncCharacterIndustryJobsHistory(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_KILLLOG, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterKillLogSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterKillLogSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterKillLogSync.syncCharacterKillLog(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_LOCATIONS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterLocationsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterLocationsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterLocationsSync.syncCharacterLocations(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_MAILMESSAGES, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterMailMessageSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterMailMessageSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterMailMessageSync.syncMailMessages(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_MAILBODIES, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterMailMessageBodiesSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterMailMessageBodiesSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterMailMessageBodiesSync.syncMailMessageBodies(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_MAILINGLISTS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterMailingListSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterMailingListSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterMailingListSync.syncMailingLists(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_MARKETORDERS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterMarketOrderSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterMarketOrderSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterMarketOrderSync.syncCharacterMarketOrders(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_MEDALS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterMedalSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterMedalSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterMedalSync.syncCharacterMedals(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_NOTIFICATIONS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterNotificationSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterNotificationSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterNotificationSync.syncNotifications(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_NOTIFICATIONTEXTS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterNotificationTextSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterNotificationTextSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterNotificationTextSync.syncNotificationTexts(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_PLANETARY_COLONIES, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterPlanetaryColoniesSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterPlanetaryColoniesSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterPlanetaryColoniesSync.syncPlanetaryColonies(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_RESEARCH, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterResearchAgentSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterResearchAgentSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterResearchAgentSync.syncResearchAgents(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_SKILLQUEUE, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterSkillInQueueSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterSkillInQueueSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterSkillInQueueSync.syncSkillQueue(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_SKILLS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterSkillsSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterSkillsSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterSkillsSync.syncCharacterSheet(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_STANDINGS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterStandingSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterStandingSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterStandingSync.syncStanding(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_WALLETJOURNAL, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterWalletJournalSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterWalletJournalSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterWalletJournalSync.syncCharacterWalletJournal(syncTime, syncAccount, syncUtil, charRequest);
      }
    });
    supportedFeatures.put(SynchronizationState.SYNC_CHAR_WALLETTRANSACTIONS, new CharStateHandler() {
      @Override
      public SyncStatus exclude(
                                SynchronizedEveAccount syncAccount,
                                SynchronizerUtil syncUtil) {
        return CharacterWalletTransactionSync.exclude(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
        return CharacterWalletTransactionSync.notAllowed(syncAccount, syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             SynchronizedEveAccount syncAccount,
                             SynchronizerUtil syncUtil,
                             ICharacterAPI charRequest,
                             IAccountAPI acctRequest) {
        return CharacterWalletTransactionSync.syncCharacterWalletTransaction(syncTime, syncAccount, syncUtil, charRequest);
      }
    });

  }

}
