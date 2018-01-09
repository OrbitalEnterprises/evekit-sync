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
import enterprises.orbital.evekit.model.corporation.Shareholder;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IShareholder;

public class CorporationShareholdersSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationShareholdersSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getShareholderStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setShareholderStatus(status);
    tracker.setShareholderDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) throws IOException {
    container.setShareholdersExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getShareholdersExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) throws IOException {
    assert item instanceof Shareholder;

    Shareholder api = (Shareholder) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Shareholder to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Shareholder existing = Shareholder.get(accountKey, time, api.getShareholderID());
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
    return corpRequest.requestShareholders();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IShareholder> shareholders = (Collection<IShareholder>) data;

    Set<Long> usedShareholders = new HashSet<Long>();

    for (IShareholder next : shareholders) {
      Shareholder newShareholder = new Shareholder(
          next.getShareholderID(), next.isCorporation(), next.getShareholderCorporationID(), next.getShareholderCorporationName(), next.getShareholderName(),
          next.getShares());
      usedShareholders.add(next.getShareholderID());
      updates.add(newShareholder);
    }

    // EOL missing shareholders
    for (Shareholder existing : Shareholder.getAll(syncAccount, time)) {
      if (!usedShareholders.contains(existing.getShareholderID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationShareholdersSync syncher = new CorporationShareholdersSync();

  public static SyncStatus syncShareholders(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationShareholders");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationShareholders", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationShareholders", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
