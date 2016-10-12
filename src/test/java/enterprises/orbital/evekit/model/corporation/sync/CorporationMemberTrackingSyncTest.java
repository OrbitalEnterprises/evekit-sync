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
import enterprises.orbital.evekit.model.corporation.MemberTracking;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberTracking;

public class CorporationMemberTrackingSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  // 0 long characterID;
  // 1 String base;
  // 2 long baseID;
  // 3 long grantableRoles;
  // 4 String location;
  // 5 long locationID;
  // 6 long logoffDateTime;
  // 7 long logonDateTime;
  // 8 String name;
  // 9 long roles;
  // 10 String shipType;
  // 11 int shipTypeID;
  // 12 long startDateTime;
  // 13 String title;
  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 1000 + TestBase.getRandomInt(1000);
    testData = new Object[size][14];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomText(100);
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomText(100);
      testData[i][5] = TestBase.getRandomLong();
      testData[i][6] = TestBase.getRandomLong();
      testData[i][7] = TestBase.getRandomLong();
      testData[i][8] = TestBase.getRandomText(100);
      testData[i][9] = TestBase.getRandomLong();
      testData[i][10] = TestBase.getRandomText(100);
      testData[i][11] = TestBase.getRandomInt();
      testData[i][12] = TestBase.getRandomLong();
      testData[i][13] = TestBase.getRandomText(100);
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
    Collection<IMemberTracking> tracks = new ArrayList<IMemberTracking>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      tracks.add(new IMemberTracking() {

        @Override
        public String getBase() {
          return (String) instanceData[1];
        }

        @Override
        public long getBaseID() {
          return (Long) instanceData[2];
        }

        @Override
        public long getCharacterID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getGrantableRoles() {
          return (Long) instanceData[3];
        }

        @Override
        public String getLocation() {
          return (String) instanceData[4];
        }

        @Override
        public long getLocationID() {
          return (Long) instanceData[5];
        }

        @Override
        public Date getLogoffDateTime() {
          return new Date((Long) instanceData[6]);
        }

        @Override
        public Date getLogonDateTime() {
          return new Date((Long) instanceData[7]);
        }

        @Override
        public String getName() {
          return (String) instanceData[8];
        }

        @Override
        public long getRoles() {
          return (Long) instanceData[9];
        }

        @Override
        public String getShipType() {
          return (String) instanceData[10];
        }

        @Override
        public int getShipTypeID() {
          return (Integer) instanceData[11];
        }

        @Override
        public Date getStartDateTime() {
          return new Date((Long) instanceData[12]);
        }

        @Override
        public String getTitle() {
          return (String) instanceData[13];
        }

      });

    }

    EasyMock.expect(mockServer.requestMemberTracking(true)).andReturn(tracks);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new tracks
  @Test
  public void testCorporationMemberTrackingSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberTrackingSync.syncCorporationMemberTracking(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify tracks were added correctly.
    for (int i = 0; i < testData.length; i++) {
      MemberTracking next = MemberTracking.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getCharacterID());
      Assert.assertEquals(testData[i][1], next.getBase());
      Assert.assertEquals(testData[i][2], next.getBaseID());
      Assert.assertEquals(testData[i][3], next.getGrantableRoles());
      Assert.assertEquals(testData[i][4], next.getLocation());
      Assert.assertEquals(testData[i][5], next.getLocationID());
      Assert.assertEquals(testData[i][6], next.getLogoffDateTime());
      Assert.assertEquals(testData[i][7], next.getLogonDateTime());
      Assert.assertEquals(testData[i][8], next.getName());
      Assert.assertEquals(testData[i][9], next.getRoles());
      Assert.assertEquals(testData[i][10], next.getShipType());
      Assert.assertEquals(testData[i][11], next.getShipTypeID());
      Assert.assertEquals(testData[i][12], next.getStartDateTime());
      Assert.assertEquals(testData[i][13], next.getTitle());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberTrackingExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberTrackingStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberTrackingDetail());
  }

  // Test update with tracks already populated
  @Test
  public void testCorporationMemberTrackingSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing tracks
    for (int i = 0; i < testData.length; i++) {
      MemberTracking next = new MemberTracking(
          (Long) testData[i][0] + 2, (String) testData[i][1] + "A", (Long) testData[i][2] + 2, (Long) testData[i][3] + 2, (String) testData[i][4] + "A",
          (Long) testData[i][5] + 2, (Long) testData[i][6] + 2, (Long) testData[i][7] + 2, (String) testData[i][8] + "A", (Long) testData[i][9] + 2,
          (String) testData[i][10] + "A", (Integer) testData[i][11] + 2, (Long) testData[i][12] + 2, (String) testData[i][13] + "A");
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberTrackingSync.syncCorporationMemberTracking(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify tracks have been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      MemberTracking next = MemberTracking.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getCharacterID());
      Assert.assertEquals(testData[i][1], next.getBase());
      Assert.assertEquals(testData[i][2], next.getBaseID());
      Assert.assertEquals(testData[i][3], next.getGrantableRoles());
      Assert.assertEquals(testData[i][4], next.getLocation());
      Assert.assertEquals(testData[i][5], next.getLocationID());
      Assert.assertEquals(testData[i][6], next.getLogoffDateTime());
      Assert.assertEquals(testData[i][7], next.getLogonDateTime());
      Assert.assertEquals(testData[i][8], next.getName());
      Assert.assertEquals(testData[i][9], next.getRoles());
      Assert.assertEquals(testData[i][10], next.getShipType());
      Assert.assertEquals(testData[i][11], next.getShipTypeID());
      Assert.assertEquals(testData[i][12], next.getStartDateTime());
      Assert.assertEquals(testData[i][13], next.getTitle());
    }

    // Verify previous tracks were removed from the system.
    long continuation = -1;
    int count = 0;
    Collection<MemberTracking> tracks = MemberTracking.getAll(syncAccount, testTime, 100, continuation);
    while (tracks.size() > 0) {
      count += tracks.size();
      for (MemberTracking i : tracks) {
        continuation = Math.max(continuation, i.getCharacterID());
      }
      tracks = MemberTracking.getAll(syncAccount, testTime, 100, continuation);
    }
    Assert.assertEquals(testData.length, count);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberTrackingExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberTrackingStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberTrackingDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationMemberTrackingSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing tracks
    for (int i = 0; i < testData.length; i++) {
      MemberTracking next = new MemberTracking(
          (Long) testData[i][0] + 2, (String) testData[i][1] + "A", (Long) testData[i][2] + 2, (Long) testData[i][3] + 2, (String) testData[i][4] + "A",
          (Long) testData[i][5] + 2, (Long) testData[i][6] + 2, (Long) testData[i][7] + 2, (String) testData[i][8] + "A", (Long) testData[i][9] + 2,
          (String) testData[i][10] + "A", (Integer) testData[i][11] + 2, (Long) testData[i][12] + 2, (String) testData[i][13] + "A");
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setMemberTrackingStatus(SyncState.UPDATED);
    tracker.setMemberTrackingDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setMemberTrackingExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberTrackingSync.syncCorporationMemberTracking(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify tracks unchanged
    for (int i = 0; i < testData.length; i++) {
      MemberTracking next = MemberTracking.get(syncAccount, testTime, (Long) testData[i][0] + 2);
      Assert.assertEquals((Long) testData[i][0] + 2, next.getCharacterID());
      Assert.assertEquals((String) testData[i][1] + "A", next.getBase());
      Assert.assertEquals((Long) testData[i][2] + 2, next.getBaseID());
      Assert.assertEquals((Long) testData[i][3] + 2, next.getGrantableRoles());
      Assert.assertEquals((String) testData[i][4] + "A", next.getLocation());
      Assert.assertEquals((Long) testData[i][5] + 2, next.getLocationID());
      Assert.assertEquals((Long) testData[i][6] + 2, next.getLogoffDateTime());
      Assert.assertEquals((Long) testData[i][7] + 2, next.getLogonDateTime());
      Assert.assertEquals((String) testData[i][8] + "A", next.getName());
      Assert.assertEquals((Long) testData[i][9] + 2, next.getRoles());
      Assert.assertEquals((String) testData[i][10] + "A", next.getShipType());
      Assert.assertEquals((Integer) testData[i][11] + 2, next.getShipTypeID());
      Assert.assertEquals((Long) testData[i][12] + 2, next.getStartDateTime());
      Assert.assertEquals((String) testData[i][13] + "A", next.getTitle());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getMemberTrackingExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberTrackingStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberTrackingDetail());
  }

}
