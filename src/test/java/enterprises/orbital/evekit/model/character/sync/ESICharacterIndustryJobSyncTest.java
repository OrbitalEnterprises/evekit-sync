package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdIndustryJobs200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.IndustryJob;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterIndustryJobSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private IndustryApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] jobTestData;

  static {
    // IndustryJob test data
    // 0 long jobID;
    // 1 long installerID;
    // 2 long facilityID;
    // 3 long stationID;
    // 4 int activityID;
    // 5 long blueprintID;
    // 6 int blueprintTypeID;
    // 7 long blueprintLocationID;
    // 8 long outputLocationID;
    // 9 int runs;
    // 10 BigDecimal cost;
    // 11 int licensedRuns;
    // 12 float probability;
    // 13 int productTypeID;
    // 14 String status; - ENUMERATED
    // 15 int timeInSeconds;
    // 16 long startDate = -1;
    // 17 long endDate = -1;
    // 18 long pauseDate = -1;
    // 19 long completedDate = -1;
    // 20 int completedCharacterID;
    // 21 int successfulRuns;
    int size = 50 + TestBase.getRandomInt(50);
    jobTestData = new Object[size][22];
    int statusLen = GetCharactersCharacterIdIndustryJobs200Ok.StatusEnum.values().length;
    for (int i = 0; i < size; i++) {
      jobTestData[i][0] = TestBase.getUniqueRandomInteger();
      jobTestData[i][1] = TestBase.getRandomInt();
      jobTestData[i][2] = TestBase.getRandomLong();
      jobTestData[i][3] = TestBase.getRandomLong();
      jobTestData[i][4] = TestBase.getRandomInt();
      jobTestData[i][5] = TestBase.getRandomLong();
      jobTestData[i][6] = TestBase.getRandomInt();
      jobTestData[i][7] = TestBase.getRandomLong();
      jobTestData[i][8] = TestBase.getRandomLong();
      jobTestData[i][9] = TestBase.getRandomInt();
      jobTestData[i][10] = BigDecimal.valueOf(TestBase.getRandomDouble(10000))
                                     .setScale(2, RoundingMode.HALF_UP);
      jobTestData[i][11] = TestBase.getRandomInt();
      jobTestData[i][12] = (float) TestBase.getRandomDouble(1);
      jobTestData[i][13] = TestBase.getRandomInt();
      jobTestData[i][14] = GetCharactersCharacterIdIndustryJobs200Ok.StatusEnum.values()[TestBase.getRandomInt(
          statusLen)];
      jobTestData[i][15] = TestBase.getRandomInt();
      jobTestData[i][16] = TestBase.getRandomLong();
      jobTestData[i][17] = TestBase.getRandomLong();
      jobTestData[i][18] = TestBase.getRandomLong();
      jobTestData[i][19] = TestBase.getRandomLong();
      jobTestData[i][20] = TestBase.getRandomInt();
      jobTestData[i][21] = TestBase.getRandomInt();
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_INDUSTRY, 1234L, null);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM IndustryJob ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(IndustryApi.class);
    // Setup mock calls
    List<GetCharactersCharacterIdIndustryJobs200Ok> jobList =
        Arrays.stream(jobTestData)
              .map(x -> {
                GetCharactersCharacterIdIndustryJobs200Ok nextJob = new GetCharactersCharacterIdIndustryJobs200Ok();
                nextJob.setJobId((Integer) x[0]);
                nextJob.setInstallerId((Integer) x[1]);
                nextJob.setFacilityId((Long) x[2]);
                nextJob.setStationId((Long) x[3]);
                nextJob.setActivityId((Integer) x[4]);
                nextJob.setBlueprintId((Long) x[5]);
                nextJob.setBlueprintTypeId((Integer) x[6]);
                nextJob.setBlueprintLocationId((Long) x[7]);
                nextJob.setOutputLocationId((Long) x[8]);
                nextJob.setRuns((Integer) x[9]);
                nextJob.setCost(((BigDecimal) x[10]).doubleValue());
                nextJob.setLicensedRuns((Integer) x[11]);
                nextJob.setProbability((Float) x[12]);
                nextJob.setProductTypeId((Integer) x[13]);
                nextJob.setStatus((GetCharactersCharacterIdIndustryJobs200Ok.StatusEnum) x[14]);
                nextJob.setDuration((Integer) x[15]);
                nextJob.setStartDate(new DateTime(new Date((Long) x[16])));
                nextJob.setEndDate(new DateTime(new Date((Long) x[17])));
                nextJob.setPauseDate(new DateTime(new Date((Long) x[18])));
                nextJob.setCompletedDate(new DateTime(new Date((Long) x[19])));
                nextJob.setCompletedCharacterId((Integer) x[20]);
                nextJob.setSuccessfulRuns((Integer) x[21]);
                return nextJob;
              })
              .collect(Collectors.toList());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdIndustryJobs200Ok>> apir = new ApiResponse<>(200, headers, jobList);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdIndustryJobsWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.eq(true),
        EasyMock.anyString()))
            .andReturn(apir);
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getIndustryApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<IndustryJob> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        IndustryJob.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(jobTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < jobTestData.length; i++) {
      IndustryJob nextEl = storedData.get(i);
      Assert.assertEquals((int) (Integer) jobTestData[i][0], nextEl.getJobID());
      Assert.assertEquals((int) (Integer) jobTestData[i][1], nextEl.getInstallerID());
      Assert.assertEquals((long) (Long) jobTestData[i][2], nextEl.getFacilityID());
      Assert.assertEquals((long) (Long) jobTestData[i][3], nextEl.getStationID());
      Assert.assertEquals((int) (Integer) jobTestData[i][4], nextEl.getActivityID());
      Assert.assertEquals((long) (Long) jobTestData[i][5], nextEl.getBlueprintID());
      Assert.assertEquals((int) (Integer) jobTestData[i][6], nextEl.getBlueprintTypeID());
      Assert.assertEquals((long) (Long) jobTestData[i][7], nextEl.getBlueprintLocationID());
      Assert.assertEquals((long) (Long) jobTestData[i][8], nextEl.getOutputLocationID());
      Assert.assertEquals((int) (Integer) jobTestData[i][9], nextEl.getRuns());
      Assert.assertEquals(jobTestData[i][10], nextEl.getCost());
      Assert.assertEquals((int) (Integer) jobTestData[i][11], nextEl.getLicensedRuns());
      Assert.assertEquals((Float) jobTestData[i][12], nextEl.getProbability(), 0.001);
      Assert.assertEquals((int) (Integer) jobTestData[i][13], nextEl.getProductTypeID());
      Assert.assertEquals(jobTestData[i][14].toString(), nextEl.getStatus());
      Assert.assertEquals((int) (Integer) jobTestData[i][15], nextEl.getTimeInSeconds());
      Assert.assertEquals((long) (Long) jobTestData[i][16], nextEl.getStartDate());
      Assert.assertEquals((long) (Long) jobTestData[i][17], nextEl.getEndDate());
      Assert.assertEquals((long) (Long) jobTestData[i][18], nextEl.getPauseDate());
      Assert.assertEquals((long) (Long) jobTestData[i][19], nextEl.getCompletedDate());
      Assert.assertEquals((int) (Integer) jobTestData[i][20], nextEl.getCompletedCharacterID());
      Assert.assertEquals((int) (Integer) jobTestData[i][21], nextEl.getSuccessfulRuns());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterIndustryJobSync sync = new ESICharacterIndustryJobSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_INDUSTRY);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_INDUSTRY);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    for (Object[] jobData : jobTestData) {
      IndustryJob newEl = new IndustryJob((Integer) jobData[0],
                                          (Integer) jobData[1] + 1,
                                          (Long) jobData[2] + 1,
                                          (Long) jobData[3] + 1,
                                          (Integer) jobData[4] + 1,
                                          (Long) jobData[5] + 1,
                                          (Integer) jobData[6] + 1,
                                          (Long) jobData[7] + 1,
                                          (Long) jobData[8] + 1,
                                          (Integer) jobData[9] + 1,
                                          (BigDecimal) jobData[10],
                                          (Integer) jobData[11] + 1,
                                          (Float) jobData[12] + 1.0F,
                                          (Integer) jobData[13] + 1,
                                          jobData[14].toString(),
                                          (Integer) jobData[15] + 1,
                                          (Long) jobData[16] + 1,
                                          (Long) jobData[17] + 1,
                                          (Long) jobData[18] + 1,
                                          (Long) jobData[19] + 1,
                                          (Integer) jobData[20] + 1,
                                          (Integer) jobData[21] + 1);
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterIndustryJobSync sync = new ESICharacterIndustryJobSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<IndustryJob> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        IndustryJob.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(jobTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < jobTestData.length; i++) {
      IndustryJob nextEl = oldEls.get(i);
      Assert.assertEquals((int) (Integer) jobTestData[i][0], nextEl.getJobID());
      Assert.assertEquals((Integer) jobTestData[i][1] + 1, nextEl.getInstallerID());
      Assert.assertEquals((Long) jobTestData[i][2] + 1, nextEl.getFacilityID());
      Assert.assertEquals((Long) jobTestData[i][3] + 1, nextEl.getStationID());
      Assert.assertEquals((Integer) jobTestData[i][4] + 1, nextEl.getActivityID());
      Assert.assertEquals((Long) jobTestData[i][5] + 1, nextEl.getBlueprintID());
      Assert.assertEquals((Integer) jobTestData[i][6] + 1, nextEl.getBlueprintTypeID());
      Assert.assertEquals((Long) jobTestData[i][7] + 1, nextEl.getBlueprintLocationID());
      Assert.assertEquals((Long) jobTestData[i][8] + 1, nextEl.getOutputLocationID());
      Assert.assertEquals((Integer) jobTestData[i][9] + 1, nextEl.getRuns());
      Assert.assertEquals(jobTestData[i][10], nextEl.getCost());
      Assert.assertEquals((Integer) jobTestData[i][11] + 1, nextEl.getLicensedRuns());
      Assert.assertEquals((Float) jobTestData[i][12] + 1.0F, nextEl.getProbability(), 0.001);
      Assert.assertEquals((Integer) jobTestData[i][13] + 1, nextEl.getProductTypeID());
      Assert.assertEquals(jobTestData[i][14].toString(), nextEl.getStatus());
      Assert.assertEquals((Integer) jobTestData[i][15] + 1, nextEl.getTimeInSeconds());
      Assert.assertEquals((Long) jobTestData[i][16] + 1, nextEl.getStartDate());
      Assert.assertEquals((Long) jobTestData[i][17] + 1, nextEl.getEndDate());
      Assert.assertEquals((Long) jobTestData[i][18] + 1, nextEl.getPauseDate());
      Assert.assertEquals((Long) jobTestData[i][19] + 1, nextEl.getCompletedDate());
      Assert.assertEquals((Integer) jobTestData[i][20] + 1, nextEl.getCompletedCharacterID());
      Assert.assertEquals((Integer) jobTestData[i][21] + 1, nextEl.getSuccessfulRuns());
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_INDUSTRY);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_INDUSTRY);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
