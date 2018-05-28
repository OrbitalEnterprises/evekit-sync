package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.LoyaltyApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdLoyaltyPoints200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.LoyaltyPoints;
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
public class ESICharacterLoyaltyPointsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private LoyaltyApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] lpTestData;

  static {
    // LP test data
    // 0 int corporationID
    // 1 int loyaltyPoints
    int size = 50 + TestBase.getRandomInt(50);
    lpTestData = new Object[size][2];
    for (int i = 0; i < size; i++) {
      lpTestData[i][0] = TestBase.getUniqueRandomInteger();
      lpTestData[i][1] = TestBase.getRandomInt();
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_LOYALTY, 1234L, null);

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
                                                        .createQuery("DELETE FROM LoyaltyPoints ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(LoyaltyApi.class);

    // Set up LPs
    List<GetCharactersCharacterIdLoyaltyPoints200Ok> fittings = Arrays.stream(lpTestData)
                                                                      .map(x -> {
                                                                        GetCharactersCharacterIdLoyaltyPoints200Ok newLP = new GetCharactersCharacterIdLoyaltyPoints200Ok();
                                                                        newLP.setCorporationId((int) x[0]);
                                                                        newLP.setLoyaltyPoints((int) x[1]);
                                                                        return newLP;
                                                                      })
                                                                      .collect(Collectors.toList());

    // Setup retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdLoyaltyPoints200Ok>> apir = new ApiResponse<>(200, headers, fittings);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdLoyaltyPointsWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getLoyaltyApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {

    // Retrieve stored LPs
    List<LoyaltyPoints> storedLPs = CachedData.retrieveAll(time,
                                                           (contid, at) -> LoyaltyPoints.accessQuery(
                                                               charSyncAccount, contid, 1000, false, at,
                                                               AttributeSelector.any(), AttributeSelector.any()));

    // Compare against test data
    Assert.assertEquals(testData.length, storedLPs.size());
    for (int i = 0; i < testData.length; i++) {
      LoyaltyPoints nextLP = storedLPs.get(i);
      Assert.assertEquals((int) testData[i][0], nextLP.getCorporationID());
      Assert.assertEquals((int) testData[i][1], nextLP.getLoyaltyPoints());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterLoyaltyPointsSync sync = new ESICharacterLoyaltyPointsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, lpTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_LOYALTY);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_LOYALTY);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Create LPs which will be modified by update.
    Object[][] newTestData = new Object[lpTestData.length][2];
    for (int i = 0; i < newTestData.length; i++) {
      newTestData[i][0] = lpTestData[i][0];
      newTestData[i][1] = (int) lpTestData[i][1] + 1;

      LoyaltyPoints oldLP = new LoyaltyPoints((int) newTestData[i][0],
                                              (int) newTestData[i][1]);
      oldLP.setup(charSyncAccount, testTime - 1);
      CachedData.update(oldLP);
    }

    // Perform the sync
    ESICharacterLoyaltyPointsSync sync = new ESICharacterLoyaltyPointsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates which will also verify that all old data were properly end of life
    verifyDataUpdate(testTime - 1, newTestData);
    verifyDataUpdate(testTime, lpTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_LOYALTY);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_LOYALTY);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
