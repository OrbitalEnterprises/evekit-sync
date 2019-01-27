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
import java.util.*;
import java.util.concurrent.TimeUnit;
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

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<OrderSet> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    final String errTrap = "Character does not have required role";
    OrderSet orders = new OrderSet();
    MarketApi apiInstance = cp.getMarketApi();

    // Retrieve live orders
    Pair<Long, List<GetCorporationsCorporationIdOrders200Ok>> liveResult;
    try {
      liveResult = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdOrdersWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
    } catch (ApiException e) {
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        liveResult = Pair.of(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS),
                             Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
    long expiry = liveResult.getLeft() > 0 ? liveResult.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    orders.liveOrders = liveResult.getRight();

    // Retrieve historical orders
    Pair<Long, List<GetCorporationsCorporationIdOrdersHistory200Ok>> histResult;
    try {
      histResult = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdOrdersHistoryWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
    } catch (ApiException e) {
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        histResult = Pair.of(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS),
                             Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
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
    for (GetCorporationsCorporationIdOrders200Ok next : data.getData().liveOrders) {
      MarketOrder nextOrder = new MarketOrder(next.getOrderId(),
                                              next.getWalletDivision(),
                                              nullSafeBoolean(next.getIsBuyOrder(), false),
                                              0,
                                              next.getDuration(),
                                              BigDecimal.valueOf(
                                                  nullSafeDouble(next.getEscrow(), 0D))
                                                        .setScale(2, RoundingMode.HALF_UP),
                                              next.getIssued()
                                                  .getMillis(),
                                              next.getIssuedBy(),
                                              nullSafeInteger(next.getMinVolume(), 1),
                                              "open",
                                              BigDecimal.valueOf(next.getPrice())
                                                        .setScale(2, RoundingMode.HALF_UP),
                                              next.getRange()
                                                  .toString(),
                                              next.getTypeId(),
                                              next.getVolumeTotal(),
                                              next.getVolumeRemain(),
                                              next.getRegionId(),
                                              next.getLocationId(),
                                              true);
      updates.add(nextOrder);
    }

    // For efficiency, we do a bulk retrieve of all stored orders according to the history we just
    // retrieved.  This avoids an individual DB get call for each order we need to check.
    OptionalLong minResult = data.getData().historicalOrders.stream()
                                                            .mapToLong(
                                                                GetCorporationsCorporationIdOrdersHistory200Ok::getOrderId)
                                                            .min();
    Map<Long, MarketOrder> marketHistory = new HashMap<>();
    if (minResult.isPresent()) {
      long minOrderId = minResult.getAsLong();
      for (MarketOrder hval : retrieveAll(time,
                                          (contid, at) -> MarketOrder.accessQuery(account, contid, 1000, false, at,
                                                                                  AttributeSelector.range(minOrderId,
                                                                                                          Long.MAX_VALUE),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any()))) {
        marketHistory.put(hval.getOrderID(), hval);
      }
    }

    for (GetCorporationsCorporationIdOrdersHistory200Ok next : data.getData().historicalOrders) {
      // Only process order if we've already recorded this order.  This is necessary in order to account
      // for optional fields.
      MarketOrder existing = marketHistory.get(next.getOrderId());
      if (existing != null) {
        if (next.getPrice() != existing.getPrice()
                                       .doubleValue() ||
            next.getVolumeRemain() != existing.getVolRemaining() ||
            next.getIssued()
                .getMillis() != existing.getIssued() ||
            (next.getEscrow() != null && next.getEscrow() != existing.getEscrow()
                                                                     .doubleValue()) ||
            !next.getState()
                 .toString()
                 .equals(existing.getOrderState())) {
          MarketOrder nextOrder = new MarketOrder(existing.getOrderID(),
                                                  existing.getWalletDivision(),
                                                  existing.isBid(),
                                                  0,
                                                  existing.getDuration(),
                                                  next.getEscrow() == null ? existing.getEscrow() : BigDecimal.valueOf(
                                                      next.getEscrow())
                                                                                                              .setScale(
                                                                                                                  2,
                                                                                                                  RoundingMode.HALF_UP),
                                                  next.getIssued()
                                                      .getMillis(),
                                                  next.getIssuedBy(),
                                                  existing.getMinVolume(),
                                                  next.getState()
                                                      .toString(),
                                                  BigDecimal.valueOf(next.getPrice())
                                                            .setScale(2, RoundingMode.HALF_UP),
                                                  existing.getOrderRange(),
                                                  existing.getTypeID(),
                                                  existing.getVolEntered(),
                                                  next.getVolumeRemain(),
                                                  existing.getRegionID(),
                                                  existing.getLocationID(),
                                                  true);
          updates.add(nextOrder);
        }
      }
    }

  }


}
