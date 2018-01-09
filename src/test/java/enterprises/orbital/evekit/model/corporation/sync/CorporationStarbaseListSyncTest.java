package enterprises.orbital.evekit.model.corporation.sync;

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
import enterprises.orbital.evekit.model.corporation.Fuel;
import enterprises.orbital.evekit.model.corporation.Starbase;
import enterprises.orbital.evekit.model.corporation.StarbaseDetail;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IStarbase;

public class CorporationStarbaseListSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  // 0 long itemID;
  // 1 long locationID;
  // 2 int moonID;
  // 3 long onlineDate;
  // 4 int state;
  // 5 long stateDate;
  // 6 int typeID;
  // 7 long standingOwnerID;
  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 100 + TestBase.getRandomInt(100);
    testData = new Object[size][8];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomInt();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomInt();
      testData[i][5] = TestBase.getRandomLong();
      testData[i][6] = TestBase.getRandomInt();
      testData[i][7] = TestBase.getRandomLong();
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
    Collection<IStarbase> starbases = new ArrayList<IStarbase>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      starbases.add(new IStarbase() {

        @Override
        public long getItemID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getLocationID() {
          return (Long) instanceData[1];
        }

        @Override
        public int getMoonID() {
          return (Integer) instanceData[2];
        }

        @Override
        public Date getOnlineTimestamp() {
          return new Date((Long) instanceData[3]);
        }

        @Override
        public int getState() {
          return (Integer) instanceData[4];
        }

        @Override
        public Date getStateTimestamp() {
          return new Date((Long) instanceData[5]);
        }

        @Override
        public int getTypeID() {
          return (Integer) instanceData[6];
        }

        @Override
        public long getStandingOwnerID() {
          return (Long) instanceData[7];
        }

      });

    }

    EasyMock.expect(mockServer.requestStarbaseList()).andReturn(starbases);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new starbases
  @Test
  public void testCorporationStarbaseListSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationStarbaseListSync.syncStarbaseList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify starbases were added correctly.
    for (int i = 0; i < testData.length; i++) {
      Starbase next = Starbase.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getItemID());
      Assert.assertEquals(testData[i][1], next.getLocationID());
      Assert.assertEquals(testData[i][2], next.getMoonID());
      Assert.assertEquals(testData[i][3], next.getOnlineTimestamp());
      Assert.assertEquals(testData[i][4], next.getState());
      Assert.assertEquals(testData[i][5], next.getStateTimestamp());
      Assert.assertEquals(testData[i][6], next.getTypeID());
      Assert.assertEquals(testData[i][7], next.getStandingOwnerID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getStarbaseListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseListDetail());
  }

  // Test update with starbases already populated
  @Test
  public void testCorporatioStarbaseListSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing starbases
    int totalFuelCount = 0;
    for (int i = 0; i < testData.length; i++) {
      Starbase next = new Starbase(
          (Long) testData[i][0] + 2, (Long) testData[i][1] + 2, (Integer) testData[i][2] + 2, (Long) testData[i][3] + 2, (Integer) testData[i][4] + 2,
          (Long) testData[i][5] + 2, (Integer) testData[i][6] + 2, (Long) testData[i][7] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      StarbaseDetail dt = new StarbaseDetail(next.getItemID(), 0, 0, 0, 0, 0, false, false, 0, false, 0, false, 0, false, 0, false, 0);
      dt.setup(syncAccount, testTime);
      dt = CachedData.update(dt);
      int fuelCount = 3 + TestBase.getRandomInt(5);
      totalFuelCount += fuelCount;
      for (int j = 0; j < fuelCount; j++) {
        Fuel f = new Fuel(next.getItemID(), TestBase.getUniqueRandomInteger(), TestBase.getUniqueRandomInteger());
        f.setup(syncAccount, testTime);
        f = CachedData.update(f);
      }
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationStarbaseListSync.syncStarbaseList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify starbases have been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      Starbase next = Starbase.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getItemID());
      Assert.assertEquals(testData[i][1], next.getLocationID());
      Assert.assertEquals(testData[i][2], next.getMoonID());
      Assert.assertEquals(testData[i][3], next.getOnlineTimestamp());
      Assert.assertEquals(testData[i][4], next.getState());
      Assert.assertEquals(testData[i][5], next.getStateTimestamp());
      Assert.assertEquals(testData[i][6], next.getTypeID());
      Assert.assertEquals(testData[i][7], next.getStandingOwnerID());
    }

    // Verify previous starbase details and fuels were evolved
    Assert.assertEquals(testData.length, StarbaseDetail.getAll(syncAccount, testTime).size());
    Assert.assertEquals(totalFuelCount, Fuel.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getStarbaseListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseListDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationStarbaseListSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing starbases
    int totalFuelCount = 0;
    for (int i = 0; i < testData.length; i++) {
      Starbase next = new Starbase(
          (Long) testData[i][0] + 2, (Long) testData[i][1] + 2, (Integer) testData[i][2] + 2, (Long) testData[i][3] + 2, (Integer) testData[i][4] + 2,
          (Long) testData[i][5] + 2, (Integer) testData[i][6] + 2, (Long) testData[i][7] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      StarbaseDetail dt = new StarbaseDetail(next.getItemID(), 0, 0, 0, 0, 0, false, false, 0, false, 0, false, 0, false, 0, false, 0);
      dt.setup(syncAccount, testTime);
      dt = CachedData.update(dt);
      int fuelCount = 3 + TestBase.getRandomInt(5);
      totalFuelCount += fuelCount;
      for (int j = 0; j < fuelCount; j++) {
        Fuel f = new Fuel(next.getItemID(), TestBase.getUniqueRandomInteger(), TestBase.getUniqueRandomInteger());
        f.setup(syncAccount, testTime);
        f = CachedData.update(f);
      }
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setStarbaseListStatus(SyncState.UPDATED);
    tracker.setStarbaseListDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setStarbaseListExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationStarbaseListSync.syncStarbaseList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify starbase list unchanged
    for (int i = 0; i < testData.length; i++) {
      Starbase next = Starbase.get(syncAccount, testTime, (Long) testData[i][0] + 2);
      Assert.assertEquals((Long) testData[i][0] + 2, next.getItemID());
      Assert.assertEquals((Long) testData[i][1] + 2, next.getLocationID());
      Assert.assertEquals((Integer) testData[i][2] + 2, next.getMoonID());
      Assert.assertEquals((Long) testData[i][3] + 2, next.getOnlineTimestamp());
      Assert.assertEquals((Integer) testData[i][4] + 2, next.getState());
      Assert.assertEquals((Long) testData[i][5] + 2, next.getStateTimestamp());
      Assert.assertEquals((Integer) testData[i][6] + 2, next.getTypeID());
      Assert.assertEquals((Long) testData[i][7] + 2, next.getStandingOwnerID());
    }

    // Verify previous starbase details and fuels were unchanged
    Assert.assertEquals(testData.length, StarbaseDetail.getAll(syncAccount, testTime).size());
    Assert.assertEquals(totalFuelCount, Fuel.getAll(syncAccount, testTime).size());

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getStarbaseListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseListDetail());
  }

}
