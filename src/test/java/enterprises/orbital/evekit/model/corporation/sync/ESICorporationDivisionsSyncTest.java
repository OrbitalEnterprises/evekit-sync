package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsHangarHangar;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsWalletWallet;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Division;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ESICorporationDivisionsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;
  private static Object[][] divisionTestData;

  static {
    // Division test data
    // 0 boolean wallet
    // 1 int division
    // 2 String name
    int size = 10 + TestBase.getRandomInt(10);
    divisionTestData = new Object[size][3];
    for (int i = 0; i < size; i++) {
      divisionTestData[i][0] = TestBase.getRandomBoolean();
      divisionTestData[i][1] = TestBase.getUniqueRandomInteger();
      divisionTestData[i][2] = TestBase.getRandomText(50);
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_DIVISIONS, 1234L, null);

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
                                                        .createQuery("DELETE FROM Division ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    GetCorporationsCorporationIdDivisionsOk testDivs = new GetCorporationsCorporationIdDivisionsOk();
    for (Object[] aDivisionTestData : divisionTestData) {
      boolean wallet = (boolean) aDivisionTestData[0];
      int division = (int) aDivisionTestData[1];
      String name = (String) aDivisionTestData[2];
      if (wallet) {
        GetCorporationsCorporationIdDivisionsWalletWallet el = new GetCorporationsCorporationIdDivisionsWalletWallet();
        el.setDivision(division);
        el.setName(name);
        testDivs.getWallet()
                .add(el);
      } else {
        GetCorporationsCorporationIdDivisionsHangarHangar el = new GetCorporationsCorporationIdDivisionsHangarHangar();
        el.setDivision(division);
        el.setName(name);
        testDivs.getHangar()
                .add(el);
      }
    }

    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    mockEndpoint = EasyMock.createMock(CorporationApi.class);
    ApiResponse<GetCorporationsCorporationIdDivisionsOk> apir = new ApiResponse<>(200, headers, testDivs);
    EasyMock.expect(mockEndpoint.getCorporationsCorporationIdDivisionsWithHttpInfo(
        EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long time, Object[][] testData) throws IOException {
    List<Object[]> wallet = Arrays.stream(testData)
                                  .filter(objects -> (boolean) objects[0])
                                  .collect(Collectors.toList());
    List<Object[]> hangar = Arrays.stream(testData)
                                  .filter(objects -> !(boolean) objects[0])
                                  .collect(Collectors.toList());
    List<Division> walletDivisions = CachedData.retrieveAll(time,
                                                            (contid, at) -> Division.accessQuery(corpSyncAccount,
                                                                                                 contid, 1000, false,
                                                                                                 at,
                                                                                                 AttributeSelector.values(
                                                                                                     true),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any()));
    List<Division> hangarDivisions = CachedData.retrieveAll(time,
                                                            (contid, at) -> Division.accessQuery(corpSyncAccount,
                                                                                                 contid, 1000, false,
                                                                                                 at,
                                                                                                 AttributeSelector.values(
                                                                                                     false),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any()));
    Assert.assertEquals(wallet.size(), walletDivisions.size());
    Assert.assertEquals(hangar.size(), hangarDivisions.size());
    for (int i = 0; i < wallet.size(); i++) {
      Object[] next = wallet.get(i);
      Division obj = walletDivisions.get(i);
      Assert.assertEquals(next[0], obj.isWallet());
      Assert.assertEquals((int) next[1], obj.getDivision());
      Assert.assertEquals(next[2], obj.getName());
    }
    for (int i = 0; i < hangar.size(); i++) {
      Object[] next = hangar.get(i);
      Division obj = hangarDivisions.get(i);
      Assert.assertEquals(next[0], obj.isWallet());
      Assert.assertEquals((int) next[1], obj.getDivision());
      Assert.assertEquals(next[2], obj.getName());
    }
  }


  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationDivisionsSync sync = new ESICorporationDivisionsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, divisionTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_DIVISIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_DIVISIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    Object[][] old = new Object[divisionTestData.length][3];
    for (int i = 0; i < old.length; i++) {
      old[i][0] = divisionTestData[i][0];
      old[i][1] = divisionTestData[i][1];
      old[i][2] = TestBase.getRandomText(50);

      Division existing = new Division((boolean) old[i][0],
                                       (int) old[i][1],
                                       (String) old[i][2]);

      existing.setup(corpSyncAccount, testTime - 1);
      CachedData.update(existing);
    }

    // Perform the sync
    ESICorporationDivisionsSync sync = new ESICorporationDivisionsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old object was evolved properly
    verifyDataUpdate(testTime - 1, old);
    verifyDataUpdate(testTime, divisionTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_DIVISIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_DIVISIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
