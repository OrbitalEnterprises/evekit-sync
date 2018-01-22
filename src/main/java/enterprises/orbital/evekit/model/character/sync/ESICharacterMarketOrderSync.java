package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MarketApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOrders200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.MarketOrder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterMarketOrderSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdOrders200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterMarketOrderSync.class.getName());

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
  protected ESIAccountServerResult<List<GetCharactersCharacterIdOrders200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    MarketApi apiInstance = cp.getMarketApi();
    ApiResponse<List<GetCharactersCharacterIdOrders200Ok>> result = apiInstance.getCharactersCharacterIdOrdersWithHttpInfo(
        (int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdOrders200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record orders
    for (GetCharactersCharacterIdOrders200Ok next : data.getData()) {
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
  }


}
