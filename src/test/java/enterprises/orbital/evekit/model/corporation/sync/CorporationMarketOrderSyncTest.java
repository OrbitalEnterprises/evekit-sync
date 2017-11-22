package enterprises.orbital.evekit.model.corporation.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.OrbitalProperties.TimeGenerator;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.MarketOrder;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IMarketOrder;

public class CorporationMarketOrderSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  // Random test data
  static Object[][]      testData;

  static {
    // Generate random test data
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][15];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomInt();
      testData[i][2] = TestBase.getRandomBoolean();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomInt();
      testData[i][5] = (new BigDecimal(TestBase.getRandomDouble(1000))).setScale(2, RoundingMode.HALF_UP);
      testData[i][6] = TestBase.getUniqueRandomLong();
      testData[i][7] = TestBase.getRandomInt();
      // Order state
      testData[i][8] = TestBase.getRandomInt();
      testData[i][9] = (new BigDecimal(TestBase.getRandomDouble(100000))).setScale(2, RoundingMode.HALF_UP);
      testData[i][10] = TestBase.getRandomInt();
      testData[i][11] = TestBase.getRandomLong();
      testData[i][12] = TestBase.getRandomInt();
      testData[i][13] = TestBase.getRandomInt();
      testData[i][14] = TestBase.getRandomInt();
    }
  }

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public MarketOrder makeOrder(long time, Object[] instanceData, Integer orderState) throws Exception {
    long orderID = (Long) instanceData[0];
    MarketOrder order = new MarketOrder(
        orderID, (Integer) instanceData[1], (Boolean) instanceData[2], (Long) instanceData[3], (Integer) instanceData[4], (BigDecimal) instanceData[5],
        (Long) instanceData[6], (Integer) instanceData[7], orderState != null ? orderState : (Integer) instanceData[8], (BigDecimal) instanceData[9],
        (Integer) instanceData[10], (Long) instanceData[11], (Integer) instanceData[12], (Integer) instanceData[13], (Integer) instanceData[14]);
    order.setup(syncAccount, time);
    return order;
  }

  public void compareWithTestData(MarketOrder order, Object[] instanceData, Long altTime) {
    if (altTime == null) {
      altTime = (Long) instanceData[6];
    }
    Assert.assertEquals(order.getOrderID(), (long) ((Long) instanceData[0]));
    Assert.assertEquals(order.getAccountKey(), (int) ((Integer) instanceData[1]));
    Assert.assertEquals(order.isBid(), (boolean) ((Boolean) instanceData[2]));
    Assert.assertEquals(order.getCharID(), (long) ((Long) instanceData[3]));
    Assert.assertEquals(order.getDuration(), (int) ((Integer) instanceData[4]));
    Assert.assertEquals(order.getEscrow(), instanceData[5]);
    Assert.assertEquals(order.getIssued(), (long) altTime);
    Assert.assertEquals(order.getMinVolume(), (int) ((Integer) instanceData[7]));
    Assert.assertEquals(order.getOrderState(), (int) ((Integer) instanceData[8]));
    Assert.assertEquals(order.getPrice(), instanceData[9]);
    Assert.assertEquals(order.getOrderRange(), (int) ((Integer) instanceData[10]));
    Assert.assertEquals(order.getStationID(), (long) ((Long) instanceData[11]));
    Assert.assertEquals(order.getTypeID(), (int) ((Integer) instanceData[12]));
    Assert.assertEquals(order.getVolEntered(), (int) ((Integer) instanceData[13]));
    Assert.assertEquals(order.getVolRemaining(), (int) ((Integer) instanceData[14]));
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    List<IMarketOrder> orders = new ArrayList<IMarketOrder>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      IMarketOrder next = new IMarketOrder() {

        @Override
        public int getAccountKey() {
          return (Integer) instanceData[1];
        }

        @Override
        public int getBid() {
          if ((Boolean) instanceData[2]) { return 1; }
          return 0;
        }

        @Override
        public long getCharID() {
          return (Long) instanceData[3];
        }

        @Override
        public int getDuration() {
          return (Integer) instanceData[4];
        }

        @Override
        public BigDecimal getEscrow() {
          return (BigDecimal) instanceData[5];
        }

        @Override
        public Date getIssued() {
          return new Date((Long) instanceData[6]);
        }

        @Override
        public int getMinVolume() {
          return (Integer) instanceData[7];
        }

        @Override
        public long getOrderID() {
          return (Long) instanceData[0];
        }

        @Override
        public int getOrderState() {
          return (Integer) instanceData[8];
        }

        @Override
        public BigDecimal getPrice() {
          return (BigDecimal) instanceData[9];
        }

        @Override
        public int getRange() {
          return (Integer) instanceData[10];
        }

        @Override
        public long getStationID() {
          return (Long) instanceData[11];
        }

        @Override
        public int getTypeID() {
          return (Integer) instanceData[12];
        }

        @Override
        public int getVolEntered() {
          return (Integer) instanceData[13];
        }

        @Override
        public int getVolRemaining() {
          return (Integer) instanceData[14];
        }

      };
      orders.add(next);
    }

    EasyMock.expect(mockServer.requestMarketOrders()).andReturn(orders);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  public void setupOpenCheckMock(TimeGenerator gen, List<IMarketOrder> batchOrders, Map<Long, IMarketOrder> closedOrders) throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      final long issueTime = gen.getTime();
      IMarketOrder next = new IMarketOrder() {

        @Override
        public int getAccountKey() {
          return (Integer) instanceData[1];
        }

        @Override
        public int getBid() {
          if ((Boolean) instanceData[2]) { return 1; }
          return 0;
        }

        @Override
        public long getCharID() {
          return (Long) instanceData[3];
        }

        @Override
        public int getDuration() {
          return (Integer) instanceData[4];
        }

        @Override
        public BigDecimal getEscrow() {
          return (BigDecimal) instanceData[5];
        }

        @Override
        public Date getIssued() {
          return new Date(issueTime);
        }

        @Override
        public int getMinVolume() {
          return (Integer) instanceData[7];
        }

        @Override
        public long getOrderID() {
          return (Long) instanceData[0];
        }

        @Override
        public int getOrderState() {
          // Return state, but ensure we never return the open state.
          int state = (Integer) instanceData[8];
          if (state == 0) {
            state = 2;
          }
          return state;
        }

        @Override
        public BigDecimal getPrice() {
          return (BigDecimal) instanceData[9];
        }

        @Override
        public int getRange() {
          return (Integer) instanceData[10];
        }

        @Override
        public long getStationID() {
          return (Long) instanceData[11];
        }

        @Override
        public int getTypeID() {
          return (Integer) instanceData[12];
        }

        @Override
        public int getVolEntered() {
          return (Integer) instanceData[13];
        }

        @Override
        public int getVolRemaining() {
          return (Integer) instanceData[14];
        }

      };
      if (i % 2 == 0) {
        batchOrders.add(next);
      } else {
        closedOrders.put(next.getOrderID(), next);
      }
    }

    EasyMock.expect(mockServer.requestMarketOrders()).andReturn(batchOrders);
    for (int i = 0; i < testData.length; i++) {
      if (i % 2 != 0) {
        long orderID = (Long) testData[i][0];
        EasyMock.expect(mockServer.requestMarketOrder(orderID)).andReturn(closedOrders.get(orderID));
      }
    }
    EasyMock.expect(mockServer.isError()).andReturn(false).anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate)).anyTimes();
  }

  // Test update with new market orders
  @Test
  public void testCorporationMarketOrderSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationMarketOrderSync.syncCorporationMarketOrders(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify orders were added correctly.
    for (int i = 0; i < testData.length; i++) {
      long orderID = (Long) testData[i][0];
      MarketOrder next = MarketOrder.get(syncAccount, testTime, orderID);
      compareWithTestData(next, testData[i], null);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMarketOrdersExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersDetail());
  }

  // Test update with stats already populated
  @Test
  public void testCorporationMarketOrderSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate orders
    for (int i = 0; i < testData.length; i++) {
      CachedData.updateData(makeOrder(testTime, testData[i], null));
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationMarketOrderSync.syncCorporationMarketOrders(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify orders are unchanged
    for (int i = 0; i < testData.length; i++) {
      long orderID = (Long) testData[i][0];
      MarketOrder next = MarketOrder.get(syncAccount, testTime, orderID);
      compareWithTestData(next, testData[i], null);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMarketOrdersExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationMarketOrderSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing orders
    for (int i = 0; i < testData.length; i++) {
      CachedData.updateData(makeOrder(testTime, testData[i], null));
    }

    // Set the tracker as already updated and populate the container
    tracker.setMarketOrdersStatus(SyncState.UPDATED);
    tracker.setMarketOrdersDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setMarketOrdersExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationMarketOrderSync.syncCorporationMarketOrders(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify jobs unchanged
    for (int i = 0; i < testData.length; i++) {
      long orderID = (Long) testData[i][0];
      MarketOrder next = MarketOrder.get(syncAccount, testTime, orderID);
      compareWithTestData(next, testData[i], null);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getMarketOrdersExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersDetail());
  }

  // Test update with open state market orders that are later updated
  @Test
  public void testCorporationMarketOrderOpenStateChange() throws Exception {
    long testTime = 1234L;

    TimeGenerator gen = new TimeGenerator() {
      @Override
      public long getTime() {
        return 10L;
      }
    };
    OrbitalProperties.setTimeGenerator(gen);
    // The orders in this list are returned on requestMarketOrders
    List<IMarketOrder> batchOrders = new ArrayList<IMarketOrder>();
    // The orders in this map are populated in the open state, but will not
    // be returned in requestMarketOrders. These will need to be requested
    // individually as part of the sync.
    Map<Long, IMarketOrder> closedOrders = new HashMap<Long, IMarketOrder>();
    setupOpenCheckMock(gen, batchOrders, closedOrders);
    EasyMock.replay(mockServer);

    // Pre-populate with all batch and closed orders. Closed orders are populated in the open state.
    for (int i = 0; i < testData.length; i++) {
      long orderID = (Long) testData[i][0];
      MarketOrder next = makeOrder(testTime, testData[i], closedOrders.containsKey(orderID) ? new Integer(0) : null);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationMarketOrderSync.syncCorporationMarketOrders(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify orders were added correctly. This includes updating the state of the missing orders.
    for (int i = 0; i < testData.length; i++) {
      long orderID = (Long) testData[i][0];
      MarketOrder next = MarketOrder.get(syncAccount, testTime, orderID);
      compareWithTestData(next, testData[i], 10L);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMarketOrdersExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMarketOrdersDetail());
  }

}
