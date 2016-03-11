package enterprises.orbital.evekit.model.corporation.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.hsqldb.rights.User;
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
import enterprises.orbital.evekit.model.common.Blueprint;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IBlueprint;

public class CorporationBlueprintsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  User                   testUser;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  static Object[][]      testData;

  static {
    // Generate test data
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][9];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomInt();
      testData[i][3] = TestBase.getRandomText(50);
      testData[i][4] = TestBase.getRandomInt(10);
      testData[i][5] = TestBase.getRandomInt(5000);
      testData[i][6] = TestBase.getRandomInt(20);
      testData[i][7] = TestBase.getRandomInt(20);
      testData[i][8] = TestBase.getRandomInt(100);
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
    Collection<IBlueprint> blueprints = new ArrayList<IBlueprint>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      blueprints.add(new IBlueprint() {

        @Override
        public long getItemID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getLocationID() {
          return (Long) instanceData[1];
        }

        @Override
        public int getTypeID() {
          return (Integer) instanceData[2];
        }

        @Override
        public String getTypeName() {
          return (String) instanceData[3];
        }

        @Override
        public int getFlagID() {
          return (Integer) instanceData[4];
        }

        @Override
        public int getQuantity() {
          return (Integer) instanceData[5];
        }

        @Override
        public int getTimeEfficiency() {
          return (Integer) instanceData[6];
        }

        @Override
        public int getMaterialEfficiency() {
          return (Integer) instanceData[7];
        }

        @Override
        public int getRuns() {
          return (Integer) instanceData[8];
        }
      });
    }

    EasyMock.expect(mockServer.requestBlueprints()).andReturn(blueprints);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  @Test
  public void testBlueprintsSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationBlueprintsSync.syncCorporationBlueprints(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify new data populated.
    for (int i = 0; i < testData.length; i++) {
      long itemID = (Long) testData[i][0];
      Blueprint next = Blueprint.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][1], next.getLocationID());
      Assert.assertEquals(testData[i][2], next.getTypeID());
      Assert.assertEquals(testData[i][3], next.getTypeName());
      Assert.assertEquals(testData[i][4], next.getFlagID());
      Assert.assertEquals(testData[i][5], next.getQuantity());
      Assert.assertEquals(testData[i][6], next.getTimeEfficiency());
      Assert.assertEquals(testData[i][7], next.getMaterialEfficiency());
      Assert.assertEquals(testData[i][8], next.getRuns());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getBlueprintsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsDetail());
  }

  @Test
  public void testBlueprintsSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contacts
    for (int i = 0; i < testData.length; i++) {
      long itemID = (Long) testData[i][0];
      Blueprint next = new Blueprint(
          itemID, (Long) testData[i][1] + 7, (Integer) testData[i][2] + 7, (String) testData[i][3] + "foo", (Integer) testData[i][4] + 7,
          (Integer) testData[i][5] + 7, (Integer) testData[i][6] + 7, (Integer) testData[i][7] + 7, (Integer) testData[i][8] + 7);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationBlueprintsSync.syncCorporationBlueprints(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      long itemID = (Long) testData[i][0];
      Blueprint next = Blueprint.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][1], next.getLocationID());
      Assert.assertEquals(testData[i][2], next.getTypeID());
      Assert.assertEquals(testData[i][3], next.getTypeName());
      Assert.assertEquals(testData[i][4], next.getFlagID());
      Assert.assertEquals(testData[i][5], next.getQuantity());
      Assert.assertEquals(testData[i][6], next.getTimeEfficiency());
      Assert.assertEquals(testData[i][7], next.getMaterialEfficiency());
      Assert.assertEquals(testData[i][8], next.getRuns());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getBlueprintsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testBlueprintsSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contacts
    for (int i = 0; i < testData.length; i++) {
      long itemID = (Long) testData[i][0];
      Blueprint next = new Blueprint(
          itemID, (Long) testData[i][1] + 7, (Integer) testData[i][2] + 7, (String) testData[i][3] + "foo", (Integer) testData[i][4] + 7,
          (Integer) testData[i][5] + 7, (Integer) testData[i][6] + 7, (Integer) testData[i][7] + 7, (Integer) testData[i][8] + 7);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setBlueprintsStatus(SyncState.UPDATED);
    tracker.setBlueprintsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setBlueprintsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationBlueprintsSync.syncCorporationBlueprints(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify data unchanged
    for (int i = 0; i < testData.length; i++) {
      long itemID = (Long) testData[i][0];
      Blueprint next = Blueprint.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals((Long) testData[i][1] + 7, next.getLocationID());
      Assert.assertEquals((Integer) testData[i][2] + 7, next.getTypeID());
      Assert.assertEquals((String) testData[i][3] + "foo", next.getTypeName());
      Assert.assertEquals((Integer) testData[i][4] + 7, next.getFlagID());
      Assert.assertEquals((Integer) testData[i][5] + 7, next.getQuantity());
      Assert.assertEquals((Integer) testData[i][6] + 7, next.getTimeEfficiency());
      Assert.assertEquals((Integer) testData[i][7] + 7, next.getMaterialEfficiency());
      Assert.assertEquals((Integer) testData[i][8] + 7, next.getRuns());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getBlueprintsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsDetail());
  }

  // Test update with blueprints which should be deleted
  @Test
  public void testBlueprintsSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing blueprints which should be deleted
    List<Blueprint> toDelete = new ArrayList<Blueprint>();
    for (int i = 0; i < 5; i++) {
      long itemID = TestBase.getUniqueRandomLong();
      Blueprint next = new Blueprint(
          itemID, TestBase.getRandomLong(), TestBase.getRandomInt(), TestBase.getRandomText(50), TestBase.getRandomInt(), TestBase.getRandomInt(),
          TestBase.getRandomInt(), TestBase.getRandomInt(), TestBase.getRandomInt());
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
      toDelete.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationBlueprintsSync.syncCorporationBlueprints(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted blueprints no longer exist
    Assert.assertEquals(testData.length, Blueprint.getAllBlueprints(syncAccount, testTime, -1, 0).size());
    for (int i = 0; i < testData.length; i++) {
      long itemID = (Long) testData[i][0];
      Blueprint next = Blueprint.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][1], next.getLocationID());
      Assert.assertEquals(testData[i][2], next.getTypeID());
      Assert.assertEquals(testData[i][3], next.getTypeName());
      Assert.assertEquals(testData[i][4], next.getFlagID());
      Assert.assertEquals(testData[i][5], next.getQuantity());
      Assert.assertEquals(testData[i][6], next.getTimeEfficiency());
      Assert.assertEquals(testData[i][7], next.getMaterialEfficiency());
      Assert.assertEquals(testData[i][8], next.getRuns());
    }
    for (Blueprint i : toDelete) {
      Assert.assertNull(Blueprint.get(syncAccount, testTime, i.getItemID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getBlueprintsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBlueprintsDetail());
  }
}