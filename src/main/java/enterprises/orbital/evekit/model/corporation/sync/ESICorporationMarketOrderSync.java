package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MarketApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOrders200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOrdersHistory200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.MarketOrder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

public class ESICorporationMarketOrderSync extends AbstractESIAccountSync<ESICorporationMarketOrderSync.OrderSet> {
  protected static final Logger log = Logger.getLogger(ESICorporationMarketOrderSync.class.getName());

  class OrderSet {
    List<GetCorporationsCorporationIdOrders200Ok> liveOrders;
    List<GetCorporationsCorporationIdOrdersHistory200Ok> historicalOrders;
  }

  public ESICorporationMarketOrderSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_MARKET;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof MarketOrder;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = MarketOrder.get(account, time, ((MarketOrder) item).getOrderID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<OrderSet> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    OrderSet orders = new OrderSet();
    MarketApi apiInstance = cp.getMarketApi();

    // Retrieve live orders
    Pair<Long, List<GetCorporationsCorporationIdOrders200Ok>> liveResult = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdOrdersWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });
    long expiry = liveResult.getLeft() > 0 ? liveResult.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    orders.liveOrders = liveResult.getRight();

    // Retrieve historical orders
    Pair<Long, List<GetCorporationsCorporationIdOrdersHistory200Ok>> histResult = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdOrdersHistoryWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });
    expiry = histResult.getLeft() > 0 ? Math.max(histResult.getLeft(), expiry) : expiry;
    orders.historicalOrders = histResult.getRight();

    return new ESIAccountServerResult<>(expiry, orders);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<OrderSet> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record orders
    if (data.getData().liveOrders != null)
      for (GetCorporationsCorporationIdOrders200Ok next : data.getData().liveOrders) {
        MarketOrder nextOrder = new MarketOrder(next.getOrderId(), next.getWalletDivision(), next.getIsBuyOrder(), 0,
                                                next.getDuration(),
                                                BigDecimal.valueOf(next.getEscrow())
                                                          .setScale(2, RoundingMode.HALF_UP),
                                                next.getIssued()
                                                    .getMillis(), next.getMinVolume(),
                                                next.getState()
                                                    .toString(), BigDecimal.valueOf(next.getPrice())
                                                                           .setScale(2, RoundingMode.HALF_UP),
                                                next.getRange()
                                                    .toString(), next.getTypeId(), next.getVolumeTotal(),
                                                next.getVolumeRemain(),
                                                next.getRegionId(), next.getLocationId(), true);
        updates.add(nextOrder);
      }

    if (data.getData().historicalOrders != null)
      for (GetCorporationsCorporationIdOrdersHistory200Ok next : data.getData().historicalOrders) {
        MarketOrder nextOrder = new MarketOrder(next.getOrderId(), next.getWalletDivision(), next.getIsBuyOrder(), 0,
                                                next.getDuration(),
                                                BigDecimal.valueOf(next.getEscrow())
                                                          .setScale(2, RoundingMode.HALF_UP),
                                                next.getIssued()
                                                    .getMillis(), next.getMinVolume(),
                                                next.getState()
                                                    .toString(), BigDecimal.valueOf(next.getPrice())
                                                                           .setScale(2, RoundingMode.HALF_UP),
                                                next.getRange()
                                                    .toString(), next.getTypeId(), next.getVolumeTotal(),
                                                next.getVolumeRemain(),
                                                next.getRegionId(), next.getLocationId(), true);
        updates.add(nextOrder);
      }

  }


}
