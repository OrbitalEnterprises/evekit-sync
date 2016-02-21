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
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.Facility;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IFacility;

public class CorporationFacilitiesSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationFacilitiesSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getFacilitiesStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setFacilitiesStatus(status);
    tracker.setFacilitiesDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setFacilitiesExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getFacilitiesExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof Facility;

    Facility api = (Facility) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Facility to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Facility existing = Facility.get(accountKey, time, api.getFacilityID());
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
    return corpRequest.requestFacilities();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IFacility> facilities = (Collection<IFacility>) data;

    Set<Long> usedFacilities = new HashSet<Long>();

    for (IFacility next : facilities) {
      Facility newFacility = new Facility(
          next.getFacilityID(), next.getTypeID(), next.getTypeName(), next.getSolarSystemID(), next.getSolarSystemName(), next.getRegionID(),
          next.getRegionName(), next.getStarbaseModifier(), next.getTax());
      usedFacilities.add(next.getFacilityID());
      updates.add(newFacility);
    }

    for (Facility existing : Facility.getAll(syncAccount, time)) {
      if (!usedFacilities.contains(existing.getFacilityID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationFacilitiesSync syncher = new CorporationFacilitiesSync();

  public static SyncStatus syncFacilities(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationFacilites");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationFacilities", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationFacilities", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
