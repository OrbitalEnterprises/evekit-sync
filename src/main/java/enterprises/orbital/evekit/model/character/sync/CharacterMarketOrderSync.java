package enterprises.orbital.evekit.model.character.sync;

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
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.MarketOrder;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IMarketOrder;

public class CharacterMarketOrderSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterMarketOrderSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getMarketOrdersStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setMarketOrdersStatus(status);
    tracker.setMarketOrdersDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setMarketOrdersExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getMarketOrdersExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
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
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestMarketOrders();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
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
      IMarketOrder updatedOrder = charRequest.requestMarketOrder(next.getOrderID());
      if (updatedOrder != null) {
        // public MarketOrder(long orderID, int accountKey, boolean bid, long charID, int duration, BigDecimal escrow, long issued, int minVolume, int
        // orderState,
        // BigDecimal price, int orderRange, long stationID, int typeID, int volEntered, int volRemaining) {
        MarketOrder instance = new MarketOrder(
            updatedOrder.getOrderID(), updatedOrder.getAccountKey(), updatedOrder.getBid() != 0, updatedOrder.getCharID(), updatedOrder.getDuration(),
            updatedOrder.getEscrow().setScale(2, RoundingMode.HALF_UP), ModelUtil.safeConvertDate(updatedOrder.getIssued()), updatedOrder.getMinVolume(),
            updatedOrder.getOrderState(), updatedOrder.getPrice().setScale(2, RoundingMode.HALF_UP), updatedOrder.getRange(), updatedOrder.getStationID(),
            updatedOrder.getTypeID(), updatedOrder.getVolEntered(), updatedOrder.getVolRemaining());
        updates.add(instance);
      }
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterMarketOrderSync syncher = new CharacterMarketOrderSync();

  public static SyncStatus syncCharacterMarketOrders(
                                                     long time,
                                                     SynchronizedEveAccount syncAccount,
                                                     SynchronizerUtil syncUtil,
                                                     ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterMarketOrders");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterMarketOrders", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterMarketOrders", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
