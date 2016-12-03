package enterprises.orbital.evekit.model.character.sync;

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
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evekit.model.common.Location;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.ILocation;

public class CharacterLocationsSync extends AbstractCharacterSync {
  protected static final int    LOCATION_BATCH_SIZE = (int) PersistentProperty
      .getLongPropertyWithFallback(OrbitalProperties.getPropertyName(CharacterLocationsSync.class, "batchSize"), 100);
  protected static final Logger log                 = Logger.getLogger(CharacterLocationsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getLocationsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setLocationsStatus(status);
    tracker.setLocationsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setLocationsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getLocationsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
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
        // Existing, evolve if changed
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
                                 ICharacterAPI charRequest)
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
        Collection<ILocation> nextLocations = charRequest.requestLocations(nextFetch);
        if (charRequest.isError())
          // Break out if a request fails
          return result;
        result.addAll(nextLocations);
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
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<ILocation> locations = (Collection<ILocation>) data;
    // Add all retrieved locations to update set and prepare membership to check for locations no longer present
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
          // Location no longer present, retire
          next.evolve(null, time);
          updates.add(next);
        }
      }
      contid = nextBatch.get(nextBatch.size() - 1).getItemID();
      nextBatch = Location.getAllLocations(syncAccount, time, 1000, contid);
    }
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterLocationsSync syncher = new CharacterLocationsSync();

  public static SyncStatus syncCharacterLocations(
                                                  long time,
                                                  SynchronizedEveAccount syncAccount,
                                                  SynchronizerUtil syncUtil,
                                                  ICharacterAPI charRequest) {
    syncher.requestOwner = syncAccount;
    syncher.requestTime = time;
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterLocations");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterLocations", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterLocations", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
