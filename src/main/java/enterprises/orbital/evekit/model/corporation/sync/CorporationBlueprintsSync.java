package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
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
import enterprises.orbital.evekit.model.common.Blueprint;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IBlueprint;

public class CorporationBlueprintsSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationBlueprintsSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getBlueprintsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setBlueprintsStatus(status);
    tracker.setBlueprintsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setBlueprintsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getBlueprintsExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof Blueprint;

    Blueprint api = (Blueprint) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Blueprint to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Blueprint existing = Blueprint.get(accountKey, time, api.getItemID());
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
    return corpRequest.requestBlueprints();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IBlueprint> blueprints = (Collection<IBlueprint>) data;

    Set<Long> blueprintSet = new HashSet<Long>();
    for (IBlueprint next : blueprints) {
      Blueprint newBlueprint = new Blueprint(
          next.getItemID(), next.getLocationID(), next.getTypeID(), next.getTypeName(), next.getFlagID(), next.getQuantity(), next.getTimeEfficiency(),
          next.getMaterialEfficiency(), next.getRuns());
      updates.add(newBlueprint);
      blueprintSet.add(newBlueprint.getItemID());
    }

    // From the set of all blueprints, find blueprints no longer in the list.
    // These should all be EOL
    long contid = -1;
    List<Blueprint> nextBatch = Blueprint.getAllBlueprints(syncAccount, time, 1000, contid);
    while (!nextBatch.isEmpty()) {
      for (Blueprint next : nextBatch) {
        long itemID = next.getItemID();
        if (!blueprintSet.contains(itemID)) {
          next.evolve(null, time);
          updates.add(next);
        }
        contid = Math.max(contid, itemID);
      }
      nextBatch = Blueprint.getAllBlueprints(syncAccount, time, 1000, contid);
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationBlueprintsSync syncher = new CorporationBlueprintsSync();

  public static SyncStatus syncCorporationBlueprints(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationBlueprints");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationBlueprints", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationBlueprints", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
