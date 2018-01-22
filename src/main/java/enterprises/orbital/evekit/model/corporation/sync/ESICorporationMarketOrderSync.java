package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MarketApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOrders200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.MarketOrder;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

public class ESICorporationMarketOrderSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdOrders200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationMarketOrderSync.class.getName());

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
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdOrders200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    MarketApi apiInstance = cp.getMarketApi();
    Pair<Long, List<GetCorporationsCorporationIdOrders200Ok>> result = pagedResultRetriever((page) ->
                                                                                                apiInstance.getCorporationsCorporationIdOrdersWithHttpInfo(
                                                                                                    (int) account.getEveCorporationID(),
                                                                                                    null,
                                                                                                    page,
                                                                                                    accessToken(),
                                                                                                    null,
                                                                                                    null));
    return new ESIAccountServerResult<>(
        result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay(),
        result.getRight());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdOrders200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record orders
    for (GetCorporationsCorporationIdOrders200Ok next : data.getData()) {
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
