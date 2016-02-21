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
import enterprises.orbital.evekit.model.common.Standing;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IStanding;
import enterprises.orbital.evexmlapi.shared.IStandingSet;

public class CharacterStandingSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterStandingSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getStandingsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setStandingsStatus(status);
    tracker.setStandingsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setStandingsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getStandingsExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
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
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestStandings();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
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

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterStandingSync syncher = new CharacterStandingSync();

  public static SyncStatus syncStanding(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterStandings");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterStandings", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterStandings", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
