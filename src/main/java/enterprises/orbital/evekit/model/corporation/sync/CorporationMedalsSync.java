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
import enterprises.orbital.evekit.model.corporation.CorporationMedal;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.ICorporationMedal;

public class CorporationMedalsSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationMedalsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getCorpMedalsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setCorpMedalsStatus(status);
    tracker.setCorpMedalsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) throws IOException {
    container.setMedalsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getMedalsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof CorporationMedal;

    CorporationMedal api = (CorporationMedal) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing CorporationMedal to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      CorporationMedal existing = CorporationMedal.get(accountKey, time, api.getMedalID());
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
    return corpRequest.requestMedals();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<ICorporationMedal> medals = (Collection<ICorporationMedal>) data;

    Set<Integer> usedMedals = new HashSet<Integer>();

    for (ICorporationMedal next : medals) {
      CorporationMedal newMedal = new CorporationMedal(
          next.getMedalID(), next.getDescription(), next.getTitle(), ModelUtil.safeConvertDate(next.getCreated()), next.getCreatorID());
      usedMedals.add(next.getMedalID());
      updates.add(newMedal);
    }

    for (CorporationMedal existing : CorporationMedal.getAll(syncAccount, time)) {
      if (!usedMedals.contains(existing.getMedalID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationMedalsSync syncher = new CorporationMedalsSync();

  public static SyncStatus syncCorporationMedals(
                                                 long time,
                                                 SynchronizedEveAccount syncAccount,
                                                 SynchronizerUtil syncUtil,
                                                 ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationMedals");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMedals", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMedals", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
