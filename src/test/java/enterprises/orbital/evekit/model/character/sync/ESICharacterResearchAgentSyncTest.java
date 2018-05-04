package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdAgentsResearch200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.ResearchAgent;
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

public class ESICharacterResearchAgentSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] agentTestData;

  static {
    // Agent test data
    // 0 int agentID
    // 1 float pointsPerDay
    // 2 float remainderPoints
    // 3 long researchStartDate
    // 4 int skillTypeID
    int size = 20 + TestBase.getRandomInt(20);
    agentTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      agentTestData[i][0] = TestBase.getUniqueRandomInteger();
      agentTestData[i][1] = TestBase.getRandomFloat(1000);
      agentTestData[i][2] = TestBase.getRandomFloat(1000);
      agentTestData[i][3] = Math.abs(TestBase.getRandomLong());
      agentTestData[i][4] = TestBase.getRandomInt();

    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_AGENTS, 1234L, null);

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
                                                        .createQuery("DELETE FROM ResearchAgent ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    List<GetCharactersCharacterIdAgentsResearch200Ok> agentList =
        Arrays.stream(agentTestData)
              .map(x -> {
                GetCharactersCharacterIdAgentsResearch200Ok nextAgent = new GetCharactersCharacterIdAgentsResearch200Ok();
                nextAgent.setAgentId((Integer) x[0]);
                nextAgent.setPointsPerDay((Float) x[1]);
                nextAgent.setRemainderPoints((Float) x[2]);
                nextAgent.setStartedAt(new DateTime(new Date((Long) x[3])));
                nextAgent.setSkillTypeId((Integer) x[4]);
                return nextAgent;
              })
              .collect(Collectors.toList());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdAgentsResearch200Ok>> apir = new ApiResponse<>(200, headers, agentList);
    mockEndpoint = EasyMock.createMock(CharacterApi.class);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdAgentsResearchWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<ResearchAgent> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        ResearchAgent.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(agentTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < agentTestData.length; i++) {
      ResearchAgent nextEl = storedData.get(i);
      Assert.assertEquals((int) (Integer) agentTestData[i][0], nextEl.getAgentID());
      Assert.assertEquals((Float) agentTestData[i][1], nextEl.getPointsPerDay(), 0.001);
      Assert.assertEquals((Float) agentTestData[i][2], nextEl.getRemainderPoints(), 0.001);
      Assert.assertEquals((long) (Long) agentTestData[i][3], nextEl.getResearchStartDate());
      Assert.assertEquals((int) (Integer) agentTestData[i][4], nextEl.getSkillTypeID());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterResearchAgentSync sync = new ESICharacterResearchAgentSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_AGENTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_AGENTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // Half the entries we insert are purposely not present in the server data so we can test deletion.
    // Compute the modified IDs
    int[] modifiedIDs = new int[agentTestData.length];
    for (int i = 0; i < modifiedIDs.length; i++) {
      if (i % 2 == 0)
        modifiedIDs[i] = TestBase.getUniqueRandomInteger();
      else
        modifiedIDs[i] = (int) (Integer) agentTestData[i][0];
    }
    for (int i = 0; i < agentTestData.length; i++) {
      ResearchAgent newEl = new ResearchAgent(modifiedIDs[i],
                                              (Float) agentTestData[i][1] + 1.0F,
                                              (Float) agentTestData[i][2] + 1.0F,
                                              (Long) agentTestData[i][3] + 1,
                                              (Integer) agentTestData[i][4] + 1);
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterResearchAgentSync sync = new ESICharacterResearchAgentSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<ResearchAgent> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        ResearchAgent.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(agentTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < agentTestData.length; i++) {
      ResearchAgent nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals(modifiedIDs[i], nextEl.getAgentID());
      Assert.assertEquals((Float) agentTestData[i][1] + 1.0D, nextEl.getPointsPerDay(), 0.001);
      Assert.assertEquals((Float) agentTestData[i][2] + 1.0D, nextEl.getRemainderPoints(), 0.001);
      Assert.assertEquals((Long) agentTestData[i][3] + 1, nextEl.getResearchStartDate());
    }

    // Verify updates which will also verify that all old alliances were properly end of lifed
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_AGENTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_AGENTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
