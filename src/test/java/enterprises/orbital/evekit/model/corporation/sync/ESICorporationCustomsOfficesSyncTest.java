package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.PlanetaryInteractionApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdCustomsOffices200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CustomsOffice;
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
public class ESICorporationCustomsOfficesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private PlanetaryInteractionApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] officesTestData;
  private static int[] officesPages;

  static {
    // Customs Office test data
    // 0 long officeID
    // 1 int solarSystemID
    // 2 int reinforceExitStart
    // 3 int reinforceExitEnd
    // 4 boolean allowAlliance
    // 5 boolean allowStandings
    // 6 String standingLevel
    // 7 float taxRateAlliance
    // 8 float taxRateCorp
    // 9 float taxRateStandingExcellent
    // 10 float taxRateStandingGood
    // 11 float taxRateStandingNeutral
    // 12 float taxRateStandingBad
    // 13 float taxRateStandingTerrible
    int size = 100 + TestBase.getRandomInt(100);
    int standingLevelSize = GetCorporationsCorporationIdCustomsOffices200Ok.StandingLevelEnum.values().length;
    officesTestData = new Object[size][14];
    for (int i = 0; i < size; i++) {
      officesTestData[i][0] = TestBase.getUniqueRandomLong();
      officesTestData[i][1] = TestBase.getRandomInt();
      officesTestData[i][2] = TestBase.getRandomInt();
      officesTestData[i][3] = TestBase.getRandomInt();
      officesTestData[i][4] = TestBase.getRandomBoolean();
      officesTestData[i][5] = TestBase.getRandomBoolean();
      officesTestData[i][6] = GetCorporationsCorporationIdCustomsOffices200Ok.StandingLevelEnum.values()[TestBase.getRandomInt(
          standingLevelSize)];
      officesTestData[i][7] = TestBase.getRandomFloat(10);
      officesTestData[i][8] = TestBase.getRandomFloat(10);
      officesTestData[i][9] = TestBase.getRandomFloat(10);
      officesTestData[i][10] = TestBase.getRandomFloat(10);
      officesTestData[i][11] = TestBase.getRandomFloat(10);
      officesTestData[i][12] = TestBase.getRandomFloat(10);
      officesTestData[i][13] = TestBase.getRandomFloat(10);
    }

    // Configure page separations
    int pageCount = 2 + TestBase.getRandomInt(4);
    officesPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      officesPages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CUSTOMS, 1234L, null);

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
                                                        .createQuery("DELETE FROM CustomsOffice ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(PlanetaryInteractionApi.class);

    // Setup folders mock calls
    List<GetCorporationsCorporationIdCustomsOffices200Ok> shareholdersList =
        Arrays.stream(officesTestData)
              .map(x -> {
                GetCorporationsCorporationIdCustomsOffices200Ok nextOffice = new GetCorporationsCorporationIdCustomsOffices200Ok();
                nextOffice.setOfficeId((long) x[0]);
                nextOffice.setSystemId((int) x[1]);
                nextOffice.setReinforceExitStart((int) x[2]);
                nextOffice.setReinforceExitEnd((int) x[3]);
                nextOffice.setAllowAllianceAccess((boolean) x[4]);
                nextOffice.setAllowAccessWithStandings((boolean) x[5]);
                nextOffice.setStandingLevel((GetCorporationsCorporationIdCustomsOffices200Ok.StandingLevelEnum) x[6]);
                nextOffice.setAllianceTaxRate((float) x[7]);
                nextOffice.setCorporationTaxRate((float) x[8]);
                nextOffice.setExcellentStandingTaxRate((float) x[9]);
                nextOffice.setGoodStandingTaxRate((float) x[10]);
                nextOffice.setNeutralStandingTaxRate((float) x[11]);
                nextOffice.setBadStandingTaxRate((float) x[12]);
                nextOffice.setTerribleStandingTaxRate((float) x[13]);
                return nextOffice;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < officesPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(officesPages.length));
      ApiResponse<List<GetCorporationsCorporationIdCustomsOffices200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                                  shareholdersList.subList(
                                                                                                      last,
                                                                                                      officesPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdCustomsOfficesWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = officesPages[i];
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getPlanetaryInteractionApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<CustomsOffice> storedData = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        CustomsOffice.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
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
      CustomsOffice nextEl = storedData.get(i);
      Assert.assertEquals((long) testData[i][0], nextEl.getOfficeID());
      Assert.assertEquals((int) testData[i][1], nextEl.getSolarSystemID());
      Assert.assertEquals((int) testData[i][2], nextEl.getReinforceExitStart());
      Assert.assertEquals((int) testData[i][3], nextEl.getReinforceExitEnd());
      Assert.assertEquals(testData[i][4], nextEl.isAllowAlliance());
      Assert.assertEquals(testData[i][5], nextEl.isAllowStandings());
      Assert.assertEquals(String.valueOf(testData[i][6]), nextEl.getStandingLevel());
      Assert.assertEquals((float) testData[i][7], nextEl.getTaxRateAlliance(), 0.001);
      Assert.assertEquals((float) testData[i][8], nextEl.getTaxRateCorp(), 0.001);
      Assert.assertEquals((float) testData[i][9], nextEl.getTaxRateStandingExcellent(), 0.001);
      Assert.assertEquals((float) testData[i][10], nextEl.getTaxRateStandingGood(), 0.001);
      Assert.assertEquals((float) testData[i][11], nextEl.getTaxRateStandingNeutral(), 0.001);
      Assert.assertEquals((float) testData[i][12], nextEl.getTaxRateStandingBad(), 0.001);
      Assert.assertEquals((float) testData[i][13], nextEl.getTaxRateStandingTerrible(), 0.001);
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationCustomsOfficesSync sync = new ESICorporationCustomsOfficesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, officesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CUSTOMS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CUSTOMS);
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
    Object[][] oldData = new Object[officesTestData.length][14];
    int standingLevelSize = GetCorporationsCorporationIdCustomsOffices200Ok.StandingLevelEnum.values().length;
    for (int i = 0; i < officesTestData.length; i++) {
      oldData[i][0] = i % 2 == 0 ? officesTestData[i][0] : TestBase.getUniqueRandomLong();
      oldData[i][1] = TestBase.getRandomInt();
      oldData[i][2] = TestBase.getRandomInt();
      oldData[i][3] = TestBase.getRandomInt();
      oldData[i][4] = TestBase.getRandomBoolean();
      oldData[i][5] = TestBase.getRandomBoolean();
      oldData[i][6] = GetCorporationsCorporationIdCustomsOffices200Ok.StandingLevelEnum.values()[TestBase.getRandomInt(
          standingLevelSize)];
      oldData[i][7] = TestBase.getRandomFloat(10);
      oldData[i][8] = TestBase.getRandomFloat(10);
      oldData[i][9] = TestBase.getRandomFloat(10);
      oldData[i][10] = TestBase.getRandomFloat(10);
      oldData[i][11] = TestBase.getRandomFloat(10);
      oldData[i][12] = TestBase.getRandomFloat(10);
      oldData[i][13] = TestBase.getRandomFloat(10);

      CustomsOffice newEl = new CustomsOffice((long) oldData[i][0],
                                              (int) oldData[i][1],
                                              (int) oldData[i][2],
                                              (int) oldData[i][3],
                                              (boolean) oldData[i][4],
                                              (boolean) oldData[i][5],
                                              String.valueOf(oldData[i][6]),
                                              (float) oldData[i][7],
                                              (float) oldData[i][8],
                                              (float) oldData[i][9],
                                              (float) oldData[i][10],
                                              (float) oldData[i][11],
                                              (float) oldData[i][12],
                                              (float) oldData[i][13]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationCustomsOfficesSync sync = new ESICorporationCustomsOfficesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyDataUpdate(testTime - 1, oldData);

    // Verify updates
    verifyDataUpdate(testTime, officesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CUSTOMS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CUSTOMS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
