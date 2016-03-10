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
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.Starbase;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IStarbase;

public class CorporationStarbaseListSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationStarbaseListSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getStarbaseListStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setStarbaseListStatus(status);
    tracker.setStarbaseListDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setStarbaseListExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getStarbaseListExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof Starbase;

    Starbase api = (Starbase) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Starbase to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Starbase existing = Starbase.get(accountKey, time, api.getItemID());
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
  protected Object getServerData(
                                 ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestStarbaseList();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IStarbase> bases = (Collection<IStarbase>) data;

    Set<Long> usedBases = new HashSet<Long>();

    for (IStarbase next : bases) {
      usedBases.add(next.getItemID());
      Starbase newBase = new Starbase(
          next.getItemID(), next.getLocationID(), next.getMoonID(), ModelUtil.safeConvertDate(next.getOnlineTimestamp()), next.getState(),
          ModelUtil.safeConvertDate(next.getStateTimestamp()), next.getTypeID(), next.getStandingOwnerID());
      updates.add(newBase);
    }

    // EOL missing starbases
    for (Starbase existing : Starbase.getAll(syncAccount, time)) {
      if (!usedBases.contains(existing.getItemID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationStarbaseListSync syncher = new CorporationStarbaseListSync();

  public static SyncStatus syncStarbaseList(
                                            long time,
                                            SynchronizedEveAccount syncAccount,
                                            SynchronizerUtil syncUtil,
                                            ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "StarbaseList");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "StarbaseList", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "StarbaseList", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
