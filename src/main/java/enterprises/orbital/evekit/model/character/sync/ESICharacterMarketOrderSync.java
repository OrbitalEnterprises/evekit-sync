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
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterMarketOrderSync extends AbstractESIAccountSync<ESICharacterMarketOrderSync.OrderSet> {
  protected static final Logger log = Logger.getLogger(ESICharacterMarketOrderSync.class.getName());

  class OrderSet {
    List<GetCharactersCharacterIdOrders200Ok> liveOrders;
    List<GetCharactersCharacterIdOrdersHistory200Ok> historicalOrders;
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
        (int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(liveResult);
    long expiry = extractExpiry(liveResult, OrbitalProperties.getCurrentTime() + maxDelay());
    orders.liveOrders = liveResult.getData();

    // Retrieve historical orders
    Pair<Long, List<GetCharactersCharacterIdOrdersHistory200Ok>> histResult = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdOrdersHistoryWithHttpInfo(
          (int) account.getEveCharacterID(),
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
  protected void processServerData(long time, ESIAccountServerResult<OrderSet> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record orders
    if (data.getData().liveOrders != null)
      for (GetCharactersCharacterIdOrders200Ok next : data.getData().liveOrders) {
        MarketOrder nextOrder = new MarketOrder(next.getOrderId(), 1, next.getIsBuyOrder(), 0, next.getDuration(),
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
                                                next.getRegionId(), next.getLocationId(), next.getIsCorp());
        updates.add(nextOrder);
      }

    if (data.getData().historicalOrders != null)
      for (GetCharactersCharacterIdOrdersHistory200Ok next : data.getData().historicalOrders) {
        MarketOrder nextOrder = new MarketOrder(next.getOrderId(), 1, next.getIsBuyOrder(), 0, next.getDuration(),
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
                                                next.getRegionId(), next.getLocationId(), next.getIsCorporation());
        updates.add(nextOrder);
      }

  }


}
