package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.FittingsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdFittings200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdFittingsItem;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.Fitting;
import enterprises.orbital.evekit.model.character.FittingItem;
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
public class ESICharacterFittingsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private FittingsApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] fittingsTestData;

  static {
    // Fitting test data
    // 0 int fittingID
    // 1 String name
    // 2 String description
    // 3 int shipTypeID
    // 4 Object[][] items
    int size = 50 + TestBase.getRandomInt(50);
    fittingsTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      fittingsTestData[i][0] = TestBase.getUniqueRandomInteger();
      fittingsTestData[i][1] = TestBase.getRandomText(50);
      fittingsTestData[i][2] = TestBase.getRandomText(50);
      fittingsTestData[i][3] = TestBase.getRandomInt();

      {
        // items
        int itemCount = 10 + TestBase.getRandomInt(10);
        Object[][] itemData = new Object[itemCount][3];
        fittingsTestData[i][4] = itemData;
        for (int j = 0; j < itemCount; j++) {
          itemData[j][0] = TestBase.getUniqueRandomInteger();
          itemData[j][1] = TestBase.getRandomInt();
          itemData[j][2] = TestBase.getRandomInt();
        }
      }
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_FITTINGS, 1234L, null);

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
                                                        .createQuery("DELETE FROM Fitting ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM FittingItem ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(FittingsApi.class);

    // Set up channels
    List<GetCharactersCharacterIdFittings200Ok> fittings = Arrays.stream(fittingsTestData)
                                                                 .map(x -> {
                                                                   GetCharactersCharacterIdFittings200Ok newFitting = new GetCharactersCharacterIdFittings200Ok();
                                                                   newFitting.setFittingId((int) x[0]);
                                                                   newFitting.setName((String) x[1]);
                                                                   newFitting.setDescription((String) x[2]);
                                                                   newFitting.setShipTypeId((int) x[3]);

                                                                   newFitting.getItems()
                                                                             .addAll(
                                                                                 Arrays.stream((Object[][]) x[4])
                                                                                       .map(y -> {
                                                                                         GetCharactersCharacterIdFittingsItem newItem = new GetCharactersCharacterIdFittingsItem();
                                                                                         newItem.setTypeId((int) y[0]);
                                                                                         newItem.setFlag((int) y[1]);
                                                                                         newItem.setQuantity(
                                                                                             (int) y[2]);
                                                                                         return newItem;
                                                                                       })
                                                                                       .collect(Collectors.toList()));

                                                                   return newFitting;
                                                                 })
                                                                 .collect(Collectors.toList());

    // Setup retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdFittings200Ok>> apir = new ApiResponse<>(200, headers, fittings);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdFittingsWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getFittingsApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {

    // Retrieve stored fittings
    List<Fitting> storedFittings = CachedData.retrieveAll(time,
                                                          (contid, at) -> Fitting.accessQuery(
                                                              charSyncAccount, contid, 1000, false, at,
                                                              AttributeSelector.any(), AttributeSelector.any(),
                                                              AttributeSelector.any(), AttributeSelector.any()));

    // Compare against test data
    Assert.assertEquals(testData.length, storedFittings.size());
    for (int i = 0; i < testData.length; i++) {
      Fitting nextFitting = storedFittings.get(i);
      Assert.assertEquals((int) testData[i][0], nextFitting.getFittingID());
      Assert.assertEquals(testData[i][1], nextFitting.getName());
      Assert.assertEquals(testData[i][2], nextFitting.getDescription());
      Assert.assertEquals((int) testData[i][3], nextFitting.getShipTypeID());
    }

    // Retrieved stored items
    List<FittingItem> storedItems = CachedData.retrieveAll(time,
                                                           (contid, at) -> FittingItem.accessQuery(
                                                               charSyncAccount, contid, 1000, false, at,
                                                               AttributeSelector.any(), AttributeSelector.any(),
                                                               AttributeSelector.any(), AttributeSelector.any()));

    // Compare against test data
    int itemCount = Arrays.stream(testData)
                          .map(x -> ((Object[][]) x[4]).length)
                          .reduce(0, Integer::sum);
    Assert.assertEquals(itemCount, storedItems.size());
    for (int i = 0, j = 0; i < testData.length; i++) {
      for (Object[] next : (Object[][]) testData[i][4]) {
        FittingItem nextItem = storedItems.get(j++);
        Assert.assertEquals((int) testData[i][0], nextItem.getFittingID());
        Assert.assertEquals((int) next[0], nextItem.getTypeID());
        Assert.assertEquals((int) next[1], nextItem.getFlag());
        Assert.assertEquals((int) next[2], nextItem.getQuantity());
      }
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterFittingsSync sync = new ESICharacterFittingsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, fittingsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_FITTINGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_FITTINGS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Create fittings which will be modified by update.
    Object[][] newTestData = new Object[fittingsTestData.length][5];
    for (int i = 0; i < newTestData.length; i++) {
      newTestData[i][0] = fittingsTestData[i][0];
      newTestData[i][1] = fittingsTestData[i][1] + "1";
      newTestData[i][2] = fittingsTestData[i][2] + "1";
      newTestData[i][3] = (int) fittingsTestData[i][3] + 1;

      Fitting oldFitting = new Fitting((int) newTestData[i][0],
                                       (String) newTestData[i][1],
                                       (String) newTestData[i][2],
                                       (int) newTestData[i][3]);
      oldFitting.setup(charSyncAccount, testTime - 1);
      CachedData.update(oldFitting);

      {
        // Items
        Object[][] oldData = (Object[][]) fittingsTestData[i][4];
        int itemCount = oldData.length;
        Object[][] itemData = new Object[itemCount][3];
        newTestData[i][4] = itemData;
        for (int j = 0; j < itemCount; j++) {
          itemData[j][0] = oldData[j][0];
          itemData[j][1] = (int) oldData[j][1] + 1;
          itemData[j][2] = (int) oldData[j][2] + 1;

          FittingItem oldItem = new FittingItem((int) newTestData[i][0],
                                                (int) itemData[j][0],
                                                (int) itemData[j][1],
                                                (int) itemData[j][2]);
          oldItem.setup(charSyncAccount, testTime - 1);
          CachedData.update(oldItem);
        }
      }

    }

    // Perform the sync
    ESICharacterFittingsSync sync = new ESICharacterFittingsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates which will also verify that all old data were properly end of life
    verifyDataUpdate(testTime - 1, newTestData);
    verifyDataUpdate(testTime, fittingsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_FITTINGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_FITTINGS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
