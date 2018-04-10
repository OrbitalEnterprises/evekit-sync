package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdShareholders200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Shareholder;
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
public class ESICorporationShareholdersSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] shareholdersTestData;
  private static int[] shareholdersPages;

  static {
    // Shareholders test data
    // 0 int shareholderID
    // 1 String shareholderType
    // 2 long shares
    int size = 100 + TestBase.getRandomInt(100);
    int typeSize = GetCorporationsCorporationIdShareholders200Ok.ShareholderTypeEnum.values().length;
    shareholdersTestData = new Object[size][3];
    for (int i = 0; i < size; i++) {
      shareholdersTestData[i][0] = TestBase.getUniqueRandomInteger();
      shareholdersTestData[i][1] = GetCorporationsCorporationIdShareholders200Ok.ShareholderTypeEnum.values()[TestBase.getRandomInt(
          typeSize)];
      shareholdersTestData[i][2] = TestBase.getRandomLong();
    }

    // Configure page separations
    int pageCount = 2 + TestBase.getRandomInt(4);
    shareholdersPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      shareholdersPages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_SHAREHOLDERS, 1234L, null);

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
                                                        .createQuery("DELETE FROM Shareholder ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);

    // Setup folders mock calls
    List<GetCorporationsCorporationIdShareholders200Ok> shareholdersList =
        Arrays.stream(shareholdersTestData)
              .map(x -> {
                GetCorporationsCorporationIdShareholders200Ok nextShareholder = new GetCorporationsCorporationIdShareholders200Ok();
                nextShareholder.setShareholderId((int) x[0]);
                nextShareholder.setShareholderType(
                    (GetCorporationsCorporationIdShareholders200Ok.ShareholderTypeEnum) x[1]);
                nextShareholder.setShareCount((long) x[2]);
                return nextShareholder;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < shareholdersPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(shareholdersPages.length));
      ApiResponse<List<GetCorporationsCorporationIdShareholders200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                                shareholdersList.subList(
                                                                                                    last,
                                                                                                    shareholdersPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdShareholdersWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = shareholdersPages[i];
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<Shareholder> storedData = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        Shareholder.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      Shareholder nextEl = storedData.get(i);
      Assert.assertEquals((int) testData[i][0], nextEl.getShareholderID());
      Assert.assertEquals(String.valueOf(testData[i][1]), nextEl.getShareholderType());
      Assert.assertEquals((long) testData[i][2], nextEl.getShares());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationShareholdersSync sync = new ESICorporationShareholdersSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, shareholdersTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_SHAREHOLDERS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_SHAREHOLDERS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // All of these objects are not in the server data so we can test deletion.
    Object[][] oldData = new Object[shareholdersTestData.length][3];
    int typeSize = GetCorporationsCorporationIdShareholders200Ok.ShareholderTypeEnum.values().length;
    for (int i = 0; i < shareholdersTestData.length; i++) {
      oldData[i][0] = i % 2 == 0 ? shareholdersTestData[i][0] : TestBase.getUniqueRandomInteger();
      oldData[i][1] = GetCorporationsCorporationIdShareholders200Ok.ShareholderTypeEnum.values()[TestBase.getRandomInt(
          typeSize)];
      oldData[i][2] = TestBase.getRandomLong();

      Shareholder newEl = new Shareholder((int) oldData[i][0],
                                          String.valueOf(oldData[i][1]),
                                          (long) oldData[i][2]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationShareholdersSync sync = new ESICorporationShareholdersSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyDataUpdate(testTime - 1, oldData);

    // Verify updates
    verifyDataUpdate(testTime, shareholdersTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_SHAREHOLDERS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_SHAREHOLDERS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
