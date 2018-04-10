package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdFacilities200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Facility;
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
public class ESICorporationFacilitiesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] facilitiesTestData;

  static {
    // Facility test data
    // 0 long facilityID
    // 1 int typeID
    // 2 int solarSystemID
    int size = 100 + TestBase.getRandomInt(100);
    facilitiesTestData = new Object[size][3];
    for (int i = 0; i < size; i++) {
      facilitiesTestData[i][0] = TestBase.getUniqueRandomLong();
      facilitiesTestData[i][1] = TestBase.getRandomInt();
      facilitiesTestData[i][2] = TestBase.getRandomInt();
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_FACILITIES, 1234L, null);

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
                                                        .createQuery("DELETE FROM Facility ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);

    // Setup folders mock calls
    List<GetCorporationsCorporationIdFacilities200Ok> facilitiesList =
        Arrays.stream(facilitiesTestData)
              .map(x -> {
                GetCorporationsCorporationIdFacilities200Ok nextFacility = new GetCorporationsCorporationIdFacilities200Ok();
                nextFacility.setFacilityId((long) x[0]);
                nextFacility.setTypeId((int) x[1]);
                nextFacility.setSystemId((int) x[2]);
                return nextFacility;
              })
              .collect(Collectors.toList());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCorporationsCorporationIdFacilities200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                            facilitiesList);
    EasyMock.expect(mockEndpoint.getCorporationsCorporationIdFacilitiesWithHttpInfo(
        EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<Facility> storedData = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        Facility.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      Facility nextEl = storedData.get(i);
      Assert.assertEquals((long) testData[i][0], nextEl.getFacilityID());
      Assert.assertEquals((int) testData[i][1], nextEl.getTypeID());
      Assert.assertEquals((int) testData[i][2], nextEl.getSolarSystemID());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationFacilitiesSync sync = new ESICorporationFacilitiesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, facilitiesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_FACILITIES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_FACILITIES);
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
    Object[][] oldData = new Object[facilitiesTestData.length][3];
    for (int i = 0; i < facilitiesTestData.length; i++) {
      oldData[i][0] = i % 2 == 0 ? facilitiesTestData[i][0] : TestBase.getUniqueRandomLong();
      oldData[i][1] = TestBase.getRandomInt();
      oldData[i][2] = TestBase.getRandomInt();

      Facility newEl = new Facility((long) oldData[i][0],
                                    (int) oldData[i][1],
                                    (int) oldData[i][2]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationFacilitiesSync sync = new ESICorporationFacilitiesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyDataUpdate(testTime - 1, oldData);

    // Verify updates
    verifyDataUpdate(testTime, facilitiesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_FACILITIES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_FACILITIES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
