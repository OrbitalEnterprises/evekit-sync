package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBlueprints200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Blueprint;
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
public class ESICharacterBlueprintsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] blueprintsTestData;
  private static int[] pages;

  static {
    // Blueprints test data
    // 0 long itemID;
    // 1 long locationID;
    // 2 String locationFlag;
    // 3 int typeID;
    // 4 int quantity;
    // 5 int timeEfficiency;
    // 6 int materialEfficiency;
    // 7 int runs;
    int size = 200 + TestBase.getRandomInt(500);
    blueprintsTestData = new Object[size][8];
    int locationFlagLen = GetCharactersCharacterIdBlueprints200Ok.LocationFlagEnum.values().length;
    for (int i = 0; i < size; i++) {
      blueprintsTestData[i][0] = TestBase.getUniqueRandomLong();
      blueprintsTestData[i][1] = TestBase.getRandomLong();
      blueprintsTestData[i][2] = GetCharactersCharacterIdBlueprints200Ok.LocationFlagEnum.values()[TestBase.getRandomInt(
          locationFlagLen)];
      blueprintsTestData[i][3] = TestBase.getRandomInt();
      blueprintsTestData[i][4] = TestBase.getRandomInt();
      blueprintsTestData[i][5] = TestBase.getRandomInt();
      blueprintsTestData[i][6] = TestBase.getRandomInt();
      blueprintsTestData[i][7] = TestBase.getRandomInt();
    }

    int pageCount = 2 + TestBase.getRandomInt(4);
    pages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      pages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_BLUEPRINTS, 1234L, null);

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
                                                        .createQuery("DELETE FROM Blueprint ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CharacterApi.class);
    // Setup asset retrieval mock calls
    List<GetCharactersCharacterIdBlueprints200Ok> blueprintList =
        Arrays.stream(blueprintsTestData)
              .map(x -> {
                GetCharactersCharacterIdBlueprints200Ok nextBlueprint = new GetCharactersCharacterIdBlueprints200Ok();
                nextBlueprint.setItemId((Long) x[0]);
                nextBlueprint.setLocationId((Long) x[1]);
                nextBlueprint.setLocationFlag((GetCharactersCharacterIdBlueprints200Ok.LocationFlagEnum) x[2]);
                nextBlueprint.setTypeId((Integer) x[3]);
                nextBlueprint.setQuantity((Integer) x[4]);
                nextBlueprint.setTimeEfficiency((Integer) x[5]);
                nextBlueprint.setMaterialEfficiency((Integer) x[6]);
                nextBlueprint.setRuns((Integer) x[7]);
                return nextBlueprint;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < pages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCharactersCharacterIdBlueprints200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                          blueprintList.subList(last,
                                                                                                                pages[i]));
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdBlueprintsWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = pages[i];
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Blueprint> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Blueprint.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(blueprintsTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < blueprintsTestData.length; i++) {
      Blueprint nextEl = storedData.get(i);
      Assert.assertEquals((long) (Long) blueprintsTestData[i][0], nextEl.getItemID());
      Assert.assertEquals((long) (Long) blueprintsTestData[i][1], nextEl.getLocationID());
      Assert.assertEquals(String.valueOf(blueprintsTestData[i][2]), nextEl.getLocationFlag());
      Assert.assertEquals((int) (Integer) blueprintsTestData[i][3], nextEl.getTypeID());
      Assert.assertEquals((int) (Integer) blueprintsTestData[i][4], nextEl.getQuantity());
      Assert.assertEquals((int) (Integer) blueprintsTestData[i][5], nextEl.getTimeEfficiency());
      Assert.assertEquals((int) (Integer) blueprintsTestData[i][6], nextEl.getMaterialEfficiency());
      Assert.assertEquals((int) (Integer) blueprintsTestData[i][7], nextEl.getRuns());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterBlueprintsSync sync = new ESICharacterBlueprintsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_BLUEPRINTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_BLUEPRINTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // Half the entries we insert are purposely not present in the server data so we can test deletion.
    // Compute the modified IDs
    long[] modifiedIDs = new long[blueprintsTestData.length];
    for (int i = 0; i < modifiedIDs.length; i++) {
      if (i % 2 == 0)
        modifiedIDs[i] = TestBase.getUniqueRandomLong();
      else
        modifiedIDs[i] = (long) (Long) blueprintsTestData[i][0];
    }
    for (int i = 0; i < blueprintsTestData.length; i++) {
      Blueprint newEl = new Blueprint(modifiedIDs[i],
                                      (Long) blueprintsTestData[i][1] + 1L,
                                      String.valueOf(blueprintsTestData[i][2]),
                                      (Integer) blueprintsTestData[i][3] + 1,
                                      (Integer) blueprintsTestData[i][4] + 1,
                                      (Integer) blueprintsTestData[i][5] + 1,
                                      (Integer) blueprintsTestData[i][6] + 1,
                                      (Integer) blueprintsTestData[i][7] + 1);
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterBlueprintsSync sync = new ESICharacterBlueprintsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<Blueprint> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        Blueprint.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(blueprintsTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < blueprintsTestData.length; i++) {
      Blueprint nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals(modifiedIDs[i], nextEl.getItemID());
      Assert.assertEquals((Long) blueprintsTestData[i][1] + 1L, nextEl.getLocationID());
      Assert.assertEquals(String.valueOf(blueprintsTestData[i][2]), nextEl.getLocationFlag());
      Assert.assertEquals((Integer) blueprintsTestData[i][3] + 1, nextEl.getTypeID());
      Assert.assertEquals((Integer) blueprintsTestData[i][4] + 1, nextEl.getQuantity());
      Assert.assertEquals((Integer) blueprintsTestData[i][5] + 1, nextEl.getTimeEfficiency());
      Assert.assertEquals((Integer) blueprintsTestData[i][6] + 1, nextEl.getMaterialEfficiency());
      Assert.assertEquals((Integer) blueprintsTestData[i][7] + 1, nextEl.getRuns());
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_BLUEPRINTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_BLUEPRINTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
