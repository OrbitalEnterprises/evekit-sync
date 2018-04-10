package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContainersLogs200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.ContainerLog;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICorporationContainerLogSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] containerLogTestData;
  private static int[] pages;

  static {
    // ContainerLog test data
    // 0 long logTime = -1;
    // 1 String action;
    // 2 int characterID;
    // 3 String locationFlag;
    // 4 long containerID;
    // 5 int containerTypeID;
    // 6 long locationID;
    // 7 int newConfiguration;
    // 8 int oldConfiguration;
    // 9 String passwordType;
    // 10 int quantity;
    // 11 int typeID;
    int size = 200 + TestBase.getRandomInt(500);
    containerLogTestData = new Object[size][12];
    int actionLen = GetCorporationsCorporationIdContainersLogs200Ok.ActionEnum.values().length;
    int locationFlagLen = GetCorporationsCorporationIdContainersLogs200Ok.LocationFlagEnum.values().length;
    int passwordTypeLen = GetCorporationsCorporationIdContainersLogs200Ok.PasswordTypeEnum.values().length;
    for (int i = 0; i < size; i++) {
      containerLogTestData[i][0] = TestBase.getUniqueRandomLong();
      containerLogTestData[i][1] = GetCorporationsCorporationIdContainersLogs200Ok.ActionEnum.values()[TestBase.getRandomInt(
          actionLen)];
      containerLogTestData[i][2] = TestBase.getRandomInt();
      containerLogTestData[i][3] = GetCorporationsCorporationIdContainersLogs200Ok.LocationFlagEnum.values()[TestBase.getRandomInt(
          locationFlagLen)];
      containerLogTestData[i][4] = TestBase.getRandomLong();
      containerLogTestData[i][5] = TestBase.getRandomInt();
      containerLogTestData[i][6] = TestBase.getRandomLong();
      containerLogTestData[i][7] = TestBase.getRandomInt();
      containerLogTestData[i][8] = TestBase.getRandomInt();
      containerLogTestData[i][9] = GetCorporationsCorporationIdContainersLogs200Ok.PasswordTypeEnum.values()[TestBase.getRandomInt(
          passwordTypeLen)];
      containerLogTestData[i][10] = TestBase.getRandomInt();
      containerLogTestData[i][11] = TestBase.getRandomInt();
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
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTAINER_LOGS, 1234L, null);

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
                                                        .createQuery("DELETE FROM ContainerLog ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);
    // Setup retrieval mock calls
    List<GetCorporationsCorporationIdContainersLogs200Ok> containerLogList =
        Arrays.stream(containerLogTestData)
              .map(x -> {
                GetCorporationsCorporationIdContainersLogs200Ok nextLog = new GetCorporationsCorporationIdContainersLogs200Ok();
                nextLog.setLoggedAt(new DateTime(new Date((Long) x[0])));
                nextLog.setAction((GetCorporationsCorporationIdContainersLogs200Ok.ActionEnum) x[1]);
                nextLog.setCharacterId((Integer) x[2]);
                nextLog.setLocationFlag((GetCorporationsCorporationIdContainersLogs200Ok.LocationFlagEnum) x[3]);
                nextLog.setContainerId((Long) x[4]);
                nextLog.setContainerTypeId((Integer) x[5]);
                nextLog.setLocationId((Long) x[6]);
                nextLog.setNewConfigBitmask((Integer) x[7]);
                nextLog.setOldConfigBitmask((Integer) x[8]);
                nextLog.setPasswordType((GetCorporationsCorporationIdContainersLogs200Ok.PasswordTypeEnum) x[9]);
                nextLog.setQuantity((Integer) x[10]);
                nextLog.setTypeId((Integer) x[11]);
                return nextLog;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < pages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCorporationsCorporationIdContainersLogs200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                                  containerLogList.subList(
                                                                                                      last,
                                                                                                      pages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdContainersLogsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = pages[i];
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<ContainerLog> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        ContainerLog.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      ContainerLog nextEl = storedData.get(i);
      Assert.assertEquals((long) (Long) testData[i][0], nextEl.getLogTime());
      Assert.assertEquals(String.valueOf(testData[i][1]), nextEl.getAction());
      Assert.assertEquals((int) (Integer) testData[i][2], nextEl.getCharacterID());
      Assert.assertEquals(String.valueOf(testData[i][3]), nextEl.getLocationFlag());
      Assert.assertEquals((long) (Long) testData[i][4], nextEl.getContainerID());
      Assert.assertEquals((int) (Integer) testData[i][5], nextEl.getContainerTypeID());
      Assert.assertEquals((long) (Long) testData[i][6], nextEl.getLocationID());
      Assert.assertEquals((int) (Integer) testData[i][7], nextEl.getNewConfiguration());
      Assert.assertEquals((int) (Integer) testData[i][8], nextEl.getOldConfiguration());
      Assert.assertEquals(String.valueOf(testData[i][9]), nextEl.getPasswordType());
      Assert.assertEquals((int) (Integer) testData[i][10], nextEl.getQuantity());
      Assert.assertEquals((int) (Integer) testData[i][11], nextEl.getTypeID());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationContainerLogSync sync = new ESICorporationContainerLogSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(containerLogTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CONTAINER_LOGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTAINER_LOGS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.  These should not be modified.  This is just a copy of the existing test data with
    // lower log times and slightly adjusted data values.
    Object[][] allData = new Object[containerLogTestData.length * 2][12];
    System.arraycopy(containerLogTestData, 0, allData, containerLogTestData.length, containerLogTestData.length);
    long firstLogTime = (Long) Arrays.stream(containerLogTestData)
                                     .min(Comparator.comparingLong(x -> (Long) x[0]))
                                     .get()[0];
    firstLogTime -= containerLogTestData.length - 1;
    for (int i = 0; i < allData.length / 2; i++) {
      System.arraycopy(allData[i + containerLogTestData.length], 0, allData[i], 0, 12);
      allData[i][0] = firstLogTime + i;
      Object[] dt = allData[i];
      ContainerLog newEl = new ContainerLog((Long) dt[0],
                                            String.valueOf(dt[1]),
                                            (Integer) dt[2],
                                            String.valueOf(dt[3]),
                                            (Long) dt[4],
                                            (Integer) dt[5],
                                            (Long) dt[6],
                                            (Integer) dt[7],
                                            (Integer) dt[8],
                                            String.valueOf(dt[9]),
                                            (Integer) dt[10],
                                            (Integer) dt[11]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationContainerLogSync sync = new ESICorporationContainerLogSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate(allData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CONTAINER_LOGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTAINER_LOGS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
