package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.LocationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdLocationOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterLocation;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ESICharacterLocationSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private LocationApi mockEndpoint;
  private long testTime = 1238L;
  private GetCharactersCharacterIdLocationOk testLocation;

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_LOCATION, 1234L, null);

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
                                                        .createQuery("DELETE FROM CharacterLocation ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    testLocation = new GetCharactersCharacterIdLocationOk();
    testLocation.setSolarSystemId(TestBase.getRandomInt());
    testLocation.setStationId(TestBase.getRandomInt());
    testLocation.setStructureId(TestBase.getRandomLong());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<GetCharactersCharacterIdLocationOk> apir = new ApiResponse<>(200, headers, testLocation);
    mockEndpoint = EasyMock.createMock(LocationApi.class);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdLocationWithHttpInfo(
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
    ESICharacterLocationSync sync = new ESICharacterLocationSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    CharacterLocation result = CharacterLocation.get(charSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());
    Assert.assertEquals((int) testLocation.getSolarSystemId(), result.getSolarSystemID());
    Assert.assertEquals((int) testLocation.getStationId(), result.getStationID());
    Assert.assertEquals((long) testLocation.getStructureId(), result.getStructureID());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_LOCATION);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_LOCATION);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    CharacterLocation existing = new CharacterLocation(testLocation.getSolarSystemId() + 1,
                                                       testLocation.getStationId() + 1,
                                                       testLocation.getStructureId() + 1L);
    existing.setup(charSyncAccount, testTime - 1);
    CachedData.update(existing);

    // Perform the sync
    ESICharacterLocationSync sync = new ESICharacterLocationSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old object was evolved properly
    CharacterLocation result = CharacterLocation.get(charSyncAccount, testTime - 1);
    Assert.assertEquals(testTime - 1, result.getLifeStart());
    Assert.assertEquals(testTime, result.getLifeEnd());
    Assert.assertEquals(testLocation.getSolarSystemId() + 1, result.getSolarSystemID());
    Assert.assertEquals(testLocation.getStationId() + 1, result.getStationID());
    Assert.assertEquals(testLocation.getStructureId() + 1L, result.getStructureID());

    // Verify updated properly
    result = CharacterLocation.get(charSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());
    Assert.assertEquals((int) testLocation.getSolarSystemId(), result.getSolarSystemID());
    Assert.assertEquals((int) testLocation.getStationId(), result.getStationID());
    Assert.assertEquals((long) testLocation.getStructureId(), result.getStructureID());

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_LOCATION);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_LOCATION);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
