package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.IndustryJob;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IIndustryJob;

public class CharacterIndustryJobsHistorySync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterIndustryJobsHistorySync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getIndustryJobsHistoryStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setIndustryJobsHistoryStatus(status);
    tracker.setIndustryJobsHistoryDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setIndustryJobsHistoryExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getIndustryJobsHistoryExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
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
    } // else skip: history sync only updates existing jobs

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest)
    throws IOException {
    return charRequest.requestIndustryJobsHistory();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IIndustryJob> jobs = (Collection<IIndustryJob>) data;

    for (IIndustryJob next : jobs) {
      if (ModelUtil.safeConvertDate(next.getCompletedDate()) > 0 && next.getCompletedCharacterID() != 0) {
        // Only include completed jobs.
        IndustryJob instance = new IndustryJob(
            next.getJobID(), next.getInstallerID(), next.getInstallerName(), next.getFacilityID(), next.getSolarSystemID(), next.getSolarSystemName(),
            next.getStationID(), next.getActivityID(), next.getBlueprintID(), next.getBlueprintTypeID(), next.getBlueprintTypeName(),
            next.getBlueprintLocationID(), next.getOutputLocationID(), next.getRuns(), next.getCost().setScale(2, RoundingMode.HALF_UP), next.getTeamID(),
            next.getLicensedRuns(), next.getProbability(), next.getProductTypeID(), next.getProductTypeName(), next.getStatus(), next.getTimeInSeconds(),
            ModelUtil.safeConvertDate(next.getStartDate()), ModelUtil.safeConvertDate(next.getEndDate()), ModelUtil.safeConvertDate(next.getPauseDate()),
            ModelUtil.safeConvertDate(next.getCompletedDate()), next.getCompletedCharacterID(), next.getSuccessfulRuns());
        updates.add(instance);
      }
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterIndustryJobsHistorySync syncher = new CharacterIndustryJobsHistorySync();

  public static SyncStatus syncCharacterIndustryJobsHistory(
                                                            long time,
                                                            SynchronizedEveAccount syncAccount,
                                                            SynchronizerUtil syncUtil,
                                                            ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterIndustryJobsHistory");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterIndustryJobsHistory", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterIndustryJobsHistory", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
