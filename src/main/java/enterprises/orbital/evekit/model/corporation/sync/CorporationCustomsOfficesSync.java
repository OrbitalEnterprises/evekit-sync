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
import enterprises.orbital.evekit.model.corporation.CustomsOffice;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.ICustomsOffice;

public class CorporationCustomsOfficesSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationCustomsOfficesSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getCustomsOfficeStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setCustomsOfficeStatus(status);
    tracker.setCustomsOfficeDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setCustomsOfficeExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getCustomsOfficeExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof CustomsOffice;

    CustomsOffice api = (CustomsOffice) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing CustomsOffice to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      CustomsOffice existing = CustomsOffice.get(accountKey, time, api.getItemID());
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
    return corpRequest.requestCustomsOffices();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<ICustomsOffice> offices = (Collection<ICustomsOffice>) data;

    Set<Long> usedOffices = new HashSet<Long>();

    for (ICustomsOffice next : offices) {
      CustomsOffice newOffice = new CustomsOffice(
          next.getItemID(), next.getSolarSystemID(), next.getSolarSystemName(), next.getReinforceHour(), next.isAllowAlliance(), next.isAllowStandings(),
          next.getStandingLevel(), next.getTaxRateAlliance(), next.getTaxRateCorp(), next.getTaxRateStandingHigh(), next.getTaxRateStandingGood(),
          next.getTaxRateStandingNeutral(), next.getTaxRateStandingBad(), next.getTaxRateStandingHorrible());
      usedOffices.add(next.getItemID());
      updates.add(newOffice);
    }

    for (CustomsOffice existing : CustomsOffice.getAll(syncAccount, time)) {
      if (!usedOffices.contains(existing.getItemID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationCustomsOfficesSync syncher = new CorporationCustomsOfficesSync();

  public static SyncStatus syncCustomsOffices(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationCustomsOffices");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationCustomsOffices", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationCustomsOffices", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
