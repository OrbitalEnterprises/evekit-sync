package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ClonesApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdClonesHomeLocation;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdClonesJumpClone;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdClonesOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheetClone;
import enterprises.orbital.evekit.model.character.JumpClone;
import enterprises.orbital.evekit.model.character.JumpCloneImplant;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterSheetCloneSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private ClonesApi mockEndpoint;
  private long testTime = 1238L;
  private GetCharactersCharacterIdClonesOk testClones;

  private static Object[][] cloneTestData;

  static {
    // Clone test data
    // 0 int jumpCloneID;
    // 1 long locationID;
    // 2 String cloneName;
    // 3 String locationType;
    // 4 int[] implants
    int size = 10 + TestBase.getRandomInt(10);
    cloneTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      cloneTestData[i][0] = TestBase.getUniqueRandomInteger();
      cloneTestData[i][1] = TestBase.getRandomLong();
      cloneTestData[i][2] = TestBase.getRandomText(50);
      cloneTestData[i][3] = GetCharactersCharacterIdClonesJumpClone.LocationTypeEnum.values()[TestBase.getRandomInt(2)];
      int count = 4 + TestBase.getRandomInt(4);
      int[] implants = new int[count];
      for (int j = 0; j < count; j++) {
        implants[j] = TestBase.getUniqueRandomInteger();
      }
      cloneTestData[i][4] = implants;
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CLONES, 1234L, null);

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
                                                        .createQuery("DELETE FROM JumpClone ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM JumpCloneImplant ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CharacterSheetClone ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(ClonesApi.class);

    // Set up clone info
    testClones = new GetCharactersCharacterIdClonesOk();
    GetCharactersCharacterIdClonesHomeLocation loc = new GetCharactersCharacterIdClonesHomeLocation();
    loc.setLocationId(TestBase.getRandomLong());
    loc.setLocationType(GetCharactersCharacterIdClonesHomeLocation.LocationTypeEnum.values()[TestBase.getRandomInt(2)]);
    testClones.setHomeLocation(loc);
    testClones.setLastCloneJumpDate(new DateTime(new Date(TestBase.getRandomLong())));
    testClones.setLastStationChangeDate(new DateTime(new Date(TestBase.getRandomLong())));

    // Set up jump clones
    List<GetCharactersCharacterIdClonesJumpClone> cloneList = Arrays.stream(cloneTestData)
                                                                    .
                                                                        map(x -> {
                                                                          GetCharactersCharacterIdClonesJumpClone nextClone = new GetCharactersCharacterIdClonesJumpClone();
                                                                          nextClone.setJumpCloneId((int) x[0]);
                                                                          nextClone.setLocationId((long) x[1]);
                                                                          nextClone.setName((String) x[2]);
                                                                          nextClone.setLocationType(
                                                                              (GetCharactersCharacterIdClonesJumpClone.LocationTypeEnum) x[3]);
                                                                          List<Integer> implants = new ArrayList<>();
                                                                          for (int i : (int[]) x[4]) implants.add(i);
                                                                          nextClone.setImplants(implants);
                                                                          return nextClone;
                                                                        })
                                                                    .collect(Collectors.toList());
    testClones.setJumpClones(cloneList);

    // Setup asset retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<GetCharactersCharacterIdClonesOk> apir = new ApiResponse<>(200, headers, testClones);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdClonesWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getClonesApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Check attributes
    CharacterSheetClone storedClone = CharacterSheetClone.get(charSyncAccount, testTime);
    Assert.assertEquals(testClones.getLastCloneJumpDate()
                                  .getMillis(), storedClone.getCloneJumpDate());
    Assert.assertEquals((long) testClones.getHomeLocation()
                                         .getLocationId(), storedClone.getHomeStationID());
    Assert.assertEquals(testClones.getHomeLocation()
                                  .getLocationType()
                                  .toString(), storedClone.getHomeStationType());
    Assert.assertEquals(testClones.getLastStationChangeDate()
                                  .getMillis(), storedClone.getLastStationChangeDate());

    // Retrieve all stored data
    List<JumpClone> storedData = AbstractESIAccountSync.retrieveAll(testTime,
                                                                    (long contid, AttributeSelector at) ->
                                                                        JumpClone.accessQuery(charSyncAccount,
                                                                                              contid, 1000,
                                                                                              false, at,
                                                                                              AbstractESIAccountSync.ANY_SELECTOR,
                                                                                              AbstractESIAccountSync.ANY_SELECTOR,
                                                                                              AbstractESIAccountSync.ANY_SELECTOR,
                                                                                              AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(cloneTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < cloneTestData.length; i++) {
      JumpClone nextEl = storedData.get(i);
      Assert.assertEquals((int) cloneTestData[i][0], nextEl.getJumpCloneID());
      Assert.assertEquals((long) cloneTestData[i][1], nextEl.getLocationID());
      Assert.assertEquals(cloneTestData[i][2], nextEl.getCloneName());
      Assert.assertEquals(String.valueOf(cloneTestData[i][3]), nextEl.getLocationType());
      List<JumpCloneImplant> storedImplant = AbstractESIAccountSync.retrieveAll(testTime,
                                                                                (long contid, AttributeSelector at) ->
                                                                                    JumpCloneImplant.accessQuery(
                                                                                        charSyncAccount,
                                                                                        contid, 1000,
                                                                                        false, at,
                                                                                        AttributeSelector.values(
                                                                                            nextEl.getJumpCloneID()),
                                                                                        AbstractESIAccountSync.ANY_SELECTOR));
      int[] implantTestData = (int[]) cloneTestData[i][4];
      for (int j = 0; j < implantTestData.length; j++) {
        JumpCloneImplant nextImplant = storedImplant.get(j);
        Assert.assertEquals(nextEl.getJumpCloneID(), nextImplant.getJumpCloneID());
        Assert.assertEquals(implantTestData[j], nextImplant.getTypeID());
      }
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterSheetCloneSync sync = new ESICharacterSheetCloneSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CLONES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CLONES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // Jump clone implants are all replaced in this test.
    //
    CharacterSheetClone storedClone = new CharacterSheetClone(testClones.getLastCloneJumpDate()
                                                                        .getMillis() + 1,
                                                              testClones.getHomeLocation()
                                                                        .getLocationId() + 1,
                                                              testClones.getHomeLocation()
                                                                        .getLocationType()
                                                                        .toString(),
                                                              testClones.getLastStationChangeDate()
                                                                        .getMillis() + 1);
    storedClone.setup(charSyncAccount, testTime - 1);
    CachedData.update(storedClone);

    for (Object[] sk : cloneTestData) {
      JumpClone storedJumpClone = new JumpClone((int) sk[0],
                                                (long) sk[1] + 1,
                                                (String) sk[2],
                                                String.valueOf(sk[3]));
      storedJumpClone.setup(charSyncAccount, testTime - 1);
      CachedData.update(storedJumpClone);
      for (int i = 0; i < ((int[]) sk[4]).length; i++) {
        int typeID = TestBase.getUniqueRandomInteger();
        JumpCloneImplant ni = new JumpCloneImplant(storedJumpClone.getJumpCloneID(), typeID);
        ni.setup(charSyncAccount, testTime - 1);
        CachedData.update(ni);
      }
    }

    // Perform the sync
    ESICharacterSheetCloneSync sync = new ESICharacterSheetCloneSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    CharacterSheetClone oldClone = CharacterSheetClone.get(charSyncAccount, testTime - 1);
    Assert.assertEquals(testClones.getLastCloneJumpDate()
                                  .getMillis() + 1, oldClone.getCloneJumpDate());
    Assert.assertEquals(testClones.getHomeLocation()
                                  .getLocationId() + 1, oldClone.getHomeStationID());
    Assert.assertEquals(testClones.getHomeLocation()
                                  .getLocationType()
                                  .toString(), oldClone.getHomeStationType());
    Assert.assertEquals(testClones.getLastStationChangeDate()
                                  .getMillis() + 1, oldClone.getLastStationChangeDate());

    List<JumpClone> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                (long contid, AttributeSelector at) ->
                                                                    JumpClone.accessQuery(charSyncAccount,
                                                                                          contid, 1000, false,
                                                                                          at,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(cloneTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < cloneTestData.length; i++) {
      JumpClone nextEl = oldEls.get(i);
      Assert.assertEquals((int) cloneTestData[i][0], nextEl.getJumpCloneID());
      Assert.assertEquals((long) cloneTestData[i][1] + 1, nextEl.getLocationID());
      Assert.assertEquals(cloneTestData[i][2], nextEl.getCloneName());
      Assert.assertEquals(String.valueOf(cloneTestData[i][3]), nextEl.getLocationType());
      List<JumpCloneImplant> storedImplants = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                                 (long contid, AttributeSelector at) ->
                                                                                     JumpCloneImplant.accessQuery(
                                                                                         charSyncAccount,
                                                                                         contid, 1000,
                                                                                         false, at,
                                                                                         AttributeSelector.values(
                                                                                             nextEl.getJumpCloneID()),
                                                                                         AbstractESIAccountSync.ANY_SELECTOR));
      // All implants should be EOL
      for (JumpCloneImplant jc : storedImplants) {
        Assert.assertEquals(testTime, jc.getLifeEnd());
      }
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CLONES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CLONES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
