package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStandings200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Standing;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICorporationStandingSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] standingTestData;
  private static int[] pages;

  static {
    // Standing test data
    // 0 String standingEntity;
    // 1 int fromID;
    // 2 float standing;
    int size = 100 + TestBase.getRandomInt(100);
    standingTestData = new Object[size][3];
    int standingEntityLen = GetCorporationsCorporationIdStandings200Ok.FromTypeEnum.values().length;
    for (int i = 0; i < size; i++) {
      standingTestData[i][0] = GetCorporationsCorporationIdStandings200Ok.FromTypeEnum.values()[TestBase.getRandomInt(
          standingEntityLen)];
      standingTestData[i][1] = TestBase.getUniqueRandomInteger();
      standingTestData[i][2] = TestBase.getRandomFloat(10);
    }

    int pageCount = 2 + TestBase.getRandomInt(4);
    pages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      pages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STANDINGS, 1234L, null);

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
                                                        .createQuery("DELETE FROM Standing ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);
    // Setup standing retrieval mock calls
    List<GetCorporationsCorporationIdStandings200Ok> standingList =
        Arrays.stream(standingTestData)
              .map(x -> {
                GetCorporationsCorporationIdStandings200Ok nextStanding = new GetCorporationsCorporationIdStandings200Ok();
                nextStanding.setFromType((GetCorporationsCorporationIdStandings200Ok.FromTypeEnum) x[0]);
                nextStanding.setFromId((Integer) x[1]);
                nextStanding.setStanding((Float) x[2]);
                return nextStanding;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < pages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCorporationsCorporationIdStandings200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                             standingList.subList(
                                                                                                 last,
                                                                                                 pages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdStandingsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(apir);
      last = pages[i];
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Standing> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Standing.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(standingTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < standingTestData.length; i++) {
      Standing nextEl = storedData.get(i);
      Assert.assertEquals(standingTestData[i][0].toString(), nextEl.getStandingEntity());
      Assert.assertEquals((int) (Integer) standingTestData[i][1], nextEl.getFromID());
      Assert.assertEquals((Float) standingTestData[i][2], nextEl.getStanding(), 0.001);
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationStandingSync sync = new ESICorporationStandingSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_STANDINGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STANDINGS);
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
    int[] modifiedIDs = new int[standingTestData.length];
    for (int i = 0; i < modifiedIDs.length; i++) {
      if (i % 2 == 0)
        modifiedIDs[i] = TestBase.getUniqueRandomInteger();
      else
        modifiedIDs[i] = (int) (Integer) standingTestData[i][1];
    }
    for (int i = 0; i < standingTestData.length; i++) {
      Standing newEl = new Standing(standingTestData[i][0].toString(),
                                    modifiedIDs[i],
                                    (Float) standingTestData[i][2] + 1.0F);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationStandingSync sync = new ESICorporationStandingSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<Standing> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        Standing.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(standingTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < standingTestData.length; i++) {
      Standing nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals(standingTestData[i][0].toString(), nextEl.getStandingEntity());
      Assert.assertEquals(modifiedIDs[i], nextEl.getFromID());
      Assert.assertEquals((Float) standingTestData[i][2] + 1.0, nextEl.getStanding(), 0.001);
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_STANDINGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STANDINGS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
