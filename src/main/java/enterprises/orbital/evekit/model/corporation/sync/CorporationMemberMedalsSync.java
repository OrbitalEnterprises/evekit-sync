package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
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
import enterprises.orbital.evekit.model.corporation.CorporationMemberMedal;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberMedal;

public class CorporationMemberMedalsSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationMemberMedalsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getMemberMedalsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setMemberMedalsStatus(status);
    tracker.setMemberMedalsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setMemberMedalsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getMemberMedalsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof CorporationMemberMedal;

    CorporationMemberMedal api = (CorporationMemberMedal) item;
    CorporationMemberMedal existing = CorporationMemberMedal.get(accountKey, time, api.getMedalID(), api.getCharacterID(), api.getIssued());

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
  protected Object getServerData(
                                 ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestMemberMedals();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IMemberMedal> medals = (Collection<IMemberMedal>) data;

    for (IMemberMedal next : medals) {
      CorporationMemberMedal newMedal = new CorporationMemberMedal(
          next.getMedalID(), next.getCharacterID(), ModelUtil.safeConvertDate(next.getIssued()), next.getIssuerID(), next.getReason(), next.getStatus());
      updates.add(newMedal);
    }

    return corpRequest.getCachedUntil().getTime();

  }

  private static final CorporationMemberMedalsSync syncher = new CorporationMemberMedalsSync();

  public static SyncStatus syncCorporationMemberMedals(
                                                       long time,
                                                       SynchronizedEveAccount syncAccount,
                                                       SynchronizerUtil syncUtil,
                                                       ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationMemberMedals");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberMedals", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberMedals", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
