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
import enterprises.orbital.evekit.model.corporation.Facility;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IFacility;

public class CorporationFacilitiesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  // 0 long facilityID;
  // 1 int typeID;
  // 2 String typeName;
  // 3 int solarSystemID;
  // 4 String solarSystemName;
  // 5 int regionID;
  // 6 String regionName;
  // 7 int starbaseModifier;
  // 8 double tax;
  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 1000 + TestBase.getRandomInt(1000);
    testData = new Object[size][9];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomInt();
      testData[i][2] = TestBase.getRandomText(100);
      testData[i][3] = TestBase.getRandomInt();
      testData[i][4] = TestBase.getRandomText(100);
      testData[i][5] = TestBase.getRandomInt();
      testData[i][6] = TestBase.getRandomText(100);
      testData[i][7] = TestBase.getRandomInt();
      testData[i][8] = TestBase.getRandomDouble(1.0);
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
    Collection<IFacility> facilities = new ArrayList<IFacility>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      facilities.add(new IFacility() {

        @Override
        public long getFacilityID() {
          return (Long) instanceData[0];
        }

        @Override
        public int getTypeID() {
          return (Integer) instanceData[1];
        }

        @Override
        public String getTypeName() {
          return (String) instanceData[2];
        }

        @Override
        public int getSolarSystemID() {
          return (Integer) instanceData[3];
        }

        @Override
        public String getSolarSystemName() {
          return (String) instanceData[4];
        }

        @Override
        public int getRegionID() {
          return (Integer) instanceData[5];
        }

        @Override
        public String getRegionName() {
          return (String) instanceData[6];
        }

        @Override
        public int getStarbaseModifier() {
          return (Integer) instanceData[7];
        }

        @Override
        public double getTax() {
          return (Double) instanceData[8];
        }
      });

    }

    EasyMock.expect(mockServer.requestFacilities()).andReturn(facilities);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  @Test
  public void testFacilitiesSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationFacilitiesSync.syncFacilities(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify data was added correctly.
    for (int i = 0; i < testData.length; i++) {
      Facility next = Facility.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getFacilityID());
      Assert.assertEquals(testData[i][1], next.getTypeID());
      Assert.assertEquals(testData[i][2], next.getTypeName());
      Assert.assertEquals(testData[i][3], next.getSolarSystemID());
      Assert.assertEquals(testData[i][4], next.getSolarSystemName());
      Assert.assertEquals(testData[i][5], next.getRegionID());
      Assert.assertEquals(testData[i][6], next.getRegionName());
      Assert.assertEquals(testData[i][7], next.getStarbaseModifier());
      Assert.assertEquals(((Double) testData[i][8]).doubleValue(), next.getTax(), 0.001);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getFacilitiesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacilitiesStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacilitiesDetail());
  }

  @Test
  public void testFacilitiesSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing tracks
    for (int i = 0; i < testData.length; i++) {
      Facility next = new Facility(
          (Long) testData[i][0] + 2, (Integer) testData[i][1] + 2, (String) testData[i][2] + "A", (Integer) testData[i][3] + 2, (String) testData[i][4] + "A",
          (Integer) testData[i][5] + 2, (String) testData[i][6] + "A", (Integer) testData[i][7] + 2, (Double) testData[i][8] + 2.0);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationFacilitiesSync.syncFacilities(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify data has been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      Facility next = Facility.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getFacilityID());
      Assert.assertEquals(testData[i][1], next.getTypeID());
      Assert.assertEquals(testData[i][2], next.getTypeName());
      Assert.assertEquals(testData[i][3], next.getSolarSystemID());
      Assert.assertEquals(testData[i][4], next.getSolarSystemName());
      Assert.assertEquals(testData[i][5], next.getRegionID());
      Assert.assertEquals(testData[i][6], next.getRegionName());
      Assert.assertEquals(testData[i][7], next.getStarbaseModifier());
      Assert.assertEquals(((Double) testData[i][8]).doubleValue(), next.getTax(), 0.001);
    }

    // Verify previous tracks were removed from the system.
    Assert.assertEquals(testData.length, Facility.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getFacilitiesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacilitiesStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacilitiesDetail());
  }

  @Test
  public void testFacilitiesSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing data
    for (int i = 0; i < testData.length; i++) {
      Facility next = new Facility(
          (Long) testData[i][0] + 2, (Integer) testData[i][1] + 2, (String) testData[i][2] + "A", (Integer) testData[i][3] + 2, (String) testData[i][4] + "A",
          (Integer) testData[i][5] + 2, (String) testData[i][6] + "A", (Integer) testData[i][7] + 2, (Double) testData[i][8] + 2.0);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setFacilitiesStatus(SyncState.UPDATED);
    tracker.setFacilitiesDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setFacilitiesExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationFacilitiesSync.syncFacilities(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify data unchanged
    for (int i = 0; i < testData.length; i++) {
      Facility next = Facility.get(syncAccount, testTime, (Long) testData[i][0] + 2);
      Assert.assertEquals((Long) testData[i][0] + 2, next.getFacilityID());
      Assert.assertEquals((Integer) testData[i][1] + 2, next.getTypeID());
      Assert.assertEquals((String) testData[i][2] + "A", next.getTypeName());
      Assert.assertEquals((Integer) testData[i][3] + 2, next.getSolarSystemID());
      Assert.assertEquals((String) testData[i][4] + "A", next.getSolarSystemName());
      Assert.assertEquals((Integer) testData[i][5] + 2, next.getRegionID());
      Assert.assertEquals((String) testData[i][6] + "A", next.getRegionName());
      Assert.assertEquals((Integer) testData[i][7] + 2, next.getStarbaseModifier());
      Assert.assertEquals(((Double) testData[i][8]).doubleValue() + 2.0, next.getTax(), 0.001);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getFacilitiesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacilitiesStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacilitiesDetail());
  }

}
