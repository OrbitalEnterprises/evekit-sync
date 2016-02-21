package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.FacWarStats;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IFacWarStats;

public class CharacterFacWarStatsSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterFacWarStatsSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getFacWarStatsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setFacWarStatsStatus(status);
    tracker.setFacWarStatsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setFacWarStatsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getFacWarStatsExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
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
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestFacWarStats();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    IFacWarStats stats = (IFacWarStats) data;

    FacWarStats instance = new FacWarStats(
        stats.getCurrentRank(), stats.getEnlisted().getTime(), stats.getFactionID(), stats.getFactionName(), stats.getHighestRank(), stats.getKillsLastWeek(),
        stats.getKillsTotal(), stats.getKillsYesterday(), stats.getPilots(), stats.getVictoryPointsLastWeek(), stats.getVictoryPointsTotal(),
        stats.getVictoryPointsYesterday());

    updates.add(instance);
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterFacWarStatsSync syncher = new CharacterFacWarStatsSync();

  public static SyncStatus syncFacWarStats(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterFacWarStats");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterFacWarStats", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterFacWarStats", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
