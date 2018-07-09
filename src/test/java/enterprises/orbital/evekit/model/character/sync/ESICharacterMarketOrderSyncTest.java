package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MarketApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOrders200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOrdersHistory200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.MarketOrder;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterMarketOrderSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private MarketApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] marketTestData;
  private static Object[][] historicMarketTestData;
  private static int[] pages;

  static {
    // MarketOrder test data
    // 0 long orderID;
    // 1 int walletDivision;
    // 2 boolean bid;
    // 3 long charID;
    // 4 int duration;
    // 5 BigDecimal escrow;
    // 6 long issued = -1;
    // 7 int issuedBy;
    // 8 int minVolume;
    // 9 String orderState;
    // 10 BigDecimal price;
    // 11 String orderRange;
    // 12 int typeID;
    // 13 int volEntered;
    // 14 int volRemaining;
    // 15 int regionID;
    // 16 long locationID;
    // 17 boolean isCorp;
    int size = 50 + TestBase.getRandomInt(50);
    marketTestData = new Object[size][18];
    int orderRangeLen = GetCharactersCharacterIdOrders200Ok.RangeEnum.values().length;
    for (int i = 0; i < size; i++) {
      marketTestData[i][0] = TestBase.getUniqueRandomLong();
      marketTestData[i][1] = 1;
      marketTestData[i][2] = TestBase.getRandomBoolean();
      marketTestData[i][3] = TestBase.getRandomLong();
      marketTestData[i][4] = TestBase.getRandomInt();
      marketTestData[i][5] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                       .setScale(2, RoundingMode.HALF_UP);
      marketTestData[i][6] = TestBase.getRandomLong();
      marketTestData[i][7] = 0;
      marketTestData[i][8] = TestBase.getRandomInt();
      marketTestData[i][9] = "open";
      marketTestData[i][10] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                       .setScale(2, RoundingMode.HALF_UP);
      marketTestData[i][11] = GetCharactersCharacterIdOrders200Ok.RangeEnum.values()[TestBase.getRandomInt(
          orderRangeLen)];
      marketTestData[i][12] = TestBase.getRandomInt();
      marketTestData[i][13] = TestBase.getRandomInt();
      marketTestData[i][14] = TestBase.getRandomInt();
      marketTestData[i][15] = TestBase.getRandomInt();
      marketTestData[i][16] = TestBase.getRandomLong();
      marketTestData[i][17] = TestBase.getRandomBoolean();
    }

    // Create a few random pages of historic market orders as well
    int histSize = 200 + TestBase.getRandomInt(200);
    historicMarketTestData = new Object[histSize][18];
    int histOrderStateLen = GetCharactersCharacterIdOrdersHistory200Ok.StateEnum.values().length;
    int histOrderRangeLen = GetCharactersCharacterIdOrdersHistory200Ok.RangeEnum.values().length;
    for (int i = 0; i < histSize; i++) {
      historicMarketTestData[i][0] = TestBase.getUniqueRandomLong();
      historicMarketTestData[i][1] = 1;
      historicMarketTestData[i][2] = TestBase.getRandomBoolean();
      historicMarketTestData[i][3] = TestBase.getRandomLong();
      historicMarketTestData[i][4] = TestBase.getRandomInt();
      historicMarketTestData[i][5] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                       .setScale(2, RoundingMode.HALF_UP);
      historicMarketTestData[i][6] = TestBase.getRandomLong();
      historicMarketTestData[i][7] = 0;
      historicMarketTestData[i][8] = TestBase.getRandomInt();
      historicMarketTestData[i][9] = GetCharactersCharacterIdOrdersHistory200Ok.StateEnum.values()[TestBase.getRandomInt(
          histOrderStateLen)];
      historicMarketTestData[i][10] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                       .setScale(2, RoundingMode.HALF_UP);
      historicMarketTestData[i][11] = GetCharactersCharacterIdOrdersHistory200Ok.RangeEnum.values()[TestBase.getRandomInt(
          histOrderRangeLen)];
      historicMarketTestData[i][12] = TestBase.getRandomInt();
      historicMarketTestData[i][13] = TestBase.getRandomInt();
      historicMarketTestData[i][14] = TestBase.getRandomInt();
      historicMarketTestData[i][15] = TestBase.getRandomInt();
      historicMarketTestData[i][16] = TestBase.getRandomLong();
      historicMarketTestData[i][17] = TestBase.getRandomBoolean();
    }

    int pageCount = 2 + TestBase.getRandomInt(4);
    pages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      pages[i] = histSize - (pageCount - 1 - i) * (histSize / pageCount);

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MARKET, 1234L, null);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MarketOrder ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(MarketApi.class);

    // Setup live order calls
    List<GetCharactersCharacterIdOrders200Ok> orderList =
        Arrays.stream(marketTestData)
              .map(x -> {
                GetCharactersCharacterIdOrders200Ok nextOrder = new GetCharactersCharacterIdOrders200Ok();
                nextOrder.setOrderId((Long) x[0]);
                nextOrder.setIsBuyOrder((Boolean) x[2]);
                nextOrder.setDuration((Integer) x[4]);
                nextOrder.setEscrow(((BigDecimal) x[5]).doubleValue());
                nextOrder.setIssued(new DateTime(new Date((Long) x[6])));
                nextOrder.setMinVolume((Integer) x[8]);
                nextOrder.setPrice(((BigDecimal) x[10]).doubleValue());
                nextOrder.setRange((GetCharactersCharacterIdOrders200Ok.RangeEnum) x[11]);
                nextOrder.setTypeId((Integer) x[12]);
                nextOrder.setVolumeTotal((Integer) x[13]);
                nextOrder.setVolumeRemain((Integer) x[14]);
                nextOrder.setRegionId((Integer) x[15]);
                nextOrder.setLocationId((Long) x[16]);
                nextOrder.setIsCorporation((Boolean) x[17]);
                return nextOrder;
              })
              .collect(Collectors.toList());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdOrders200Ok>> apir = new ApiResponse<>(200, headers, orderList);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdOrdersWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString()))
            .andReturn(apir);

    // Setup historic order calls
    List<GetCharactersCharacterIdOrdersHistory200Ok> historicOrderList =
        Arrays.stream(historicMarketTestData)
              .map(x -> {
                GetCharactersCharacterIdOrdersHistory200Ok nextOrder = new GetCharactersCharacterIdOrdersHistory200Ok();
                nextOrder.setOrderId((Long) x[0]);
                nextOrder.setIsBuyOrder((Boolean) x[2]);
                nextOrder.setDuration((Integer) x[4]);
                nextOrder.setEscrow(((BigDecimal) x[5]).doubleValue());
                nextOrder.setIssued(new DateTime(new Date((Long) x[6])));
                nextOrder.setMinVolume((Integer) x[8]);
                nextOrder.setState((GetCharactersCharacterIdOrdersHistory200Ok.StateEnum) x[9]);
                nextOrder.setPrice(((BigDecimal) x[10]).doubleValue());
                nextOrder.setRange((GetCharactersCharacterIdOrdersHistory200Ok.RangeEnum) x[11]);
                nextOrder.setTypeId((Integer) x[12]);
                nextOrder.setVolumeTotal((Integer) x[13]);
                nextOrder.setVolumeRemain((Integer) x[14]);
                nextOrder.setRegionId((Integer) x[15]);
                nextOrder.setLocationId((Long) x[16]);
                nextOrder.setIsCorporation((Boolean) x[17]);
                return nextOrder;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < pages.length; i++) {
      Map<String, List<String>> historicHeaders = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCharactersCharacterIdOrdersHistory200Ok>> histApir = new ApiResponse<>(200, historicHeaders,
                                                                                                historicOrderList.subList(
                                                                                                    last,
                                                                                                    pages[i]));
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdOrdersHistoryWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(histApir);
      last = pages[i];
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getMarketApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(boolean includeHistory) throws Exception {
    // Retrieve all stored data
    List<MarketOrder> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        MarketOrder.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    if (includeHistory)
        Assert.assertEquals(marketTestData.length + historicMarketTestData.length, storedData.size());
      else
        Assert.assertEquals(marketTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < marketTestData.length; i++) {
      MarketOrder nextEl = storedData.get(i);
      Assert.assertEquals((long) (Long) marketTestData[i][0], nextEl.getOrderID());
      Assert.assertEquals((int) (Integer) marketTestData[i][1], nextEl.getWalletDivision());
      Assert.assertEquals(marketTestData[i][2], nextEl.isBid());
      Assert.assertEquals(0L, nextEl.getCharID());
      Assert.assertEquals((int) (Integer) marketTestData[i][4], nextEl.getDuration());
      Assert.assertEquals(marketTestData[i][5], nextEl.getEscrow());
      Assert.assertEquals((long) (Long) marketTestData[i][6], nextEl.getIssued());
      Assert.assertEquals((int) (Integer) marketTestData[i][7], nextEl.getIssuedBy());
      Assert.assertEquals((int) (Integer) marketTestData[i][8], nextEl.getMinVolume());
      Assert.assertEquals(marketTestData[i][9].toString(), nextEl.getOrderState());
      Assert.assertEquals(marketTestData[i][10], nextEl.getPrice());
      Assert.assertEquals(marketTestData[i][11].toString(), nextEl.getOrderRange());
      Assert.assertEquals((int) (Integer) marketTestData[i][12], nextEl.getTypeID());
      Assert.assertEquals((int) (Integer) marketTestData[i][13], nextEl.getVolEntered());
      Assert.assertEquals((int) (Integer) marketTestData[i][14], nextEl.getVolRemaining());
      Assert.assertEquals((int) (Integer) marketTestData[i][15], nextEl.getRegionID());
      Assert.assertEquals((long) (Long) marketTestData[i][16], nextEl.getLocationID());
      Assert.assertEquals(marketTestData[i][17], nextEl.isCorp());
    }

    if (includeHistory)
      for (int i = 0; i < historicMarketTestData.length; i++) {
        MarketOrder nextEl = storedData.get(i + marketTestData.length);
        Assert.assertEquals((long) (Long) historicMarketTestData[i][0], nextEl.getOrderID());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][1], nextEl.getWalletDivision());
        Assert.assertEquals(historicMarketTestData[i][2], nextEl.isBid());
        Assert.assertEquals(0L, nextEl.getCharID());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][4], nextEl.getDuration());
        Assert.assertEquals(historicMarketTestData[i][5], nextEl.getEscrow());
        Assert.assertEquals((long) (Long) historicMarketTestData[i][6], nextEl.getIssued());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][7], nextEl.getIssuedBy());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][8], nextEl.getMinVolume());
        Assert.assertEquals(historicMarketTestData[i][9].toString(), nextEl.getOrderState());
        Assert.assertEquals(historicMarketTestData[i][10], nextEl.getPrice());
        Assert.assertEquals(historicMarketTestData[i][11].toString(), nextEl.getOrderRange());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][12], nextEl.getTypeID());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][13], nextEl.getVolEntered());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][14], nextEl.getVolRemaining());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][15], nextEl.getRegionID());
        Assert.assertEquals((long) (Long) historicMarketTestData[i][16], nextEl.getLocationID());
        Assert.assertEquals(historicMarketTestData[i][17], nextEl.isCorp());
      }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterMarketOrderSync sync = new ESICharacterMarketOrderSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    // Historic endpoint should not update anything since no previous orders exist
    verifyDataUpdate(false);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MARKET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MARKET);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    for (Object[] aMarketTestData : marketTestData) {
      MarketOrder newEl = new MarketOrder((Long) aMarketTestData[0],
                                          1,
                                          !((Boolean) aMarketTestData[2]),
                                          0,
                                          (Integer) aMarketTestData[4] + 1,
                                          ((BigDecimal) aMarketTestData[5]).add(BigDecimal.ONE),
                                          (Long) aMarketTestData[6] + 1,
                                          0,
                                          (Integer) aMarketTestData[8] + 1,
                                          aMarketTestData[9].toString(),
                                          ((BigDecimal) aMarketTestData[10]).add(BigDecimal.ONE),
                                          aMarketTestData[11].toString(),
                                          (Integer) aMarketTestData[12] + 1,
                                          (Integer) aMarketTestData[13] + 1,
                                          (Integer) aMarketTestData[14] + 1,
                                          (Integer) aMarketTestData[15] + 1,
                                          (Long) aMarketTestData[16] + 1,
                                          !((Boolean) aMarketTestData[17]));
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Populate existing historic
    // Note that historic updates only modify a select set of fields:
    // price
    // volume remaining
    // issued
    // escrow
    // state
    for (Object[] aMarketTestData : historicMarketTestData) {
      MarketOrder newEl = new MarketOrder((Long) aMarketTestData[0],
                                          1,
                                          (Boolean) aMarketTestData[2],
                                          0,
                                          (Integer) aMarketTestData[4],
                                          ((BigDecimal) aMarketTestData[5]).add(BigDecimal.ONE),
                                          (Long) aMarketTestData[6] + 1,
                                          0,
                                          (Integer) aMarketTestData[8],
                                          "open",
                                          ((BigDecimal) aMarketTestData[10]).add(BigDecimal.ONE),
                                          aMarketTestData[11].toString(),
                                          (Integer) aMarketTestData[12],
                                          (Integer) aMarketTestData[13],
                                          (Integer) aMarketTestData[14] + 1,
                                          (Integer) aMarketTestData[15],
                                          (Long) aMarketTestData[16],
                                          (Boolean) aMarketTestData[17]);
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterMarketOrderSync sync = new ESICharacterMarketOrderSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<MarketOrder> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        MarketOrder.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(marketTestData.length + historicMarketTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < marketTestData.length; i++) {
      MarketOrder nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals((long) (Long) marketTestData[i][0], nextEl.getOrderID());
      Assert.assertEquals(1, nextEl.getWalletDivision());
      Assert.assertEquals(!((Boolean) marketTestData[i][2]), nextEl.isBid());
      Assert.assertEquals(0, nextEl.getCharID());
      Assert.assertEquals((Integer) marketTestData[i][4] + 1, nextEl.getDuration());
      Assert.assertEquals(((BigDecimal) marketTestData[i][5]).add(BigDecimal.ONE), nextEl.getEscrow());
      Assert.assertEquals((Long) marketTestData[i][6] + 1, nextEl.getIssued());
      Assert.assertEquals((int) (Integer) marketTestData[i][7], nextEl.getIssuedBy());
      Assert.assertEquals((Integer) marketTestData[i][8] + 1, nextEl.getMinVolume());
      Assert.assertEquals(marketTestData[i][9].toString(), nextEl.getOrderState());
      Assert.assertEquals(((BigDecimal) marketTestData[i][10]).add(BigDecimal.ONE), nextEl.getPrice());
      Assert.assertEquals(marketTestData[i][11].toString(), nextEl.getOrderRange());
      Assert.assertEquals((Integer) marketTestData[i][12] + 1, nextEl.getTypeID());
      Assert.assertEquals((Integer) marketTestData[i][13] + 1, nextEl.getVolEntered());
      Assert.assertEquals((Integer) marketTestData[i][14] + 1, nextEl.getVolRemaining());
      Assert.assertEquals((Integer) marketTestData[i][15] + 1, nextEl.getRegionID());
      Assert.assertEquals((Long) marketTestData[i][16] + 1, nextEl.getLocationID());
      Assert.assertEquals(!((Boolean) marketTestData[i][17]), nextEl.isCorp());
    }

    // Check old historic data
    // Note that historic updates only modify a select set of fields:
    // price
    // volume remaining
    // issued
    // escrow
    // state
    for (int i = 0; i < historicMarketTestData.length; i++) {
      MarketOrder nextEl = oldEls.get(i + marketTestData.length);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals((long) (Long) historicMarketTestData[i][0], nextEl.getOrderID());
      Assert.assertEquals(1, nextEl.getWalletDivision());
      Assert.assertEquals(historicMarketTestData[i][2], nextEl.isBid());
      Assert.assertEquals(0, nextEl.getCharID());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][4], nextEl.getDuration());
      Assert.assertEquals(((BigDecimal) historicMarketTestData[i][5]).add(BigDecimal.ONE), nextEl.getEscrow());
      Assert.assertEquals((Long) historicMarketTestData[i][6] + 1, nextEl.getIssued());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][8], nextEl.getMinVolume());
      Assert.assertEquals("open", nextEl.getOrderState());
      Assert.assertEquals(((BigDecimal) historicMarketTestData[i][10]).add(BigDecimal.ONE), nextEl.getPrice());
      Assert.assertEquals(historicMarketTestData[i][11].toString(), nextEl.getOrderRange());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][12], nextEl.getTypeID());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][13], nextEl.getVolEntered());
      Assert.assertEquals((Integer) historicMarketTestData[i][14] + 1, nextEl.getVolRemaining());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][15], nextEl.getRegionID());
      Assert.assertEquals((long) (Long) historicMarketTestData[i][16], nextEl.getLocationID());
      Assert.assertEquals(historicMarketTestData[i][17], nextEl.isCorp());
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    // Historic orders should also be updated
    verifyDataUpdate(true);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MARKET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MARKET);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
