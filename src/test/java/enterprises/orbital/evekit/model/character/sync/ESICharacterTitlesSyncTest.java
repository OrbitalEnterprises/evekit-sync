package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdTitles200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterTitle;
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
public class ESICharacterTitlesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] titlesTestData;

  static {
    // Title test data
    // 0 int titleID
    // 1 String titleName
    int size = 50 + TestBase.getRandomInt(50);
    titlesTestData = new Object[size][2];
    for (int i = 0; i < size; i++) {
      titlesTestData[i][0] = TestBase.getUniqueRandomInteger();
      titlesTestData[i][1] = TestBase.getRandomText(50);
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_TITLES, 1234L, null);

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
                                                        .createQuery("DELETE FROM CharacterTitle ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CharacterApi.class);

    // Set up medals
    List<GetCharactersCharacterIdTitles200Ok> titles = Arrays.stream(titlesTestData)
                                                             .map(x -> {
                                                               GetCharactersCharacterIdTitles200Ok newTitle = new GetCharactersCharacterIdTitles200Ok();
                                                               newTitle.setTitleId((int) x[0]);
                                                               newTitle.setName((String) x[1]);
                                                               return newTitle;
                                                             })
                                                             .collect(Collectors.toList());


    // Setup retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdTitles200Ok>> apir = new ApiResponse<>(200, headers, titles);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdTitlesWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
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

    // Retrieve stored titles
    List<CharacterTitle> storedTitles = CachedData.retrieveAll(time,
                                                               (contid, at) -> CharacterTitle.accessQuery(
                                                                   charSyncAccount, contid, 1000, false, at,
                                                                   AttributeSelector.any(), AttributeSelector.any()));

    // Compare against test data
    Assert.assertEquals(testData.length, storedTitles.size());
    for (int i = 0; i < testData.length; i++) {
      CharacterTitle nextTitle = storedTitles.get(i);
      Assert.assertEquals((int) testData[i][0], nextTitle.getTitleID());
      Assert.assertEquals(testData[i][1], nextTitle.getTitleName());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterTitlesSync sync = new ESICharacterTitlesSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, titlesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_TITLES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_TITLES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Create titles which will be modified by update.
    Object[][] newTestData = new Object[titlesTestData.length][2];
    for (int i = 0; i < newTestData.length; i++) {
      newTestData[i][0] = titlesTestData[i][0];
      newTestData[i][1] = TestBase.getRandomText(50);

      CharacterTitle oldTitle = new CharacterTitle((int) newTestData[i][0],
                                                   (String) newTestData[i][1]);

      oldTitle.setup(charSyncAccount, testTime - 1);
      CachedData.update(oldTitle);
    }

    // Perform the sync
    ESICharacterTitlesSync sync = new ESICharacterTitlesSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate(testTime - 1, newTestData);
    verifyDataUpdate(testTime, titlesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_TITLES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_TITLES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
