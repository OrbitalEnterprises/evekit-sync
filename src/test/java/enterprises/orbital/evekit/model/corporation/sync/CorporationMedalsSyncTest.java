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
import enterprises.orbital.evekit.model.corporation.CorporationMedal;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.ICorporationMedal;

public class CorporationMedalsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  // 0 int medalID
  // 1 String description
  // 2 String title
  // 3 long created
  // 4 long creatorID
  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 1000 + TestBase.getRandomInt(1000);
    testData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomInteger();
      testData[i][1] = TestBase.getRandomText(2000);
      testData[i][2] = TestBase.getRandomText(100);
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomLong();
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
    Collection<ICorporationMedal> medals = new ArrayList<ICorporationMedal>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      medals.add(new ICorporationMedal() {

        @Override
        public String getDescription() {
          return (String) instanceData[1];
        }

        @Override
        public int getMedalID() {
          return (Integer) instanceData[0];
        }

        @Override
        public String getTitle() {
          return (String) instanceData[2];
        }

        @Override
        public Date getCreated() {
          return new Date((Long) instanceData[3]);
        }

        @Override
        public long getCreatorID() {
          return (Long) instanceData[4];
        }

      });

    }

    EasyMock.expect(mockServer.requestMedals()).andReturn(medals);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new medals
  @Test
  public void testCorporationMedalsSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationMedalsSync.syncCorporationMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify medals were added correctly.
    for (int i = 0; i < testData.length; i++) {
      CorporationMedal next = CorporationMedal.get(syncAccount, testTime, (Integer) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getMedalID());
      Assert.assertEquals(testData[i][1], next.getDescription());
      Assert.assertEquals(testData[i][2], next.getTitle());
      Assert.assertEquals(testData[i][3], next.getCreated());
      Assert.assertEquals(testData[i][4], next.getCreatorID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpMedalsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpMedalsDetail());
  }

  // Test update with medals already populated
  @Test
  public void testCorporatioMedalsSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing tracks
    for (int i = 0; i < testData.length; i++) {
      CorporationMedal next = new CorporationMedal(
          (Integer) testData[i][0] + 2, (String) testData[i][1] + "A", (String) testData[i][2] + "A", (Long) testData[i][3] + 2, (Long) testData[i][4] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationMedalsSync.syncCorporationMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify medals have been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      CorporationMedal next = CorporationMedal.get(syncAccount, testTime, (Integer) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getMedalID());
      Assert.assertEquals(testData[i][1], next.getDescription());
      Assert.assertEquals(testData[i][2], next.getTitle());
      Assert.assertEquals(testData[i][3], next.getCreated());
      Assert.assertEquals(testData[i][4], next.getCreatorID());
    }

    // Verify previous tracks were removed from the system.
    Assert.assertEquals(testData.length, CorporationMedal.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpMedalsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpMedalsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationMedalsSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing medals
    for (int i = 0; i < testData.length; i++) {
      CorporationMedal next = new CorporationMedal(
          (Integer) testData[i][0] + 2, (String) testData[i][1] + "A", (String) testData[i][2] + "A", (Long) testData[i][3] + 2, (Long) testData[i][4] + 2);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setCorpMedalsStatus(SyncState.UPDATED);
    tracker.setCorpMedalsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setMedalsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationMedalsSync.syncCorporationMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify medals unchanged
    for (int i = 0; i < testData.length; i++) {
      CorporationMedal next = CorporationMedal.get(syncAccount, testTime, (Integer) testData[i][0] + 2);
      Assert.assertEquals((Integer) testData[i][0] + 2, next.getMedalID());
      Assert.assertEquals((String) testData[i][1] + "A", next.getDescription());
      Assert.assertEquals((String) testData[i][2] + "A", next.getTitle());
      Assert.assertEquals((Long) testData[i][3] + 2, next.getCreated());
      Assert.assertEquals((Long) testData[i][4] + 2, next.getCreatorID());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpMedalsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpMedalsDetail());
  }

}
