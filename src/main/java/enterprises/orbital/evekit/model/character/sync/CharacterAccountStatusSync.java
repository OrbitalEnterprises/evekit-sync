package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.AccountStatus;
import enterprises.orbital.evexmlapi.act.IAccountAPI;
import enterprises.orbital.evexmlapi.act.IAccountStatus;
import enterprises.orbital.evexmlapi.act.IMultiCharacterTraining;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;

public class CharacterAccountStatusSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterAccountStatusSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getAccountStatusStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getAccountStatusExpiry();
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncTracker.SyncState status, String detail) {
    tracker.setAccountStatusStatus(status);
    tracker.setAccountStatusDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setAccountStatusExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof AccountStatus;

    AccountStatus api = (AccountStatus) item;
    AccountStatus existing = AccountStatus.get(accountKey, time);

    if (existing != null) {
      // Existing, evolve if changed
      if (!existing.equivalent(api)) {
        existing.evolve(api, time);
        super.commit(time, tracker, container, accountKey, existing);
        super.commit(time, tracker, container, accountKey, api);
      }
    } else {
      // New entity
      api.setup(accountKey, time);
      super.commit(time, tracker, container, accountKey, api);
    }

    return true;
  }

  @Override
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return acctRequester.requestAccountStatus();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {

    IAccountStatus statusResult = (IAccountStatus) data;

    AccountStatus status = new AccountStatus(
        statusResult.getPaidUntil().getTime(), statusResult.getCreateDate().getTime(), statusResult.getLogonCount(), statusResult.getLogonMinutes());
    for (IMultiCharacterTraining mct : statusResult.getMultiCharacterTraining()) {
      status.getMultiCharacterTraining().add(mct.getTrainingEnd().getTime());
    }
    updates.add(status);
    return acctRequester.getCachedUntil().getTime();
  }

  protected SyncTracker.SyncState localHandleServerError(IAccountAPI acctRequest, StringBuilder errorDetail) {
    switch (acctRequest.getErrorCode()) {
    case 222:
    case 221:
    case 220:
    case 124:
      // Treat all these errors as warnings
      errorDetail.append("Warning ").append(acctRequest.getErrorCode()).append(": ").append(acctRequest.getErrorString());
      return SyncTracker.SyncState.SYNC_WARNING;

    default:
      // Everything else is treated as an error
      errorDetail.append("Error ").append(acctRequest.getErrorCode()).append(": ").append(acctRequest.getErrorString());
      return SyncTracker.SyncState.SYNC_ERROR;
    }
  }

  // Override the inherited syncData because our result status is based on IAccountAPIRequest instead of ICharacterAPI
  @Override
  protected SyncStatus syncData(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest, String description) {

    try {
      // Run pre-check.
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, this);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;
      // Pre-check passed
      log.fine("Starting refresh request for " + description + " for account " + syncAccount);
      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      try {
        Object serverData = getServerData(charRequest);

        if (acctRequester.isError()) {
          StringBuilder errStr = new StringBuilder();
          status = localHandleServerError(acctRequester, errStr);
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

  private IAccountAPI acctRequester;

  public static SyncStatus syncAccountStatus(
                                             long time,
                                             SynchronizedEveAccount syncAccount,
                                             SynchronizerUtil syncUtil,
                                             ICharacterAPI charRequest,
                                             IAccountAPI acctRequest) {
    CharacterAccountStatusSync syncher = new CharacterAccountStatusSync();
    syncher.acctRequester = acctRequest;
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterAccountStatus");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    CharacterAccountStatusSync syncher = new CharacterAccountStatusSync();
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAccountStatus", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    CharacterAccountStatusSync syncher = new CharacterAccountStatusSync();
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAccountStatus", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
