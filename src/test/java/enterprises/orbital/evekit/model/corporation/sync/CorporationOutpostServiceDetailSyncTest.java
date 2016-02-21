package enterprises.orbital.evekit.model.corporation.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.OutpostServiceDetail;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IOutpost;
import enterprises.orbital.evexmlapi.crp.IOutpostServiceDetail;

public class CorporationOutpostServiceDetailSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                          testDate;
  long                          prevDate;
  EveKitUserAccount             userAccount;
  SynchronizedEveAccount        syncAccount;
  Corporation                   container;
  CorporationSyncTracker        tracker;
  SynchronizerUtil              syncUtil;
  ICorporationAPI               mockServer;

  // 0 long stationID;
  // ...rest of Outpost data is irrelevant for this test.
  protected static Object[]     outpostData;

  // 0 long stationID;
  // 1 String serviceName;
  // 2 long ownerID;
  // 3 double minStanding;
  // 4 BigDecimal surchargePerBadStanding;
  // 5 BigDecimal discountPerGoodStanding;
  // index -> random number of services for this outpost -> array with fields above
  protected static Object[][][] testData;

  static {
    // Generate test data
    int size = 10 + TestBase.getRandomInt(10);
    outpostData = new Object[size];
    for (int i = 0; i < size; i++) {
      outpostData[i] = TestBase.getUniqueRandomLong();
    }
    testData = new Object[size][][];
    for (int i = 0; i < size; i++) {
      int numServices = 1 + TestBase.getRandomInt(3);
      testData[i] = new Object[numServices][6];
      for (int j = 0; j < numServices; j++) {
        testData[i][j][0] = outpostData[i];
        testData[i][j][1] = TestBase.getRandomText(20) + j;
        testData[i][j][2] = TestBase.getRandomLong();
        testData[i][j][3] = TestBase.getRandomDouble(2);
        testData[i][j][4] = (new BigDecimal(TestBase.getRandomDouble(100000))).setScale(2, RoundingMode.HALF_UP);
        testData[i][j][5] = (new BigDecimal(TestBase.getRandomDouble(100000))).setScale(2, RoundingMode.HALF_UP);
      }
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true, 1234, "abcd", 5678, "charname", 8765, "corpname");
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IOutpost> outposts = new ArrayList<IOutpost>();
    for (int i = 0; i < outpostData.length; i++) {
      final long stationID = (Long) outpostData[i];
      outposts.add(new IOutpost() {

        @Override
        public long getStationID() {
          return stationID;
        }

        @Override
        public long getOwnerID() {
          return 0L;
        }

        @Override
        public String getStationName() {
          return "";
        }

        @Override
        public long getSolarSystemID() {
          return 0L;
        }

        @Override
        public double getDockingCostPerShipVolume() {
          return 0d;
        }

        @Override
        public double getOfficeRentalCost() {
          return 0d;
        }

        @Override
        public int getStationTypeID() {
          return 0;
        }

        @Override
        public double getReprocessingEfficiency() {
          return 0d;
        }

        @Override
        public double getReprocessingStationTake() {
          return 0d;
        }

        @Override
        public long getStandingOwnerID() {
          return 0L;
        }

        @Override
        public long getX() {
          return 0;
        }

        @Override
        public long getY() {
          return 0;
        }

        @Override
        public long getZ() {
          return 0;
        }
      });

    }

    EasyMock.expect(mockServer.requestOutpostList()).andReturn(outposts).anyTimes();
    for (int i = 0; i < testData.length; i++) {
      Collection<IOutpostServiceDetail> details = new ArrayList<IOutpostServiceDetail>();
      for (int j = 0; j < testData[i].length; j++) {
        final Object[] instanceData = testData[i][j];
        details.add(new IOutpostServiceDetail() {

          @Override
          public long getStationID() {
            return (Long) instanceData[0];
          }

          @Override
          public String getServiceName() {
            return (String) instanceData[1];
          }

          @Override
          public long getOwnerID() {
            return (Long) instanceData[2];
          }

          @Override
          public double getMinStanding() {
            return (Double) instanceData[3];
          }

          @Override
          public double getSurchargePerBadStanding() {
            return ((BigDecimal) instanceData[4]).doubleValue();
          }

          @Override
          public double getDiscountPerGoodStanding() {
            return ((BigDecimal) instanceData[5]).doubleValue();
          }

        });
      }
      EasyMock.expect(mockServer.requestOutpostServiceDetail((Long) outpostData[i])).andReturn(details).anyTimes();
    }
    EasyMock.expect(mockServer.isError()).andReturn(false).anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate)).anyTimes();
  }

  // Test update with all new outpost details
  @Test
  public void testCorporationOutpostServiceDetailSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationOutpostServiceDetailSync.syncOutpostServiceDetail(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify outpost details were added correctly.
    for (int i = 0; i < testData.length; i++) {
      for (int j = 0; j < testData[i].length; j++) {
        OutpostServiceDetail next = OutpostServiceDetail.get(syncAccount, testTime, (Long) testData[i][j][0], (String) testData[i][j][1]);
        Assert.assertEquals(testData[i][j][0], next.getStationID());
        Assert.assertEquals(testData[i][j][1], next.getServiceName());
        Assert.assertEquals(testData[i][j][2], next.getOwnerID());
        Assert.assertEquals(testData[i][j][3], next.getMinStanding());
        Assert.assertEquals(testData[i][j][4], next.getSurchargePerBadStanding());
        Assert.assertEquals(testData[i][j][5], next.getDiscountPerGoodStanding());
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getOutpostServiceDetailExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostDetailStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostDetailDetail());
  }

  // Test update with outpost details already populated
  @Test
  public void testCorporatioOutpostServiceDetailSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing outpost details.
    for (int i = 0; i < testData.length; i++) {
      for (int j = 0; j < testData[i].length; j++) {
        OutpostServiceDetail next = new OutpostServiceDetail(
            (Long) testData[i][j][0] + 2, (String) testData[i][j][1] + "A", (Long) testData[i][j][2] + 2, (Double) testData[i][j][3] + 2d,
            ((BigDecimal) testData[i][j][4]).add(new BigDecimal(2)), ((BigDecimal) testData[i][j][5]).add(new BigDecimal(2)));
        next.setup(syncAccount, testTime);
        next = CachedData.updateData(next);
      }
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationOutpostServiceDetailSync.syncOutpostServiceDetail(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify outpost details have been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      for (int j = 0; j < testData[i].length; j++) {
        OutpostServiceDetail next = OutpostServiceDetail.get(syncAccount, testTime, (Long) testData[i][j][0], (String) testData[i][j][1]);
        Assert.assertEquals(testData[i][j][0], next.getStationID());
        Assert.assertEquals(testData[i][j][1], next.getServiceName());
        Assert.assertEquals(testData[i][j][2], next.getOwnerID());
        Assert.assertEquals(testData[i][j][3], next.getMinStanding());
        Assert.assertEquals(testData[i][j][4], next.getSurchargePerBadStanding());
        Assert.assertEquals(testData[i][j][5], next.getDiscountPerGoodStanding());
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getOutpostServiceDetailExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostDetailStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostDetailDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationOutpostServiceDetailSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing details.
    for (int i = 0; i < testData.length; i++) {
      for (int j = 0; j < testData[i].length; j++) {
        OutpostServiceDetail next = new OutpostServiceDetail(
            (Long) testData[i][j][0] + 2, (String) testData[i][j][1] + "A", (Long) testData[i][j][2] + 2, (Double) testData[i][j][3] + 2d,
            ((BigDecimal) testData[i][j][4]).add(new BigDecimal(2)), ((BigDecimal) testData[i][j][5]).add(new BigDecimal(2)));
        next.setup(syncAccount, testTime);
        next = CachedData.updateData(next);
      }
    }

    // Set the tracker as already updated and populate the container
    tracker.setOutpostDetailStatus(SyncState.UPDATED);
    tracker.setOutpostDetailDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setOutpostServiceDetailExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationOutpostServiceDetailSync.syncOutpostServiceDetail(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify detailss unchanged.
    for (int i = 0; i < testData.length; i++) {
      for (int j = 0; j < testData[i].length; j++) {
        OutpostServiceDetail next = OutpostServiceDetail.get(syncAccount, testTime, (Long) testData[i][j][0] + 2, (String) testData[i][j][1] + "A");
        Assert.assertEquals((Long) testData[i][j][0] + 2, next.getStationID());
        Assert.assertEquals((String) testData[i][j][1] + "A", next.getServiceName());
        Assert.assertEquals((Long) testData[i][j][2] + 2, next.getOwnerID());
        Assert.assertEquals((Double) testData[i][j][3] + 2d, next.getMinStanding(), 0.01);
        Assert.assertEquals(((BigDecimal) testData[i][j][4]).add(new BigDecimal(2)), next.getSurchargePerBadStanding());
        Assert.assertEquals(((BigDecimal) testData[i][j][5]).add(new BigDecimal(2)), next.getDiscountPerGoodStanding());
      }
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getOutpostServiceDetailExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostDetailStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostDetailDetail());
  }

}
