package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdStandings200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Standing;
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
public class ESICharacterStandingSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] standingTestData;

  static {
    // Standing test data
    // 0 String standingEntity;
    // 1 int fromID;
    // 2 double standing;
    int size = 50 + TestBase.getRandomInt(50);
    standingTestData = new Object[size][3];
    int standingEntityLen = GetCharactersCharacterIdStandings200Ok.FromTypeEnum.values().length;
    for (int i = 0; i < size; i++) {
      standingTestData[i][0] = GetCharactersCharacterIdStandings200Ok.FromTypeEnum.values()[TestBase.getRandomInt(
          standingEntityLen)];
      standingTestData[i][1] = TestBase.getUniqueRandomInteger();
      standingTestData[i][2] = (float) TestBase.getRandomDouble(10);
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_STANDINGS, 1234L);

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
                                                        .createQuery("DELETE FROM Standing ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CharacterApi.class);
    // Setup asset retrieval mock calls
    List<GetCharactersCharacterIdStandings200Ok> standingList =
        Arrays.stream(standingTestData)
              .map(x -> {
                GetCharactersCharacterIdStandings200Ok nextStanding = new GetCharactersCharacterIdStandings200Ok();
                nextStanding.setFromType((GetCharactersCharacterIdStandings200Ok.FromTypeEnum) x[0]);
                nextStanding.setFromId((Integer) x[1]);
                nextStanding.setStanding((Float) x[2]);
                return nextStanding;
              })
              .collect(Collectors.toList());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdStandings200Ok>> apir = new ApiResponse<>(200, headers, standingList);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdStandingsWithHttpInfo(
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

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Standing> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Standing.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(standingTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < standingTestData.length; i++) {
      Standing nextEl = storedData.get(i);
      Assert.assertEquals(standingTestData[i][0].toString(), nextEl.getStandingEntity());
      Assert.assertEquals((int) (Integer) standingTestData[i][1], nextEl.getFromID());
      Assert.assertEquals((Float) standingTestData[i][2], nextEl.getStanding(), 0.001);
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterStandingSync sync = new ESICharacterStandingSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_STANDINGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_STANDINGS);
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
    int[] modifiedIDs = new int[standingTestData.length];
    for (int i = 0; i < modifiedIDs.length; i++) {
      if (i % 2 == 0)
        modifiedIDs[i] = TestBase.getUniqueRandomInteger();
      else
        modifiedIDs[i] = (int) (Integer) standingTestData[i][1];
    }
    for (int i = 0; i < standingTestData.length; i++) {
      Standing newEl = new Standing(standingTestData[i][0].toString(),
                                    modifiedIDs[i],
                                    (Float) standingTestData[i][2] + 1.0);
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterStandingSync sync = new ESICharacterStandingSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<Standing> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        Standing.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(standingTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < standingTestData.length; i++) {
      Standing nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals(standingTestData[i][0].toString(), nextEl.getStandingEntity());
      Assert.assertEquals(modifiedIDs[i], nextEl.getFromID());
      Assert.assertEquals((Float) standingTestData[i][2] + 1.0, nextEl.getStanding(), 0.001);
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_STANDINGS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_STANDINGS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
