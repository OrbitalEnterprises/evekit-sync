package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.OpportunitiesApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOpportunities200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.Opportunity;
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
public class ESICharacterOpportunitiesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private OpportunitiesApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] oppTestData;

  static {
    // Opportunities test data
    // 0 int taskID;
    // 1 long completedAt;
    int size = 100 + TestBase.getRandomInt(100);
    oppTestData = new Object[size][2];
    for (int i = 0; i < size; i++) {
      oppTestData[i][0] = TestBase.getUniqueRandomInteger();
      oppTestData[i][1] = TestBase.getRandomLong();
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_OPPORTUNITIES, 1234L,
                                                        null);

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
                                                        .createQuery("DELETE FROM Opportunity ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(OpportunitiesApi.class);

    // Set up LPs
    List<GetCharactersCharacterIdOpportunities200Ok> opList = Arrays.stream(oppTestData)
                                                                    .map(x -> {
                                                                      GetCharactersCharacterIdOpportunities200Ok newOpp = new GetCharactersCharacterIdOpportunities200Ok();
                                                                      newOpp.setTaskId((int) x[0]);
                                                                      newOpp.setCompletedAt(
                                                                          new DateTime(new Date((long) x[1])));
                                                                      return newOpp;
                                                                    })
                                                                    .collect(Collectors.toList());

    // Setup retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdOpportunities200Ok>> apir = new ApiResponse<>(200, headers, opList);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdOpportunitiesWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getOpportunitiesApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {

    // Retrieve stored MLs
    List<Opportunity> storedOpps = CachedData.retrieveAll(time,
                                                          (contid, at) -> Opportunity.accessQuery(
                                                              charSyncAccount, contid, 1000, false, at,
                                                              AttributeSelector.any(),
                                                              AttributeSelector.any()));

    // Compare against test data
    Assert.assertEquals(testData.length, storedOpps.size());
    for (int i = 0; i < testData.length; i++) {
      Opportunity nextOpp = storedOpps.get(i);
      Assert.assertEquals((int) testData[i][0], nextOpp.getTaskID());
      Assert.assertEquals((long) testData[i][1], nextOpp.getCompletedAt());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterOpportunitiesSync sync = new ESICharacterOpportunitiesSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, oppTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_OPPORTUNITIES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_OPPORTUNITIES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Create new Opportunities which will be appended to the existing opportunities.
    Object[][] newTestData = new Object[oppTestData.length][2];
    for (int i = 0; i < newTestData.length; i++) {
      newTestData[i][0] = TestBase.getUniqueRandomInteger();
      newTestData[i][1] = TestBase.getRandomLong();

      Opportunity oldOpp = new Opportunity((int) newTestData[i][0],
                                           (long) newTestData[i][1]);
      oldOpp.setup(charSyncAccount, testTime - 1);
      CachedData.update(oldOpp);
    }

    // Perform the sync
    ESICharacterOpportunitiesSync sync = new ESICharacterOpportunitiesSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Old and new data should exist at update time.
    Object[][] allData = new Object[oppTestData.length * 2][2];
    System.arraycopy(newTestData, 0, allData, 0, oppTestData.length);
    System.arraycopy(oppTestData, 0, allData, oppTestData.length, oppTestData.length);

    // Verify updates
    verifyDataUpdate(testTime, allData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_OPPORTUNITIES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_OPPORTUNITIES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
