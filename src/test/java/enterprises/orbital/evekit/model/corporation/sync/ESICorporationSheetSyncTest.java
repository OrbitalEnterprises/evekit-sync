package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdIconsOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CorporationSheet;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class ESICorporationSheetSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;
  private GetCorporationsCorporationIdOk testSheet;
  private GetCorporationsCorporationIdIconsOk testIcons;

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_SHEET, 1234L, null);

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
                                                        .createQuery("DELETE FROM CorporationSheet ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    testSheet = new GetCorporationsCorporationIdOk();
    testSheet.setAllianceId(TestBase.getRandomInt());
    testSheet.setCeoId(TestBase.getRandomInt());
    testSheet.setName(TestBase.getRandomText(50));
    testSheet.setDescription(TestBase.getRandomText(1000));
    testSheet.setMemberCount(TestBase.getRandomInt());
    testSheet.setShares(TestBase.getRandomLong());
    testSheet.setHomeStationId(TestBase.getRandomInt());
    testSheet.setTaxRate(TestBase.getRandomFloat(10));
    testSheet.setTicker(TestBase.getRandomText(50));
    testSheet.setUrl(TestBase.getRandomText(50));
    testSheet.setDateFounded(new DateTime(new Date(TestBase.getRandomLong())));
    testSheet.setCreatorId(TestBase.getRandomInt());
    testSheet.setFactionId(TestBase.getRandomInt());
    testSheet.setWarEligible(TestBase.getRandomBoolean());

    testIcons = new GetCorporationsCorporationIdIconsOk();
    testIcons.setPx64x64(TestBase.getRandomText(50));
    testIcons.setPx128x128(TestBase.getRandomText(50));
    testIcons.setPx256x256(TestBase.getRandomText(50));

    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    mockEndpoint = EasyMock.createMock(CorporationApi.class);
    {
      ApiResponse<GetCorporationsCorporationIdOk> apir = new ApiResponse<>(200, headers, testSheet);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }
    {
      ApiResponse<GetCorporationsCorporationIdIconsOk> apir = new ApiResponse<>(200, headers, testIcons);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdIconsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationSheetSync sync = new ESICorporationSheetSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    CorporationSheet result = CorporationSheet.get(corpSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());

    Assert.assertEquals((int) testSheet.getAllianceId(), result.getAllianceID());
    Assert.assertEquals((int) testSheet.getCeoId(), result.getCeoID());
    Assert.assertEquals(corpSyncAccount.getEveCorporationID(), result.getCorporationID());
    Assert.assertEquals(testSheet.getName(), result.getCorporationName());
    Assert.assertEquals(testSheet.getDescription(), result.getDescription());
    Assert.assertEquals((int) testSheet.getMemberCount(), result.getMemberCount());
    Assert.assertEquals((long) testSheet.getShares(), result.getShares());
    Assert.assertEquals((int) testSheet.getHomeStationId(), result.getStationID());
    Assert.assertEquals(testSheet.getTaxRate(), result.getTaxRate(), 0.01);
    Assert.assertEquals(testSheet.getTicker(), result.getTicker());
    Assert.assertEquals(testSheet.getUrl(), result.getUrl());
    Assert.assertEquals(testSheet.getDateFounded()
                                 .getMillis(), result.getDateFounded());
    Assert.assertEquals((int) testSheet.getCreatorId(), result.getCreatorID());
    Assert.assertEquals((int) testSheet.getFactionId(), result.getFactionID());
    Assert.assertEquals(testSheet.getWarEligible(), result.isWarEligible());
    Assert.assertEquals(testIcons.getPx64x64(), result.getPx64x64());
    Assert.assertEquals(testIcons.getPx128x128(), result.getPx128x128());
    Assert.assertEquals(testIcons.getPx256x256(), result.getPx256x256());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_SHEET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_SHEET);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    CorporationSheet existing = new CorporationSheet(
        testSheet.getAllianceId() + 1,
        testSheet.getCeoId() + 1,
        corpSyncAccount.getEveCorporationID(),
        testSheet.getName() + "1",
        testSheet.getDescription() + "1",
        testSheet.getMemberCount() + 1,
        testSheet.getShares() + 1,
        testSheet.getHomeStationId() + 1,
        testSheet.getTaxRate() + 1,
        testSheet.getTicker() + "1",
        testSheet.getUrl() + "1",
        testSheet.getDateFounded()
                 .getMillis() + 1,
        testSheet.getCreatorId() + 1,
        testSheet.getFactionId() + 1,
        testIcons.getPx64x64() + "1",
        testIcons.getPx128x128() + "1",
        testIcons.getPx256x256() + "1",
        !testSheet.getWarEligible()
    );
    existing.setup(corpSyncAccount, testTime - 1);
    CachedData.update(existing);

    // Perform the sync
    ESICorporationSheetSync sync = new ESICorporationSheetSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old object was evolved properly
    CorporationSheet result = CorporationSheet.get(corpSyncAccount, testTime - 1);
    Assert.assertEquals(testTime - 1, result.getLifeStart());
    Assert.assertEquals(testTime, result.getLifeEnd());

    // Verify updated properly
    result = CorporationSheet.get(corpSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());

    Assert.assertEquals((int) testSheet.getAllianceId(), result.getAllianceID());
    Assert.assertEquals((int) testSheet.getCeoId(), result.getCeoID());
    Assert.assertEquals(corpSyncAccount.getEveCorporationID(), result.getCorporationID());
    Assert.assertEquals(testSheet.getName(), result.getCorporationName());
    Assert.assertEquals(testSheet.getDescription(), result.getDescription());
    Assert.assertEquals((int) testSheet.getMemberCount(), result.getMemberCount());
    Assert.assertEquals((long) testSheet.getShares(), result.getShares());
    Assert.assertEquals((int) testSheet.getHomeStationId(), result.getStationID());
    Assert.assertEquals(testSheet.getTaxRate(), result.getTaxRate(), 0.01);
    Assert.assertEquals(testSheet.getTicker(), result.getTicker());
    Assert.assertEquals(testSheet.getUrl(), result.getUrl());
    Assert.assertEquals(testSheet.getDateFounded()
                                 .getMillis(), result.getDateFounded());
    Assert.assertEquals((int) testSheet.getCreatorId(), result.getCreatorID());
    Assert.assertEquals((int) testSheet.getFactionId(), result.getFactionID());
    Assert.assertEquals(testSheet.getWarEligible(), result.isWarEligible());
    Assert.assertEquals(testIcons.getPx64x64(), result.getPx64x64());
    Assert.assertEquals(testIcons.getPx128x128(), result.getPx128x128());
    Assert.assertEquals(testIcons.getPx256x256(), result.getPx256x256());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_SHEET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_SHEET);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
