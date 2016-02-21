package enterprises.orbital.evekit.model.character.sync;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.IndustryJob;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IIndustryJob;

public class CharacterIndustryJobHistorySyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Capsuleer                   container;
  CapsuleerSyncTracker        tracker;
  SynchronizerUtil            syncUtil;
  ICharacterAPI               mockServer;

  // Random test data
  protected static Object[][] testData;

  static {
    // Set up test data
    int testDataCount = 5 + TestBase.getRandomInt(10);
    testData = new Object[testDataCount][28];
    for (int i = 0; i < testDataCount; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomText(50);
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomLong();
      testData[i][5] = TestBase.getRandomText(50);
      testData[i][6] = TestBase.getRandomLong();
      testData[i][7] = TestBase.getRandomLong();
      testData[i][8] = TestBase.getRandomLong();
      testData[i][9] = TestBase.getRandomLong();
      testData[i][10] = TestBase.getRandomText(50);
      testData[i][11] = TestBase.getRandomLong();
      testData[i][12] = TestBase.getRandomLong();
      testData[i][13] = TestBase.getRandomInt(200);
      testData[i][14] = TestBase.getRandomBigDecimal(100000);
      testData[i][15] = TestBase.getRandomLong();
      testData[i][16] = TestBase.getRandomInt(200);
      testData[i][17] = TestBase.getRandomDouble(1);
      testData[i][18] = TestBase.getRandomLong();
      testData[i][19] = TestBase.getRandomText(50);
      testData[i][20] = (i % 2) == 0 ? Integer.MIN_VALUE : TestBase.getRandomInt(10);
      testData[i][21] = new Long(TestBase.getRandomInt(1000));
      testData[i][22] = TestBase.getRandomLong();
      testData[i][23] = TestBase.getRandomLong();
      testData[i][24] = TestBase.getRandomLong();
      // Even valued jobs will still not have a completed date or completed status
      testData[i][25] = (i % 2) == 0 ? Long.MIN_VALUE : TestBase.getRandomLong();
      testData[i][26] = (i % 2) == 0 ? 0 : TestBase.getRandomLong();
      testData[i][27] = (i % 2) == 0 ? -1 : TestBase.getRandomInt();
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
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IndustryJob makeJob(long time, Object[] instanceData, Long completedCharacterID, Long completedDate, Integer status, Integer successfulRuns)
    throws Exception {
    IndustryJob job = new IndustryJob(
        (Long) instanceData[0], (Long) instanceData[1], (String) instanceData[2], (Long) instanceData[3], (Long) instanceData[4], (String) instanceData[5],
        (Long) instanceData[6], (Long) instanceData[7], (Long) instanceData[8], (Long) instanceData[9], (String) instanceData[10], (Long) instanceData[11],
        (Long) instanceData[12], (Integer) instanceData[13], (BigDecimal) instanceData[14], (Long) instanceData[15], (Integer) instanceData[16],
        (Double) instanceData[17], (Long) instanceData[18], (String) instanceData[19], status != null ? status : (Integer) instanceData[20],
        (Long) instanceData[21], (Long) instanceData[22], (Long) instanceData[23], (Long) instanceData[24],
        completedDate != null ? completedDate : (Long) instanceData[25], completedCharacterID != null ? completedCharacterID : (Long) instanceData[26],
        successfulRuns != null ? successfulRuns : (Integer) instanceData[27]);
    job.setup(syncAccount, time);

    return job;
  }

  public void compareWithTestData(IndustryJob job, Object[] instanceData) {
    Assert.assertEquals(job.getJobID(), Long.parseLong(instanceData[0].toString()));
    Assert.assertEquals(job.getInstallerID(), Long.parseLong(instanceData[1].toString()));
    Assert.assertEquals(job.getInstallerName(), instanceData[2].toString());
    Assert.assertEquals(job.getFacilityID(), Long.parseLong(instanceData[3].toString()));
    Assert.assertEquals(job.getSolarSystemID(), Long.parseLong(instanceData[4].toString()));
    Assert.assertEquals(job.getSolarSystemName(), instanceData[5].toString());
    Assert.assertEquals(job.getStationID(), Long.parseLong(instanceData[6].toString()));
    Assert.assertEquals(job.getActivityID(), Long.parseLong(instanceData[7].toString()));
    Assert.assertEquals(job.getBlueprintID(), Long.parseLong(instanceData[8].toString()));
    Assert.assertEquals(job.getBlueprintTypeID(), Long.parseLong(instanceData[9].toString()));
    Assert.assertEquals(job.getBlueprintTypeName(), instanceData[10].toString());
    Assert.assertEquals(job.getBlueprintLocationID(), Long.parseLong(instanceData[11].toString()));
    Assert.assertEquals(job.getOutputLocationID(), Long.parseLong(instanceData[12].toString()));
    Assert.assertEquals(job.getRuns(), Integer.parseInt(instanceData[13].toString()));
    Assert.assertEquals(job.getCost(), instanceData[14]);
    Assert.assertEquals(job.getTeamID(), Long.parseLong(instanceData[15].toString()));
    Assert.assertEquals(job.getLicensedRuns(), Integer.parseInt(instanceData[16].toString()));
    Assert.assertEquals(job.getProbability(), Double.parseDouble(instanceData[17].toString()), 0.01);
    Assert.assertEquals(job.getProductTypeID(), Long.parseLong(instanceData[18].toString()));
    Assert.assertEquals(job.getProductTypeName(), instanceData[19].toString());
    Assert.assertEquals(job.getStatus(), Integer.parseInt(instanceData[20].toString()));
    Assert.assertEquals(job.getTimeInSeconds(), Long.parseLong(instanceData[21].toString()));
    Assert.assertEquals(job.getStartDate(), Long.parseLong(instanceData[22].toString()));
    Assert.assertEquals(job.getEndDate(), Long.parseLong(instanceData[23].toString()));
    Assert.assertEquals(job.getPauseDate(), Long.parseLong(instanceData[24].toString()));
    Assert.assertEquals(job.getCompletedDate(), Long.parseLong(instanceData[25].toString()));
    Assert.assertEquals(job.getCompletedCharacterID(), Long.parseLong(instanceData[26].toString()));
    Assert.assertEquals(job.getSuccessfulRuns(), Integer.parseInt(instanceData[27].toString()));
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    List<IIndustryJob> jobs = new ArrayList<IIndustryJob>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      IIndustryJob next = new IIndustryJob() {

        @Override
        public long getJobID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getInstallerID() {
          return (Long) instanceData[1];
        }

        @Override
        public String getInstallerName() {
          return (String) instanceData[2];
        }

        @Override
        public long getFacilityID() {
          return (Long) instanceData[3];
        }

        @Override
        public long getSolarSystemID() {
          return (Long) instanceData[4];
        }

        @Override
        public String getSolarSystemName() {
          return (String) instanceData[5];
        }

        @Override
        public long getStationID() {
          return (Long) instanceData[6];
        }

        @Override
        public long getActivityID() {
          return (Long) instanceData[7];
        }

        @Override
        public long getBlueprintID() {
          return (Long) instanceData[8];
        }

        @Override
        public long getBlueprintTypeID() {
          return (Long) instanceData[9];
        }

        @Override
        public String getBlueprintTypeName() {
          return (String) instanceData[10];
        }

        @Override
        public long getBlueprintLocationID() {
          return (Long) instanceData[11];
        }

        @Override
        public long getOutputLocationID() {
          return (Long) instanceData[12];
        }

        @Override
        public int getRuns() {
          return (Integer) instanceData[13];
        }

        @Override
        public BigDecimal getCost() {
          return (BigDecimal) instanceData[14];
        }

        @Override
        public long getTeamID() {
          return (Long) instanceData[15];
        }

        @Override
        public int getLicensedRuns() {
          return (Integer) instanceData[16];
        }

        @Override
        public double getProbability() {
          return (Double) instanceData[17];
        }

        @Override
        public long getProductTypeID() {
          return (Long) instanceData[18];
        }

        @Override
        public String getProductTypeName() {
          return (String) instanceData[19];
        }

        @Override
        public int getStatus() {
          return (Integer) instanceData[20];
        }

        @Override
        public long getTimeInSeconds() {
          return (Long) instanceData[21];
        }

        @Override
        public Date getStartDate() {
          return new Date((Long) instanceData[22]);
        }

        @Override
        public Date getEndDate() {
          return new Date((Long) instanceData[23]);
        }

        @Override
        public Date getPauseDate() {
          return new Date((Long) instanceData[24]);
        }

        @Override
        public Date getCompletedDate() {
          return new Date((Long) instanceData[25]);
        }

        @Override
        public long getCompletedCharacterID() {
          return (Long) instanceData[26];
        }

        @Override
        public int getSuccessfulRuns() {
          return (Integer) instanceData[27];
        }

      };
      jobs.add(next);
    }

    EasyMock.expect(mockServer.requestIndustryJobsHistory()).andReturn(jobs);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // None of these jobs should be added since history only updates existing jobs.
  @Test
  public void testCharacterIndustryJobHistorySyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterIndustryJobsHistorySync.syncCharacterIndustryJobsHistory(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify no jobs were added.
    for (int i = 0; i < testData.length; i++) {
      long jobID = (Long) testData[i][0];
      IndustryJob next = IndustryJob.get(syncAccount, testTime, jobID);
      Assert.assertNull(next);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getIndustryJobsHistoryExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getIndustryJobsHistoryStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getIndustryJobsHistoryDetail());
  }

  // Populate all jobs without completed dates or IDs. Then do a sync with data containing some completed data and IDs and verify these were updated properly.
  @Test
  public void testCharacterIndustryJobHistorySyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate jobs
    for (int i = 0; i < testData.length; i++) {
      IndustryJob next = makeJob(testTime, testData[i], new Long(0), Long.MIN_VALUE, Integer.MIN_VALUE, new Integer(-1));
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterIndustryJobsHistorySync.syncCharacterIndustryJobsHistory(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify completed jobs have been marked completed and all other jobs are unaffected.
    // By construction, every odd numbered job is marked completed.
    for (int i = 0; i < testData.length; i++) {
      long jobID = (Long) testData[i][0];
      IndustryJob next = IndustryJob.get(syncAccount, testTime, jobID);
      compareWithTestData(next, testData[i]);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getIndustryJobsHistoryExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getIndustryJobsHistoryStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getIndustryJobsHistoryDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterIndustryJobHistorySyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing jobs
    for (int i = 0; i < testData.length; i++) {
      CachedData.updateData(makeJob(testTime, testData[i], null, null, null, null));
    }

    // Set the tracker as already updated and populate the container
    tracker.setIndustryJobsHistoryStatus(SyncState.UPDATED);
    tracker.setIndustryJobsHistoryDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setIndustryJobsHistoryExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterIndustryJobsHistorySync.syncCharacterIndustryJobsHistory(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify jobs unchanged
    for (int i = 0; i < testData.length; i++) {
      long jobID = (Long) testData[i][0];
      IndustryJob next = IndustryJob.get(syncAccount, testTime, jobID);
      compareWithTestData(next, testData[i]);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getIndustryJobsHistoryExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getIndustryJobsHistoryStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getIndustryJobsHistoryDetail());
  }

}
