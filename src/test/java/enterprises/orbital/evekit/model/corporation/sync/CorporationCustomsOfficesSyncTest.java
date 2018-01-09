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
import enterprises.orbital.evekit.model.corporation.CustomsOffice;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.ICustomsOffice;

public class CorporationCustomsOfficesSyncTest extends SyncTestBase {

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
  // 1 int solarSystemID;
  // 2 String solarSystemName;
  // 3 int reinforceHour;
  // 4 boolean allowAlliance;
  // 5 boolean allowStandings;
  // 6 double standingLevel;
  // 7 double taxRateAlliance;
  // 8 double taxRateCorp;
  // 9 double taxRateStandingHigh;
  // 10 double taxRateStandingGood;
  // 11 double taxRateStandingNeutral;
  // 12 double taxRateStandingBad;
  // 13 double taxRateStandingHorrible;

  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 1000 + TestBase.getRandomInt(1000);
    testData = new Object[size][14];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomInt();
      testData[i][2] = TestBase.getRandomText(100);
      testData[i][3] = TestBase.getRandomInt();
      testData[i][4] = TestBase.getRandomBoolean();
      testData[i][5] = TestBase.getRandomBoolean();
      testData[i][6] = TestBase.getRandomDouble(5.0);
      testData[i][7] = TestBase.getRandomDouble(5.0);
      testData[i][8] = TestBase.getRandomDouble(5.0);
      testData[i][9] = TestBase.getRandomDouble(5.0);
      testData[i][10] = TestBase.getRandomDouble(5.0);
      testData[i][11] = TestBase.getRandomDouble(5.0);
      testData[i][12] = TestBase.getRandomDouble(5.0);
      testData[i][13] = TestBase.getRandomDouble(5.0);
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
    Collection<ICustomsOffice> offices = new ArrayList<ICustomsOffice>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      offices.add(new ICustomsOffice() {

        @Override
        public long getItemID() {
          return (Long) instanceData[0];
        }

        @Override
        public int getSolarSystemID() {
          return (Integer) instanceData[1];
        }

        @Override
        public String getSolarSystemName() {
          return (String) instanceData[2];
        }

        @Override
        public int getReinforceHour() {
          return (Integer) instanceData[3];
        }

        @Override
        public boolean isAllowAlliance() {
          return (Boolean) instanceData[4];
        }

        @Override
        public boolean isAllowStandings() {
          return (Boolean) instanceData[5];
        }

        @Override
        public double getStandingLevel() {
          return (Double) instanceData[6];
        }

        @Override
        public double getTaxRateAlliance() {
          return (Double) instanceData[7];
        }

        @Override
        public double getTaxRateCorp() {
          return (Double) instanceData[8];
        }

        @Override
        public double getTaxRateStandingHigh() {
          return (Double) instanceData[9];
        }

        @Override
        public double getTaxRateStandingGood() {
          return (Double) instanceData[10];
        }

        @Override
        public double getTaxRateStandingNeutral() {
          return (Double) instanceData[11];
        }

        @Override
        public double getTaxRateStandingBad() {
          return (Double) instanceData[12];
        }

        @Override
        public double getTaxRateStandingHorrible() {
          return (Double) instanceData[13];
        }

      });

    }

    EasyMock.expect(mockServer.requestCustomsOffices()).andReturn(offices);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  @Test
  public void testCustomsOfficesSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationCustomsOfficesSync.syncCustomsOffices(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify data was added correctly.
    for (int i = 0; i < testData.length; i++) {
      CustomsOffice next = CustomsOffice.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getItemID());
      Assert.assertEquals(testData[i][1], next.getSolarSystemID());
      Assert.assertEquals(testData[i][2], next.getSolarSystemName());
      Assert.assertEquals(testData[i][3], next.getReinforceHour());
      Assert.assertEquals(testData[i][4], next.isAllowAlliance());
      Assert.assertEquals(testData[i][5], next.isAllowStandings());
      Assert.assertEquals(((Double) testData[i][6]).doubleValue(), next.getStandingLevel(), 0.001);
      Assert.assertEquals(((Double) testData[i][7]).doubleValue(), next.getTaxRateAlliance(), 0.001);
      Assert.assertEquals(((Double) testData[i][8]).doubleValue(), next.getTaxRateCorp(), 0.001);
      Assert.assertEquals(((Double) testData[i][9]).doubleValue(), next.getTaxRateStandingHigh(), 0.001);
      Assert.assertEquals(((Double) testData[i][10]).doubleValue(), next.getTaxRateStandingGood(), 0.001);
      Assert.assertEquals(((Double) testData[i][11]).doubleValue(), next.getTaxRateStandingNeutral(), 0.001);
      Assert.assertEquals(((Double) testData[i][12]).doubleValue(), next.getTaxRateStandingBad(), 0.001);
      Assert.assertEquals(((Double) testData[i][13]).doubleValue(), next.getTaxRateStandingHorrible(), 0.001);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getCustomsOfficeExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCustomsOfficeStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCustomsOfficeDetail());
  }

  @Test
  public void testCustomsOfficesSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing tracks
    for (int i = 0; i < testData.length; i++) {
      CustomsOffice next = new CustomsOffice(
          (Long) testData[i][0] + 2, (Integer) testData[i][1] + 2, (String) testData[i][2] + "A", (Integer) testData[i][3] + 2, (Boolean) testData[i][4],
          (Boolean) testData[i][5], (Double) testData[i][6] + 2.0, (Double) testData[i][7] + 2.0, (Double) testData[i][8] + 2.0, (Double) testData[i][9] + 2.0,
          (Double) testData[i][10] + 2.0, (Double) testData[i][11] + 2.0, (Double) testData[i][12] + 2.0, (Double) testData[i][13] + 2.0);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationCustomsOfficesSync.syncCustomsOffices(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify data has been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      CustomsOffice next = CustomsOffice.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getItemID());
      Assert.assertEquals(testData[i][1], next.getSolarSystemID());
      Assert.assertEquals(testData[i][2], next.getSolarSystemName());
      Assert.assertEquals(testData[i][3], next.getReinforceHour());
      Assert.assertEquals(testData[i][4], next.isAllowAlliance());
      Assert.assertEquals(testData[i][5], next.isAllowStandings());
      Assert.assertEquals(((Double) testData[i][6]).doubleValue(), next.getStandingLevel(), 0.001);
      Assert.assertEquals(((Double) testData[i][7]).doubleValue(), next.getTaxRateAlliance(), 0.001);
      Assert.assertEquals(((Double) testData[i][8]).doubleValue(), next.getTaxRateCorp(), 0.001);
      Assert.assertEquals(((Double) testData[i][9]).doubleValue(), next.getTaxRateStandingHigh(), 0.001);
      Assert.assertEquals(((Double) testData[i][10]).doubleValue(), next.getTaxRateStandingGood(), 0.001);
      Assert.assertEquals(((Double) testData[i][11]).doubleValue(), next.getTaxRateStandingNeutral(), 0.001);
      Assert.assertEquals(((Double) testData[i][12]).doubleValue(), next.getTaxRateStandingBad(), 0.001);
      Assert.assertEquals(((Double) testData[i][13]).doubleValue(), next.getTaxRateStandingHorrible(), 0.001);
    }

    // Verify previous tracks were removed from the system.
    Assert.assertEquals(testData.length, CustomsOffice.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getCustomsOfficeExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCustomsOfficeStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCustomsOfficeDetail());
  }

  @Test
  public void testCustomsOfficesSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing data
    for (int i = 0; i < testData.length; i++) {
      CustomsOffice next = new CustomsOffice(
          (Long) testData[i][0] + 2, (Integer) testData[i][1] + 2, (String) testData[i][2] + "A", (Integer) testData[i][3] + 2, (Boolean) testData[i][4],
          (Boolean) testData[i][5], (Double) testData[i][6] + 2.0, (Double) testData[i][7] + 2.0, (Double) testData[i][8] + 2.0, (Double) testData[i][9] + 2.0,
          (Double) testData[i][10] + 2.0, (Double) testData[i][11] + 2.0, (Double) testData[i][12] + 2.0, (Double) testData[i][13] + 2.0);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setCustomsOfficeStatus(SyncState.UPDATED);
    tracker.setCustomsOfficeDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setCustomsOfficeExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationCustomsOfficesSync.syncCustomsOffices(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify data unchanged
    for (int i = 0; i < testData.length; i++) {
      CustomsOffice next = CustomsOffice.get(syncAccount, testTime, (Long) testData[i][0] + 2);
      Assert.assertEquals((Long) testData[i][0] + 2, next.getItemID());
      Assert.assertEquals((Integer) testData[i][1] + 2, next.getSolarSystemID());
      Assert.assertEquals((String) testData[i][2] + "A", next.getSolarSystemName());
      Assert.assertEquals((Integer) testData[i][3] + 2, next.getReinforceHour());
      Assert.assertEquals(testData[i][4], next.isAllowAlliance());
      Assert.assertEquals(testData[i][5], next.isAllowStandings());
      Assert.assertEquals(((Double) testData[i][6]).doubleValue() + 2.0, next.getStandingLevel(), 0.001);
      Assert.assertEquals(((Double) testData[i][7]).doubleValue() + 2.0, next.getTaxRateAlliance(), 0.001);
      Assert.assertEquals(((Double) testData[i][8]).doubleValue() + 2.0, next.getTaxRateCorp(), 0.001);
      Assert.assertEquals(((Double) testData[i][9]).doubleValue() + 2.0, next.getTaxRateStandingHigh(), 0.001);
      Assert.assertEquals(((Double) testData[i][10]).doubleValue() + 2.0, next.getTaxRateStandingGood(), 0.001);
      Assert.assertEquals(((Double) testData[i][11]).doubleValue() + 2.0, next.getTaxRateStandingNeutral(), 0.001);
      Assert.assertEquals(((Double) testData[i][12]).doubleValue() + 2.0, next.getTaxRateStandingBad(), 0.001);
      Assert.assertEquals(((Double) testData[i][13]).doubleValue() + 2.0, next.getTaxRateStandingHorrible(), 0.001);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getCustomsOfficeExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCustomsOfficeStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCustomsOfficeDetail());
  }

}
