package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMedals200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMedalsGraphic;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterMedal;
import enterprises.orbital.evekit.model.character.CharacterMedalGraphic;
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
public class ESICharacterMedalsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] medalsTestData;

  static {
    // Medal test data
    // 0 String description
    // 1 int medalID
    // 2 String title
    // 3 int corporationID
    // 4 long issued
    // 5 int issuerID
    // 6 String reason
    // 7 String status
    // 8 Object[][] CharacterMedalGraphic data
    int size = 50 + TestBase.getRandomInt(50);
    int statusLen = GetCharactersCharacterIdMedals200Ok.StatusEnum.values().length;
    medalsTestData = new Object[size][9];
    for (int i = 0; i < size; i++) {
      medalsTestData[i][0] = TestBase.getRandomText(1000);
      medalsTestData[i][1] = TestBase.getUniqueRandomInteger();
      medalsTestData[i][2] = TestBase.getRandomText(50);
      medalsTestData[i][3] = TestBase.getRandomInt();
      medalsTestData[i][4] = TestBase.getUniqueRandomLong();
      medalsTestData[i][5] = TestBase.getRandomInt();
      medalsTestData[i][6] = TestBase.getRandomText(50);
      medalsTestData[i][7] = GetCharactersCharacterIdMedals200Ok.StatusEnum.values()[TestBase.getRandomInt(statusLen)];

      // Character medal graphics data
      // 0 int medalID
      // 1 long issued
      // 2 int part
      // 3 int layer
      // 4 String graphic
      // 5 int color
      int grfs = 3 + TestBase.getRandomInt(3);
      Object[][] graphics = new Object[grfs][6];
      medalsTestData[i][8] = graphics;
      for (int j = 0; j < grfs; j++) {
        graphics[j][0] = medalsTestData[i][1];
        graphics[j][1] = medalsTestData[i][4];
        graphics[j][2] = TestBase.getUniqueRandomInteger();
        graphics[j][3] = TestBase.getUniqueRandomInteger();
        graphics[j][4] = TestBase.getRandomText(50);
        graphics[j][5] = TestBase.getRandomInt();
      }
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MEDALS, 1234L, null);

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
                                                        .createQuery("DELETE FROM CharacterMedal ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CharacterMedalGraphic ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CharacterApi.class);

    // Set up medals
    List<GetCharactersCharacterIdMedals200Ok> medals = Arrays.stream(medalsTestData)
                                                             .map(x -> {
                                                               GetCharactersCharacterIdMedals200Ok newMedal = new GetCharactersCharacterIdMedals200Ok();
                                                               newMedal.setDescription((String) x[0]);
                                                               newMedal.setMedalId((int) x[1]);
                                                               newMedal.setTitle((String) x[2]);
                                                               newMedal.setCorporationId((int) x[3]);
                                                               newMedal.setDate(new DateTime(new Date((long) x[4])));
                                                               newMedal.setIssuerId((int) x[5]);
                                                               newMedal.setReason((String) x[6]);
                                                               newMedal.setStatus(
                                                                   (GetCharactersCharacterIdMedals200Ok.StatusEnum) x[7]);

                                                               Object[][] graphics = (Object[][]) x[8];
                                                               for (Object[] next : graphics) {
                                                                 GetCharactersCharacterIdMedalsGraphic newGraphic = new GetCharactersCharacterIdMedalsGraphic();
                                                                 newGraphic.setPart((int) next[2]);
                                                                 newGraphic.setLayer((int) next[3]);
                                                                 newGraphic.setGraphic((String) next[4]);
                                                                 newGraphic.setColor((int) next[5]);
                                                                 newMedal.getGraphics()
                                                                         .add(newGraphic);
                                                               }

                                                               return newMedal;
                                                             })
                                                             .collect(Collectors.toList());


    // Setup retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdMedals200Ok>> apir = new ApiResponse<>(200, headers, medals);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdMedalsWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {

    // Retrieve stored medals
    List<CharacterMedal> storedMedals = CachedData.retrieveAll(time,
                                                               (contid, at) -> CharacterMedal.accessQuery(
                                                                   charSyncAccount, contid, 1000, false, at,
                                                                   AttributeSelector.any(), AttributeSelector.any(),
                                                                   AttributeSelector.any(), AttributeSelector.any(),
                                                                   AttributeSelector.any(), AttributeSelector.any(),
                                                                   AttributeSelector.any(), AttributeSelector.any()));

    // Retrieve stored graphics
    List<CharacterMedalGraphic> storedMedalGraphics = CachedData.retrieveAll(time,
                                                                             (contid, at) -> CharacterMedalGraphic.accessQuery(
                                                                                 charSyncAccount, contid, 1000, false,
                                                                                 at,
                                                                                 AttributeSelector.any(),
                                                                                 AttributeSelector.any(),
                                                                                 AttributeSelector.any(),
                                                                                 AttributeSelector.any(),
                                                                                 AttributeSelector.any(),
                                                                                 AttributeSelector.any()));

    // Compare against test data
    Assert.assertEquals(testData.length, storedMedals.size());
    int j = 0;
    for (int i = 0; i < testData.length; i++) {
      CharacterMedal nextMedal = storedMedals.get(i);
      Assert.assertEquals(testData[i][0], nextMedal.getDescription());
      Assert.assertEquals((int) testData[i][1], nextMedal.getMedalID());
      Assert.assertEquals(testData[i][2], nextMedal.getTitle());
      Assert.assertEquals((int) testData[i][3], nextMedal.getCorporationID());
      Assert.assertEquals((long) testData[i][4], nextMedal.getIssued());
      Assert.assertEquals((int) testData[i][5], nextMedal.getIssuerID());
      Assert.assertEquals(testData[i][6], nextMedal.getReason());
      Assert.assertEquals(String.valueOf(testData[i][7]), nextMedal.getStatus());

      Object[][] graphics = (Object[][]) testData[i][8];
      for (Object[] graphic : graphics) {
        CharacterMedalGraphic nextGraphic = storedMedalGraphics.get(j++);
        Assert.assertEquals((int) graphic[0], nextGraphic.getMedalID());
        Assert.assertEquals((long) graphic[1], nextGraphic.getIssued());
        Assert.assertEquals((int) graphic[2], nextGraphic.getPart());
        Assert.assertEquals((int) graphic[3], nextGraphic.getLayer());
        Assert.assertEquals(graphic[4], nextGraphic.getGraphic());
        Assert.assertEquals((int) graphic[5], nextGraphic.getColor());
      }
    }
    Assert.assertEquals(j, storedMedalGraphics.size());
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterMedalsSync sync = new ESICharacterMedalsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, medalsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MEDALS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MEDALS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Create a set of existing medals that will already exist during sync.  Since medals are
    // immutable, these should remain after the sync.
    int newSize = 20 + TestBase.getRandomInt(20);
    int statusLen = GetCharactersCharacterIdMedals200Ok.StatusEnum.values().length;
    Object[][] newTestData = new Object[newSize][9];
    for (int i = 0; i < newSize; i++) {
      newTestData[i][0] = TestBase.getRandomText(1000);
      newTestData[i][1] = TestBase.getUniqueRandomInteger();
      newTestData[i][2] = TestBase.getRandomText(50);
      newTestData[i][3] = TestBase.getRandomInt();
      newTestData[i][4] = TestBase.getUniqueRandomLong();
      newTestData[i][5] = TestBase.getRandomInt();
      newTestData[i][6] = TestBase.getRandomText(50);
      newTestData[i][7] = GetCharactersCharacterIdMedals200Ok.StatusEnum.values()[TestBase.getRandomInt(statusLen)];

      CharacterMedal oldMedal = new CharacterMedal((String) newTestData[i][0],
                                                   (int) newTestData[i][1],
                                                   (String) newTestData[i][2],
                                                   (int) newTestData[i][3],
                                                   (long) newTestData[i][4],
                                                   (int) newTestData[i][5],
                                                   (String) newTestData[i][6],
                                                   String.valueOf(newTestData[i][7]));

      oldMedal.setup(charSyncAccount, testTime - 1);
      CachedData.update(oldMedal);

      int grfs = 3 + TestBase.getRandomInt(3);
      Object[][] graphics = new Object[grfs][6];
      newTestData[i][8] = graphics;
      for (int j = 0; j < grfs; j++) {
        graphics[j][0] = newTestData[i][1];
        graphics[j][1] = newTestData[i][4];
        graphics[j][2] = TestBase.getUniqueRandomInteger();
        graphics[j][3] = TestBase.getUniqueRandomInteger();
        graphics[j][4] = TestBase.getRandomText(50);
        graphics[j][5] = TestBase.getRandomInt();

        CharacterMedalGraphic oldGraphic = new CharacterMedalGraphic((int) graphics[j][0],
                                                                     (long) graphics[j][1],
                                                                     (int) graphics[j][2],
                                                                     (int) graphics[j][3],
                                                                     (String) graphics[j][4],
                                                                     (int) graphics[j][5]);

        oldGraphic.setup(charSyncAccount, testTime - 1);
        CachedData.update(oldGraphic);

      }
    }

    // Perform the sync
    ESICharacterMedalsSync sync = new ESICharacterMedalsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates which will also verify that all old alliances were properly end of life
    Object[][] allTestData = new Object[newSize + medalsTestData.length][9];
    System.arraycopy(newTestData, 0, allTestData, 0, newSize);
    System.arraycopy(medalsTestData, 0, allTestData, newSize, medalsTestData.length);
    verifyDataUpdate(testTime, allTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MEDALS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MEDALS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
