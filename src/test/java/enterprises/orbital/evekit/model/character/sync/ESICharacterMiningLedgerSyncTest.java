package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMining200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.MiningLedger;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
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
public class ESICharacterMiningLedgerSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private IndustryApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] mlTestData;
  private static int[] mlPages;

  static {
    // MiningLedger test data
    // 0 long date;
    // 1 int solarSystemID;
    // 2 int typeID;
    // 3 long quantity;
    int size = 100 + TestBase.getRandomInt(100);
    mlTestData = new Object[size][4];
    for (int i = 0; i < size; i++) {
      // Note that date must be on a day boundary, hence the multiplication
      // by the number of milliseconds in a day.
      LocalDate dt = LocalDate.fromDateFields(new Date(TestBase.getUniqueRandomLong()));
      mlTestData[i][0] = dt.toDate().getTime();
      mlTestData[i][1] = TestBase.getRandomInt();
      mlTestData[i][2] = TestBase.getRandomInt();
      mlTestData[i][3] = TestBase.getRandomLong();
    }

    // Divide data into pages
    int pageCount = 3 + TestBase.getRandomInt(3);
    mlPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--) {
      mlPages[i] = size - (pageCount - i - 1) * (size / pageCount);
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MINING, 1234L, null);

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
                                                        .createQuery("DELETE FROM MiningLedger ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(IndustryApi.class);

    // Set up LPs
    List<GetCharactersCharacterIdMining200Ok> mlList = Arrays.stream(mlTestData)
                                                             .map(x -> {
                                                               GetCharactersCharacterIdMining200Ok newML = new GetCharactersCharacterIdMining200Ok();
                                                               newML.setDate(
                                                                   LocalDate.fromDateFields(new Date((long) x[0])));
                                                               newML.setSolarSystemId((int) x[1]);
                                                               newML.setTypeId((int) x[2]);
                                                               newML.setQuantity((long) x[3]);
                                                               return newML;
                                                             })
                                                             .collect(Collectors.toList());

    // Setup retrieval mock calls
    int last = 0;
    for (int i = 0; i < mlPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(mlPages.length));
      ApiResponse<List<GetCharactersCharacterIdMining200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                      mlList.subList(last,
                                                                                                     mlPages[i]));
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdMiningWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(apir);
      last = mlPages[i];
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getIndustryApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {

    // Retrieve stored MLs
    List<MiningLedger> storedMLs = CachedData.retrieveAll(time,
                                                          (contid, at) -> MiningLedger.accessQuery(
                                                              charSyncAccount, contid, 1000, false, at,
                                                              AttributeSelector.any(),
                                                              AttributeSelector.any(),
                                                              AttributeSelector.any(),
                                                              AttributeSelector.any()));

    // Compare against test data
    Assert.assertEquals(testData.length, storedMLs.size());
    for (int i = 0; i < testData.length; i++) {
      MiningLedger nextLP = storedMLs.get(i);
      Assert.assertEquals((long) testData[i][0], nextLP.getDate());
      Assert.assertEquals((int) testData[i][1], nextLP.getSolarSystemID());
      Assert.assertEquals((int) testData[i][2], nextLP.getTypeID());
      Assert.assertEquals((long) testData[i][3], nextLP.getQuantity());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterMiningLedgerSync sync = new ESICharacterMiningLedgerSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, mlTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MINING);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MINING);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Create MLs which will be modified by update.
    Object[][] newTestData = new Object[mlTestData.length][4];
    for (int i = 0; i < newTestData.length; i++) {
      newTestData[i][0] = mlTestData[i][0];
      newTestData[i][1] = mlTestData[i][1];
      newTestData[i][2] = mlTestData[i][2];
      newTestData[i][3] = (long) mlTestData[i][3] + 1;

      MiningLedger oldLP = new MiningLedger((long) newTestData[i][0],
                                            (int) newTestData[i][1],
                                            (int) newTestData[i][2],
                                            (long) newTestData[i][3]);
      oldLP.setup(charSyncAccount, testTime - 1);
      CachedData.update(oldLP);
    }

    // Perform the sync
    ESICharacterMiningLedgerSync sync = new ESICharacterMiningLedgerSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates which will also verify that all old data were properly end of life
    verifyDataUpdate(testTime - 1, newTestData);
    verifyDataUpdate(testTime, mlTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MINING);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MINING);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
