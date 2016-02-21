package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SynchronizationHandler;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;

public abstract class AbstractCharacterSync implements SynchronizationHandler<CapsuleerSyncTracker, Capsuleer> {
  private static final Logger log = Logger.getLogger(AbstractCharacterSync.class.getName());

  public static boolean StringChanged(String a, String b) {
    return !String.valueOf(a).equals(String.valueOf(b));
  }

  @Override
  public CapsuleerSyncTracker getCurrentTracker(SynchronizedEveAccount owner) {
    return CapsuleerSyncTracker.getUnfinishedTracker(owner);
  }

  @Override
  public Capsuleer getExistingContainer(SynchronizedEveAccount owner) {
    return Capsuleer.getCapsuleer(owner);
  }

  @Override
  public boolean prereqSatisfied(CapsuleerSyncTracker tracker) {
    return true;
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, final CachedData item) {
    return CachedData.updateData(item) != null;
  }

  protected abstract Object getServerData(ICharacterAPI charRequest) throws IOException;

  /**
   * Handle a server error and return the appropriate SyncState we should store in this case. The default behavior is to log the error and return SYNC_ERROR.
   * 
   * @param charRequest
   *          the interface we used to make the request. Can be interrogated for error detail.
   * @param errorDetail
   *          string builder which should be populated with error detail we report in the sync status.
   * @return the SyncState we should store as a result of this error.
   */
  protected static SyncTracker.SyncState handleServerError(ICharacterAPI charRequest, StringBuilder errorDetail) {
    switch (charRequest.getErrorCode()) {
    case 222:
    case 221:
    case 220:
    case 124:
    case 201:
      // Treat all these errors as warnings
      errorDetail.append("Warning ").append(charRequest.getErrorCode()).append(": ").append(charRequest.getErrorString());
      return SyncTracker.SyncState.SYNC_WARNING;

    default:
      // Everything else is treated as an error
      errorDetail.append("Error ").append(charRequest.getErrorCode()).append(": ").append(charRequest.getErrorString());
      return SyncTracker.SyncState.SYNC_ERROR;
    }
  }

  /**
   * Process server data and include any updates or deletes.
   * 
   * @param time
   *          reference time for this update.
   * @param syncAccount
   *          sync account which owns updates.
   * @param charRequest
   *          handle for making XML API requests.
   * @param data
   *          data already retrieved from server.
   * @param updates
   *          list where items to update should be stored.
   * @param deletes
   *          list where keys of items to delete should be stored
   * @return date when next update should be attempted
   * @throws IOException
   *           if an error occurs while processing data
   */
  protected abstract long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException;

  /**
   * Synchronize data against the XML API.
   * 
   * @param time
   *          reference time for this update
   * @param syncAccount
   *          sync account which owns updates
   * @param syncUtil
   *          utility object for helping process updates
   * @param charRequest
   *          handle for making XML API requests
   * @param description
   *          description to use for log entries
   * @return HTTP response code to return as the result of this synchronization attempt
   */
  protected SyncStatus syncData(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest, String description) {

    try {
      // Run pre-check.
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, this);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // From old dps code
      log.fine("Starting refresh request for " + description + " for account " + syncAccount);

      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      try {
        Object serverData = getServerData(charRequest);

        if (charRequest.isError()) {
          StringBuilder errStr = new StringBuilder();
          status = handleServerError(charRequest, errStr);
          errorDetail = errStr.toString();
          if (status == SyncTracker.SyncState.SYNC_ERROR) log.warning("request failed: " + errorDetail);
        } else {
          nextExpiry = processServerData(time, syncAccount, charRequest, serverData, updateList);
        }

      } catch (IOException e) {
        status = SyncTracker.SyncState.SYNC_ERROR;
        errorDetail = "request failed with IO error";
        log.warning("request failed with error " + e);
      }

      log.fine("Completed refresh request for " + description + " for account " + syncAccount);

      // Finished sync, store result if needed
      syncUtil.storeSynchResults(time, CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, status, errorDetail, nextExpiry, description, updateList,
                                 this);

      return SyncStatus.DONE;

    } catch (IOException e) {
      // Log but give us another shot in the queue
      log.warning("store failed with error " + e);
      return SyncStatus.ERROR;
    }

  }

  /**
   * Exclude synchronization of the current state. We exclude a state by recording it as failed with a message indicating that this state was excluded.
   * 
   * @param syncAccount
   *          the synchronized account to be excluded.
   * @param syncUtil
   *          the utility to use for synchronization actions.
   * @param description
   *          a description of the synchronization operation being performed.
   * @param state
   *          the synchronization state to record in the tracker
   * @return the outcome of the exclusion. Normally, this will be HttpServletResponse.SC_OK unless there is an error updating the state.
   */
  protected SyncStatus excludeState(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, String description, SyncTracker.SyncState state) {

    try {
      // Verify we haven't already updated this state.
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, this);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // Record the exclusion.
      log.fine("Exluding synchronization for " + description + " for account " + syncAccount);
      SyncTracker.SyncState status = state;
      String errorDetail = "";
      switch (status) {
      case NOT_ALLOWED:
        errorDetail = "API key for this account does not allow synchronization of this data.  Contact the site admin if you think this is an error.";
        break;

      case SYNC_ERROR:
      default:
        errorDetail = "Synchronization skipped due to admin exclusion.  Contact the site admin for more info.";
        break;
      }

      // NOTE: time argument not relevant here since we're only updating tracker
      syncUtil.storeSynchResults(0L, CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, status, errorDetail, -1, description, null, this);

      return SyncStatus.DONE;

    } catch (IOException e) {
      // Log but give us another shot in the queue
      log.warning("store failed with error " + e);
      return SyncStatus.ERROR;
    }

  }

}
