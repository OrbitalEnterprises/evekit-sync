package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.RoundingMode;
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
import enterprises.orbital.evekit.model.common.IndustryJob;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IIndustryJob;

public class CorporationIndustryJobsSync extends AbstractCorporationSync {
  protected static final Logger log = Logger.getLogger(CorporationIndustryJobsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getIndustryJobsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setIndustryJobsStatus(status);
    tracker.setIndustryJobsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) throws IOException {
    container.setIndustryJobsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getIndustryJobsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof IndustryJob;

    IndustryJob api = (IndustryJob) item;
    IndustryJob existing = IndustryJob.get(accountKey, time, api.getJobID());

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
                                 ICorporationAPI corpRequest)
    throws IOException {
    return corpRequest.requestIndustryJobs();
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
    Collection<IIndustryJob> jobs = (Collection<IIndustryJob>) data;

    for (IIndustryJob next : jobs) {
      IndustryJob instance = new IndustryJob(
          next.getJobID(), next.getInstallerID(), next.getInstallerName(), next.getFacilityID(), next.getSolarSystemID(), next.getSolarSystemName(),
          next.getStationID(), next.getActivityID(), next.getBlueprintID(), next.getBlueprintTypeID(), next.getBlueprintTypeName(),
          next.getBlueprintLocationID(), next.getOutputLocationID(), next.getRuns(), next.getCost().setScale(2, RoundingMode.HALF_UP), next.getTeamID(),
          next.getLicensedRuns(), next.getProbability(), next.getProductTypeID(), next.getProductTypeName(), next.getStatus(), next.getTimeInSeconds(),
          ModelUtil.safeConvertDate(next.getStartDate()), ModelUtil.safeConvertDate(next.getEndDate()), ModelUtil.safeConvertDate(next.getPauseDate()),
          ModelUtil.safeConvertDate(next.getCompletedDate()), next.getCompletedCharacterID(), next.getSuccessfulRuns());
      updates.add(instance);
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationIndustryJobsSync syncher = new CorporationIndustryJobsSync();

  public static SyncStatus syncCorporationIndustryJobs(
                                                       long time,
                                                       SynchronizedEveAccount syncAccount,
                                                       SynchronizerUtil syncUtil,
                                                       ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationIndustryJobs");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationIndustryJobs", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationIndustryJobs", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
