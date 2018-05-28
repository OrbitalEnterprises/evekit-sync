package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStarbases200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStarbasesStarbaseIdFuel;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStarbasesStarbaseIdOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Fuel;
import enterprises.orbital.evekit.model.corporation.Starbase;
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
public class ESICorporationStarbasesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] basesTestData;
  private static int[] basesPages;

  static {
    // Starbase test data
    // 0 long starbaseID
    // 1 int typeID
    // 2 int systemID
    // 3 int moonID
    // 4 String state
    // 5 long unanchorAt
    // 6 long reinforcedUntil
    // 7 long onlinedSince
    // 8 String fuelBayView
    // 9 String fuelBayTake
    // 10 String anchor
    // 11 String unanchor
    // 12 String online
    // 13 String offline
    // 14 boolean allowCorporationMembers
    // 15 boolean allowAllianceMembers
    // 16 boolean useAllianceStandings
    // 17 float attackStandingThreshold
    // 18 float attackSecurityStatusThreshold
    // 19 boolean attackIfOtherSecurityStatusDropping
    // 20 boolean attackIfAtWar
    // 21 Object[][] Fuels
    int size = 100 + TestBase.getRandomInt(100);
    int stateSize = GetCorporationsCorporationIdStarbases200Ok.StateEnum.values().length;
    int roleSize = GetCorporationsCorporationIdStarbasesStarbaseIdOk.UnanchorEnum.values().length;
    basesTestData = new Object[size][22];
    for (int i = 0; i < size; i++) {
      basesTestData[i][0] = TestBase.getUniqueRandomLong();
      basesTestData[i][1] = TestBase.getRandomInt();
      basesTestData[i][2] = TestBase.getRandomInt();
      basesTestData[i][3] = TestBase.getRandomInt();
      basesTestData[i][4] = GetCorporationsCorporationIdStarbases200Ok.StateEnum.values()[TestBase.getRandomInt(
          stateSize)];
      basesTestData[i][5] = TestBase.getRandomLong();
      basesTestData[i][6] = TestBase.getRandomLong();
      basesTestData[i][7] = TestBase.getRandomLong();
      basesTestData[i][8] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.FuelBayViewEnum.values()[TestBase.getRandomInt(
          roleSize)];
      basesTestData[i][9] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.FuelBayTakeEnum.values()[TestBase.getRandomInt(
          roleSize)];
      basesTestData[i][10] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.AnchorEnum.values()[TestBase.getRandomInt(
          roleSize)];
      basesTestData[i][11] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.UnanchorEnum.values()[TestBase.getRandomInt(
          roleSize)];
      basesTestData[i][12] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.OnlineEnum.values()[TestBase.getRandomInt(
          roleSize)];
      basesTestData[i][13] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.OfflineEnum.values()[TestBase.getRandomInt(
          roleSize)];
      basesTestData[i][14] = TestBase.getRandomBoolean();
      basesTestData[i][15] = TestBase.getRandomBoolean();
      basesTestData[i][16] = TestBase.getRandomBoolean();
      basesTestData[i][17] = TestBase.getRandomFloat(10);
      basesTestData[i][18] = TestBase.getRandomFloat(10);
      basesTestData[i][19] = TestBase.getRandomBoolean();
      basesTestData[i][20] = TestBase.getRandomBoolean();

      int fuelCount = 2 + TestBase.getRandomInt(5);
      Object[][] fuels = new Object[fuelCount][3];
      basesTestData[i][21] = fuels;
      for (int j = 0; j < fuelCount; j++) {
        fuels[j][0] = basesTestData[i][0];
        fuels[j][1] = TestBase.getUniqueRandomInteger();
        fuels[j][2] = TestBase.getRandomInt();
      }
    }

    // Configure page separations
    int pageCount = 2 + TestBase.getRandomInt(4);
    basesPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      basesPages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STARBASES, 1234L, null);

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
                                                        .createQuery("DELETE FROM Starbase ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM Fuel ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  private static <T> T findEnum(T[] src, String target) {
    for (T n : src) {
      if (n.toString()
           .equals(target))
        return n;
    }
    throw new RuntimeException("Can't find " + target + " in " + Arrays.toString(src));
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);

    // Setup starbase list mock calls
    List<GetCorporationsCorporationIdStarbases200Ok> baseList =
        Arrays.stream(basesTestData)
              .map(x -> {
                GetCorporationsCorporationIdStarbases200Ok nextBase = new GetCorporationsCorporationIdStarbases200Ok();
                nextBase.setStarbaseId((long) x[0]);
                nextBase.setTypeId((int) x[1]);
                nextBase.setSystemId((int) x[2]);
                nextBase.setMoonId((int) x[3]);
                nextBase.setState((GetCorporationsCorporationIdStarbases200Ok.StateEnum) x[4]);
                nextBase.setUnanchorAt(new DateTime(new Date((long) x[5])));
                nextBase.setReinforcedUntil(new DateTime(new Date((long) x[6])));
                nextBase.setOnlinedSince(new DateTime(new Date((long) x[7])));
                return nextBase;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < basesPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(basesPages.length));
      ApiResponse<List<GetCorporationsCorporationIdStarbases200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                             baseList.subList(
                                                                                                 last,
                                                                                                 basesPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdStarbasesWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(apir);
      last = basesPages[i];
    }

    // Setup starbase info mock calls
    for (Object[] nextBase : basesTestData) {
      GetCorporationsCorporationIdStarbasesStarbaseIdOk info = new GetCorporationsCorporationIdStarbasesStarbaseIdOk();
      info.setFuelBayView(findEnum(GetCorporationsCorporationIdStarbasesStarbaseIdOk.FuelBayViewEnum.values(),
                                   String.valueOf(nextBase[8])));
      info.setFuelBayTake(findEnum(GetCorporationsCorporationIdStarbasesStarbaseIdOk.FuelBayTakeEnum.values(),
                                   String.valueOf(nextBase[9])));
      info.setAnchor(findEnum(GetCorporationsCorporationIdStarbasesStarbaseIdOk.AnchorEnum.values(),
                              String.valueOf(nextBase[10])));
      info.setUnanchor(findEnum(GetCorporationsCorporationIdStarbasesStarbaseIdOk.UnanchorEnum.values(),
                                String.valueOf(nextBase[11])));
      info.setOnline(findEnum(GetCorporationsCorporationIdStarbasesStarbaseIdOk.OnlineEnum.values(),
                              String.valueOf(nextBase[12])));
      info.setOffline(findEnum(GetCorporationsCorporationIdStarbasesStarbaseIdOk.OfflineEnum.values(),
                               String.valueOf(nextBase[13])));
      info.setAllowCorporationMembers((boolean) nextBase[14]);
      info.setAllowAllianceMembers((boolean) nextBase[15]);
      info.setUseAllianceStandings((boolean) nextBase[16]);
      info.setAttackStandingThreshold((float) nextBase[17]);
      info.setAttackSecurityStatusThreshold((float) nextBase[18]);
      info.setAttackIfOtherSecurityStatusDropping((boolean) nextBase[19]);
      info.setAttackIfAtWar((boolean) nextBase[20]);

      for (Object[] fuel : (Object[][]) nextBase[21]) {
        GetCorporationsCorporationIdStarbasesStarbaseIdFuel f = new GetCorporationsCorporationIdStarbasesStarbaseIdFuel();
        f.setTypeId((int) fuel[1]);
        f.setQuantity((int) fuel[2]);
        info.getFuels()
            .add(f);
      }

      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
      ApiResponse<GetCorporationsCorporationIdStarbasesStarbaseIdOk> apir = new ApiResponse<>(200, headers, info);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdStarbasesStarbaseIdWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.eq((long) nextBase[0]),
          EasyMock.eq((int) nextBase[2]),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<Starbase> storedData = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        Starbase.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
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
      Starbase nextEl = storedData.get(i);
      Assert.assertEquals((long) testData[i][0], nextEl.getStarbaseID());
      Assert.assertEquals((int) testData[i][1], nextEl.getTypeID());
      Assert.assertEquals((int) testData[i][2], nextEl.getSystemID());
      Assert.assertEquals((int) testData[i][3], nextEl.getMoonID());
      Assert.assertEquals(String.valueOf(testData[i][4]), nextEl.getState());
      Assert.assertEquals((long) testData[i][5], nextEl.getUnanchorAt());
      Assert.assertEquals((long) testData[i][6], nextEl.getReinforcedUntil());
      Assert.assertEquals((long) testData[i][7], nextEl.getOnlinedSince());
      Assert.assertEquals(String.valueOf(testData[i][8]), nextEl.getFuelBayView());
      Assert.assertEquals(String.valueOf(testData[i][9]), nextEl.getFuelBayTake());
      Assert.assertEquals(String.valueOf(testData[i][10]), nextEl.getAnchor());
      Assert.assertEquals(String.valueOf(testData[i][11]), nextEl.getUnanchor());
      Assert.assertEquals(String.valueOf(testData[i][12]), nextEl.getOnline());
      Assert.assertEquals(String.valueOf(testData[i][13]), nextEl.getOffline());
      Assert.assertEquals(testData[i][14], nextEl.isAllowCorporationMembers());
      Assert.assertEquals(testData[i][15], nextEl.isAllowAllianceMembers());
      Assert.assertEquals(testData[i][16], nextEl.isUseAllianceStandings());
      Assert.assertEquals((float) testData[i][17], nextEl.getAttackStandingThreshold(), 0.001);
      Assert.assertEquals((float) testData[i][18], nextEl.getAttackSecurityStatusThreshold(), 0.001);
      Assert.assertEquals(testData[i][19], nextEl.isAttackIfOtherSecurityStatusDropping());
      Assert.assertEquals(testData[i][20], nextEl.isAttackIfAtWar());
    }

    List<Fuel> storedFuel = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        Fuel.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                         AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));
    Set<Triple<Long, Integer, Integer>> testFuelData = new HashSet<>();
    for (Object[] nextBase : testData) {
      for (Object[] nextFuel : (Object[][]) nextBase[21]) {
        testFuelData.add(Triple.of((long) nextFuel[0], (int) nextFuel[1], (int) nextFuel[2]));
      }
    }

    // Check data matches test data
    Assert.assertEquals(testFuelData.size(), storedFuel.size());

    // Check stored data
    for (Fuel nextFuel : storedFuel) {
      Assert.assertTrue(testFuelData.contains(Triple.of(nextFuel.getStarbaseID(), nextFuel.getTypeID(),
                                                        nextFuel.getQuantity())));
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationStarbasesSync sync = new ESICorporationStarbasesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, basesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_STARBASES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STARBASES);
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
    Object[][] oldData = new Object[basesTestData.length][22];
    int stateSize = GetCorporationsCorporationIdStarbases200Ok.StateEnum.values().length;
    int roleSize = GetCorporationsCorporationIdStarbasesStarbaseIdOk.UnanchorEnum.values().length;
    for (int i = 0; i < basesTestData.length; i++) {
      oldData[i][0] = i % 2 == 0 ? basesTestData[i][0] : TestBase.getUniqueRandomLong();
      oldData[i][1] = TestBase.getRandomInt();
      oldData[i][2] = TestBase.getRandomInt();
      oldData[i][3] = TestBase.getRandomInt();
      oldData[i][4] = GetCorporationsCorporationIdStarbases200Ok.StateEnum.values()[TestBase.getRandomInt(stateSize)];
      oldData[i][5] = TestBase.getRandomLong();
      oldData[i][6] = TestBase.getRandomLong();
      oldData[i][7] = TestBase.getRandomLong();
      oldData[i][8] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.FuelBayViewEnum.values()[TestBase.getRandomInt(
          roleSize)];
      oldData[i][9] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.FuelBayTakeEnum.values()[TestBase.getRandomInt(
          roleSize)];
      oldData[i][10] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.AnchorEnum.values()[TestBase.getRandomInt(
          roleSize)];
      oldData[i][11] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.UnanchorEnum.values()[TestBase.getRandomInt(
          roleSize)];
      oldData[i][12] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.OnlineEnum.values()[TestBase.getRandomInt(
          roleSize)];
      oldData[i][13] = GetCorporationsCorporationIdStarbasesStarbaseIdOk.OfflineEnum.values()[TestBase.getRandomInt(
          roleSize)];
      oldData[i][14] = TestBase.getRandomBoolean();
      oldData[i][15] = TestBase.getRandomBoolean();
      oldData[i][16] = TestBase.getRandomBoolean();
      oldData[i][17] = TestBase.getRandomFloat(10);
      oldData[i][18] = TestBase.getRandomFloat(10);
      oldData[i][19] = TestBase.getRandomBoolean();
      oldData[i][20] = TestBase.getRandomBoolean();

      Starbase newEl = new Starbase((long) oldData[i][0],
                                    (int) oldData[i][1],
                                    (int) oldData[i][2],
                                    (int) oldData[i][3],
                                    String.valueOf(oldData[i][4]),
                                    (long) oldData[i][5],
                                    (long) oldData[i][6],
                                    (long) oldData[i][7],
                                    String.valueOf(oldData[i][8]),
                                    String.valueOf(oldData[i][9]),
                                    String.valueOf(oldData[i][10]),
                                    String.valueOf(oldData[i][11]),
                                    String.valueOf(oldData[i][12]),
                                    String.valueOf(oldData[i][13]),
                                    (boolean) oldData[i][14],
                                    (boolean) oldData[i][15],
                                    (boolean) oldData[i][16],
                                    (float) oldData[i][17],
                                    (float) oldData[i][18],
                                    (boolean) oldData[i][19],
                                    (boolean) oldData[i][20]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);

      int fuelCount = 2 + TestBase.getRandomInt(5);
      Object[][] fuels = new Object[fuelCount][3];
      oldData[i][21] = fuels;
      for (int j = 0; j < fuelCount; j++) {
        fuels[j][0] = oldData[i][0];
        fuels[j][1] = TestBase.getUniqueRandomInteger();
        fuels[j][2] = TestBase.getRandomInt();

        Fuel fel = new Fuel((long) fuels[j][0],
                            (int) fuels[j][1],
                            (int) fuels[j][2]);
        fel.setup(corpSyncAccount, testTime - 1);
        CachedData.update(fel);
      }

    }

    // Perform the sync
    ESICorporationStarbasesSync sync = new ESICorporationStarbasesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyDataUpdate(testTime - 1, oldData);

    // Verify updates
    verifyDataUpdate(testTime, basesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_STARBASES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_STARBASES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
