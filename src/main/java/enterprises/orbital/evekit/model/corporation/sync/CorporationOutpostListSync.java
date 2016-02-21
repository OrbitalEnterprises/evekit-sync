package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.Outpost;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IOutpost;

public class CorporationOutpostListSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationOutpostListSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getOutpostListStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setOutpostListStatus(status);
    tracker.setOutpostListDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setOutpostListExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getOutpostListExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof Outpost;

    Outpost api = (Outpost) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Outpost to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Outpost existing = Outpost.get(accountKey, time, api.getStationID());
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
    }
    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestOutpostList();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IOutpost> outposts = (Collection<IOutpost>) data;

    Set<Long> usedOutposts = new HashSet<Long>();

    for (IOutpost next : outposts) {
      Outpost newOutpost = new Outpost(
          next.getStationID(), next.getOwnerID(), next.getStationName(), next.getSolarSystemID(),
          (new BigDecimal(next.getDockingCostPerShipVolume())).setScale(2, RoundingMode.HALF_UP),
          (new BigDecimal(next.getOfficeRentalCost())).setScale(2, RoundingMode.HALF_UP), next.getStationTypeID(), next.getReprocessingEfficiency(),
          next.getReprocessingStationTake(), next.getStandingOwnerID(), next.getX(), next.getY(), next.getZ());
      usedOutposts.add(next.getStationID());
      updates.add(newOutpost);
    }

    // EOL outposts no longer in use
    for (Outpost existing : Outpost.getAll(syncAccount, time)) {
      if (!usedOutposts.contains(existing.getStationID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationOutpostListSync syncher = new CorporationOutpostListSync();

  public static SyncStatus syncOutpostList(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "OutpostList");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "OutpostList", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "OutpostList", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
