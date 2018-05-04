package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.FactionWarfareApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdFwStatsKills;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdFwStatsOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdFwStatsVictoryPoints;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.FacWarStats;
import enterprises.orbital.evekit.model.corporation.sync.ESICorporationFacWarStatsSync;
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

public class ESICharacterFacWarStatsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private FactionWarfareApi mockEndpoint;
  private long testTime = 1238L;
  private GetCorporationsCorporationIdFwStatsOk testSheet;

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_FACTION_WAR, 1234L, null);

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
                                                        .createQuery("DELETE FROM FacWarStats ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    testSheet = new GetCorporationsCorporationIdFwStatsOk();
    testSheet.setFactionId(TestBase.getRandomInt());
    testSheet.setEnlistedOn(new DateTime(new Date(TestBase.getRandomLong())));
    testSheet.setPilots(TestBase.getRandomInt());
    GetCorporationsCorporationIdFwStatsKills kills = new GetCorporationsCorporationIdFwStatsKills();
    testSheet.setKills(kills);
    GetCorporationsCorporationIdFwStatsVictoryPoints victoryPoints = new GetCorporationsCorporationIdFwStatsVictoryPoints();
    testSheet.setVictoryPoints(victoryPoints);
    kills.setLastWeek(TestBase.getRandomInt());
    kills.setTotal(TestBase.getRandomInt());
    kills.setYesterday(TestBase.getRandomInt());
    victoryPoints.setLastWeek(TestBase.getRandomInt());
    victoryPoints.setTotal(TestBase.getRandomInt());
    victoryPoints.setYesterday(TestBase.getRandomInt());

    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<GetCorporationsCorporationIdFwStatsOk> apir = new ApiResponse<>(200, headers, testSheet);
    mockEndpoint = EasyMock.createMock(FactionWarfareApi.class);
    EasyMock.expect(mockEndpoint.getCorporationsCorporationIdFwStatsWithHttpInfo(
        EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getFactionWarfareApi())
            .andReturn(mockEndpoint);
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationFacWarStatsSync sync = new ESICorporationFacWarStatsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    FacWarStats result = FacWarStats.get(corpSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());

    Assert.assertEquals(0, result.getCurrentRank());
    Assert.assertEquals(testSheet.getEnlistedOn()
                                 .getMillis(), result.getEnlisted());
    Assert.assertEquals(testSheet.getFactionId()
                                 .intValue(), result.getFactionID());
    Assert.assertEquals(0, result.getHighestRank());
    Assert.assertEquals(testSheet.getPilots()
                                 .intValue(), result.getPilots());
    Assert.assertEquals(testSheet.getKills()
                                 .getLastWeek()
                                 .intValue(), result.getKillsLastWeek());
    Assert.assertEquals(testSheet.getKills()
                                 .getTotal()
                                 .intValue(), result.getKillsTotal());
    Assert.assertEquals(testSheet.getKills()
                                 .getYesterday()
                                 .intValue(), result.getKillsYesterday());
    Assert.assertEquals(testSheet.getPilots().intValue(), result.getPilots());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getLastWeek()
                                 .intValue(), result.getVictoryPointsLastWeek());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getTotal()
                                 .intValue(), result.getVictoryPointsTotal());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getYesterday()
                                 .intValue(), result.getVictoryPointsYesterday());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_FACTION_WAR);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_FACTION_WAR);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    FacWarStats existing = new FacWarStats(0,
                                           testSheet.getEnlistedOn()
                                                    .getMillis() + 1L,
                                           testSheet.getFactionId() + 1,
                                           0,
                                           testSheet.getKills()
                                                    .getLastWeek() + 1,
                                           testSheet.getKills()
                                                    .getTotal() + 1,
                                           testSheet.getKills()
                                                    .getYesterday() + 1,
                                           testSheet.getPilots() + 1,
                                           testSheet.getVictoryPoints()
                                                    .getLastWeek() + 1,
                                           testSheet.getVictoryPoints()
                                                    .getTotal() + 1,
                                           testSheet.getVictoryPoints()
                                                    .getYesterday() + 1);
    existing.setup(corpSyncAccount, testTime - 1);
    CachedData.update(existing);

    // Perform the sync
    ESICorporationFacWarStatsSync sync = new ESICorporationFacWarStatsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old object was evolved properly
    FacWarStats result = FacWarStats.get(corpSyncAccount, testTime - 1);
    Assert.assertEquals(testTime - 1, result.getLifeStart());
    Assert.assertEquals(testTime, result.getLifeEnd());

    Assert.assertEquals(0, result.getCurrentRank());
    Assert.assertEquals(testSheet.getEnlistedOn()
                                 .getMillis() + 1L, result.getEnlisted());
    Assert.assertEquals(testSheet.getFactionId() + 1, result.getFactionID());
    Assert.assertEquals(0, result.getHighestRank());
    Assert.assertEquals(testSheet.getKills()
                                 .getLastWeek() + 1, result.getKillsLastWeek());
    Assert.assertEquals(testSheet.getKills()
                                 .getTotal() + 1, result.getKillsTotal());
    Assert.assertEquals(testSheet.getKills()
                                 .getYesterday() + 1, result.getKillsYesterday());
    Assert.assertEquals(testSheet.getPilots() + 1, result.getPilots());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getLastWeek() + 1, result.getVictoryPointsLastWeek());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getTotal() + 1, result.getVictoryPointsTotal());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getYesterday() + 1, result.getVictoryPointsYesterday());

    // Verify updated properly
    result = FacWarStats.get(corpSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());

    Assert.assertEquals(0, result.getCurrentRank());
    Assert.assertEquals(testSheet.getEnlistedOn()
                                 .getMillis(), result.getEnlisted());
    Assert.assertEquals(testSheet.getFactionId()
                                 .intValue(), result.getFactionID());
    Assert.assertEquals(0, result.getHighestRank());
    Assert.assertEquals(testSheet.getKills()
                                 .getLastWeek()
                                 .intValue(), result.getKillsLastWeek());
    Assert.assertEquals(testSheet.getKills()
                                 .getTotal()
                                 .intValue(), result.getKillsTotal());
    Assert.assertEquals(testSheet.getKills()
                                 .getYesterday()
                                 .intValue(), result.getKillsYesterday());
    Assert.assertEquals(testSheet.getPilots()
                                 .intValue(), result.getPilots());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getLastWeek()
                                 .intValue(), result.getVictoryPointsLastWeek());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getTotal()
                                 .intValue(), result.getVictoryPointsTotal());
    Assert.assertEquals(testSheet.getVictoryPoints()
                                 .getYesterday()
                                 .intValue(), result.getVictoryPointsYesterday());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_FACTION_WAR);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_FACTION_WAR);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
