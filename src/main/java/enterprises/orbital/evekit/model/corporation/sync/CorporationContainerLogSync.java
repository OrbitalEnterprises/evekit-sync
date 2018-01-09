package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.ContainerLog;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.IContainerLog;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;

public class CorporationContainerLogSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationContainerLogSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getContainerLogStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setContainerLogStatus(status);
    tracker.setContainerLogDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) throws IOException {
    container.setContainerLogExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getContainerLogExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof ContainerLog;

    ContainerLog api = (ContainerLog) item;
    ContainerLog existing = ContainerLog.get(accountKey, time, api.getLogTime());

    if (existing != null) {
      if (!existing.equivalent(api)) {
        // Evolve
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
  protected Object getServerData(
                                 ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestContainerLogs();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IContainerLog> logs = (Collection<IContainerLog>) data;

    for (IContainerLog next : logs) {
      ContainerLog newLog = new ContainerLog(
          ModelUtil.safeConvertDate(next.getLogTime()), next.getAction(), next.getActorID(), next.getActorName(), next.getFlag() & 0xFF, next.getItemID(),
          next.getItemTypeID(), next.getLocationID(), next.getNewConfiguration(), next.getOldConfiguration(), next.getPasswordType(), next.getQuantity(),
          next.getTypeID());
      updates.add(newLog);
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationContainerLogSync syncher = new CorporationContainerLogSync();

  public static SyncStatus syncCorporationContainerLog(
                                                       long time,
                                                       SynchronizedEveAccount syncAccount,
                                                       SynchronizerUtil syncUtil,
                                                       ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationContainerLog");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContainerLog", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContainerLog", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
