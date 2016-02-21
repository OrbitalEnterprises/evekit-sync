package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SynchronizationHandler;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;

public abstract class AbstractCorporationSync implements SynchronizationHandler<CorporationSyncTracker, Corporation> {
  private static final Logger log = Logger.getLogger(AbstractCorporationSync.class.getName());

  public static boolean StringChanged(String a, String b) {
    return !String.valueOf(a).equals(String.valueOf(b));
  }

  @Override
  public CorporationSyncTracker getCurrentTracker(SynchronizedEveAccount owner) {
    return CorporationSyncTracker.getUnfinishedTracker(owner);
  }

  @Override
  public Corporation getExistingContainer(SynchronizedEveAccount owner) {
    return Corporation.getCorporation(owner);
  }

  @Override
  public boolean prereqSatisfied(CorporationSyncTracker tracker) {
    return true;
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, final CachedData item) {
    return CachedData.updateData(item) != null;
  }

  protected abstract Object getServerData(ICorporationAPI charRequest) throws IOException;

  /**
   * Handle a server error and return the appropriate SyncState we should store in this case. The default behavior is to log the error and return SYNC_ERROR.
   * 
   * @param corpRequest
   *          the interface we used to make the request. Can be interrogated for error detail.
   * @param errorDetail
   *          string builder which should be populated with error detail we report in the sync status.
   * @return the SyncState we should store as a result of this error.
   */
  protected static SyncTracker.SyncState handleServerError(ICorporationAPI corpRequest, StringBuilder errorDetail) {
    switch (corpRequest.getErrorCode()) {
    case 222:
    case 221:
    case 220:
    case 125:
      // Treat all these errors as warnings
      errorDetail.append("Warning ").append(corpRequest.getErrorCode()).append(": ").append(corpRequest.getErrorString());
      return SyncTracker.SyncState.SYNC_WARNING;

    default:
      // Everything else is treated as an error
      errorDetail.append("Error ").append(corpRequest.getErrorCode()).append(": ").append(corpRequest.getErrorString());
      return SyncTracker.SyncState.SYNC_ERROR;
    }
  }

  protected abstract long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException;

  protected SyncStatus syncData(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest, String description) {

    try {
      // Run pre-check.
      SyncStatus preCheck = syncUtil.preSyncCheck(CorporationSyncTracker.class, Corporation.class, syncAccount, description, this);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // From old dps code
      log.fine("Starting refresh request for " + description + " for account " + syncAccount);

      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      try {
        Object serverData = getServerData(corpRequest);

        if (corpRequest.isError()) {
          StringBuilder errStr = new StringBuilder();
          status = handleServerError(corpRequest, errStr);
          errorDetail = errStr.toString();
          if (status == SyncTracker.SyncState.SYNC_ERROR) log.warning("request failed: " + errorDetail);
        } else {
          nextExpiry = processServerData(time, syncAccount, corpRequest, serverData, updateList);
        }

      } catch (IOException e) {
        status = SyncTracker.SyncState.SYNC_ERROR;
        errorDetail = "request failed with IO error";
        log.warning("request failed with error " + e);
      }

      log.fine("Completed refresh request for " + description + " for account " + syncAccount);

      // Finished sync, store result if needed
      syncUtil.storeSynchResults(time, CorporationSyncTracker.class, Corporation.class, syncAccount, status, errorDetail, nextExpiry, description, updateList,
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
      // Verify we havnen't already updated this state.
      SyncStatus preCheck = syncUtil.preSyncCheck(CorporationSyncTracker.class, Corporation.class, syncAccount, description, this);
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
      syncUtil.storeSynchResults(0L, CorporationSyncTracker.class, Corporation.class, syncAccount, status, errorDetail, -1, description, null, this);

      return SyncStatus.DONE;

    } catch (IOException e) {
      // Log but give us another shot in the queue
      log.warning("store failed with error " + e);
      return SyncStatus.ERROR;
    }

  }

}
