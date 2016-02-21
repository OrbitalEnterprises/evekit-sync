package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import enterprises.orbital.evekit.model.corporation.OutpostServiceDetail;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IOutpost;
import enterprises.orbital.evexmlapi.crp.IOutpostServiceDetail;

public class CorporationOutpostServiceDetailSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationOutpostServiceDetailSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getOutpostDetailStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setOutpostDetailStatus(status);
    tracker.setOutpostDetailDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setOutpostServiceDetailExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getOutpostServiceDetailExpiry();
  }

  protected static boolean bigDecimalChanged(BigDecimal a, BigDecimal b) {
    if (a == b) { return false; }
    if (a == null || b == null) { return true; }
    return !a.equals(b);
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof OutpostServiceDetail;

    OutpostServiceDetail api = (OutpostServiceDetail) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing OutpostServiceDetail to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      OutpostServiceDetail existing = OutpostServiceDetail.get(accountKey, time, api.getStationID(), api.getServiceName());
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
    Collection<IOutpostServiceDetail> details = new ArrayList<IOutpostServiceDetail>();
    Collection<IOutpost> outpostList = corpRequest.requestOutpostList();
    if (corpRequest.isError()) return null;
    for (IOutpost outpost : outpostList) {
      details.addAll(corpRequest.requestOutpostServiceDetail(outpost.getStationID()));
      if (corpRequest.isError()) return null;
    }
    return details;
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IOutpostServiceDetail> details = (Collection<IOutpostServiceDetail>) data;

    Map<Long, Set<String>> usedDetails = new HashMap<Long, Set<String>>();

    for (IOutpostServiceDetail next : details) {
      OutpostServiceDetail newDetail = new OutpostServiceDetail(
          next.getStationID(), next.getServiceName(), next.getOwnerID(), next.getMinStanding(),
          (new BigDecimal(next.getSurchargePerBadStanding())).setScale(2, RoundingMode.HALF_UP),
          (new BigDecimal(next.getDiscountPerGoodStanding())).setScale(2, RoundingMode.HALF_UP));
      if (!usedDetails.containsKey(next.getStationID())) {
        usedDetails.put(next.getStationID(), new HashSet<String>());
      }
      usedDetails.get(next.getStationID()).add(next.getServiceName());
      updates.add(newDetail);
    }

    // EOL no longer used service details
    for (OutpostServiceDetail existing : OutpostServiceDetail.getAll(syncAccount, time)) {
      long stationID = existing.getStationID();
      String serviceName = existing.getServiceName();
      if (!usedDetails.containsKey(stationID) || !usedDetails.get(stationID).contains(serviceName)) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationOutpostServiceDetailSync syncher = new CorporationOutpostServiceDetailSync();

  public static SyncStatus syncOutpostServiceDetail(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "OutpostServiceDetail");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "OutpostServiceDetail", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "OutpostServiceDetail", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
