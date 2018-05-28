package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.SkillsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdSkillqueue200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.SkillInQueue;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterSkillInQueueSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private SkillsApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] skillTestData;

  static {
    // Skill test data
    // 0 int endSP;
    // 1 long endTime   = -1;
    // 2 int level;
    // 3 int queuePosition;
    // 4 int startSP;
    // 5 long startTime = -1;
    // 6 int typeID;
    // 7 int trainingStartSP;
    int size = 10 + TestBase.getRandomInt(10);
    skillTestData = new Object[size][8];
    for (int i = 0; i < size; i++) {
      skillTestData[i][0] = TestBase.getRandomInt();
      skillTestData[i][1] = TestBase.getRandomLong();
      skillTestData[i][2] = TestBase.getRandomInt();
      skillTestData[i][3] = TestBase.getUniqueRandomInteger();
      skillTestData[i][4] = TestBase.getRandomInt();
      skillTestData[i][5] = TestBase.getRandomLong();
      skillTestData[i][6] = TestBase.getRandomInt();
      skillTestData[i][7] = TestBase.getRandomInt();
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SKILL_QUEUE, 1234L, null);

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
                                                        .createQuery("DELETE FROM SkillInQueue ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(SkillsApi.class);

    // Set up skill in queue objects
    List<GetCharactersCharacterIdSkillqueue200Ok> skillQueue = Arrays.stream(skillTestData)
                                                                     .
                                                                         map(x -> {
                                                                           GetCharactersCharacterIdSkillqueue200Ok nextSkill = new GetCharactersCharacterIdSkillqueue200Ok();
                                                                           nextSkill.setLevelEndSp((int) x[0]);
                                                                           nextSkill.setFinishDate(
                                                                               new DateTime(new Date((long) x[1])));
                                                                           nextSkill.setFinishedLevel((int) x[2]);
                                                                           nextSkill.setQueuePosition((int) x[3]);
                                                                           nextSkill.setLevelStartSp((int) x[4]);
                                                                           nextSkill.setStartDate(
                                                                               new DateTime(new Date((long) x[5])));
                                                                           nextSkill.setSkillId((int) x[6]);
                                                                           nextSkill.setTrainingStartSp((int) x[7]);
                                                                           return nextSkill;
                                                                         })
                                                                     .collect(Collectors.toList());

    // Setup asset retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdSkillqueue200Ok>> apir = new ApiResponse<>(200, headers, skillQueue);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdSkillqueueWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString()))
            .andReturn(apir);
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getSkillsApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<SkillInQueue> storedData = AbstractESIAccountSync.retrieveAll(testTime,
                                                                       (long contid, AttributeSelector at) ->
                                                                           SkillInQueue.accessQuery(charSyncAccount,
                                                                                                    contid, 1000,
                                                                                                    false, at,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(skillTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < skillTestData.length; i++) {
      SkillInQueue nextEl = storedData.get(i);
      Assert.assertEquals((int) skillTestData[i][0], nextEl.getEndSP());
      Assert.assertEquals((long) skillTestData[i][1], nextEl.getEndTime());
      Assert.assertEquals((int) skillTestData[i][2], nextEl.getLevel());
      Assert.assertEquals((int) skillTestData[i][3], nextEl.getQueuePosition());
      Assert.assertEquals((int) skillTestData[i][4], nextEl.getStartSP());
      Assert.assertEquals((long) skillTestData[i][5], nextEl.getStartTime());
      Assert.assertEquals((int) skillTestData[i][6], nextEl.getTypeID());
      Assert.assertEquals((int) skillTestData[i][7], nextEl.getTrainingStartSP());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterSkillInQueueSync sync = new ESICharacterSkillInQueueSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SKILL_QUEUE);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SKILL_QUEUE);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // This update doesn't delete any data so we only need to test modification.
    //
    for (Object[] sk : skillTestData) {
      SkillInQueue storedSkill = new SkillInQueue((int) sk[0] + 1,
                                                  (long) sk[1] + 1,
                                                  (int) sk[2] + 1,
                                                  (int) sk[3],
                                                  (int) sk[4] + 1,
                                                  (long) sk[5] + 1,
                                                  (int) sk[6] + 1,
                                                  (int) sk[7] + 1);
      storedSkill.setup(charSyncAccount, testTime - 1);
      CachedData.update(storedSkill);
    }

    // Perform the sync
    ESICharacterSkillInQueueSync sync = new ESICharacterSkillInQueueSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<SkillInQueue> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                   (long contid, AttributeSelector at) ->
                                                                       SkillInQueue.accessQuery(charSyncAccount,
                                                                                                contid, 1000, false,
                                                                                                at,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(skillTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < skillTestData.length; i++) {
      SkillInQueue nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals((int) skillTestData[i][0] + 1, nextEl.getEndSP());
      Assert.assertEquals((long) skillTestData[i][1] + 1, nextEl.getEndTime());
      Assert.assertEquals((int) skillTestData[i][2] + 1, nextEl.getLevel());
      Assert.assertEquals((int) skillTestData[i][3], nextEl.getQueuePosition());
      Assert.assertEquals((int) skillTestData[i][4] + 1, nextEl.getStartSP());
      Assert.assertEquals((long) skillTestData[i][5] + 1, nextEl.getStartTime());
      Assert.assertEquals((int) skillTestData[i][6] + 1, nextEl.getTypeID());
      Assert.assertEquals((int) skillTestData[i][7] + 1, nextEl.getTrainingStartSP());
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SKILL_QUEUE);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SKILL_QUEUE);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
