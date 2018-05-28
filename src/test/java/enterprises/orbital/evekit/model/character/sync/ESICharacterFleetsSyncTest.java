package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.FleetsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.*;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterFleetsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private FleetsApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[] charFleetTestData;
  private static Object[] fleetInfoTestData;
  private static Object[][] fleetMembersTestData;
  private static Object[][] fleetWingsTestData;

  static {

    // CharacterFleet test data
    // 0 long fleetID;
    // 1 String role;
    // 2 long squadID;
    // 3 long wingID;
    int roleLength = GetCharactersCharacterIdFleetOk.RoleEnum.values().length;
    charFleetTestData = new Object[4];
    charFleetTestData[0] = TestBase.getUniqueRandomLong();
    charFleetTestData[1] = GetCharactersCharacterIdFleetOk.RoleEnum.values()[TestBase.getRandomInt(roleLength)];
    charFleetTestData[2] = TestBase.getRandomLong();
    charFleetTestData[3] = TestBase.getRandomLong();

    // FleetInfo test data
    // 0 long fleetID;
    // 1 boolean isFreeMove;
    // 2 boolean isRegistered;
    // 3 boolean isVoiceEnabled;
    // 4 String motd;
    fleetInfoTestData = new Object[5];
    fleetInfoTestData[0] = charFleetTestData[0];
    fleetInfoTestData[1] = TestBase.getRandomBoolean();
    fleetInfoTestData[2] = TestBase.getRandomBoolean();
    fleetInfoTestData[3] = TestBase.getRandomBoolean();
    fleetInfoTestData[4] = TestBase.getRandomText(1000);

    // FleetMembers test data
    // 0 long fleetID;
    // 1 int characterID;
    // 2 long joinTime;
    // 3 String role;
    // 4 String roleName;
    // 5 int shipTypeID;
    // 6 int solarSystemID;
    // 7 long squadID;
    // 8 long stationID;
    // 9 boolean takesFleetWarp;
    // 10 long wingID;
    int size = 100 + TestBase.getRandomInt(100);
    int memRoleLength = GetFleetsFleetIdMembers200Ok.RoleEnum.values().length;
    fleetMembersTestData = new Object[size][11];
    for (int i = 0; i < size; i++) {
      fleetMembersTestData[i][0] = charFleetTestData[0];
      fleetMembersTestData[i][1] = TestBase.getUniqueRandomInteger();
      fleetMembersTestData[i][2] = TestBase.getRandomLong();
      fleetMembersTestData[i][3] = GetFleetsFleetIdMembers200Ok.RoleEnum.values()[TestBase.getRandomInt(memRoleLength)];
      fleetMembersTestData[i][4] = TestBase.getRandomText(50);
      fleetMembersTestData[i][5] = TestBase.getRandomInt();
      fleetMembersTestData[i][6] = TestBase.getRandomInt();
      fleetMembersTestData[i][7] = TestBase.getRandomLong();
      fleetMembersTestData[i][8] = TestBase.getRandomLong();
      fleetMembersTestData[i][9] = TestBase.getRandomBoolean();
      fleetMembersTestData[i][10] = TestBase.getRandomLong();
    }

    // FleetWings test data
    // 0 long fleetID;
    // 1 long wingID;
    // 2 String name;
    // 3 Object[][] squads
    size = 10 + TestBase.getRandomInt(5);
    fleetWingsTestData = new Object[size][4];
    for (int i = 0; i < size; i++) {
      fleetWingsTestData[i][0] = charFleetTestData[0];
      fleetWingsTestData[i][1] = TestBase.getUniqueRandomLong();
      fleetWingsTestData[i][2] = TestBase.getRandomText(50);

      int squadSize = 5 + TestBase.getRandomInt(5);
      Object[][] squad = new Object[squadSize][4];
      fleetWingsTestData[i][3] = squad;
      for (int j = 0; j < squadSize; j++) {
        squad[j][0] = charFleetTestData[0];
        squad[j][1] = fleetWingsTestData[i][1];
        squad[j][2] = TestBase.getUniqueRandomLong();
        squad[j][3] = TestBase.getRandomText(50);
      }
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_FLEETS, 1234L, "0");

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
                                                        .createQuery("DELETE FROM CharacterFleet ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM FleetInfo ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM FleetMember ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM FleetWing ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM FleetSquad ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  @SuppressWarnings("Duplicates")
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(FleetsApi.class);

    {
      // Mock CharacterFleet
      GetCharactersCharacterIdFleetOk f = new GetCharactersCharacterIdFleetOk();
      f.setFleetId((long) charFleetTestData[0]);
      f.setRole((GetCharactersCharacterIdFleetOk.RoleEnum) charFleetTestData[1]);
      f.setSquadId((long) charFleetTestData[2]);
      f.setWingId((long) charFleetTestData[3]);

      ApiResponse<GetCharactersCharacterIdFleetOk> apir = new ApiResponse<>(200, createHeaders("Expires",
                                                                                               "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                            f);

      EasyMock.expect(mockEndpoint.getCharactersCharacterIdFleetWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }

    {
      // Mock FleetInfo
      GetFleetsFleetIdOk f = new GetFleetsFleetIdOk();
      f.setIsFreeMove((boolean) fleetInfoTestData[1]);
      f.setIsRegistered((boolean) fleetInfoTestData[2]);
      f.setIsVoiceEnabled((boolean) fleetInfoTestData[3]);
      f.setMotd((String) fleetInfoTestData[4]);

      ApiResponse<GetFleetsFleetIdOk> apir = new ApiResponse<>(200, createHeaders("Expires",
                                                                                  "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                               f);

      EasyMock.expect(mockEndpoint.getFleetsFleetIdWithHttpInfo(
          EasyMock.eq((long) charFleetTestData[0]),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }

    {
      // Mock FleetMembers
      List<GetFleetsFleetIdMembers200Ok> memberList =
          Arrays.stream(fleetMembersTestData)
                .map(x -> {
                  GetFleetsFleetIdMembers200Ok m = new GetFleetsFleetIdMembers200Ok();
                  m.setCharacterId((int) x[1]);
                  m.setJoinTime(new DateTime(new Date((long) x[2])));
                  m.setRole((GetFleetsFleetIdMembers200Ok.RoleEnum) x[3]);
                  m.setRoleName((String) x[4]);
                  m.setShipTypeId((int) x[5]);
                  m.setSolarSystemId((int) x[6]);
                  m.setSquadId((long) x[7]);
                  m.setStationId((long) x[8]);
                  m.setTakesFleetWarp((boolean) x[9]);
                  m.setWingId((long) x[10]);
                  return m;
                })
                .collect(Collectors.toList());

      ApiResponse<List<GetFleetsFleetIdMembers200Ok>> apir = new ApiResponse<>(200, createHeaders("Expires",
                                                                                                  "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                               memberList);

      EasyMock.expect(mockEndpoint.getFleetsFleetIdMembersWithHttpInfo(
          EasyMock.eq((long) charFleetTestData[0]),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }

    {
      // Mock FleetWings
      List<GetFleetsFleetIdWings200Ok> wingList =
          Arrays.stream(fleetWingsTestData)
                .map(x -> {
                  GetFleetsFleetIdWings200Ok w = new GetFleetsFleetIdWings200Ok();
                  w.setId((long) x[1]);
                  w.setName((String) x[2]);
                  w.getSquads()
                   .addAll(
                       Arrays.stream((Object[][]) x[3])
                             .map(y -> {
                               GetFleetsFleetIdWingsSquad s = new GetFleetsFleetIdWingsSquad();
                               s.setId((long) y[2]);
                               s.setName((String) y[3]);
                               return s;
                             })
                             .collect(Collectors.toList()));
                  return w;
                })
                .collect(Collectors.toList());

      ApiResponse<List<GetFleetsFleetIdWings200Ok>> apir = new ApiResponse<>(200, createHeaders("Expires",
                                                                                                "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                             wingList);

      EasyMock.expect(mockEndpoint.getFleetsFleetIdWingsWithHttpInfo(
          EasyMock.eq((long) charFleetTestData[0]),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getFleetsApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long time, Object[] testCharFleet, Object[] testFleetInfo, Object[][] testFleetMembers,
                                Object[][] testFleetWings) throws Exception {
    // Compare stored CharacterFleet
    List<CharacterFleet> storedCharFleet = CachedData.retrieveAll(time,
                                                                  (contid, at) -> CharacterFleet.accessQuery(
                                                                      charSyncAccount, contid, 1000, false, at,
                                                                      AttributeSelector.any(), AttributeSelector.any(),
                                                                      AttributeSelector.any(),
                                                                      AttributeSelector.any()));
    Assert.assertEquals(1, storedCharFleet.size());
    CharacterFleet storedCF = storedCharFleet.get(0);
    Assert.assertEquals((long) testCharFleet[0], storedCF.getFleetID());
    Assert.assertEquals(String.valueOf(testCharFleet[1]), storedCF.getRole());
    Assert.assertEquals((long) testCharFleet[2], storedCF.getSquadID());
    Assert.assertEquals((long) testCharFleet[3], storedCF.getWingID());

    // Compare stored FleetInfo
    List<FleetInfo> storedFleetInfo = CachedData.retrieveAll(time,
                                                             (contid, at) -> FleetInfo.accessQuery(charSyncAccount,
                                                                                                   contid, 1000, false,
                                                                                                   at,
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any()));
    Assert.assertEquals(1, storedFleetInfo.size());
    FleetInfo storedFI = storedFleetInfo.get(0);
    Assert.assertEquals((long) testFleetInfo[0], storedFI.getFleetID());
    Assert.assertEquals(testFleetInfo[1], storedFI.isFreeMove());
    Assert.assertEquals(testFleetInfo[2], storedFI.isRegistered());
    Assert.assertEquals(testFleetInfo[3], storedFI.isVoiceEnabled());
    Assert.assertEquals(testFleetInfo[4], storedFI.getMotd());

    // Compare stored FleetMembers
    List<FleetMember> storedMembers = CachedData.retrieveAll(time,
                                                             (contid, at) -> FleetMember.accessQuery(charSyncAccount,
                                                                                                     contid, 1000,
                                                                                                     false, at,
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any(),
                                                                                                     AttributeSelector.any()));

    Assert.assertEquals(testFleetMembers.length, storedMembers.size());

    for (int i = 0; i < testFleetMembers.length; i++) {
      FleetMember nextEl = storedMembers.get(i);
      Object[] dt = testFleetMembers[i];
      Assert.assertEquals((long) dt[0], nextEl.getFleetID());
      Assert.assertEquals((int) dt[1], nextEl.getCharacterID());
      Assert.assertEquals((long) dt[2], nextEl.getJoinTime());
      Assert.assertEquals(String.valueOf(dt[3]), nextEl.getRole());
      Assert.assertEquals(dt[4], nextEl.getRoleName());
      Assert.assertEquals((int) dt[5], nextEl.getShipTypeID());
      Assert.assertEquals((int) dt[6], nextEl.getSolarSystemID());
      Assert.assertEquals((long) dt[7], nextEl.getSquadID());
      Assert.assertEquals((long) dt[8], nextEl.getStationID());
      Assert.assertEquals(dt[9], nextEl.isTakesFleetWarp());
      Assert.assertEquals((long) dt[10], nextEl.getWingID());
    }

    // Compare stored FleetWings and FleetSquads
    List<FleetWing> storedWings = CachedData.retrieveAll(time,
                                                         (contid, at) -> FleetWing.accessQuery(charSyncAccount, contid,
                                                                                               1000, false, at,
                                                                                               AttributeSelector.any(),
                                                                                               AttributeSelector.any(),
                                                                                               AttributeSelector.any()));
    List<FleetSquad> storedSquads = CachedData.retrieveAll(time,
                                                           (contid, at) -> FleetSquad.accessQuery(charSyncAccount,
                                                                                                  contid, 1000, false,
                                                                                                  at,
                                                                                                  AttributeSelector.any(),
                                                                                                  AttributeSelector.any(),
                                                                                                  AttributeSelector.any(),
                                                                                                  AttributeSelector.any()));

    Assert.assertEquals(testFleetWings.length, storedWings.size());

    int squadCount = Arrays.stream(testFleetWings)
                           .map(x -> ((Object[][]) x[3]).length)
                           .reduce(0,
                                   (a, b) -> a + b);
    Assert.assertEquals(squadCount, storedSquads.size());

    for (int i = 0, j = 0; i < testFleetWings.length; i++) {
      FleetWing nextEl = storedWings.get(i);
      Object[] dt = testFleetWings[i];
      Assert.assertEquals((long) dt[0], nextEl.getFleetID());
      Assert.assertEquals((long) dt[1], nextEl.getWingID());
      Assert.assertEquals(dt[2], nextEl.getName());

      Object[][] squads = (Object[][]) dt[3];
      for (int k = 0; k < squads.length; j++, k++) {
        FleetSquad nextSquad = storedSquads.get(j);
        Object[] st = squads[k];
        Assert.assertEquals((long) st[0], nextSquad.getFleetID());
        Assert.assertEquals((long) st[1], nextSquad.getWingID());
        Assert.assertEquals((long) st[2], nextSquad.getSquadID());
        Assert.assertEquals(st[3], nextSquad.getName());
      }
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterFleetsSync sync = new ESICharacterFleetsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, charFleetTestData, fleetInfoTestData, fleetMembersTestData, fleetWingsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_FLEETS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_FLEETS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate an old fleet.  This should be present at testTime -1, and replaced at testTime.
    Object[] oldCharFleetTestData;
    Object[] oldFleetInfoTestData;
    Object[][] oldFleetMembersTestData;
    Object[][] oldFleetWingsTestData;

    int roleLength = GetCharactersCharacterIdFleetOk.RoleEnum.values().length;
    oldCharFleetTestData = new Object[4];
    oldCharFleetTestData[0] = TestBase.getUniqueRandomLong();
    oldCharFleetTestData[1] = GetCharactersCharacterIdFleetOk.RoleEnum.values()[TestBase.getRandomInt(roleLength)];
    oldCharFleetTestData[2] = TestBase.getRandomLong();
    oldCharFleetTestData[3] = TestBase.getRandomLong();

    CharacterFleet ncf = new CharacterFleet((long) oldCharFleetTestData[0],
                                            String.valueOf(oldCharFleetTestData[1]),
                                            (long) oldCharFleetTestData[2],
                                            (long) oldCharFleetTestData[3]);
    ncf.setup(charSyncAccount, testTime - 1);
    CachedData.update(ncf);

    oldFleetInfoTestData = new Object[5];
    oldFleetInfoTestData[0] = oldCharFleetTestData[0];
    oldFleetInfoTestData[1] = TestBase.getRandomBoolean();
    oldFleetInfoTestData[2] = TestBase.getRandomBoolean();
    oldFleetInfoTestData[3] = TestBase.getRandomBoolean();
    oldFleetInfoTestData[4] = TestBase.getRandomText(1000);

    FleetInfo nfi = new FleetInfo((long) oldFleetInfoTestData[0],
                                  (boolean) oldFleetInfoTestData[1],
                                  (boolean) oldFleetInfoTestData[2],
                                  (boolean) oldFleetInfoTestData[3],
                                  (String) oldFleetInfoTestData[4]);
    nfi.setup(charSyncAccount, testTime - 1);
    CachedData.update(nfi);

    int size = 100 + TestBase.getRandomInt(100);
    int memRoleLength = GetFleetsFleetIdMembers200Ok.RoleEnum.values().length;
    oldFleetMembersTestData = new Object[size][11];
    for (int i = 0; i < size; i++) {
      oldFleetMembersTestData[i][0] = oldCharFleetTestData[0];
      oldFleetMembersTestData[i][1] = TestBase.getUniqueRandomInteger();
      oldFleetMembersTestData[i][2] = TestBase.getRandomLong();
      oldFleetMembersTestData[i][3] = GetFleetsFleetIdMembers200Ok.RoleEnum.values()[TestBase.getRandomInt(
          memRoleLength)];
      oldFleetMembersTestData[i][4] = TestBase.getRandomText(50);
      oldFleetMembersTestData[i][5] = TestBase.getRandomInt();
      oldFleetMembersTestData[i][6] = TestBase.getRandomInt();
      oldFleetMembersTestData[i][7] = TestBase.getRandomLong();
      oldFleetMembersTestData[i][8] = TestBase.getRandomLong();
      oldFleetMembersTestData[i][9] = TestBase.getRandomBoolean();
      oldFleetMembersTestData[i][10] = TestBase.getRandomLong();

      FleetMember nfm = new FleetMember((long) oldFleetMembersTestData[i][0],
                                        (int) oldFleetMembersTestData[i][1],
                                        (long) oldFleetMembersTestData[i][2],
                                        String.valueOf(oldFleetMembersTestData[i][3]),
                                        (String) oldFleetMembersTestData[i][4],
                                        (int) oldFleetMembersTestData[i][5],
                                        (int) oldFleetMembersTestData[i][6],
                                        (long) oldFleetMembersTestData[i][7],
                                        (long) oldFleetMembersTestData[i][8],
                                        (boolean) oldFleetMembersTestData[i][9],
                                        (long) oldFleetMembersTestData[i][10]);
      nfm.setup(charSyncAccount, testTime - 1);
      CachedData.update(nfm);
    }

    size = 10 + TestBase.getRandomInt(5);
    oldFleetWingsTestData = new Object[size][4];
    for (int i = 0; i < size; i++) {
      oldFleetWingsTestData[i][0] = oldCharFleetTestData[0];
      oldFleetWingsTestData[i][1] = TestBase.getUniqueRandomLong();
      oldFleetWingsTestData[i][2] = TestBase.getRandomText(50);

      FleetWing nfw = new FleetWing((long) oldFleetWingsTestData[i][0],
                                    (long) oldFleetWingsTestData[i][1],
                                    (String) oldFleetWingsTestData[i][2]);
      nfw.setup(charSyncAccount, testTime - 1);
      CachedData.update(nfw);

      int squadSize = 5 + TestBase.getRandomInt(5);
      Object[][] squad = new Object[squadSize][4];
      oldFleetWingsTestData[i][3] = squad;
      for (int j = 0; j < squadSize; j++) {
        squad[j][0] = oldCharFleetTestData[0];
        squad[j][1] = oldFleetWingsTestData[i][1];
        squad[j][2] = TestBase.getUniqueRandomLong();
        squad[j][3] = TestBase.getRandomText(50);

        FleetSquad nfs = new FleetSquad((long) squad[j][0],
                                        (long) squad[j][1],
                                        (long) squad[j][2],
                                        (String) squad[j][3]);
        nfs.setup(charSyncAccount, testTime - 1);
        CachedData.update(nfs);
      }
    }

    // Perform the sync
    ESICharacterFleetsSync sync = new ESICharacterFleetsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    verifyDataUpdate(testTime - 1, oldCharFleetTestData, oldFleetInfoTestData, oldFleetMembersTestData,
                     oldFleetWingsTestData);
    verifyDataUpdate(testTime, charFleetTestData, fleetInfoTestData, fleetMembersTestData, fleetWingsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_FLEETS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_FLEETS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
