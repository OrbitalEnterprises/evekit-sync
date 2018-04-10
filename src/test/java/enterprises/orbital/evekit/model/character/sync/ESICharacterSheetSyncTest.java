package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheet;
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

public class ESICharacterSheetSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;
  private GetCharactersCharacterIdOk testSheet;

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SHEET, 1234L, null);

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
                                                        .createQuery("DELETE FROM CharacterSheet ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    testSheet = new GetCharactersCharacterIdOk();
    testSheet.setName(TestBase.getRandomText(50));
    testSheet.setCorporationId(TestBase.getRandomInt());
    testSheet.setRaceId(TestBase.getRandomInt());
    testSheet.setBirthday(new DateTime(new Date(TestBase.getRandomLong())));
    testSheet.setBloodlineId(TestBase.getRandomInt());
    testSheet.setAncestryId(TestBase.getRandomInt());
    testSheet.setGender(GetCharactersCharacterIdOk.GenderEnum.MALE);
    testSheet.setAllianceId(TestBase.getRandomInt());
    testSheet.setFactionId(TestBase.getRandomInt());
    testSheet.setDescription(TestBase.getRandomText(50));
    testSheet.setSecurityStatus(TestBase.getRandomFloat(10));
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<GetCharactersCharacterIdOk> apir = new ApiResponse<>(200, headers, testSheet);
    mockEndpoint = EasyMock.createMock(CharacterApi.class);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(mockEndpoint);
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterSheetSync sync = new ESICharacterSheetSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    CharacterSheet result = CharacterSheet.get(charSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());
    Assert.assertEquals(charSyncAccount.getEveCharacterID(), result.getCharacterID());
    Assert.assertEquals(testSheet.getName(), result.getName());
    Assert.assertEquals((int) testSheet.getCorporationId(), result.getCorporationID());
    Assert.assertEquals((int) testSheet.getRaceId(), result.getRaceID());
    Assert.assertEquals(testSheet.getBirthday()
                                 .getMillis(), result.getDoB());
    Assert.assertEquals((int) testSheet.getBloodlineId(), result.getBloodlineID());
    Assert.assertEquals((int) testSheet.getAncestryId(), result.getAncestryID());
    Assert.assertEquals(testSheet.getGender()
                                 .toString(), result.getGender());
    Assert.assertEquals((int) testSheet.getAllianceId(), result.getAllianceID());
    Assert.assertEquals((int) testSheet.getFactionId(), result.getFactionID());
    Assert.assertEquals(testSheet.getDescription(), result.getDescription());
    Assert.assertEquals(testSheet.getSecurityStatus(), result.getSecurityStatus(), 0.001);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SHEET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SHEET);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    CharacterSheet existing = new CharacterSheet(charSyncAccount.getEveCharacterID(),
                                                 testSheet.getName() + "1",
                                                 testSheet.getCorporationId() + 1,
                                                 testSheet.getRaceId() + 1,
                                                 testSheet.getBirthday()
                                                          .getMillis(),
                                                 testSheet.getBloodlineId() + 1,
                                                 testSheet.getAncestryId() + 1,
                                                 testSheet.getGender()
                                                          .toString(),
                                                 testSheet.getAllianceId() + 1,
                                                 testSheet.getFactionId() + 1,
                                                 testSheet.getDescription() + "1",
                                                 testSheet.getSecurityStatus());
    existing.setup(charSyncAccount, testTime - 1);
    CachedData.update(existing);

    // Perform the sync
    ESICharacterSheetSync sync = new ESICharacterSheetSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old object was evolved properly
    CharacterSheet result = CharacterSheet.get(charSyncAccount, testTime - 1);
    Assert.assertEquals(testTime - 1, result.getLifeStart());
    Assert.assertEquals(testTime, result.getLifeEnd());
    Assert.assertEquals(charSyncAccount.getEveCharacterID(), result.getCharacterID());
    Assert.assertEquals(testSheet.getName() + "1", result.getName());
    Assert.assertEquals(testSheet.getCorporationId() + 1, result.getCorporationID());
    Assert.assertEquals(testSheet.getRaceId() + 1, result.getRaceID());
    Assert.assertEquals(testSheet.getBirthday()
                                 .getMillis(), result.getDoB());
    Assert.assertEquals(testSheet.getBloodlineId() + 1, result.getBloodlineID());
    Assert.assertEquals(testSheet.getAncestryId() + 1, result.getAncestryID());
    Assert.assertEquals(testSheet.getGender()
                                 .toString(), result.getGender());
    Assert.assertEquals(testSheet.getAllianceId() + 1, result.getAllianceID());
    Assert.assertEquals(testSheet.getFactionId() + 1, result.getFactionID());
    Assert.assertEquals(testSheet.getDescription() + "1", result.getDescription());
    Assert.assertEquals(testSheet.getSecurityStatus(), result.getSecurityStatus(), 0.001);

    // Verify updated properly
    result = CharacterSheet.get(charSyncAccount, testTime);
    Assert.assertEquals(testTime, result.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, result.getLifeEnd());
    Assert.assertEquals(charSyncAccount.getEveCharacterID(), result.getCharacterID());
    Assert.assertEquals(testSheet.getName(), result.getName());
    Assert.assertEquals((int) testSheet.getCorporationId(), result.getCorporationID());
    Assert.assertEquals((int) testSheet.getRaceId(), result.getRaceID());
    Assert.assertEquals(testSheet.getBirthday()
                                 .getMillis(), result.getDoB());
    Assert.assertEquals((int) testSheet.getBloodlineId(), result.getBloodlineID());
    Assert.assertEquals((int) testSheet.getAncestryId(), result.getAncestryID());
    Assert.assertEquals(testSheet.getGender()
                                 .toString(), result.getGender());
    Assert.assertEquals((int) testSheet.getAllianceId(), result.getAllianceID());
    Assert.assertEquals((int) testSheet.getFactionId(), result.getFactionID());
    Assert.assertEquals(testSheet.getDescription(), result.getDescription());
    Assert.assertEquals(testSheet.getSecurityStatus(), result.getSecurityStatus(), 0.001);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SHEET);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SHEET);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
