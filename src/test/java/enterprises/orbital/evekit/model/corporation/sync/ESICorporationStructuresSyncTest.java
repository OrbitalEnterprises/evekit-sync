package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStructures200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStructuresService;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Structure;
import enterprises.orbital.evekit.model.corporation.StructureService;
import org.apache.commons.lang3.tuple.Triple;
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
public class ESICorporationStructuresSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] structuresTestData;
  private static int[] structurePages;

  static {
    // Structure test data
    // 0 long structureID;
    // 1 int corporationID;
    // 2 long fuelExpires;
    // 3 long nextReinforceApply;
    // 4 int nextReinforceHour;
    // 5 int nextReinforceWeekday;
    // 6 int profileID;
    // 7 int reinforceHour;
    // 8 int reinforceWeekday;
    // 9 String state; - enumerated
    // 10 long stateTimerEnd;
    // 11 long stateTimerStart;
    // 12 int systemID;
    // 13 int typeID;
    // 14 long unanchorsAt;
    // 15 Object[][] services
    int size = 100 + TestBase.getRandomInt(100);
    int stateSize = GetCorporationsCorporationIdStructures200Ok.StateEnum.values().length;
    int servSize = GetCorporationsCorporationIdStructuresService.StateEnum.values().length;
    structuresTestData = new Object[size][16];
    for (int i = 0; i < size; i++) {
      structuresTestData[i][0] = TestBase.getUniqueRandomLong();
      structuresTestData[i][1] = TestBase.getRandomInt();
      structuresTestData[i][2] = TestBase.getRandomLong();
      structuresTestData[i][3] = TestBase.getRandomLong();
      structuresTestData[i][4] = TestBase.getRandomInt();
      structuresTestData[i][5] = TestBase.getRandomInt();
      structuresTestData[i][6] = TestBase.getRandomInt();
      structuresTestData[i][7] = TestBase.getRandomInt();
      structuresTestData[i][8] = TestBase.getRandomInt();
      structuresTestData[i][9] = GetCorporationsCorporationIdStructures200Ok.StateEnum.values()[TestBase.getRandomInt(
          stateSize)];
      structuresTestData[i][10] = TestBase.getRandomLong();
      structuresTestData[i][11] = TestBase.getRandomLong();
      structuresTestData[i][12] = TestBase.getRandomInt();
      structuresTestData[i][13] = TestBase.getRandomInt();
      structuresTestData[i][14] = TestBase.getRandomLong();

      int serviceCount = 2 + TestBase.getRandomInt(5);
      Object[][] services = new Object[serviceCount][3];
      structuresTestData[i][15] = services;
      for (int j = 0; j < serviceCount; j++) {
        services[j][0] = structuresTestData[i][0];
        services[j][1] = TestBase.getRandomText(50) + String.valueOf(TestBase.getUniqueRandomInteger());
        services[j][2] = GetCorporationsCorporationIdStructuresService.StateEnum.values()[TestBase.getRandomInt(
            servSize)];
      }
    }

    // Configure page separations
    int pageCount = 2 + TestBase.getRandomInt(4);
    structurePages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      structurePages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STRUCTURES, 1234L, null);

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
                                                        .createQuery("DELETE FROM Structure ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM StructureService ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);

    // Setup structure list mock calls
    List<GetCorporationsCorporationIdStructures200Ok> structureList =
        Arrays.stream(structuresTestData)
              .map(x -> {
                GetCorporationsCorporationIdStructures200Ok nextBase = new GetCorporationsCorporationIdStructures200Ok();
                nextBase.setStructureId((long) x[0]);
                nextBase.setCorporationId((int) x[1]);
                nextBase.setFuelExpires(new DateTime(new Date((long) x[2])));
                nextBase.setNextReinforceApply(new DateTime(new Date((long) x[3])));
                nextBase.setNextReinforceHour((int) x[4]);
                nextBase.setNextReinforceWeekday((int) x[5]);
                nextBase.setProfileId((int) x[6]);
                nextBase.setReinforceHour((int) x[7]);
                nextBase.setReinforceWeekday((int) x[8]);
                nextBase.setState((GetCorporationsCorporationIdStructures200Ok.StateEnum) x[9]);
                nextBase.setStateTimerEnd(new DateTime(new Date((long) x[10])));
                nextBase.setStateTimerStart(new DateTime(new Date((long) x[11])));
                nextBase.setSystemId((int) x[12]);
                nextBase.setTypeId((int) x[13]);
                nextBase.setUnanchorsAt(new DateTime(new Date((long) x[14])));
                nextBase.getServices()
                        .addAll(
                            Arrays.stream((Object[][]) x[15])
                                  .map(y -> {
                                    GetCorporationsCorporationIdStructuresService nextService = new GetCorporationsCorporationIdStructuresService();
                                    nextService.setName((String) y[1]);
                                    nextService.setState(
                                        (GetCorporationsCorporationIdStructuresService.StateEnum) y[2]);
                                    return nextService;
                                  })
                                  .collect(Collectors.toList())
                               );
                return nextBase;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < structurePages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(structurePages.length));
      ApiResponse<List<GetCorporationsCorporationIdStructures200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                              structureList.subList(
                                                                                                  last,
                                                                                                  structurePages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdStructuresWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = structurePages[i];
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<Structure> storedData = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        Structure.accessQuery(corpSyncAccount, contid, 1000, false, at,
                              AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      Structure nextEl = storedData.get(i);
      Assert.assertEquals((long) testData[i][0], nextEl.getStructureID());
      Assert.assertEquals((int) testData[i][1], nextEl.getCorporationID());
      Assert.assertEquals((long) testData[i][2], nextEl.getFuelExpires());
      Assert.assertEquals((long) testData[i][3], nextEl.getNextReinforceApply());
      Assert.assertEquals((int) testData[i][4], nextEl.getNextReinforceHour());
      Assert.assertEquals((int) testData[i][5], nextEl.getNextReinforceWeekday());
      Assert.assertEquals((int) testData[i][6], nextEl.getProfileID());
      Assert.assertEquals((int) testData[i][7], nextEl.getReinforceHour());
      Assert.assertEquals((int) testData[i][8], nextEl.getReinforceWeekday());
      Assert.assertEquals(String.valueOf(testData[i][9]), nextEl.getState());
      Assert.assertEquals((long) testData[i][10], nextEl.getStateTimerEnd());
      Assert.assertEquals((long) testData[i][11], nextEl.getStateTimerStart());
      Assert.assertEquals((int) testData[i][12], nextEl.getSystemID());
      Assert.assertEquals((int) testData[i][13], nextEl.getTypeID());
      Assert.assertEquals((long) testData[i][14], nextEl.getUnanchorsAt());
    }

    List<StructureService> storedService = AbstractESIAccountSync.retrieveAll(time,
                                                                              (long contid, AttributeSelector at) ->
                                                                                  StructureService.accessQuery(
                                                                                      corpSyncAccount, contid, 1000,
                                                                                      false, at,
                                                                                      AbstractESIAccountSync.ANY_SELECTOR,
                                                                                      AbstractESIAccountSync.ANY_SELECTOR,
                                                                                      AbstractESIAccountSync.ANY_SELECTOR));
    Set<Triple<Long, String, String>> testServiceData = new HashSet<>();
    for (Object[] nextBase : testData) {
      for (Object[] nextService : (Object[][]) nextBase[15]) {
        testServiceData.add(Triple.of((long) nextService[0], (String) nextService[1], String.valueOf(nextService[2])));
      }
    }

    // Check data matches test data
    Assert.assertEquals(testServiceData.size(), storedService.size());

    // Check stored data
    for (StructureService nextService : storedService) {
      Assert.assertTrue(testServiceData.contains(Triple.of(nextService.getStructureID(),
                                                           nextService.getName(),
                                                           nextService.getState())));
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationStructuresSync sync = new ESICorporationStructuresSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, structuresTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_STRUCTURES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STRUCTURES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // Half of these objects are not in the server data so we can test deletion.
    Object[][] oldData = new Object[structuresTestData.length][16];
    int stateSize = GetCorporationsCorporationIdStructures200Ok.StateEnum.values().length;
    int servSize = GetCorporationsCorporationIdStructuresService.StateEnum.values().length;
    for (int i = 0; i < structuresTestData.length; i++) {
      oldData[i][0] = i % 2 == 0 ? structuresTestData[i][0] : TestBase.getUniqueRandomLong();
      oldData[i][1] = TestBase.getRandomInt();
      oldData[i][2] = TestBase.getRandomLong();
      oldData[i][3] = TestBase.getRandomLong();
      oldData[i][4] = TestBase.getRandomInt();
      oldData[i][5] = TestBase.getRandomInt();
      oldData[i][6] = TestBase.getRandomInt();
      oldData[i][7] = TestBase.getRandomInt();
      oldData[i][8] = TestBase.getRandomInt();
      oldData[i][9] = GetCorporationsCorporationIdStructures200Ok.StateEnum.values()[TestBase.getRandomInt(stateSize)];
      oldData[i][10] = TestBase.getRandomLong();
      oldData[i][11] = TestBase.getRandomLong();
      oldData[i][12] = TestBase.getRandomInt();
      oldData[i][13] = TestBase.getRandomInt();
      oldData[i][14] = TestBase.getRandomLong();

      Structure newEl = new Structure((long) oldData[i][0],
                                      (int) oldData[i][1],
                                      (long) oldData[i][2],
                                      (long) oldData[i][3],
                                      (int) oldData[i][4],
                                      (int) oldData[i][5],
                                      (int) oldData[i][6],
                                      (int) oldData[i][7],
                                      (int) oldData[i][8],
                                      String.valueOf(oldData[i][9]),
                                      (long) oldData[i][10],
                                      (long) oldData[i][11],
                                      (int) oldData[i][12],
                                      (int) oldData[i][13],
                                      (long) oldData[i][14]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);

      int serviceCount = 2 + TestBase.getRandomInt(5);
      Object[][] services = new Object[serviceCount][3];
      oldData[i][15] = services;
      for (int j = 0; j < serviceCount; j++) {
        services[j][0] = oldData[i][0];
        services[j][1] = TestBase.getRandomText(50) + String.valueOf(TestBase.getUniqueRandomInteger());
        services[j][2] = GetCorporationsCorporationIdStructuresService.StateEnum.values()[TestBase.getRandomInt(
            servSize)];

        StructureService ss = new StructureService((long) services[j][0],
                                                   (String) services[j][1],
                                                   String.valueOf(services[j][2]));
        ss.setup(corpSyncAccount, testTime - 1);
        CachedData.update(ss);
      }

    }

    // Perform the sync
    ESICorporationStructuresSync sync = new ESICorporationStructuresSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyDataUpdate(testTime - 1, oldData);

    // Verify updates
    verifyDataUpdate(testTime, structuresTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_STRUCTURES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STRUCTURES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
