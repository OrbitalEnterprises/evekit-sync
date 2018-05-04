package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdRolesOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterRole;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

public class ESICharacterRolesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Map<String, Set<String>> rolesTestData;

  static {

    int corpEnumCount = GetCharactersCharacterIdRolesOk.RolesEnum.values().length;
    int baseEnumCount = GetCharactersCharacterIdRolesOk.RolesAtBaseEnum.values().length;
    int hqEnumCount = GetCharactersCharacterIdRolesOk.RolesAtHqEnum.values().length;
    int otherEnumCount = GetCharactersCharacterIdRolesOk.RolesAtOtherEnum.values().length;

    int corpCount = 5 + TestBase.getRandomInt(corpEnumCount / 2);
    int baseCount = 5 + TestBase.getRandomInt(baseEnumCount / 2);
    int hqCount = 5 + TestBase.getRandomInt(hqEnumCount / 2);
    int otherCount = 5 + TestBase.getRandomInt(otherEnumCount / 2);

    rolesTestData = new HashMap<>();
    rolesTestData.put(CharacterRole.CAT_CORPORATION, new HashSet<>());
    rolesTestData.put(CharacterRole.CAT_CORPORATION_AT_HQ, new HashSet<>());
    rolesTestData.put(CharacterRole.CAT_CORPORATION_AT_BASE, new HashSet<>());
    rolesTestData.put(CharacterRole.CAT_CORPORATION_AT_OTHER, new HashSet<>());

    for (int i = 0; i < corpCount; i++) {
      rolesTestData.get(CharacterRole.CAT_CORPORATION)
                   .add(GetCharactersCharacterIdRolesOk.RolesEnum.values()[TestBase.getRandomInt(
                       corpEnumCount)].name());
    }
    for (int i = 0; i < baseCount; i++) {
      rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_BASE)
                   .add(GetCharactersCharacterIdRolesOk.RolesAtBaseEnum.values()[TestBase.getRandomInt(
                       baseEnumCount)].name());
    }
    for (int i = 0; i < hqCount; i++) {
      rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_HQ)
                   .add(GetCharactersCharacterIdRolesOk.RolesAtHqEnum.values()[TestBase.getRandomInt(
                       hqEnumCount)].name());
    }
    for (int i = 0; i < otherCount; i++) {
      rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_OTHER)
                   .add(GetCharactersCharacterIdRolesOk.RolesAtOtherEnum.values()[TestBase.getRandomInt(
                       otherEnumCount)].name());
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CORP_ROLES, 1234L, null);

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
                                                        .createQuery("DELETE FROM CharacterRole ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CharacterApi.class);

    GetCharactersCharacterIdRolesOk testRoles = new GetCharactersCharacterIdRolesOk();

    for (String next : rolesTestData.get(CharacterRole.CAT_CORPORATION))
      testRoles.getRoles()
               .add(GetCharactersCharacterIdRolesOk.RolesEnum.valueOf(next));
    for (String next : rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_BASE))
      testRoles.getRolesAtBase()
               .add(GetCharactersCharacterIdRolesOk.RolesAtBaseEnum.valueOf(next));
    for (String next : rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_HQ))
      testRoles.getRolesAtHq()
               .add(GetCharactersCharacterIdRolesOk.RolesAtHqEnum.valueOf(next));
    for (String next : rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_OTHER))
      testRoles.getRolesAtOther()
               .add(GetCharactersCharacterIdRolesOk.RolesAtOtherEnum.valueOf(next));

    ApiResponse<GetCharactersCharacterIdRolesOk> apir = new ApiResponse<>(200,
                                                                          createHeaders("Expires",
                                                                                        "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                          testRoles);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdRolesWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    // Complete mock with provider
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long atTime, Map<String, Set<String>> testData) throws Exception {

    // Retrieve stored roles
    List<CharacterRole> storedRoles = AbstractESIAccountSync.retrieveAll(atTime,
                                                                         (long contid, AttributeSelector at) ->
                                                                             CharacterRole.accessQuery(
                                                                                 charSyncAccount, contid, 1000,
                                                                                 false, at,
                                                                                 AbstractESIAccountSync.ANY_SELECTOR,
                                                                                 AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    int testRoleCount = 0;
    testRoleCount += testData.get(CharacterRole.CAT_CORPORATION)
                             .size();
    testRoleCount += testData.get(CharacterRole.CAT_CORPORATION_AT_BASE)
                             .size();
    testRoleCount += testData.get(CharacterRole.CAT_CORPORATION_AT_HQ)
                             .size();
    testRoleCount += testData.get(CharacterRole.CAT_CORPORATION_AT_OTHER)
                             .size();
    Assert.assertEquals(testRoleCount, storedRoles.size());

    // Check stored data
    for (CharacterRole next : storedRoles) {
      switch (next.getRoleCategory()) {
        case CharacterRole.CAT_CORPORATION:
          Assert.assertTrue(testData.get(CharacterRole.CAT_CORPORATION)
                                    .stream()
                                    .map(x -> GetCharactersCharacterIdRolesOk.RolesEnum.valueOf(x).toString())
                                    .collect(Collectors.toSet())
                                    .contains(next.getRoleName()));
          break;
        case CharacterRole.CAT_CORPORATION_AT_BASE:
          Assert.assertTrue(testData.get(CharacterRole.CAT_CORPORATION_AT_BASE)
                                    .stream()
                                    .map(x -> GetCharactersCharacterIdRolesOk.RolesAtBaseEnum.valueOf(x).toString())
                                    .collect(Collectors.toSet())
                                    .contains(next.getRoleName()));
          break;
        case CharacterRole.CAT_CORPORATION_AT_HQ:
          Assert.assertTrue(testData.get(CharacterRole.CAT_CORPORATION_AT_HQ)
                                    .stream()
                                    .map(x -> GetCharactersCharacterIdRolesOk.RolesAtHqEnum.valueOf(x).toString())
                                    .collect(Collectors.toSet())
                                    .contains(next.getRoleName()));
          break;
        case CharacterRole.CAT_CORPORATION_AT_OTHER:
          Assert.assertTrue(testData.get(CharacterRole.CAT_CORPORATION_AT_OTHER)
                                    .stream()
                                    .map(x -> GetCharactersCharacterIdRolesOk.RolesAtOtherEnum.valueOf(x).toString())
                                    .collect(Collectors.toSet())
                                    .contains(next.getRoleName()));
          break;
        default:
          Assert.fail("Unknown category: " + next.getRoleCategory());

      }
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterRolesSync sync = new ESICharacterRolesSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, rolesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CORP_ROLES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CORP_ROLES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.
    Map<String, Set<String>> newTestData = new HashMap<>();
    newTestData.put(CharacterRole.CAT_CORPORATION, new HashSet<>());
    newTestData.put(CharacterRole.CAT_CORPORATION_AT_HQ, new HashSet<>());
    newTestData.put(CharacterRole.CAT_CORPORATION_AT_BASE, new HashSet<>());
    newTestData.put(CharacterRole.CAT_CORPORATION_AT_OTHER, new HashSet<>());

    Set<String> availCorp = Arrays.stream(GetCharactersCharacterIdRolesOk.RolesEnum.values())
                                  .map(Enum::name)
                                  .collect(Collectors.toSet());
    Set<String> availBase = Arrays.stream(GetCharactersCharacterIdRolesOk.RolesAtBaseEnum.values())
                                  .map(Enum::name)
                                  .collect(Collectors.toSet());
    Set<String> availHQ = Arrays.stream(GetCharactersCharacterIdRolesOk.RolesAtHqEnum.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet());
    Set<String> availOther = Arrays.stream(GetCharactersCharacterIdRolesOk.RolesAtOtherEnum.values())
                                   .map(Enum::name)
                                   .collect(Collectors.toSet());
    availCorp.removeAll(rolesTestData.get(CharacterRole.CAT_CORPORATION));
    availBase.removeAll(rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_BASE));
    availHQ.removeAll(rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_HQ));
    availOther.removeAll(rolesTestData.get(CharacterRole.CAT_CORPORATION_AT_OTHER));

    int corpCount = 5 + TestBase.getRandomInt(availCorp.size() / 2);
    int baseCount = 5 + TestBase.getRandomInt(availBase.size() / 2);
    int hqCount = 5 + TestBase.getRandomInt(availHQ.size() / 2);
    int otherCount = 5 + TestBase.getRandomInt(availOther.size() / 2);

    for (int i = 0; i < corpCount; i++) {
      String[] opts = availCorp.toArray(new String[0]);
      String value = opts[TestBase.getRandomInt(opts.length)];
      newTestData.get(CharacterRole.CAT_CORPORATION)
                 .add(value);
      availCorp.remove(value);
      CharacterRole newRole = new CharacterRole(CharacterRole.CAT_CORPORATION,
                                                GetCharactersCharacterIdRolesOk.RolesEnum.valueOf(value).toString());
      newRole.setup(charSyncAccount, testTime - 1);
      CachedData.update(newRole);
    }
    for (int i = 0; i < baseCount; i++) {
      String[] opts = availBase.toArray(new String[0]);
      String value = opts[TestBase.getRandomInt(opts.length)];
      newTestData.get(CharacterRole.CAT_CORPORATION_AT_BASE)
                 .add(value);
      availBase.remove(value);
      CharacterRole newRole = new CharacterRole(CharacterRole.CAT_CORPORATION_AT_BASE,
                                                GetCharactersCharacterIdRolesOk.RolesAtBaseEnum.valueOf(value).toString());
      newRole.setup(charSyncAccount, testTime - 1);
      CachedData.update(newRole);
    }
    for (int i = 0; i < hqCount; i++) {
      String[] opts = availHQ.toArray(new String[0]);
      String value = opts[TestBase.getRandomInt(opts.length)];
      newTestData.get(CharacterRole.CAT_CORPORATION_AT_HQ)
                 .add(value);
      availHQ.remove(value);
      CharacterRole newRole = new CharacterRole(CharacterRole.CAT_CORPORATION_AT_HQ,
                                                GetCharactersCharacterIdRolesOk.RolesAtHqEnum.valueOf(value).toString());
      newRole.setup(charSyncAccount, testTime - 1);
      CachedData.update(newRole);
    }
    for (int i = 0; i < otherCount; i++) {
      String[] opts = availOther.toArray(new String[0]);
      String value = opts[TestBase.getRandomInt(opts.length)];
      newTestData.get(CharacterRole.CAT_CORPORATION_AT_OTHER)
                 .add(value);
      availOther.remove(value);
      CharacterRole newRole = new CharacterRole(CharacterRole.CAT_CORPORATION_AT_OTHER,
                                                GetCharactersCharacterIdRolesOk.RolesAtOtherEnum.valueOf(value).toString());
      newRole.setup(charSyncAccount, testTime - 1);
      CachedData.update(newRole);
    }

    // Perform the sync
    ESICharacterRolesSync sync = new ESICharacterRolesSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old data updates
    verifyDataUpdate(testTime - 1, newTestData);

    // Verify new data updates
    verifyDataUpdate(testTime, rolesTestData);


    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CORP_ROLES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CORP_ROLES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
