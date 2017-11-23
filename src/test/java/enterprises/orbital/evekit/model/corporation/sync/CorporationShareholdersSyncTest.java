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
import enterprises.orbital.evekit.model.corporation.Shareholder;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IShareholder;

public class CorporationShareholdersSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  // 0 long shareholderID;
  // 1 boolean isCorporation;
  // 2 long shareholderCorporationID;
  // 3 String shareholderCorporationName;
  // 4 String shareholderName;
  // 5 int shares;
  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 1000 + TestBase.getRandomInt(1000);
    testData = new Object[size][6];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomBoolean();
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = TestBase.getRandomText(100);
      testData[i][4] = TestBase.getRandomText(100);
      testData[i][5] = TestBase.getRandomInt(100000);
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
    Collection<IShareholder> shareholders = new ArrayList<IShareholder>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      shareholders.add(new IShareholder() {

        @Override
        public boolean isCorporation() {
          return (Boolean) instanceData[1];
        }

        @Override
        public long getShareholderCorporationID() {
          return (Long) instanceData[2];
        }

        @Override
        public String getShareholderCorporationName() {
          return (String) instanceData[3];
        }

        @Override
        public long getShareholderID() {
          return (Long) instanceData[0];
        }

        @Override
        public String getShareholderName() {
          return (String) instanceData[4];
        }

        @Override
        public int getShares() {
          return (Integer) instanceData[5];
        }
      });

    }

    EasyMock.expect(mockServer.requestShareholders()).andReturn(shareholders);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new shareholders.
  @Test
  public void testCorporationShareholdersSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationShareholdersSync.syncShareholders(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify shareholders were added correctly.
    for (int i = 0; i < testData.length; i++) {
      Shareholder next = Shareholder.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getShareholderID());
      Assert.assertEquals(testData[i][1], next.isCorporation());
      Assert.assertEquals(testData[i][2], next.getShareholderCorporationID());
      Assert.assertEquals(testData[i][3], next.getShareholderCorporationName());
      Assert.assertEquals(testData[i][4], next.getShareholderName());
      Assert.assertEquals(testData[i][5], next.getShares());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getShareholdersExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getShareholderStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getShareholderDetail());
  }

  // Test update with shareholders already populated.
  @Test
  public void testCorporatioShareholddersSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing shareholders.
    for (int i = 0; i < testData.length; i++) {
      Shareholder next = new Shareholder(
          (Long) testData[i][0] + 2, (Boolean) testData[i][1], (Long) testData[i][2] + 2, (String) testData[i][3] + "A", (String) testData[i][4] + "A",
          (Integer) testData[i][5] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationShareholdersSync.syncShareholders(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify shareholders have been changed to downloaded version.
    for (int i = 0; i < testData.length; i++) {
      Shareholder next = Shareholder.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getShareholderID());
      Assert.assertEquals(testData[i][1], next.isCorporation());
      Assert.assertEquals(testData[i][2], next.getShareholderCorporationID());
      Assert.assertEquals(testData[i][3], next.getShareholderCorporationName());
      Assert.assertEquals(testData[i][4], next.getShareholderName());
      Assert.assertEquals(testData[i][5], next.getShares());
    }

    // Verify previous shareholders were removed from the system.
    Assert.assertEquals(testData.length, Shareholder.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getShareholdersExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getShareholderStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getShareholderDetail());
  }

  // Test skips update when already updated.
  @Test
  public void testCorporationShareholdersSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing medals
    for (int i = 0; i < testData.length; i++) {
      Shareholder next = new Shareholder(
          (Long) testData[i][0] + 2, (Boolean) testData[i][1], (Long) testData[i][2] + 2, (String) testData[i][3] + "A", (String) testData[i][4] + "A",
          (Integer) testData[i][5] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container.
    tracker.setShareholderStatus(SyncState.UPDATED);
    tracker.setShareholderDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setShareholdersExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync.
    SyncStatus syncOutcome = CorporationShareholdersSync.syncShareholders(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify shareholders unchanged.
    for (int i = 0; i < testData.length; i++) {
      Shareholder next = Shareholder.get(syncAccount, testTime, (Long) testData[i][0] + 2);
      Assert.assertEquals((Long) testData[i][0] + 2, next.getShareholderID());
      Assert.assertEquals(testData[i][1], next.isCorporation());
      Assert.assertEquals((Long) testData[i][2] + 2, next.getShareholderCorporationID());
      Assert.assertEquals((String) testData[i][3] + "A", next.getShareholderCorporationName());
      Assert.assertEquals((String) testData[i][4] + "A", next.getShareholderName());
      Assert.assertEquals((Integer) testData[i][5] + 2, next.getShares());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getShareholdersExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getShareholderStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getShareholderDetail());
  }

}
