package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MarketApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOrders200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOrdersHistory200Ok;
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
public class ESICorporationMarketOrderSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private MarketApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] marketTestData;
  private static Object[][] historicMarketTestData;
  private static int[] pages;
  private static int[] historicPages;


  static {
    // MarketOrder test data
    // 0 long orderID;
    // 1 int walletDivision;
    // 2 boolean bid;
    // 3 long charID;
    // 4 int duration;
    // 5 BigDecimal escrow;
    // 6 long issued = -1;
    // 7 int minVolume;
    // 8 String orderState;
    // 9 BigDecimal price;
    // 10 String orderRange;
    // 11 int typeID;
    // 12 int volEntered;
    // 13 int volRemaining;
    // 14 int regionID;
    // 15 long locationID;
    // 16 boolean isCorp;
    int size = 200 + TestBase.getRandomInt(200);
    marketTestData = new Object[size][17];
    int orderStateLen = GetCorporationsCorporationIdOrders200Ok.StateEnum.values().length;
    int orderRangeLen = GetCorporationsCorporationIdOrders200Ok.RangeEnum.values().length;
    for (int i = 0; i < size; i++) {
      marketTestData[i][0] = TestBase.getUniqueRandomLong();
      marketTestData[i][1] = TestBase.getRandomInt(6) + 1;
      marketTestData[i][2] = TestBase.getRandomBoolean();
      marketTestData[i][3] = TestBase.getRandomLong();
      marketTestData[i][4] = TestBase.getRandomInt();
      marketTestData[i][5] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                       .setScale(2, RoundingMode.HALF_UP);
      marketTestData[i][6] = TestBase.getRandomLong();
      marketTestData[i][7] = TestBase.getRandomInt();
      marketTestData[i][8] = GetCorporationsCorporationIdOrders200Ok.StateEnum.values()[TestBase.getRandomInt(
          orderStateLen)];
      marketTestData[i][9] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                       .setScale(2, RoundingMode.HALF_UP);
      marketTestData[i][10] = GetCorporationsCorporationIdOrders200Ok.RangeEnum.values()[TestBase.getRandomInt(
          orderRangeLen)];
      marketTestData[i][11] = TestBase.getRandomInt();
      marketTestData[i][12] = TestBase.getRandomInt();
      marketTestData[i][13] = TestBase.getRandomInt();
      marketTestData[i][14] = TestBase.getRandomInt();
      marketTestData[i][15] = TestBase.getRandomLong();
      marketTestData[i][16] = true;
    }

    int pageCount = 2 + TestBase.getRandomInt(4);
    pages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      pages[i] = size - (pageCount - 1 - i) * (size / pageCount);

    // Create a few random pages of historic market orders as well
    int histSize = 200 + TestBase.getRandomInt(200);
    historicMarketTestData = new Object[histSize][17];
    int histOrderStateLen = GetCorporationsCorporationIdOrdersHistory200Ok.StateEnum.values().length;
    int histOrderRangeLen = GetCorporationsCorporationIdOrdersHistory200Ok.RangeEnum.values().length;
    for (int i = 0; i < histSize; i++) {
      historicMarketTestData[i][0] = TestBase.getUniqueRandomLong();
      historicMarketTestData[i][1] = TestBase.getRandomInt(6) + 1;
      historicMarketTestData[i][2] = TestBase.getRandomBoolean();
      historicMarketTestData[i][3] = TestBase.getRandomLong();
      historicMarketTestData[i][4] = TestBase.getRandomInt();
      historicMarketTestData[i][5] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                               .setScale(2, RoundingMode.HALF_UP);
      historicMarketTestData[i][6] = TestBase.getRandomLong();
      historicMarketTestData[i][7] = TestBase.getRandomInt();
      historicMarketTestData[i][8] = GetCorporationsCorporationIdOrdersHistory200Ok.StateEnum.values()[TestBase.getRandomInt(
          histOrderStateLen)];
      historicMarketTestData[i][9] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                               .setScale(2, RoundingMode.HALF_UP);
      historicMarketTestData[i][10] = GetCorporationsCorporationIdOrdersHistory200Ok.RangeEnum.values()[TestBase.getRandomInt(
          histOrderRangeLen)];
      historicMarketTestData[i][11] = TestBase.getRandomInt();
      historicMarketTestData[i][12] = TestBase.getRandomInt();
      historicMarketTestData[i][13] = TestBase.getRandomInt();
      historicMarketTestData[i][14] = TestBase.getRandomInt();
      historicMarketTestData[i][15] = TestBase.getRandomLong();
      historicMarketTestData[i][16] = true;
    }

    int histPageCount = 2 + TestBase.getRandomInt(4);
    historicPages = new int[histPageCount];
    for (int i = histPageCount - 1; i >= 0; i--)
      historicPages[i] = histSize - (histPageCount - 1 - i) * (histSize / histPageCount);

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MARKET, 1234L, null);

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
    List<GetCorporationsCorporationIdOrders200Ok> orderList =
        Arrays.stream(marketTestData)
              .map(x -> {
                GetCorporationsCorporationIdOrders200Ok nextOrder = new GetCorporationsCorporationIdOrders200Ok();
                nextOrder.setOrderId((Long) x[0]);
                nextOrder.setWalletDivision((Integer) x[1]);
                nextOrder.setIsBuyOrder((Boolean) x[2]);
                nextOrder.setDuration((Integer) x[4]);
                nextOrder.setEscrow(((BigDecimal) x[5]).doubleValue());
                nextOrder.setIssued(new DateTime(new Date((Long) x[6])));
                nextOrder.setMinVolume((Integer) x[7]);
                nextOrder.setState((GetCorporationsCorporationIdOrders200Ok.StateEnum) x[8]);
                nextOrder.setPrice(((BigDecimal) x[9]).doubleValue());
                nextOrder.setRange((GetCorporationsCorporationIdOrders200Ok.RangeEnum) x[10]);
                nextOrder.setTypeId((Integer) x[11]);
                nextOrder.setVolumeTotal((Integer) x[12]);
                nextOrder.setVolumeRemain((Integer) x[13]);
                nextOrder.setRegionId((Integer) x[14]);
                nextOrder.setLocationId((Long) x[15]);
                return nextOrder;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < pages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCorporationsCorporationIdOrders200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                          orderList.subList(
                                                                                              last,
                                                                                              pages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdOrdersWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = pages[i];
    }

    // Setup historic order calls
    List<GetCorporationsCorporationIdOrdersHistory200Ok> historicOrderList =
        Arrays.stream(historicMarketTestData)
              .map(x -> {
                GetCorporationsCorporationIdOrdersHistory200Ok nextOrder = new GetCorporationsCorporationIdOrdersHistory200Ok();
                nextOrder.setOrderId((Long) x[0]);
                nextOrder.setWalletDivision((Integer) x[1]);
                nextOrder.setIsBuyOrder((Boolean) x[2]);
                nextOrder.setDuration((Integer) x[4]);
                nextOrder.setEscrow(((BigDecimal) x[5]).doubleValue());
                nextOrder.setIssued(new DateTime(new Date((Long) x[6])));
                nextOrder.setMinVolume((Integer) x[7]);
                nextOrder.setState((GetCorporationsCorporationIdOrdersHistory200Ok.StateEnum) x[8]);
                nextOrder.setPrice(((BigDecimal) x[9]).doubleValue());
                nextOrder.setRange((GetCorporationsCorporationIdOrdersHistory200Ok.RangeEnum) x[10]);
                nextOrder.setTypeId((Integer) x[11]);
                nextOrder.setVolumeTotal((Integer) x[12]);
                nextOrder.setVolumeRemain((Integer) x[13]);
                nextOrder.setRegionId((Integer) x[14]);
                nextOrder.setLocationId((Long) x[15]);
                return nextOrder;
              })
              .collect(Collectors.toList());
    last = 0;
    for (int i = 0; i < historicPages.length; i++) {
      Map<String, List<String>> historicHeaders = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                                String.valueOf(historicPages.length));
      ApiResponse<List<GetCorporationsCorporationIdOrdersHistory200Ok>> histApir = new ApiResponse<>(200, historicHeaders,
                                                                                                 historicOrderList.subList(
                                                                                                     last,
                                                                                                     historicPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdOrdersHistoryWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(histApir);
      last = historicPages[i];
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getMarketApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(boolean includeHistory) throws Exception {
    // Retrieve all stored data
    List<MarketOrder> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        MarketOrder.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

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
      Assert.assertEquals((int) (Integer) marketTestData[i][7], nextEl.getMinVolume());
      Assert.assertEquals(marketTestData[i][8].toString(), nextEl.getOrderState());
      Assert.assertEquals(marketTestData[i][9], nextEl.getPrice());
      Assert.assertEquals(marketTestData[i][10].toString(), nextEl.getOrderRange());
      Assert.assertEquals((int) (Integer) marketTestData[i][11], nextEl.getTypeID());
      Assert.assertEquals((int) (Integer) marketTestData[i][12], nextEl.getVolEntered());
      Assert.assertEquals((int) (Integer) marketTestData[i][13], nextEl.getVolRemaining());
      Assert.assertEquals((int) (Integer) marketTestData[i][14], nextEl.getRegionID());
      Assert.assertEquals((long) (Long) marketTestData[i][15], nextEl.getLocationID());
      Assert.assertTrue(nextEl.isCorp());
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
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][7], nextEl.getMinVolume());
        Assert.assertEquals(historicMarketTestData[i][8].toString(), nextEl.getOrderState());
        Assert.assertEquals(historicMarketTestData[i][9], nextEl.getPrice());
        Assert.assertEquals(historicMarketTestData[i][10].toString(), nextEl.getOrderRange());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][11], nextEl.getTypeID());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][12], nextEl.getVolEntered());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][13], nextEl.getVolRemaining());
        Assert.assertEquals((int) (Integer) historicMarketTestData[i][14], nextEl.getRegionID());
        Assert.assertEquals((long) (Long) historicMarketTestData[i][15], nextEl.getLocationID());
        Assert.assertTrue(nextEl.isCorp());
      }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationMarketOrderSync sync = new ESICorporationMarketOrderSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    // Historic endpoint should not update anything since no previous orders exist
    verifyDataUpdate(false);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MARKET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MARKET);
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
                                          (Integer) aMarketTestData[1],
                                          !((Boolean) aMarketTestData[2]),
                                          0,
                                          (Integer) aMarketTestData[4] + 1,
                                          ((BigDecimal) aMarketTestData[5]).add(BigDecimal.ONE),
                                          (Long) aMarketTestData[6] + 1,
                                          (Integer) aMarketTestData[7] + 1,
                                          aMarketTestData[8].toString(),
                                          ((BigDecimal) aMarketTestData[9]).add(BigDecimal.ONE),
                                          aMarketTestData[10].toString(),
                                          (Integer) aMarketTestData[11] + 1,
                                          (Integer) aMarketTestData[12] + 1,
                                          (Integer) aMarketTestData[13] + 1,
                                          (Integer) aMarketTestData[14] + 1,
                                          (Long) aMarketTestData[15] + 1,
                                          true);
      newEl.setup(corpSyncAccount, testTime - 1);
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
                                          (Integer) aMarketTestData[1],
                                          (Boolean) aMarketTestData[2],
                                          0,
                                          (Integer) aMarketTestData[4],
                                          ((BigDecimal) aMarketTestData[5]).add(BigDecimal.ONE),
                                          (Long) aMarketTestData[6] + 1,
                                          (Integer) aMarketTestData[7],
                                          GetCorporationsCorporationIdOrders200Ok.StateEnum.OPEN.toString(),
                                          ((BigDecimal) aMarketTestData[9]).add(BigDecimal.ONE),
                                          aMarketTestData[10].toString(),
                                          (Integer) aMarketTestData[11],
                                          (Integer) aMarketTestData[12],
                                          (Integer) aMarketTestData[13] + 1,
                                          (Integer) aMarketTestData[14],
                                          (Long) aMarketTestData[15],
                                          true);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationMarketOrderSync sync = new ESICorporationMarketOrderSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<MarketOrder> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        MarketOrder.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(marketTestData.length + historicMarketTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < marketTestData.length; i++) {
      MarketOrder nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals((long) (Long) marketTestData[i][0], nextEl.getOrderID());
      Assert.assertEquals((int) (Integer) marketTestData[i][1], nextEl.getWalletDivision());
      Assert.assertEquals(!((Boolean) marketTestData[i][2]), nextEl.isBid());
      Assert.assertEquals(0, nextEl.getCharID());
      Assert.assertEquals((Integer) marketTestData[i][4] + 1, nextEl.getDuration());
      Assert.assertEquals(((BigDecimal) marketTestData[i][5]).add(BigDecimal.ONE), nextEl.getEscrow());
      Assert.assertEquals((Long) marketTestData[i][6] + 1, nextEl.getIssued());
      Assert.assertEquals((Integer) marketTestData[i][7] + 1, nextEl.getMinVolume());
      Assert.assertEquals(marketTestData[i][8].toString(), nextEl.getOrderState());
      Assert.assertEquals(((BigDecimal) marketTestData[i][9]).add(BigDecimal.ONE), nextEl.getPrice());
      Assert.assertEquals(marketTestData[i][10].toString(), nextEl.getOrderRange());
      Assert.assertEquals((Integer) marketTestData[i][11] + 1, nextEl.getTypeID());
      Assert.assertEquals((Integer) marketTestData[i][12] + 1, nextEl.getVolEntered());
      Assert.assertEquals((Integer) marketTestData[i][13] + 1, nextEl.getVolRemaining());
      Assert.assertEquals((Integer) marketTestData[i][14] + 1, nextEl.getRegionID());
      Assert.assertEquals((Long) marketTestData[i][15] + 1, nextEl.getLocationID());
      Assert.assertTrue(nextEl.isCorp());
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
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][1], nextEl.getWalletDivision());
      Assert.assertEquals(historicMarketTestData[i][2], nextEl.isBid());
      Assert.assertEquals(0, nextEl.getCharID());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][4], nextEl.getDuration());
      Assert.assertEquals(((BigDecimal) historicMarketTestData[i][5]).add(BigDecimal.ONE), nextEl.getEscrow());
      Assert.assertEquals((Long) historicMarketTestData[i][6] + 1, nextEl.getIssued());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][7], nextEl.getMinVolume());
      Assert.assertEquals(GetCorporationsCorporationIdOrders200Ok.StateEnum.OPEN.toString(), nextEl.getOrderState());
      Assert.assertEquals(((BigDecimal) historicMarketTestData[i][9]).add(BigDecimal.ONE), nextEl.getPrice());
      Assert.assertEquals(historicMarketTestData[i][10].toString(), nextEl.getOrderRange());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][11], nextEl.getTypeID());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][12], nextEl.getVolEntered());
      Assert.assertEquals((Integer) historicMarketTestData[i][13] + 1, nextEl.getVolRemaining());
      Assert.assertEquals((int) (Integer) historicMarketTestData[i][14], nextEl.getRegionID());
      Assert.assertEquals((long) (Long) historicMarketTestData[i][15], nextEl.getLocationID());
      Assert.assertTrue(nextEl.isCorp());
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    // Historic orders should also be updated
    verifyDataUpdate(true);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MARKET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MARKET);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
