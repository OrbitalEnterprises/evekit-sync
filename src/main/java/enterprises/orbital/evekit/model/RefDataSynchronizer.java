package enterprises.orbital.evekit.model;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.model.RefSynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.server.sync.ServerStatusSync;
import enterprises.orbital.evexmlapi.EveXmlApiAdapter;
import enterprises.orbital.evexmlapi.EveXmlApiConfig;
import enterprises.orbital.evexmlapi.IEveXmlApi;

/**
 * Reference data synchronizer class. There should only ever be one reference data synchronizer operating at any given time. During synchronization,
 * synchronizers perform the following actions:
 * 
 * <ol>
 * <li>Verify synchronization is not stuck. If an unfinished synchronizer exists but has been open for too long. Then it is finished immediately.
 * <li>Verify enough time has passed since the last synchronization. If not enough time has passed, then synchronization is skipped.
 * <li>Create a new tracker if it's time for reference data to be synched.
 * <li>Synch reference data until we complete the synch or encounter an unrecoverable error.
 * </ol>
 */
public class RefDataSynchronizer {
  protected static final Logger log                          = Logger.getLogger(RefDataSynchronizer.class.getName());

  // List of states we should skip during synchronization (separate with '|')
  public static final String    PROP_SKIP_SYNC               = "enterprises.orbital.evekit.ref.skip_sync";
  // Minimum number of milliseconds that must elapse between attempts to synchronize reference data
  public static final String    PROP_SYNC_ATTEMPT_SEPARATION = "enterprises.orbital.evekit.ref.sync_attempt_separation";
  // Maximum number of milliseconds a tracker is allowed to remain unfinished
  public static final String    PROP_SYNC_TERM_DELAY         = "enterprises.orbital.evekit.ref.sync_terminate_delay";
  // XML API server connection timeout max (milliseconds)
  public static final String    PROP_CONNECT_TIMEOUT         = "enterprises.orbital.evekit.timeout.connect";
  // XML API server connection read timeout max (milliseconds)
  public static final String    PROP_READ_TIMEOUT            = "enterprises.orbital.evekit.timeout.read";
  // Agent to set for all XML API requests
  public static final String    PROP_SITE_AGENT              = "enterprises.orbital.evekit.site_agent";
  // XML API URL to use
  public static final String    PROP_XML_API_URL             = "enterprises.orbital.evekit.api_server_url";

  public static interface RefStateHandler {
    public SyncStatus exclude(
                              RefSynchronizerUtil syncUtil);

    public SyncStatus notAllowed(
                                 RefSynchronizerUtil syncUtil);

    public SyncStatus sync(
                           long syncTime,
                           RefSynchronizerUtil syncUtil,
                           IEveXmlApi apiHandle);
  }

  /**
   * Retrieve the synchronization states that have been excluded from synchronization by the admin.
   * 
   * @return the set of excluded synchronization states.
   */
  protected static Set<SynchronizationState> getExcludedStates() {
    String[] excludedStates = PersistentProperty.getPropertyWithFallback(RefDataSynchronizer.PROP_SKIP_SYNC, "").split("\\|");
    Set<SynchronizationState> excluded = new HashSet<SynchronizationState>();
    for (String next : excludedStates) {
      try {
        if (!next.isEmpty()) {
          SynchronizationState val = SynchronizationState.valueOf(next);
          switch (val) {
          case SYNC_REF_START:
          case SYNC_REF_END:
            log.warning("Not allowed to exclude start or stop states, ignoring: " + val);
            break;
          default:
            excluded.add(val);
          }
        }
      } catch (Exception e) {
        // Protect against mangled configuration value.
        log.warning("Error handling excluded state name: " + next + ", ignoring with error: " + e);
      }
    }
    return excluded;
  }

  /**
   * Retrieve an XML endpoint handle using global configuration properties.
   * 
   * @return an XML API endpoint handle
   * @throws URISyntaxException
   *           if a config error prevents the creation of the handle
   */
  public static IEveXmlApi getApiHandle() throws URISyntaxException {
    String agentValue = OrbitalProperties.getGlobalProperty(RefDataSynchronizer.PROP_SITE_AGENT, "unknown-agent");
    int connectTimeout = (int) OrbitalProperties.getLongGlobalProperty(RefDataSynchronizer.PROP_CONNECT_TIMEOUT, 60000L);
    int readTimeout = (int) OrbitalProperties.getLongGlobalProperty(RefDataSynchronizer.PROP_READ_TIMEOUT, 60000L);
    String serverURI = OrbitalProperties.getGlobalProperty(RefDataSynchronizer.PROP_XML_API_URL, "https://api.eveonline.com");
    return new EveXmlApiAdapter(EveXmlApiConfig.get().serverURI(serverURI).agent(agentValue).connectTimeout(connectTimeout).readTimeout(readTimeout));
  }

  /**
   * Verify there are no stuck ref data trackers. If a stuck tracker is found, it is immediately finished.
   * 
   * @return true if a stuck tracker was not found, false if a stuck tracker was found and terminated
   */
  protected static boolean verifyTrackerNotStuck() {
    long terminateDelay = PersistentProperty.getLongPropertyWithFallback(RefDataSynchronizer.PROP_SYNC_TERM_DELAY, Long.MAX_VALUE);
    long now = OrbitalProperties.getCurrentTime();
    RefSyncTracker next = RefSyncTracker.getUnfinishedTracker();
    if (next != null) {
      // Check whether this tracker has been unfinished for too long
      long delaySinceStart = now - next.getSyncStart();
      if (delaySinceStart > terminateDelay) {
        // This sync has been running too long, finish it immediately
        log.fine("Forcing tracker to terminate due to delay: " + next);
        RefSyncTracker.finishTracker(next);
        return false;
      }
    }
    return true;
  }

  /**
   * Verify enough time has passed since the last time ref data was sync'd.
   * 
   * @return true if enough time has passed since the last sync of ref data, false otherwise.
   */
  protected static boolean verifyTrackerSeparation() {
    long spacing = PersistentProperty.getLongPropertyWithFallback(RefDataSynchronizer.PROP_SYNC_ATTEMPT_SEPARATION,
                                                                  TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES));
    RefSyncTracker tracker = RefSyncTracker.getLatestFinishedTracker();
    long now = OrbitalProperties.getCurrentTime();
    long earliestStart = tracker != null ? tracker.getSyncEnd() + spacing : now;
    boolean sync = earliestStart <= now;
    if (!sync) log.fine("Insufficient tracker separation, skipping");
    return sync;
  }

  // Support synchronization features (see bottom of file for initialization)
  public static final Map<SynchronizationState, RefStateHandler> supportedFeatures = new HashMap<SynchronizationState, RefStateHandler>();

  /**
   * Synchronize reference data until either the synchronization is complete, or an unrecoverable error occurs. This method assumes we are the only synchronizer
   * for reference data. The caller is responsible for interrupting this call if it takes too long to complete.
   * 
   * @throws IOException
   *           if an IO error occurs while performing synchronization.
   * @throws URISyntaxException
   *           if an error occurs while trying to build an XML API endpoint handle.
   */
  public void synchronize() throws IOException, URISyntaxException {
    log.fine("Starting sync");
    // Steps:
    // 1) Verify no tracker is stuck. If an unfinished tracker exists but has been open for too long. Then it is finished immediately.
    IEveXmlApi apiHandle = getApiHandle();
    if (!verifyTrackerNotStuck()) return;
    // 2) Verify enough time has passed since the last synchronization. If not enough time has passed, then synchronization is skipped.
    if (!verifyTrackerSeparation()) return;
    // 3) Create a new tracker if it's time to synch.
    RefSyncTracker tracker = RefSyncTracker.createOrGetUnfinishedTracker();
    if (tracker.getSyncStart() <= 0) {
      // Tracker isn't started yet so start it.
      tracker.setSyncStart(OrbitalProperties.getCurrentTime());
      tracker = RefSyncTracker.updateTracker(tracker);
    }
    // Make sure a containing RefData exists as well.
    RefData.getOrCreateRefData();
    // 4) Synch until we complete the synch or encounter an unrecoverable error.
    RefSynchronizerUtil syncUtil = new RefSynchronizerUtil();
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
        RefStateHandler handler = supportedFeatures.get(state);
        if (handler != null) {
          if (excluded.contains(state)) {
            // Not sync'ing this state, record it.
            lastStatus = handler.exclude(syncUtil);
          } else {
            // Attempt to sync this state
            lastStatus = handler.sync(syncTime, syncUtil, apiHandle);
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
        tracker = RefSyncTracker.getUnfinishedTracker();
        if (tracker != null) {
          // Mark the state as failed
          tracker.setState(state, SyncTracker.SyncState.SYNC_ERROR, "Internal error.  Contact the admin for more information");
          RefSyncTracker.updateTracker(tracker);
        }
      }
      // Prep for next step or exit.
      tracker = RefSyncTracker.getUnfinishedTracker();
      state = tracker == null ? null : tracker.trackerComplete(supportedFeatures.keySet());
    } while (state != null && lastStatus == SyncStatus.DONE);
    // Check if we can finish this tracker.
    if (tracker != null && tracker.trackerComplete(supportedFeatures.keySet()) == null) {
      log.fine("Tracker done, marking as finished");
      RefSyncTracker.finishTracker(tracker);
    }
  }

  // Initialize support features
  static {
    supportedFeatures.put(SynchronizationState.SYNC_REF_SERVER_SERVERSTATUS, new RefStateHandler() {

      @Override
      public SyncStatus exclude(
                                RefSynchronizerUtil syncUtil) {
        return ServerStatusSync.exclude(syncUtil);
      }

      @Override
      public SyncStatus notAllowed(
                                   RefSynchronizerUtil syncUtil) {
        return ServerStatusSync.notAllowed(syncUtil);
      }

      @Override
      public SyncStatus sync(
                             long syncTime,
                             RefSynchronizerUtil syncUtil,
                             IEveXmlApi apiHandle) {
        return ServerStatusSync.sync(syncTime, syncUtil, apiHandle.getServerAPIService());
      }

    });

  }

}
