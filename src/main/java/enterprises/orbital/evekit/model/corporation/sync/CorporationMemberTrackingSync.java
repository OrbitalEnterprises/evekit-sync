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
import enterprises.orbital.evekit.model.corporation.MemberTracking;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberTracking;

public class CorporationMemberTrackingSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationMemberTrackingSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getMemberTrackingStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setMemberTrackingStatus(status);
    tracker.setMemberTrackingDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setMemberTrackingExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getMemberTrackingExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof MemberTracking;

    MemberTracking api = (MemberTracking) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing MemberTracking to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      MemberTracking existing = MemberTracking.get(accountKey, time, api.getCharacterID());
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
    return corpRequest.requestMemberTracking();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IMemberTracking> tracks = (Collection<IMemberTracking>) data;

    Set<Long> members = new HashSet<Long>();

    for (IMemberTracking next : tracks) {
      MemberTracking newTracking = new MemberTracking(
          next.getCharacterID(), next.getBase(), next.getBaseID(), next.getGrantableRoles(), next.getLocation(), next.getLocationID(),
          ModelUtil.safeConvertDate(next.getLogoffDateTime()), ModelUtil.safeConvertDate(next.getLogonDateTime()), next.getName(), next.getRoles(),
          next.getShipType(), next.getShipTypeID(), ModelUtil.safeConvertDate(next.getStartDateTime()), next.getTitle());
      members.add(next.getCharacterID());
      updates.add(newTracking);
    }

    // Schedule any missing members for EOL.
    long contid = -1;
    List<MemberTracking> nextBatch = MemberTracking.getAll(syncAccount, time, 1000, contid);
    while (!nextBatch.isEmpty()) {
      for (MemberTracking existing : nextBatch) {
        if (!members.contains(existing.getCharacterID())) {
          existing.evolve(null, time);
          updates.add(existing);
        }
        contid = Math.max(contid, existing.getCharacterID());
      }
      nextBatch = MemberTracking.getAll(syncAccount, time, 1000, contid);
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationMemberTrackingSync syncher = new CorporationMemberTrackingSync();

  public static SyncStatus syncCorporationMemberTracking(
                                                         long time,
                                                         SynchronizedEveAccount syncAccount,
                                                         SynchronizerUtil syncUtil,
                                                         ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationMemberTracking");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberTracking", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberTracking", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
