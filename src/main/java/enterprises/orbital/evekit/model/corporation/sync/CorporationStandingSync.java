package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.Standing;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IStanding;
import enterprises.orbital.evexmlapi.shared.IStandingSet;

public class CorporationStandingSync extends AbstractCorporationSync {
  protected static final Logger log = Logger.getLogger(CorporationStandingSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getStandingsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setStandingsStatus(status);
    tracker.setStandingsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setStandingsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getStandingsExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof Standing;

    Standing api = (Standing) item;
    Standing existing = Standing.get(accountKey, time, api.getStandingEntity(), api.getFromID());

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
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestStandings();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    IStandingSet standings = (IStandingSet) data;

    // Update standings. Standings are additive, so we don't ever have to worry about removing old standings.
    for (IStanding nextStanding : standings.getAgentStandings()) {
      Standing newStanding = new Standing("AGENT", nextStanding.getFromID(), nextStanding.getFromName(), nextStanding.getStanding());
      updates.add(newStanding);
    }
    for (IStanding nextStanding : standings.getFactionStandings()) {
      Standing newStanding = new Standing("FACTION", nextStanding.getFromID(), nextStanding.getFromName(), nextStanding.getStanding());
      updates.add(newStanding);
    }
    for (IStanding nextStanding : standings.getNPCCorporationStandings()) {
      Standing newStanding = new Standing("NPC_CORPORATION", nextStanding.getFromID(), nextStanding.getFromName(), nextStanding.getStanding());
      updates.add(newStanding);
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationStandingSync syncher = new CorporationStandingSync();

  public static SyncStatus syncStanding(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationStandings");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationStandings", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationStandings", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
