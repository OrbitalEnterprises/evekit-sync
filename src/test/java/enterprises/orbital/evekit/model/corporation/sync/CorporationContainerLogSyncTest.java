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
import enterprises.orbital.evekit.model.corporation.ContainerLog;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.IContainerLog;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;

public class CorporationContainerLogSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 1000 + TestBase.getRandomInt(1000);
    testData = new Object[size][13];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomText(50);
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = TestBase.getRandomText(50);
      testData[i][4] = TestBase.getRandomInt() & 0xFF;
      testData[i][5] = TestBase.getRandomLong();
      testData[i][6] = TestBase.getRandomInt();
      testData[i][7] = TestBase.getRandomLong();
      testData[i][8] = TestBase.getRandomText(50);
      testData[i][9] = TestBase.getRandomText(50);
      testData[i][10] = TestBase.getRandomText(50);
      testData[i][11] = TestBase.getRandomLong();
      testData[i][12] = TestBase.getRandomInt();
    }
  }

  // Mock up server interface
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
    Collection<IContainerLog> logs = new ArrayList<IContainerLog>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      logs.add(new IContainerLog() {

        @Override
        public String getAction() {
          return (String) instanceData[1];
        }

        @Override
        public long getActorID() {
          return (Long) instanceData[2];
        }

        @Override
        public String getActorName() {
          return (String) instanceData[3];
        }

        @Override
        public byte getFlag() {
          return (byte) (((Integer) instanceData[4]).intValue() & 0xFF);
        }

        @Override
        public long getItemID() {
          return (Long) instanceData[5];
        }

        @Override
        public int getItemTypeID() {
          return (Integer) instanceData[6];
        }

        @Override
        public long getLocationID() {
          return (Long) instanceData[7];
        }

        @Override
        public Date getLogTime() {
          return new Date((Long) instanceData[0]);
        }

        @Override
        public String getNewConfiguration() {
          return (String) instanceData[8];
        }

        @Override
        public String getOldConfiguration() {
          return (String) instanceData[9];
        }

        @Override
        public String getPasswordType() {
          return (String) instanceData[10];
        }

        @Override
        public long getQuantity() {
          return (Long) instanceData[11];
        }

        @Override
        public int getTypeID() {
          return (Integer) instanceData[12];
        }

      });

    }

    EasyMock.expect(mockServer.requestContainerLogs()).andReturn(logs);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new logs
  @Test
  public void testCorporationContainerLogsSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationContainerLogSync.syncCorporationContainerLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify logs were added correctly.
    for (int i = 0; i < testData.length; i++) {
      ContainerLog next = ContainerLog.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getLogTime());
      Assert.assertEquals(testData[i][1], next.getAction());
      Assert.assertEquals(testData[i][2], next.getActorID());
      Assert.assertEquals(testData[i][3], next.getActorName());
      Assert.assertEquals(((Integer) testData[i][4]).intValue() & 0xFF, next.getFlag());
      Assert.assertEquals(testData[i][5], next.getItemID());
      Assert.assertEquals(testData[i][6], next.getItemTypeID());
      Assert.assertEquals(testData[i][7], next.getLocationID());
      Assert.assertEquals(testData[i][8], next.getNewConfiguration());
      Assert.assertEquals(testData[i][9], next.getOldConfiguration());
      Assert.assertEquals(testData[i][10], next.getPasswordType());
      Assert.assertEquals(testData[i][11], next.getQuantity());
      Assert.assertEquals(testData[i][12], next.getTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContainerLogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContainerLogStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContainerLogDetail());
  }

  // Test update with logs already populated
  @Test
  public void testCorporationContainerLogSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing logs
    for (int i = 0; i < testData.length; i++) {
      ContainerLog next = new ContainerLog(
          (Long) testData[i][0], (String) testData[i][1] + "A", (Long) testData[i][2] + 2, (String) testData[i][3] + "A", (Integer) testData[i][4] + 2,
          (Long) testData[i][5] + 2, (Integer) testData[i][6] + 2, (Long) testData[i][7] + 2, (String) testData[i][8] + "A", (String) testData[i][9] + "A",
          (String) testData[i][10] + "A", (Long) testData[i][11] + 2, (Integer) testData[i][12] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationContainerLogSync.syncCorporationContainerLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify logs have been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      ContainerLog next = ContainerLog.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getLogTime());
      Assert.assertEquals(testData[i][1], next.getAction());
      Assert.assertEquals(testData[i][2], next.getActorID());
      Assert.assertEquals(testData[i][3], next.getActorName());
      Assert.assertEquals(testData[i][4], next.getFlag());
      Assert.assertEquals(testData[i][5], next.getItemID());
      Assert.assertEquals(testData[i][6], next.getItemTypeID());
      Assert.assertEquals(testData[i][7], next.getLocationID());
      Assert.assertEquals(testData[i][8], next.getNewConfiguration());
      Assert.assertEquals(testData[i][9], next.getOldConfiguration());
      Assert.assertEquals(testData[i][10], next.getPasswordType());
      Assert.assertEquals(testData[i][11], next.getQuantity());
      Assert.assertEquals(testData[i][12], next.getTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContainerLogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContainerLogStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContainerLogDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationContainerLogSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing logs
    for (int i = 0; i < testData.length; i++) {
      ContainerLog next = new ContainerLog(
          (Long) testData[i][0], (String) testData[i][1] + "A", (Long) testData[i][2] + 2, (String) testData[i][3] + "A", (Integer) testData[i][4] + 2,
          (Long) testData[i][5] + 2, (Integer) testData[i][6] + 2, (Long) testData[i][7] + 2, (String) testData[i][8] + "A", (String) testData[i][9] + "A",
          (String) testData[i][10] + "A", (Long) testData[i][11] + 2, (Integer) testData[i][12] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setContainerLogStatus(SyncState.UPDATED);
    tracker.setContainerLogDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setContainerLogExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationContainerLogSync.syncCorporationContainerLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify logs unchanged
    for (int i = 0; i < testData.length; i++) {
      ContainerLog next = ContainerLog.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getLogTime());
      Assert.assertEquals((String) testData[i][1] + "A", next.getAction());
      Assert.assertEquals((Long) testData[i][2] + 2, next.getActorID());
      Assert.assertEquals((String) testData[i][3] + "A", next.getActorName());
      Assert.assertEquals((Integer) testData[i][4] + 2, next.getFlag());
      Assert.assertEquals((Long) testData[i][5] + 2, next.getItemID());
      Assert.assertEquals((Integer) testData[i][6] + 2, next.getItemTypeID());
      Assert.assertEquals((Long) testData[i][7] + 2, next.getLocationID());
      Assert.assertEquals((String) testData[i][8] + "A", next.getNewConfiguration());
      Assert.assertEquals((String) testData[i][9] + "A", next.getOldConfiguration());
      Assert.assertEquals((String) testData[i][10] + "A", next.getPasswordType());
      Assert.assertEquals((Long) testData[i][11] + 2, next.getQuantity());
      Assert.assertEquals((Integer) testData[i][12] + 2, next.getTypeID());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getContainerLogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContainerLogStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContainerLogDetail());
  }

}
