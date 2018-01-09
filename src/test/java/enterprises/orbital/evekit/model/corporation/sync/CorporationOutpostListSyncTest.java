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
import enterprises.orbital.evekit.model.corporation.Outpost;
import enterprises.orbital.evekit.model.corporation.OutpostServiceDetail;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IOutpost;

public class CorporationOutpostListSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  // 0 long stationID;
  // 1 long ownerID;
  // 2 String stationName;
  // 3 int solarSystemID;
  // 4 BigDecimal dockingCostPerShipVolume;
  // 5 BigDecimal officeRentalCost;
  // 6 int stationTypeID;
  // 7 double reprocessingEfficiency;
  // 8 double reprocessingStationTake;
  // 9 long standingOwnerID;
  // 10 long x
  // 11 long y
  // 12 long z
  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 10 + TestBase.getRandomInt(10);
    testData = new Object[size][13];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomText(100);
      testData[i][3] = TestBase.getRandomInt();
      testData[i][4] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
      testData[i][5] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
      testData[i][6] = TestBase.getRandomInt();
      testData[i][7] = TestBase.getRandomDouble(2);
      testData[i][8] = TestBase.getRandomDouble(2);
      testData[i][9] = TestBase.getRandomLong();
      testData[i][10] = TestBase.getRandomLong();
      testData[i][11] = TestBase.getRandomLong();
      testData[i][12] = TestBase.getRandomLong();
    }
  }

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

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IOutpost> outposts = new ArrayList<IOutpost>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      outposts.add(new IOutpost() {

        @Override
        public long getStationID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getOwnerID() {
          return (Long) instanceData[1];
        }

        @Override
        public String getStationName() {
          return (String) instanceData[2];
        }

        @Override
        public int getSolarSystemID() {
          return (Integer) instanceData[3];
        }

        @Override
        public double getDockingCostPerShipVolume() {
          return ((BigDecimal) instanceData[4]).doubleValue();
        }

        @Override
        public double getOfficeRentalCost() {
          return ((BigDecimal) instanceData[5]).doubleValue();
        }

        @Override
        public int getStationTypeID() {
          return (Integer) instanceData[6];
        }

        @Override
        public double getReprocessingEfficiency() {
          return (Double) instanceData[7];
        }

        @Override
        public double getReprocessingStationTake() {
          return (Double) instanceData[8];
        }

        @Override
        public long getStandingOwnerID() {
          return (Long) instanceData[9];
        }

        @Override
        public long getX() {
          return (Long) instanceData[10];
        }

        @Override
        public long getY() {
          return (Long) instanceData[11];
        }

        @Override
        public long getZ() {
          return (Long) instanceData[12];
        }
      });

    }

    EasyMock.expect(mockServer.requestOutpostList()).andReturn(outposts);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new outposts
  @Test
  public void testCorporationOutpostListSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationOutpostListSync.syncOutpostList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify outposts were added correctly.
    for (int i = 0; i < testData.length; i++) {
      Outpost next = Outpost.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getStationID());
      Assert.assertEquals(testData[i][1], next.getOwnerID());
      Assert.assertEquals(testData[i][2], next.getStationName());
      Assert.assertEquals(testData[i][3], next.getSolarSystemID());
      Assert.assertEquals(testData[i][4], next.getDockingCostPerShipVolume());
      Assert.assertEquals(testData[i][5], next.getOfficeRentalCost());
      Assert.assertEquals(testData[i][6], next.getStationTypeID());
      Assert.assertEquals(testData[i][7], next.getReprocessingEfficiency());
      Assert.assertEquals(testData[i][8], next.getReprocessingStationTake());
      Assert.assertEquals(testData[i][9], next.getStandingOwnerID());
      Assert.assertEquals(testData[i][10], next.getX());
      Assert.assertEquals(testData[i][11], next.getY());
      Assert.assertEquals(testData[i][12], next.getZ());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getOutpostListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostListDetail());
  }

  // Test update with outposts already populated
  @Test
  public void testCorporatioOutpostListSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing outposts.
    int totalDetailCount = 0;
    for (int i = 0; i < testData.length; i++) {
      Outpost next = new Outpost(
          (Long) testData[i][0] + 2, (Long) testData[i][1] + 2, (String) testData[i][2] + "A", (Integer) testData[i][3] + 2,
          ((BigDecimal) testData[i][4]).add(new BigDecimal(2)), ((BigDecimal) testData[i][5]).add(new BigDecimal(2)), (Integer) testData[i][6] + 2,
          (Double) testData[i][7] + 2, (Double) testData[i][8] + 2, (Long) testData[i][9] + 2, (Long) testData[i][10] + 2, (Long) testData[i][11] + 2,
          (Long) testData[i][12] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      // Add outpost details. These should be deleted.
      int detailCount = 3 + TestBase.getRandomInt(5);
      totalDetailCount += detailCount;
      for (int j = 0; j < detailCount; j++) {
        OutpostServiceDetail dt = new OutpostServiceDetail(
            next.getStationID(), TestBase.getRandomText(20) + String.valueOf(j), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        dt.setup(syncAccount, testTime);
        dt = CachedData.update(dt);
      }
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationOutpostListSync.syncOutpostList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify outposts have been evolved to downloaded version
    for (int i = 0; i < testData.length; i++) {
      Outpost next = Outpost.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getStationID());
      Assert.assertEquals(testData[i][1], next.getOwnerID());
      Assert.assertEquals(testData[i][2], next.getStationName());
      Assert.assertEquals(testData[i][3], next.getSolarSystemID());
      Assert.assertEquals(testData[i][4], next.getDockingCostPerShipVolume());
      Assert.assertEquals(testData[i][5], next.getOfficeRentalCost());
      Assert.assertEquals(testData[i][6], next.getStationTypeID());
      Assert.assertEquals(testData[i][7], next.getReprocessingEfficiency());
      Assert.assertEquals(testData[i][8], next.getReprocessingStationTake());
      Assert.assertEquals(testData[i][9], next.getStandingOwnerID());
      Assert.assertEquals(testData[i][10], next.getX());
      Assert.assertEquals(testData[i][11], next.getY());
      Assert.assertEquals(testData[i][12], next.getZ());
    }

    // Verify old outpost service details were evolved
    Assert.assertEquals(totalDetailCount, OutpostServiceDetail.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getOutpostListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostListDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationOutpostListSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing outposts.
    int totalDetail = 0;
    for (int i = 0; i < testData.length; i++) {
      Outpost next = new Outpost(
          (Long) testData[i][0] + 2, (Long) testData[i][1] + 2, (String) testData[i][2] + "A", (Integer) testData[i][3] + 2,
          ((BigDecimal) testData[i][4]).add(new BigDecimal(2)), ((BigDecimal) testData[i][5]).add(new BigDecimal(2)), (Integer) testData[i][6] + 2,
          (Double) testData[i][7] + 2, (Double) testData[i][8] + 2, (Long) testData[i][9] + 2, (Long) testData[i][10] + 2, (Long) testData[i][11] + 2,
          (Long) testData[i][12] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      // Add outpost details. These should be deleted.
      int detailCount = 3 + TestBase.getRandomInt(5);
      for (int j = 0; j < detailCount; j++) {
        OutpostServiceDetail dt = new OutpostServiceDetail(
            next.getStationID(), TestBase.getRandomText(20) + String.valueOf(j), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        dt.setup(syncAccount, testTime);
        dt = CachedData.update(dt);
      }
      totalDetail += detailCount;
    }

    // Set the tracker as already updated and populate the container
    tracker.setOutpostListStatus(SyncState.UPDATED);
    tracker.setOutpostListDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setOutpostListExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationOutpostListSync.syncOutpostList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify outposts unchanged.
    for (int i = 0; i < testData.length; i++) {
      Outpost next = Outpost.get(syncAccount, testTime, (Long) testData[i][0] + 2);
      Assert.assertEquals((Long) testData[i][0] + 2, next.getStationID());
      Assert.assertEquals((Long) testData[i][1] + 2, next.getOwnerID());
      Assert.assertEquals((String) testData[i][2] + "A", next.getStationName());
      Assert.assertEquals((Integer) testData[i][3] + 2, next.getSolarSystemID());
      Assert.assertEquals(((BigDecimal) testData[i][4]).add(new BigDecimal(2)), next.getDockingCostPerShipVolume());
      Assert.assertEquals(((BigDecimal) testData[i][5]).add(new BigDecimal(2)), next.getOfficeRentalCost());
      Assert.assertEquals((Integer) testData[i][6] + 2, next.getStationTypeID());
      Assert.assertEquals((Double) testData[i][7] + 2, next.getReprocessingEfficiency(), 0.01);
      Assert.assertEquals((Double) testData[i][8] + 2, next.getReprocessingStationTake(), 0.01);
      Assert.assertEquals((Long) testData[i][9] + 2, next.getStandingOwnerID());
      Assert.assertEquals((Long) testData[i][10] + 2, next.getX());
      Assert.assertEquals((Long) testData[i][11] + 2, next.getY());
      Assert.assertEquals((Long) testData[i][12] + 2, next.getZ());
    }

    // Verify old outpost service details were maintained.
    Assert.assertEquals(totalDetail, OutpostServiceDetail.getAll(syncAccount, testTime).size());

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getOutpostListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getOutpostListDetail());
  }
}
