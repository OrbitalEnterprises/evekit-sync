package enterprises.orbital.evekit.model;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evexmlapi.EveXmlApiAdapter;
import enterprises.orbital.evexmlapi.EveXmlApiConfig;
import enterprises.orbital.evexmlapi.IEveXmlApi;
import enterprises.orbital.evexmlapi.act.IAPIKeyInfo;
import enterprises.orbital.evexmlapi.act.IAccountAPI;
import enterprises.orbital.evexmlapi.act.ICharacter;

/**
 * Base synchronizer class. Synchronizers observe one invariant: for the account they are assigned, it is assumed they are the only synchronizer working on that
 * account. During synchronization, synchronizers perform the following actions:
 * 
 * <ol>
 * <li>Verify the API key is not expired. Synchronization is skipped if the key is expired.
 * <li>Verify the account is active. Synchronization is skipped if the account is not active or marked for delete.
 * <li>Verify the account is not stuck. If an unfinished synchronizer exists but has been open for too long. Then it is finished immediately.
 * <li>Verify enough time has passed since the last synchronization. If not enough time has passed, then synchronization is skipped.
 * <li>Create a new tracker if it's time for this account to be synched.
 * <li>Synch the current account until we complete the synch or encounter an unrecoverable error.
 * </ol>
 */
public abstract class AbstractSynchronizer {
  protected static final Logger log                          = Logger.getLogger(AbstractSynchronizer.class.getName());

  // List of states we should skip during synchronization (separate with '|')
  public static final String    PROP_SKIP_SYNC               = "enterprises.orbital.evekit.model.skip_sync";
  // Minimum number of milliseconds that must elapse between attempts to synchronize an account
  public static final String    PROP_SYNC_ATTEMPT_SEPARATION = "enterprises.orbital.evekit.sync_attempt_separation";
  // Maximum number of milliseconds a tracker is allowed to remain unfinished
  public static final String    PROP_SYNC_TERM_DELAY         = "enterprises.orbital.evekit.sync_terminate_delay";
  // XML API server connection timeout max (milliseconds)
  public static final String    PROP_CONNECT_TIMEOUT         = "enterprises.orbital.evekit.timeout.connect";
  // XML API server connection read timeout max (milliseconds)
  public static final String    PROP_READ_TIMEOUT            = "enterprises.orbital.evekit.timeout.read";
  // Agent to set for all XML API requests
  public static final String    PROP_SITE_AGENT              = "enterprises.orbital.evekit.site_agent";
  // XML API URL to use
  public static final String    PROP_XML_API_URL             = "enterprises.orbital.evekit.api_server_url";

  public static interface StateHandler {
    public SyncStatus exclude(
                              SynchronizedEveAccount syncAccount,
                              SynchronizerUtil syncUtil);

    public SyncStatus notAllowed(
                                 SynchronizedEveAccount syncAccount,
                                 SynchronizerUtil syncUtil);
  }

  /**
   * Retrieve the synchronization states that have been excluded from synchronization by the admin.
   * 
   * @return the set of excluded synchronization states.
   */
  protected static Set<SynchronizationState> getExcludedStates() {
    String[] excludedStates = PersistentProperty.getPropertyWithFallback(AbstractSynchronizer.PROP_SKIP_SYNC, "").split("\\|");
    Set<SynchronizationState> excluded = new HashSet<SynchronizationState>();
    for (String next : excludedStates) {
      try {
        if (!next.isEmpty()) {
          SynchronizationState val = SynchronizationState.valueOf(next);
          switch (val) {
          case SYNC_CHAR_START:
          case SYNC_CHAR_END:
          case SYNC_CORP_START:
          case SYNC_CORP_END:
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
    String agentValue = OrbitalProperties.getGlobalProperty(AbstractSynchronizer.PROP_SITE_AGENT, "unknown-agent");
    int connectTimeout = (int) OrbitalProperties.getLongGlobalProperty(AbstractSynchronizer.PROP_CONNECT_TIMEOUT, 60000L);
    int readTimeout = (int) OrbitalProperties.getLongGlobalProperty(AbstractSynchronizer.PROP_READ_TIMEOUT, 60000L);
    String serverURI = OrbitalProperties.getGlobalProperty(AbstractSynchronizer.PROP_XML_API_URL, "https://api.eveonline.com");
    return new EveXmlApiAdapter(EveXmlApiConfig.get().serverURI(serverURI).agent(agentValue).connectTimeout(connectTimeout).readTimeout(readTimeout));
  }

  /**
   * Get API key info using the given Account API handle. If the key is expired or the credentials are wrong, then we return non-permissive API info. If an IO
   * error occurs while retrieving the key, then we return all permissive API info.
   * 
   * @param acctRequest
   *          the Account API handle to use.
   * @return API key info with appropriate defaults if an error occurs while retrieving the handle.
   */
  protected static IAPIKeyInfo getKeyInfo(
                                          IAccountAPI acctRequest) {
    IAPIKeyInfo keyInfo = null;
    try {
      keyInfo = acctRequest.requestAPIKeyInfo();
      if (acctRequest.isError()) {
        int code = acctRequest.getErrorCode();
        if (code == 220 || code == 203 || code == 222) {
          // Default to non-permissive key for certain expected errors.
          log.log(Level.INFO, "Switching to non-permissive key due to: " + acctRequest.getErrorString() + " (" + acctRequest.getErrorCode() + ")");
          return new IAPIKeyInfo() {

            @Override
            public long getAccessMask() {
              return 0x0L;
            }

            @Override
            public String getType() {
              throw new UnsupportedOperationException();
            }

            @Override
            public Date getExpires() {
              throw new UnsupportedOperationException();
            }

            @Override
            public Collection<ICharacter> getCharacters() {
              throw new UnsupportedOperationException();
            }

          };
        } else {
          throw new IOException("APIKeyInfo request failed with error: " + acctRequest.getErrorString() + " (" + acctRequest.getErrorCode() + ")");
        }
      }
      return keyInfo;
    } catch (IOException e) {
      // Log the error but attempt sync anyway with a mask that permits everything.
      log.log(Level.WARNING, "Unable to retrieve key info, attempting to continue", e);
      return new IAPIKeyInfo() {

        @Override
        public long getAccessMask() {
          return 0xFFFFFFFL;
        }

        @Override
        public String getType() {
          throw new UnsupportedOperationException();
        }

        @Override
        public Date getExpires() {
          throw new UnsupportedOperationException();
        }

        @Override
        public Collection<ICharacter> getCharacters() {
          throw new UnsupportedOperationException();
        }

      };
    }
  }

  /**
   * Check the the given API key is not expired.
   * 
   * @param keyInfo
   *          the API key to check.
   * @return true if not expired, false otherwise.
   */
  protected static boolean verifyNotExpired(
                                            IAPIKeyInfo keyInfo) {
    if (keyInfo.getExpires() != null && keyInfo.getExpires().before(OrbitalProperties.getCurrentDate())) {
      log.fine("Skipping sync because key expired at time: " + keyInfo.getExpires());
      return false;
    }
    return true;
  }

  /**
   * Verify user owning account is active and account is not marked for delete.
   * 
   * @param syncAccount
   *          the account to check
   * @return true if user is active and account not marked for delete, false otherwise.
   */
  protected static boolean verifyActiveAndNotDeleted(
                                                     SynchronizedEveAccount syncAccount) {
    if (!syncAccount.getUserAccount().isActive()) {
      log.fine("Skipping sync because user account is inactive: " + syncAccount.getUserAccount());
      return false;
    }
    if (syncAccount.getMarkedForDelete() > 0) {
      log.fine("Skipping sync because account scheduled for deletion");
      return false;
    }
    return true;
  }

  /**
   * Verify the given account has no stuck unfinished trackers. If a stuck tracker is found, it is immediately finished.
   * 
   * @param syncAccount
   *          account to check.
   * @return true if a stuck tracker was not found, false if a stuck tracker was found and terminated
   */
  protected static boolean verifyTrackerNotStuck(
                                                 SynchronizedEveAccount syncAccount) {
    long terminateDelay = PersistentProperty.getLongPropertyWithFallback(AbstractSynchronizer.PROP_SYNC_TERM_DELAY, Long.MAX_VALUE);
    long now = OrbitalProperties.getCurrentTime();
    SyncTracker next = SyncTracker.getUnfinishedTracker(syncAccount);
    if (next != null) {
      // Check whether this tracker has been unfinished for too long
      long delaySinceStart = now - next.getSyncStart();
      if (delaySinceStart > terminateDelay) {
        // This sync has been running too long, finish it immediately
        log.fine("Forcing tracker to terminate due to delay: " + next);
        SyncTracker.finishTracker(next);
        return false;
      }
    }
    return true;
  }

  /**
   * Verify enough time has passed since the last time this account was sync'd.
   * 
   * @param syncAccount
   *          the account to check
   * @return true if enough time has passed since the last sync of this account, false otherwise.
   */
  protected static boolean verifyTrackerSeparation(
                                                   SynchronizedEveAccount syncAccount) {
    long spacing = PersistentProperty.getLongPropertyWithFallback(AbstractSynchronizer.PROP_SYNC_ATTEMPT_SEPARATION,
                                                                  TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES));
    SyncTracker tracker = SyncTracker.getLatestFinishedTracker(syncAccount);
    long now = OrbitalProperties.getCurrentTime();
    long earliestStart = tracker != null ? tracker.getSyncEnd() + spacing : now;
    boolean sync = earliestStart <= now;
    if (!sync) log.fine("Insufficient tracker separation, skipping: " + syncAccount);
    return sync;
  }

  /**
   * Synchronize the given account until either the synchronization is complete, or an unrecoverable error occurs. This method assumes we are the only
   * synchronizer of the current account. The caller is responsible for interrupting this call if it takes too long to complete.
   * 
   * @param syncAccount
   *          account to synchronize
   * @throws IOException
   *           if an IO error occurs while performing synchronization.
   * @throws URISyntaxException
   *           if an error occurs while trying to build an XML API endpoint handle.
   */
  public abstract void synchronize(
                                   SynchronizedEveAccount syncAccount) throws IOException, URISyntaxException;

}
