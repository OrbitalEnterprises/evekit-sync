package enterprises.orbital.evekit.model;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evexmlapi.IEveXmlApi;
import enterprises.orbital.evexmlapi.act.IAPIKeyInfo;
import enterprises.orbital.evexmlapi.act.IAccountAPI;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    ICharacterAPI charRequest = apiHandle.getCharacterAPIService(syncAccount.getEveKey(), syncAccount.getEveVCode(),
                                                                 syncAccount.getEveCharacterID());
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
          log.warning(
              "No handler for state " + state + ".  This sync will eventually time out if this state is expected to be handled!");
          lastStatus = SyncStatus.DONE;
        }
      } catch (Throwable e) {
        // Trap any errors which escape and treat the current step as a failure. We'll log the failure but otherwise not retry the current step.
        log.log(Level.SEVERE, "Current step " + state + " will be marked as failed and not retried", e);
        lastStatus = SyncStatus.DONE;
        tracker = CapsuleerSyncTracker.getUnfinishedTracker(syncAccount);
        if (tracker != null) {
          // Mark the state as failed
          tracker.setState(state, SyncTracker.SyncState.SYNC_ERROR,
                           "Internal error.  Contact the admin for more information");
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


}
