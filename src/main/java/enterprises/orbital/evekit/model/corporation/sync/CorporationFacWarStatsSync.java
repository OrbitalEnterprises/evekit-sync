package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
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
import enterprises.orbital.evekit.model.common.FacWarStats;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IFacWarStats;

public class CorporationFacWarStatsSync extends AbstractCorporationSync {
  protected static final Logger log = Logger.getLogger(CorporationFacWarStatsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getFacWarStatsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setFacWarStatsStatus(status);
    tracker.setFacWarStatsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setFacWarStatsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getFacWarStatsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof FacWarStats;

    FacWarStats api = (FacWarStats) item;
    FacWarStats existing = FacWarStats.get(accountKey, time);

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
    return corpRequest.requestFacWarStats();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    IFacWarStats stats = (IFacWarStats) data;

    FacWarStats instance = new FacWarStats(
        stats.getCurrentRank(), ModelUtil.safeConvertDate(stats.getEnlisted()), stats.getFactionID(), stats.getFactionName(), stats.getHighestRank(),
        stats.getKillsLastWeek(), stats.getKillsTotal(), stats.getKillsYesterday(), stats.getPilots(), stats.getVictoryPointsLastWeek(),
        stats.getVictoryPointsTotal(), stats.getVictoryPointsYesterday());

    updates.add(instance);
    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationFacWarStatsSync syncher = new CorporationFacWarStatsSync();

  public static SyncStatus syncFacWarStats(
                                           long time,
                                           SynchronizedEveAccount syncAccount,
                                           SynchronizerUtil syncUtil,
                                           ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationFacWarStats");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationFacWarStats", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationFacWarStats", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
