package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evekit.model.common.Location;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.ILocation;

public class CorporationLocationsSync extends AbstractCorporationSync {
  protected static final int    LOCATION_BATCH_SIZE = (int) PersistentProperty
      .getLongPropertyWithFallback(OrbitalProperties.getPropertyName(CorporationLocationsSync.class, "batchSize"), 100);
  protected static final Logger log                 = Logger.getLogger(CorporationLocationsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getLocationsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setLocationsStatus(status);
    tracker.setLocationsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setLocationsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getLocationsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof Location;

    Location api = (Location) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Location to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Location existing = Location.get(accountKey, time, api.getItemID());
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

  private long                   requestTime;
  private SynchronizedEveAccount requestOwner;

  @Override
  protected Object getServerData(
                                 ICorporationAPI corpRequest)
    throws IOException {
    // Request locations in batches of 500 based on current asset map
    List<ILocation> result = new ArrayList<>();
    long contid = -1;
    List<Asset> retrieved = Asset.getAllAssets(requestOwner, requestTime, 1000, contid);
    while (retrieved.size() > 0) {
      for (int i = 0; i < retrieved.size(); i += LOCATION_BATCH_SIZE) {
        int nextBatchSize = Math.min(retrieved.size(), i + LOCATION_BATCH_SIZE) - i;
        long[] nextFetch = new long[nextBatchSize];
        int j = 0;
        for (Asset next : retrieved.subList(i, i + nextBatchSize))
          nextFetch[j++] = next.getItemID();
        result.addAll(corpRequest.requestLocations(nextFetch));
        if (corpRequest.isError())
          // Break out if a request fails
          return result;
      }
      contid = retrieved.get(retrieved.size() - 1).getItemID();
      retrieved = Asset.getAllAssets(requestOwner, requestTime, 1000, contid);
    }
    return result;
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
    Collection<ILocation> locations = (Collection<ILocation>) data;

    Set<Long> locationSet = new HashSet<Long>();
    for (ILocation next : locations) {
      Location newLocation = new Location(next.getItemID(), next.getItemName(), next.getX(), next.getY(), next.getZ());
      updates.add(newLocation);
      locationSet.add(newLocation.getItemID());
    }

    // From the set of all locations, find locations no longer in the list.
    // These should all be EOL
    long contid = -1;
    List<Location> nextBatch = Location.getAllLocations(syncAccount, time, 1000, contid);
    while (!nextBatch.isEmpty()) {
      for (Location next : nextBatch) {
        long itemID = next.getItemID();
        if (!locationSet.contains(itemID)) {
          next.evolve(null, time);
          updates.add(next);
        }
      }
      contid = nextBatch.get(nextBatch.size() - 1).getItemID();
      nextBatch = Location.getAllLocations(syncAccount, time, 1000, contid);
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationLocationsSync syncher = new CorporationLocationsSync();

  public static SyncStatus syncCorporationLocations(
                                                    long time,
                                                    SynchronizedEveAccount syncAccount,
                                                    SynchronizerUtil syncUtil,
                                                    ICorporationAPI corpRequest) {
    syncher.requestOwner = syncAccount;
    syncher.requestTime = time;
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationLocations");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationLocations", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationLocations", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
