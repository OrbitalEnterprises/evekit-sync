package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMedals200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMedalsIssued200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CorporationMedal;
import enterprises.orbital.evekit.model.corporation.CorporationMemberMedal;
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

import static java.lang.String.valueOf;

@SuppressWarnings("Duplicates")
public class ESICorporationMedalsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] medalsTestData;
  private static int[] medalsPages;
  private static Object[][] issuedTestData;
  private static int[] issuedPages;

  static {
    // Medals test data
    // 0 int medalID
    // 1 String description
    // 2 String title
    // 3 long created
    // 4 int creatorID
    int size = 100 + TestBase.getRandomInt(100);
    medalsTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      medalsTestData[i][0] = TestBase.getUniqueRandomInteger();
      medalsTestData[i][1] = TestBase.getRandomText(1000);
      medalsTestData[i][2] = TestBase.getRandomText(100);
      medalsTestData[i][3] = TestBase.getRandomLong();
      medalsTestData[i][4] = TestBase.getRandomInt();
    }

    int pageCount = 2 + TestBase.getRandomInt(4);
    medalsPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      medalsPages[i] = size - (pageCount - 1 - i) * (size / pageCount);

    // Member medals test data
    // 0 int medalID
    // 1 int characterID
    // 2 long issued
    // 3 int issuerID
    // 4 String reason
    // 5 String status
    size = 100 + TestBase.getRandomInt(100);
    int statusLength = GetCorporationsCorporationIdMedalsIssued200Ok.StatusEnum.values().length;
    issuedTestData = new Object[size][6];
    for (int i = 0; i < size; i++) {
      issuedTestData[i][0] = TestBase.getUniqueRandomInteger();
      issuedTestData[i][1] = TestBase.getRandomInt();
      issuedTestData[i][2] = TestBase.getRandomLong();
      issuedTestData[i][3] = TestBase.getRandomInt();
      issuedTestData[i][4] = TestBase.getRandomText(50);
      issuedTestData[i][5] = GetCorporationsCorporationIdMedalsIssued200Ok.StatusEnum.values()[TestBase.getRandomInt(
          statusLength)];
    }

    pageCount = 2 + TestBase.getRandomInt(4);
    issuedPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      issuedPages[i] = size - (pageCount - 1 - i) * (size / pageCount);

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MEDALS, 1234L);

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
                                                        .createQuery("DELETE FROM CorporationMedal ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CorporationMemberMedal ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);
    // Setup retrieval mock calls
    List<GetCorporationsCorporationIdMedals200Ok> medalsList =
        Arrays.stream(medalsTestData)
              .map(x -> {
                GetCorporationsCorporationIdMedals200Ok nextMedal = new GetCorporationsCorporationIdMedals200Ok();
                nextMedal.setMedalId((int) x[0]);
                nextMedal.setDescription((String) x[1]);
                nextMedal.setTitle((String) x[2]);
                nextMedal.setCreatedAt(new DateTime(new Date((long) x[3])));
                nextMedal.setCreatorId((int) x[4]);
                return nextMedal;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < medalsPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        valueOf(medalsPages.length));
      ApiResponse<List<GetCorporationsCorporationIdMedals200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                          medalsList.subList(
                                                                                              last,
                                                                                              medalsPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdMedalsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = medalsPages[i];
    }

    // Setup retrieval mock calls
    List<GetCorporationsCorporationIdMedalsIssued200Ok> issuedList =
        Arrays.stream(issuedTestData)
              .map(x -> {
                GetCorporationsCorporationIdMedalsIssued200Ok nextIssued = new GetCorporationsCorporationIdMedalsIssued200Ok();
                nextIssued.setMedalId((int) x[0]);
                nextIssued.setCharacterId((int) x[1]);
                nextIssued.setIssuedAt(new DateTime(new Date((long) x[2])));
                nextIssued.setIssuerId((int) x[3]);
                nextIssued.setReason((String) x[4]);
                nextIssued.setStatus((GetCorporationsCorporationIdMedalsIssued200Ok.StatusEnum) x[5]);
                return nextIssued;
              })
              .collect(Collectors.toList());
    last = 0;
    for (int i = 0; i < issuedPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        valueOf(issuedPages.length));
      ApiResponse<List<GetCorporationsCorporationIdMedalsIssued200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                                issuedList.subList(
                                                                                                    last,
                                                                                                    issuedPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdMedalsIssuedWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = issuedPages[i];
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testMedalData, Object[][] testIssuedData) throws Exception {
    {
      // Retrieve all stored data
      List<CorporationMedal> storedData = AbstractESIAccountSync.retrieveAll(time,
                                                                             (long contid, AttributeSelector at) ->
                                                                                 CorporationMedal.accessQuery(
                                                                                     corpSyncAccount, contid, 1000,
                                                                                     false, at,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR));

      // Check data matches test data
      Assert.assertEquals(testMedalData.length, storedData.size());

      // Check stored data
      for (int i = 0; i < testMedalData.length; i++) {
        CorporationMedal nextEl = storedData.get(i);
        Assert.assertEquals((int) testMedalData[i][0], nextEl.getMedalID());
        Assert.assertEquals(testMedalData[i][1], nextEl.getDescription());
        Assert.assertEquals(testMedalData[i][2], nextEl.getTitle());
        Assert.assertEquals((long) testMedalData[i][3], nextEl.getCreated());
        Assert.assertEquals((int) testMedalData[i][4], nextEl.getCreatorID());
      }
    }

    {
      // Retrieve all stored data
      List<CorporationMemberMedal> storedData = AbstractESIAccountSync.retrieveAll(time,
                                                                                   (long contid, AttributeSelector at) ->
                                                                                       CorporationMemberMedal.accessQuery(
                                                                                           corpSyncAccount, contid,
                                                                                           1000,
                                                                                           false, at,
                                                                                           AbstractESIAccountSync.ANY_SELECTOR,
                                                                                           AbstractESIAccountSync.ANY_SELECTOR,
                                                                                           AbstractESIAccountSync.ANY_SELECTOR,
                                                                                           AbstractESIAccountSync.ANY_SELECTOR,
                                                                                           AbstractESIAccountSync.ANY_SELECTOR,
                                                                                           AbstractESIAccountSync.ANY_SELECTOR));

      // Check data matches test data
      Assert.assertEquals(testIssuedData.length, storedData.size());

      // Check stored data
      for (int i = 0; i < testIssuedData.length; i++) {
        CorporationMemberMedal nextEl = storedData.get(i);
        Assert.assertEquals((int) testIssuedData[i][0], nextEl.getMedalID());
        Assert.assertEquals((int) testIssuedData[i][1], nextEl.getCharacterID());
        Assert.assertEquals((long) testIssuedData[i][2], nextEl.getIssued());
        Assert.assertEquals((int) testIssuedData[i][3], nextEl.getIssuerID());
        Assert.assertEquals(testIssuedData[i][4], nextEl.getReason());
        Assert.assertEquals(String.valueOf(testIssuedData[i][5]), nextEl.getStatus());
      }
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationMedalsSync sync = new ESICorporationMedalsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, medalsTestData, issuedTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MEDALS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MEDALS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.
    //
    // Half the medals we insert are purposely not present in the server data so we can test deletion.
    // Compute the modified IDs
    int[] modifiedIDs = new int[medalsTestData.length];
    for (int i = 0; i < modifiedIDs.length; i++) {
      if (i % 2 == 0)
        modifiedIDs[i] = TestBase.getUniqueRandomInteger();
      else
        modifiedIDs[i] = (int) medalsTestData[i][0];
    }
    Object[][] oldMedalData = new Object[medalsTestData.length][5];
    for (int i = 0; i < medalsTestData.length; i++) {
      oldMedalData[i][0] = modifiedIDs[i];
      oldMedalData[i][1] = medalsTestData[i][1] + "1";
      oldMedalData[i][2] = medalsTestData[i][2] + "1";
      oldMedalData[i][3] = (long) medalsTestData[i][3] + 1;
      oldMedalData[i][4] = (int) medalsTestData[i][4] + 1;

      CorporationMedal newEl = new CorporationMedal((int) oldMedalData[i][0],
                                                    (String) oldMedalData[i][1],
                                                    (String) oldMedalData[i][2],
                                                    (long) oldMedalData[i][3],
                                                    (int) oldMedalData[i][4]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // CorporationMemberMedals can't be modified, so we generate all new data which should still
    // be present after update.
    Object[][] oldIssuedData = new Object[issuedTestData.length][6];
    for (int i = 0; i < issuedTestData.length; i++) {
      oldIssuedData[i][0] = TestBase.getUniqueRandomInteger();
      oldIssuedData[i][1] = (int) issuedTestData[i][1] + 1;
      oldIssuedData[i][2] = (long) issuedTestData[i][2] + 1;
      oldIssuedData[i][3] = (int) issuedTestData[i][3] + 1;
      oldIssuedData[i][4] = issuedTestData[i][4] + "1";
      oldIssuedData[i][5] = issuedTestData[i][4];

      CorporationMemberMedal newEl = new CorporationMemberMedal((int) oldIssuedData[i][0],
                                                                (int) oldIssuedData[i][1],
                                                                (long) oldIssuedData[i][2],
                                                                (int) oldIssuedData[i][3],
                                                                (String) oldIssuedData[i][4],
                                                                String.valueOf(oldIssuedData[i][5]));
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationMedalsSync sync = new ESICorporationMedalsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    verifyDataUpdate(testTime - 1, oldMedalData, oldIssuedData);

    // Verify updates are correct.
    Object[][] joinedIssueData = new Object[issuedTestData.length * 2][];
    System.arraycopy(oldIssuedData, 0, joinedIssueData, 0, oldIssuedData.length);
    System.arraycopy(issuedTestData, 0, joinedIssueData, oldIssuedData.length, issuedTestData.length);
    verifyDataUpdate(testTime, medalsTestData, joinedIssueData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MEDALS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MEDALS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
