package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.MarketOrder;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IMarketOrder;

public class CorporationMarketOrderSync extends AbstractCorporationSync {
  protected static final Logger log = Logger.getLogger(CorporationIndustryJobsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getMarketOrdersStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setMarketOrdersStatus(status);
    tracker.setMarketOrdersDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setMarketOrdersExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getMarketOrdersExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof MarketOrder;

    MarketOrder api = (MarketOrder) item;
    MarketOrder existing = MarketOrder.get(accountKey, time, api.getOrderID());

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
    return corpRequest.requestMarketOrders();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IMarketOrder> orders = (Collection<IMarketOrder>) data;

    Set<Long> seenOrders = new HashSet<Long>();
    for (IMarketOrder next : orders) {
      seenOrders.add(next.getOrderID());
      MarketOrder instance = new MarketOrder(
          next.getOrderID(), next.getAccountKey(), next.getBid() != 0, next.getCharID(), next.getDuration(), next.getEscrow().setScale(2, RoundingMode.HALF_UP),
          ModelUtil.safeConvertDate(next.getIssued()), next.getMinVolume(), next.getOrderState(), next.getPrice().setScale(2, RoundingMode.HALF_UP),
          next.getRange(), next.getStationID(), next.getTypeID(), next.getVolEntered(), next.getVolRemaining());
      updates.add(instance);
    }

    // Batch retrieval of market orders will only return live orders. To update non-live orders
    // we need to request them individually. We do this by looking up live orders for the last 180 days.
    // If we find a live order not included in the current set of market orders then we retrieve it and
    // add it to the update list.
    final long millisPerDay = 24 * 60 * 60 * 1000L;
    long refTime = OrbitalProperties.getCurrentTime();
    for (MarketOrder next : MarketOrder.getAllActive(syncAccount, time, 10000, refTime - 180 * millisPerDay, refTime, 180)) {
      if (seenOrders.contains(next.getOrderID())) continue;
      // Possible this order was updated, attempt to retrieve. If null, then we weren't able to retrieve
      // this order, possibly because it has fallen out of the EVE Online cache. Just ignore in that
      // case.
      IMarketOrder updatedOrder = corpRequest.requestMarketOrder(next.getOrderID());
      if (updatedOrder != null) {
        MarketOrder instance = new MarketOrder(
            updatedOrder.getOrderID(), updatedOrder.getAccountKey(), updatedOrder.getBid() != 0, updatedOrder.getCharID(), updatedOrder.getDuration(),
            updatedOrder.getEscrow().setScale(2, RoundingMode.HALF_UP), ModelUtil.safeConvertDate(updatedOrder.getIssued()), updatedOrder.getMinVolume(),
            updatedOrder.getOrderState(), updatedOrder.getPrice().setScale(2, RoundingMode.HALF_UP), updatedOrder.getRange(), updatedOrder.getStationID(),
            updatedOrder.getTypeID(), updatedOrder.getVolEntered(), updatedOrder.getVolRemaining());
        updates.add(instance);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationMarketOrderSync syncher = new CorporationMarketOrderSync();

  public static SyncStatus syncCorporationMarketOrders(
                                                       long time,
                                                       SynchronizedEveAccount syncAccount,
                                                       SynchronizerUtil syncUtil,
                                                       ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationMarketOrders");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMarketOrders", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMarketOrders", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
