package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMembertracking200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.MemberLimit;
import enterprises.orbital.evekit.model.corporation.MemberTracking;
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

@SuppressWarnings("Duplicates")
public class ESICorporationMemberTrackingSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] trackingTestData;
  private static int limitTestData;


  static {
    // Member limit test data
    limitTestData = TestBase.getRandomInt();

    // Tracking test data
    // 0 int characterID
    // 1 int baseID
    // 2 long locationID
    // 3 long logoffDateTime
    // 4 long logonDateTime
    // 5 int shipTypeID
    // 6 long startDateTime
    int size = 200 + TestBase.getRandomInt(200);
    trackingTestData = new Object[size][7];
    for (int i = 0; i < size; i++) {
      trackingTestData[i][0] = TestBase.getUniqueRandomInteger();
      trackingTestData[i][1] = TestBase.getRandomInt();
      trackingTestData[i][2] = TestBase.getRandomLong();
      trackingTestData[i][3] = TestBase.getRandomLong();
      trackingTestData[i][4] = TestBase.getRandomLong();
      trackingTestData[i][5] = TestBase.getRandomInt();
      trackingTestData[i][6] = TestBase.getRandomLong();
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_TRACK_MEMBERS, 1234L, null);

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
                                                        .createQuery("DELETE FROM MemberLimit ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MemberTracking ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);

    // Setup limit call
    {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
      ApiResponse<Integer> apir = new ApiResponse<>(200, headers, limitTestData);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdMembersLimitWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Setup member tracking call
    {
      List<GetCorporationsCorporationIdMembertracking200Ok> memberList =
          Arrays.stream(trackingTestData)
                .map(x -> {
                  GetCorporationsCorporationIdMembertracking200Ok nextMember = new GetCorporationsCorporationIdMembertracking200Ok();
                  nextMember.setCharacterId((int) x[0]);
                  nextMember.setBaseId((int) x[1]);
                  nextMember.setLocationId((long) x[2]);
                  nextMember.setLogoffDate(new DateTime(new Date((long) x[3])));
                  nextMember.setLogonDate(new DateTime(new Date((long) x[4])));
                  nextMember.setShipTypeId((int) x[5]);
                  nextMember.setStartDate(new DateTime(new Date((long) x[6])));
                  return nextMember;
                })
                .collect(Collectors.toList());

      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
      ApiResponse<List<GetCorporationsCorporationIdMembertracking200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                                  memberList);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdMembertrackingWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, int testLimit, Object[][] testMembers) throws Exception {
    // Retrieve and check stored limit
    List<MemberLimit> storedLimit = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        MemberLimit.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR));
    Assert.assertEquals(1, storedLimit.size());
    Assert.assertEquals(testLimit, storedLimit.get(0)
                                              .getMemberLimit());

    // Retrieve and check stored member tracking
    List<MemberTracking> storedData = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        MemberTracking.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                   AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                   AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                   AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testMembers.length, storedData.size());

    // Check stored data
    for (int i = 0; i < testMembers.length; i++) {
      MemberTracking nextEl = storedData.get(i);
      Assert.assertEquals((int) testMembers[i][0], nextEl.getCharacterID());
      Assert.assertEquals((int) testMembers[i][1], nextEl.getBaseID());
      Assert.assertEquals((long) testMembers[i][2], nextEl.getLocationID());
      Assert.assertEquals((long) testMembers[i][3], nextEl.getLogoffDateTime());
      Assert.assertEquals((long) testMembers[i][4], nextEl.getLogonDateTime());
      Assert.assertEquals((int) testMembers[i][5], nextEl.getShipTypeID());
      Assert.assertEquals((long) testMembers[i][6], nextEl.getStartDateTime());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationMemberTrackingSync sync = new ESICorporationMemberTrackingSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, limitTestData, trackingTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_TRACK_MEMBERS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_TRACK_MEMBERS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing limit
    int oldLimit = TestBase.getRandomInt();
    {
      MemberLimit newEl = new MemberLimit(oldLimit);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Populate existing trackers.  By construction, half of the data will be deleted on update.
    Object[][] oldData = new Object[trackingTestData.length][7];
    for (int i = 0; i < trackingTestData.length; i++) {
      oldData[i][0] = i % 2 == 0 ? TestBase.getUniqueRandomInteger() : trackingTestData[i][0];
      oldData[i][1] = TestBase.getRandomInt();
      oldData[i][2] = TestBase.getRandomLong();
      oldData[i][3] = TestBase.getRandomLong();
      oldData[i][4] = TestBase.getRandomLong();
      oldData[i][5] = TestBase.getRandomInt();
      oldData[i][6] = TestBase.getRandomLong();

      MemberTracking newEl = new MemberTracking((int) oldData[i][0],
                                                (int) oldData[i][1],
                                                (long) oldData[i][2],
                                                (long) oldData[i][3],
                                                (long) oldData[i][4],
                                                (int) oldData[i][5],
                                                (long) oldData[i][6]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationMemberTrackingSync sync = new ESICorporationMemberTrackingSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    verifyDataUpdate(testTime - 1, oldLimit, oldData);

    // Verify updates.
    verifyDataUpdate(testTime, limitTestData, trackingTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_TRACK_MEMBERS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_TRACK_MEMBERS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
