package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.LocationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdShipOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterShip;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ESICharacterShipSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private LocationApi mockEndpoint;
  private long testTime = 1238L;
  private GetCharactersCharacterIdShipOk testShip;

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SHIP_TYPE, 1234L);

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
                                                        .createQuery("DELETE FROM CharacterShip ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    testShip = new GetCharactersCharacterIdShipOk();
    testShip.setShipTypeId(TestBase.getRandomInt());
    testShip.setShipItemId(TestBase.getRandomLong());
    testShip.setShipName(TestBase.getRandomText(50));
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<GetCharactersCharacterIdShipOk> apir = new ApiResponse<>(200, headers, testShip);
    mockEndpoint = EasyMock.createMock(LocationApi.class);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdShipWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getLocationApi())
            .andReturn(mockEndpoint);
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterShipSync sync = new ESICharacterShipSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    CharacterShip result = CharacterShip.get(charSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());
    Assert.assertEquals((int) testShip.getShipTypeId(), result.getShipTypeID());
    Assert.assertEquals((long) testShip.getShipItemId(), result.getShipItemID());
    Assert.assertEquals(testShip.getShipName(), result.getShipName());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SHIP_TYPE);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SHIP_TYPE);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    CharacterShip existing = new CharacterShip(testShip.getShipTypeId() + 1,
                                               testShip.getShipItemId() + 1L,
                                               testShip.getShipName() + "1");
    existing.setup(charSyncAccount, testTime - 1);
    CachedData.update(existing);

    // Perform the sync
    ESICharacterShipSync sync = new ESICharacterShipSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old object was evolved properly
    CharacterShip result = CharacterShip.get(charSyncAccount, testTime - 1);
    Assert.assertEquals(testTime - 1, result.getLifeStart());
    Assert.assertEquals(testTime, result.getLifeEnd());
    Assert.assertEquals(testShip.getShipTypeId() + 1, result.getShipTypeID());
    Assert.assertEquals(testShip.getShipItemId() + 1L, result.getShipItemID());
    Assert.assertEquals(testShip.getShipName() + "1", result.getShipName());

    // Verify updated properly
    result = CharacterShip.get(charSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());
    Assert.assertEquals((int) testShip.getShipTypeId(), result.getShipTypeID());
    Assert.assertEquals((long) testShip.getShipItemId(), result.getShipItemID());
    Assert.assertEquals(testShip.getShipName(), result.getShipName());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SHIP_TYPE);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SHIP_TYPE);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
