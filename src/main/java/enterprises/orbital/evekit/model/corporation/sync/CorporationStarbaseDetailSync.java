package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import enterprises.orbital.evekit.model.corporation.Fuel;
import enterprises.orbital.evekit.model.corporation.StarbaseDetail;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IFuel;
import enterprises.orbital.evexmlapi.crp.IStarbase;
import enterprises.orbital.evexmlapi.crp.IStarbaseDetail;

public class CorporationStarbaseDetailSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationStarbaseDetailSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getStarbaseDetailStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setStarbaseDetailStatus(status);
    tracker.setStarbaseDetailDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setStarbaseDetailExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getStarbaseDetailExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    if (item instanceof StarbaseDetail) {
      StarbaseDetail api = (StarbaseDetail) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing StarbaseDetail to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        StarbaseDetail existing = StarbaseDetail.get(accountKey, time, api.getItemID());
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
    } else if (item instanceof Fuel) {
      Fuel api = (Fuel) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing Fuel to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        Fuel existing = Fuel.get(accountKey, time, api.getItemID(), api.getTypeID());
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
    } else {
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    Map<Long, IStarbaseDetail> detailMap = new HashMap<Long, IStarbaseDetail>();
    Collection<IStarbase> starbaseList = corpRequest.requestStarbaseList();
    if (corpRequest.isError()) return null;
    for (IStarbase starbase : starbaseList) {
      detailMap.put(starbase.getItemID(), corpRequest.requestStarbaseDetail(starbase.getItemID()));
      if (corpRequest.isError()) return null;
    }
    return detailMap;
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Map<Long, IStarbaseDetail> details = (Map<Long, IStarbaseDetail>) data;

    Set<Long> usedDetails = new HashSet<Long>();

    for (Entry<Long, IStarbaseDetail> nextPair : details.entrySet()) {
      IStarbaseDetail next = nextPair.getValue();
      StarbaseDetail newDetail = new StarbaseDetail(
          nextPair.getKey(), next.getState(), next.getStateTimestamp().getTime(), next.getOnlineTimestamp().getTime(), next.getUsageFlags(),
          next.getDeployFlags(), next.isAllowAllianceMembers(), next.isAllowCorporationMembers(), next.getUseStandingsFrom(),
          next.getOnAggression().isEnabled(), next.getOnAggression().getStanding(), next.getOnCorporationWar().isEnabled(),
          next.getOnCorporationWar().getStanding(), next.getOnStandingDrop().isEnabled(), next.getOnStandingDrop().getStanding(),
          next.getOnStatusDrop().isEnabled(), next.getOnStatusDrop().getStanding());
      // Handle new Fuel entries
      for (IFuel nextFuel : next.getFuelMap()) {
        Fuel newFuel = new Fuel(nextPair.getKey(), nextFuel.getTypeID(), nextFuel.getQuantity());
        updates.add(newFuel);
      }
      usedDetails.add(nextPair.getKey());
      updates.add(newDetail);
    }

    // EOL starbase details and fuel no longer in use
    for (StarbaseDetail existing : StarbaseDetail.getAll(syncAccount, time)) {
      long itemID = existing.getItemID();
      if (!usedDetails.contains(itemID)) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
    for (Fuel existing : Fuel.getAll(syncAccount, time)) {
      long itemID = existing.getItemID();
      if (!usedDetails.contains(itemID)) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationStarbaseDetailSync syncher = new CorporationStarbaseDetailSync();

  public static SyncStatus syncStarbaseDetail(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "StarbaseDetail");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "StarbaseDetail", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "StarbaseDetail", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
