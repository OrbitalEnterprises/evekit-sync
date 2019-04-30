package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MarketApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOrders200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOrdersHistory200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.MarketOrder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;

public class ESICharacterMarketOrderSync extends AbstractESIAccountSync<ESICharacterMarketOrderSync.OrderSet> {
  protected static final Logger log = Logger.getLogger(ESICharacterMarketOrderSync.class.getName());

  class OrderSet {
    List<GetCharactersCharacterIdOrders200Ok> liveOrders;
    List<GetCharactersCharacterIdOrdersHistory200Ok> historicalOrders;
  }

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  public ESICharacterMarketOrderSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_MARKET;
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
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdOrders200Ok>> liveResult = apiInstance.getCharactersCharacterIdOrdersWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(liveResult);
    long expiry = extractExpiry(liveResult, OrbitalProperties.getCurrentTime() + maxDelay());
    orders.liveOrders = liveResult.getData();

    // Retrieve historical orders
    Pair<Long, List<GetCharactersCharacterIdOrdersHistory200Ok>> histResult = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdOrdersHistoryWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          null,
          page,
          accessToken());
    });
    expiry = histResult.getLeft() > 0 ? Math.max(histResult.getLeft(), expiry) : expiry;
    orders.historicalOrders = histResult.getRight();

    return new ESIAccountServerResult<>(expiry, orders);
  }

  @SuppressWarnings({"Duplicates"})
  @Override
  protected void processServerData(long time, ESIAccountServerResult<OrderSet> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(2);

    // Compute current orders hash
    List<MarketOrder> retrievedOrders = new ArrayList<>();
    for (GetCharactersCharacterIdOrders200Ok next : data.getData().liveOrders) {
      MarketOrder nextOrder = new MarketOrder(next.getOrderId(),
                                              1,
                                              nullSafeBoolean(next.getIsBuyOrder(), false),
                                              0,
                                              next.getDuration(),
                                              BigDecimal.valueOf(nullSafeDouble(next.getEscrow(), 0D))
                                                        .setScale(2, RoundingMode.HALF_UP),
                                              next.getIssued()
                                                  .getMillis(),
                                              0,
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
                                              next.getIsCorporation());
      retrievedOrders.add(nextOrder);
    }
    retrievedOrders.sort(Comparator.comparingLong(MarketOrder::getOrderID));
    String orderHashResult = CachedData.dataHashHelper(retrievedOrders.stream()
                                                                      .map(MarketOrder::dataHash)
                                                                      .toArray());

    // Check hash
    if (cachedHash[0] == null || !cachedHash[0].equals(orderHashResult)) {
      cacheMiss();
      cachedHash[0] = orderHashResult;
      updates.addAll(retrievedOrders);
    } else {
      cacheHit();
    }

    // Compute historic orders hash
    retrievedOrders.clear();
    for (GetCharactersCharacterIdOrdersHistory200Ok next : data.getData().historicalOrders) {
      MarketOrder nextOrder = new MarketOrder(next.getOrderId(),
                                              1,
                                              nullSafeBoolean(next.getIsBuyOrder(), false),
                                              0,
                                              next.getDuration(),
                                              BigDecimal.valueOf(nullSafeDouble(next.getEscrow(), 0D))
                                                        .setScale(2, RoundingMode.HALF_UP),
                                              next.getIssued()
                                                  .getMillis(),
                                              0,
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
                                              next.getIsCorporation());
      retrievedOrders.add(nextOrder);
    }
    retrievedOrders.sort(Comparator.comparingLong(MarketOrder::getOrderID));
    orderHashResult = CachedData.dataHashHelper(retrievedOrders.stream()
                                                               .map(MarketOrder::dataHash)
                                                               .toArray());

    // Check hash
    if (cachedHash[1] == null || !cachedHash[1].equals(orderHashResult)) {
      cacheMiss();
      cachedHash[1] = orderHashResult;

      // For efficiency, we do a bulk retrieve of all stored orders according to the history we just
      // retrieved.  This avoids an individual DB get call for each order we need to check.
      OptionalLong minResult = data.getData().historicalOrders.stream()
                                                              .mapToLong(
                                                                  GetCharactersCharacterIdOrdersHistory200Ok::getOrderId)
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

      for (GetCharactersCharacterIdOrdersHistory200Ok next : data.getData().historicalOrders) {
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
                                                    1,
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
                                                    0,
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
                                                    existing.isCorp());
            updates.add(nextOrder);
          }
        }
      }

    } else {
      cacheHit();
    }

    // Save hashes for next execution
    currentETag = String.join("|", cachedHash);
  }


}
