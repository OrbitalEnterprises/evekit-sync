package enterprises.orbital.evekit.model.corporation.sync;

import java.text.DateFormat;
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
import enterprises.orbital.evekit.model.common.FacWarStats;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IFacWarStats;

public class CorporationFacWarStatsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  // currentRank
  // enlisted (date)
  // factionID
  // factionName
  // highestRank
  // killsLastWeek
  // killsTotal
  // killsYesterday
  // pilots
  // victoryPointsLastWeek
  // victoryPointsTotal
  // victoryPointsYesterday
  Object[][]             testData = new Object[][] {
                                      {
                                          TestBase.getRandomInt(), TestBase.getRandomLong(), TestBase.getRandomInt(), TestBase.getRandomText(50),
                                          TestBase.getRandomInt(), TestBase.getRandomInt(), TestBase.getRandomInt(), TestBase.getRandomInt(),
                                          TestBase.getRandomInt(), TestBase.getRandomInt(), TestBase.getRandomInt(), TestBase.getRandomInt()
                                        }
                                    };

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void compareWithTestData(FacWarStats stats) {
    Assert.assertEquals(stats.getCurrentRank(), Integer.valueOf(testData[0][0].toString()).intValue());
    Assert.assertEquals(stats.getEnlisted(), Long.valueOf(testData[0][1].toString()).longValue());
    Assert.assertEquals(stats.getFactionID(), Integer.valueOf(testData[0][2].toString()).intValue());
    Assert.assertEquals(stats.getFactionName(), testData[0][3]);
    Assert.assertEquals(stats.getHighestRank(), Integer.valueOf(testData[0][4].toString()).intValue());
    Assert.assertEquals(stats.getKillsLastWeek(), Integer.valueOf(testData[0][5].toString()).intValue());
    Assert.assertEquals(stats.getKillsTotal(), Integer.valueOf(testData[0][6].toString()).intValue());
    Assert.assertEquals(stats.getKillsYesterday(), Integer.valueOf(testData[0][7].toString()).intValue());
    Assert.assertEquals(stats.getPilots(), Integer.valueOf(testData[0][8].toString()).intValue());
    Assert.assertEquals(stats.getVictoryPointsLastWeek(), Integer.valueOf(testData[0][9].toString()).intValue());
    Assert.assertEquals(stats.getVictoryPointsTotal(), Integer.valueOf(testData[0][10].toString()).intValue());
    Assert.assertEquals(stats.getVictoryPointsYesterday(), Integer.valueOf(testData[0][11].toString()).intValue());
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    final Object[] instanceData = testData[0];
    IFacWarStats stats = new IFacWarStats() {

      @Override
      public int getCurrentRank() {
        return (Integer) instanceData[0];
      }

      @Override
      public Date getEnlisted() {
        return new Date((Long) instanceData[1]);
      }

      @Override
      public int getFactionID() {
        return (Integer) instanceData[2];
      }

      @Override
      public String getFactionName() {
        return (String) instanceData[3];
      }

      @Override
      public int getHighestRank() {
        return (Integer) instanceData[4];
      }

      @Override
      public int getKillsLastWeek() {
        return (Integer) instanceData[5];
      }

      @Override
      public int getKillsTotal() {
        return (Integer) instanceData[6];
      }

      @Override
      public int getKillsYesterday() {
        return (Integer) instanceData[7];
      }

      @Override
      public int getPilots() {
        return (Integer) instanceData[8];
      }

      @Override
      public int getVictoryPointsLastWeek() {
        return (Integer) instanceData[9];
      }

      @Override
      public int getVictoryPointsTotal() {
        return (Integer) instanceData[10];
      }

      @Override
      public int getVictoryPointsYesterday() {
        return (Integer) instanceData[11];
      }

    };

    EasyMock.expect(mockServer.requestFacWarStats()).andReturn(stats);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with new fac war stats
  @Test
  public void testCorporationFacWarStatsSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationFacWarStatsSync.syncFacWarStats(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify stats were added correctly.
    compareWithTestData(FacWarStats.get(syncAccount, testTime));

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getFacWarStatsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacWarStatsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacWarStatsDetail());
  }

  // Test update with stats already populated
  @Test
  public void testCorporationFacWarStatsSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate stats
    FacWarStats stats = new FacWarStats(
        (Integer) testData[0][0], (Long) testData[0][1], (Integer) testData[0][2], (String) testData[0][3], (Integer) testData[0][4], (Integer) testData[0][5],
        (Integer) testData[0][6], (Integer) testData[0][7], (Integer) testData[0][8], (Integer) testData[0][9], (Integer) testData[0][10],
        (Integer) testData[0][11]);
    stats.setup(syncAccount, testTime);
    stats = CachedData.update(stats);

    // Perform the sync
    SyncStatus syncOutcome = CorporationFacWarStatsSync.syncFacWarStats(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify stats are unchanged
    compareWithTestData(FacWarStats.get(syncAccount, testTime));

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getFacWarStatsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacWarStatsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacWarStatsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationFacWarStatsSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing stats
    FacWarStats stats = new FacWarStats(
        (Integer) testData[0][0], (Long) testData[0][1], (Integer) testData[0][2], (String) testData[0][3], (Integer) testData[0][4], (Integer) testData[0][5],
        (Integer) testData[0][6], (Integer) testData[0][7], (Integer) testData[0][8], (Integer) testData[0][9], (Integer) testData[0][10],
        (Integer) testData[0][11]);
    stats.setup(syncAccount, testTime);
    stats = CachedData.update(stats);

    // Set the tracker as already updated and populate the container
    tracker.setFacWarStatsStatus(SyncState.UPDATED);
    tracker.setFacWarStatsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setFacWarStatsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationFacWarStatsSync.syncFacWarStats(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify stats unchanged
    compareWithTestData(FacWarStats.get(syncAccount, testTime));

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getFacWarStatsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacWarStatsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getFacWarStatsDetail());
  }

}
