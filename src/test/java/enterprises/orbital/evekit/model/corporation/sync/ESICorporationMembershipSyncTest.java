package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdRoles200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdRolesHistory200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Member;
import enterprises.orbital.evekit.model.corporation.MemberRole;
import enterprises.orbital.evekit.model.corporation.MemberRoleHistory;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
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
public class ESICorporationMembershipSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[] membersTestData;
  private static Object[][] rolesTestData;
  private static Object[][] roleHistoryTestData;
  private static int[] roleHistoryPages;

  private static final int GRANTABLE = 1;
  private static final int AT_HQ = 1 << 1;
  private static final int AT_BASE = 1 << 2;
  private static final int AT_OTHER = 1 << 3;

  private static int makeMask() {
    int nextMask = TestBase.getRandomBoolean() ? GRANTABLE : 0;
    switch (TestBase.getRandomInt(4)) {
      case 1:
        nextMask |= AT_HQ;
        break;
      case 2:
        nextMask |= AT_BASE;
        break;
      case 3:
        nextMask |= AT_OTHER;
        break;
      case 0:
      default:
        break;
    }
    return nextMask;
  }

  static {
    // Members test data.  Just an array of unique integers.
    int size = 100 + TestBase.getRandomInt(100);
    membersTestData = new Object[size];
    for (int i = 0; i < size; i++) {
      membersTestData[i] = TestBase.getUniqueRandomInteger();
    }

    size = 100 + TestBase.getRandomInt(100);
    int rolesLen = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values().length;
    rolesTestData = new Object[size][2];
    for (int i = 0; i < size; i++) {
      // MemberRole test data
      // 0 int characterID
      // 1 Object[][] roles
      rolesTestData[i][0] = TestBase.getUniqueRandomInteger();
      int roleCount = 5 + TestBase.getRandomInt(10);
      Object[][] roleList = new Object[roleCount][2];
      rolesTestData[i][1] = roleList;
      Set<Pair<String, Integer>> usedRoles = new HashSet<>();
      for (int j = 0; j < roleCount; j++) {
        String nextRoleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
            rolesLen)].toString();
        int nextMask = makeMask();
        while (usedRoles.contains(Pair.of(nextRoleName, nextMask))) {
          nextRoleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
              rolesLen)].toString();
          nextMask = makeMask();
        }
        usedRoles.add(Pair.of(nextRoleName, nextMask));
        roleList[j][0] = nextRoleName;
        roleList[j][1] = nextMask;
      }
    }

    size = 100 + TestBase.getRandomInt(100);
    int roleTypeLen = GetCorporationsCorporationIdRolesHistory200Ok.RoleTypeEnum.values().length;
    roleHistoryTestData = new Object[size][6];
    for (int i = 0; i < size; i++) {
      // MemberRoleHistory test data
      // 0 int characterID
      // 1 long changedAt
      // 2 int issuerID
      // 3 String roleType
      // 4 Object[] oldRoles;
      // 5 Object[] newRoles;
      roleHistoryTestData[i][0] = TestBase.getUniqueRandomInteger();
      roleHistoryTestData[i][1] = TestBase.getRandomLong();
      roleHistoryTestData[i][2] = TestBase.getRandomInt();
      roleHistoryTestData[i][3] = GetCorporationsCorporationIdRolesHistory200Ok.RoleTypeEnum.values()[TestBase.getRandomInt(
          roleTypeLen)];
      int oldRoleCount = 3 + TestBase.getRandomInt(3);
      Object[] oldRoles = new Object[oldRoleCount];
      roleHistoryTestData[i][4] = oldRoles;
      Set<String> usedRoles = new HashSet<>();
      for (int j = 0; j < oldRoleCount; j++) {
        String roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
            rolesLen)].toString();
        while (usedRoles.contains(roleName)) {
          roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
              rolesLen)].toString();
        }
        usedRoles.add(roleName);
        oldRoles[j] = roleName;
      }
      usedRoles.clear();
      int newRoleCount = 3 + TestBase.getRandomInt(3);
      Object[] newRoles = new Object[newRoleCount];
      roleHistoryTestData[i][5] = newRoles;
      for (int j = 0; j < newRoleCount; j++) {
        String roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
            rolesLen)].toString();
        while (usedRoles.contains(roleName)) {
          roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
              rolesLen)].toString();
        }
        usedRoles.add(roleName);
        newRoles[j] = roleName;
      }
    }

    // Divide data into pages to test paging sync feature
    int pageCount = 3 + TestBase.getRandomInt(3);
    roleHistoryPages = new int[pageCount];
    for (int i = 0; i < pageCount; i++) {
      roleHistoryPages[i] = (i + 1) * size / pageCount;
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MEMBERSHIP, 1234L);

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
                                                        .createQuery("DELETE FROM Member ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MemberRole ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MemberRoleHistory ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  private static <T> T findEnum(T[] src, String target) {
    for (T n : src) {
      if (n.toString()
           .equals(target))
        return n;
    }
    throw new RuntimeException("Can't find " + target + " in " + Arrays.toString(src));
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CorporationApi.class);

    // Setup member list
    List<Integer> memberList =
        Arrays.stream(membersTestData)
              .map(x -> (Integer) x)
              .collect(Collectors.toList());

    {
      ApiResponse<List<Integer>> apir = new ApiResponse<>(200,
                                                          createHeaders("Expires",
                                                                        "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                          memberList);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdMembersWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Setup member role list
    List<GetCorporationsCorporationIdRoles200Ok> roleList = new ArrayList<>();
    for (Object[] next : rolesTestData) {
      GetCorporationsCorporationIdRoles200Ok obj = new GetCorporationsCorporationIdRoles200Ok();
      obj.setCharacterId((Integer) next[0]);
      for (Object[] role : (Object[][]) next[1]) {
        String roleName = (String) role[0];
        int mask = (int) role[1];
        switch (mask) {
          case AT_BASE:
            obj.getRolesAtBase()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.RolesAtBaseEnum.values(), roleName));
            break;
          case AT_HQ:
            obj.getRolesAtHq()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.RolesAtHqEnum.values(), roleName));
            break;
          case AT_OTHER:
            obj.getRolesAtOther()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.RolesAtOtherEnum.values(), roleName));
            break;
          case GRANTABLE:
            obj.getGrantableRoles()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.GrantableRolesEnum.values(), roleName));
            break;
          case GRANTABLE | AT_BASE:
            obj.getGrantableRolesAtBase()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.GrantableRolesAtBaseEnum.values(), roleName));
            break;
          case GRANTABLE | AT_HQ:
            obj.getGrantableRolesAtHq()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.GrantableRolesAtHqEnum.values(), roleName));
            break;
          case GRANTABLE | AT_OTHER:
            obj.getGrantableRolesAtOther()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.GrantableRolesAtOtherEnum.values(), roleName));
            break;
          default:
            obj.getRoles()
               .add(findEnum(GetCorporationsCorporationIdRoles200Ok.RolesEnum.values(), roleName));
        }
      }
      roleList.add(obj);
    }

    {
      ApiResponse<List<GetCorporationsCorporationIdRoles200Ok>> apir = new ApiResponse<>(200,
                                                                                         createHeaders("Expires",
                                                                                                       "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                         roleList);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdRolesWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Setup role history list
    List<GetCorporationsCorporationIdRolesHistory200Ok> roleHistoryList = new ArrayList<>();
    for (Object[] next : roleHistoryTestData) {
      GetCorporationsCorporationIdRolesHistory200Ok obj = new GetCorporationsCorporationIdRolesHistory200Ok();
      obj.setCharacterId((int) next[0]);
      obj.setChangedAt(new DateTime(new Date((long) next[1])));
      obj.setIssuerId((int) next[2]);
      obj.setRoleType((GetCorporationsCorporationIdRolesHistory200Ok.RoleTypeEnum) next[3]);
      for (Object role : (Object[]) next[4]) {
        obj.getOldRoles()
           .add(findEnum(GetCorporationsCorporationIdRolesHistory200Ok.OldRolesEnum.values(), String.valueOf(role)));
      }
      for (Object role : (Object[]) next[5]) {
        obj.getNewRoles()
           .add(findEnum(GetCorporationsCorporationIdRolesHistory200Ok.NewRolesEnum.values(), String.valueOf(role)));
      }
      roleHistoryList.add(obj);
    }

    int last = 0;
    for (int i = 0; i < roleHistoryPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(roleHistoryPages.length));
      ApiResponse<List<GetCorporationsCorporationIdRolesHistory200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                                roleHistoryList.subList(
                                                                                                    last,
                                                                                                    roleHistoryPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdRolesHistoryWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = roleHistoryPages[i];
    }

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private static class MRHWrapper {
    MemberRoleHistory wrapped;

    MRHWrapper(MemberRoleHistory wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MRHWrapper && wrapped.equivalent(((MRHWrapper) o).wrapped);
    }

    @Override
    public int hashCode() {
      return Objects.hash(wrapped.getCharacterID(),
                          wrapped.getChangedAt(),
                          wrapped.getIssuerID(),
                          wrapped.getRoleType(),
                          wrapped.getRoleName(),
                          wrapped.isOld());
    }
  }

  private void verifyDataUpdate(long time, Object[] mData, Object[][] rData, Object[][] rhData) throws Exception {
    // Verify members data
    List<Member> storedMembers = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        Member.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR));

    Assert.assertEquals(mData.length, storedMembers.size());

    Set<Integer> memberSet = Arrays.stream(mData)
                                   .map(x -> (int) x)
                                   .collect(Collectors.toSet());
    for (Member next : storedMembers) {
      Assert.assertTrue(memberSet.contains(next.getCharacterID()));
    }

    // Verify role data
    List<MemberRole> storedRoles = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        MemberRole.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR));

    Set<Triple<Integer, String, Integer>> roleSet = new HashSet<>();
    for (Object[] next : rData) {
      int charID = (int) next[0];
      for (Object[] role : (Object[][]) next[1]) {
        String roleName = (String) role[0];
        int mask = (int) role[1];
        roleSet.add(Triple.of(charID, roleName, mask));
      }
    }

    Assert.assertEquals(roleSet.size(), storedRoles.size());

    for (MemberRole next : storedRoles) {
      int mask = next.isGrantable() ? GRANTABLE : 0;
      mask |= next.isAtBase() ? AT_BASE : 0;
      mask |= next.isAtHQ() ? AT_HQ : 0;
      mask |= next.isAtOther() ? AT_OTHER : 0;
      Assert.assertTrue(roleSet.contains(Triple.of(next.getCharacterID(), next.getRoleName(), mask)));
    }

    // Verify role history data
    List<MemberRoleHistory> storedHistory = AbstractESIAccountSync.retrieveAll(time,
                                                                               (long contid, AttributeSelector at) ->
                                                                                   MemberRoleHistory.accessQuery(
                                                                                       corpSyncAccount, contid, 1000,
                                                                                       false, at,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR));

    Set<MRHWrapper> roleHistorySet = new HashSet<>();
    for (Object[] next : rhData) {
      int charID = (int) next[0];
      long changedAt = (long) next[1];
      int issuerID = (int) next[2];
      String roleType = String.valueOf(next[3]);
      for (Object role : (Object[]) next[4]) {
        MemberRoleHistory obj = new MemberRoleHistory(charID, changedAt, issuerID, roleType, String.valueOf(role),
                                                      true);
        roleHistorySet.add(new MRHWrapper(obj));
      }
      for (Object role : (Object[]) next[5]) {
        MemberRoleHistory obj = new MemberRoleHistory(charID, changedAt, issuerID, roleType, String.valueOf(role),
                                                      false);
        roleHistorySet.add(new MRHWrapper(obj));
      }
    }

    Assert.assertEquals(roleHistorySet.size(), storedHistory.size());

    for (MemberRoleHistory next : storedHistory) {
      Assert.assertTrue(roleHistorySet.contains(new MRHWrapper(next)));
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationMembershipSync sync = new ESICorporationMembershipSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, membersTestData, rolesTestData, roleHistoryTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MEMBERSHIP);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MEMBERSHIP);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Generate new random member list which should be completely replaced
    Object[] oldMembers = new Object[membersTestData.length];
    for (int i = 0; i < oldMembers.length; i++) {
      oldMembers[i] = TestBase.getUniqueRandomInteger();
      Member obj = new Member((int) oldMembers[i]);
      obj.setup(corpSyncAccount, testTime - 1);
      CachedData.update(obj);
    }

    // Modify half the roles so we can test both updates and EOL.
    Object[][] oldRoles = new Object[rolesTestData.length][2];
    int rolesLen = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values().length;
    for (int i = 0; i < oldRoles.length; i++) {
      oldRoles[i][0] = i % 2 == 0 ? rolesTestData[i][0] : TestBase.getUniqueRandomInteger();
      int roleCount = 5 + TestBase.getRandomInt(5);
      Object[][] roleList = new Object[roleCount][2];
      oldRoles[i][1] = roleList;
      Set<Pair<String, Integer>> usedRoles = new HashSet<>();
      for (int j = 0; j < roleCount; j++) {
        String nextRoleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
            rolesLen)].toString();
        int nextMask = makeMask();
        while (usedRoles.contains(Pair.of(nextRoleName, nextMask))) {
          nextRoleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
              rolesLen)].toString();
          nextMask = makeMask();
        }
        usedRoles.add(Pair.of(nextRoleName, nextMask));
        roleList[j][0] = nextRoleName;
        roleList[j][1] = nextMask;

        MemberRole obj = new MemberRole((int) oldRoles[i][0],
                                        nextRoleName,
                                        (nextMask & GRANTABLE) > 0,
                                        (nextMask & AT_HQ) > 0,
                                        (nextMask & AT_BASE) > 0,
                                        (nextMask & AT_OTHER) > 0);
        obj.setup(corpSyncAccount, testTime - 1);
        CachedData.update(obj);
      }
    }

    // Create older history which should still exist after update
    Object[][] oldHistory = new Object[roleHistoryTestData.length][6];
    int roleTypeLen = GetCorporationsCorporationIdRolesHistory200Ok.RoleTypeEnum.values().length;
    for (int i = 0; i < oldHistory.length; i++) {
      oldHistory[i][0] = TestBase.getUniqueRandomInteger();
      oldHistory[i][1] = TestBase.getRandomLong();
      oldHistory[i][2] = TestBase.getRandomInt();
      oldHistory[i][3] = GetCorporationsCorporationIdRolesHistory200Ok.RoleTypeEnum.values()[TestBase.getRandomInt(
          roleTypeLen)];
      int oldRoleCount = 3 + TestBase.getRandomInt(3);
      Object[] oldR = new Object[oldRoleCount];
      oldHistory[i][4] = oldR;
      Set<String> usedRoles = new HashSet<>();
      for (int j = 0; j < oldRoleCount; j++) {
        String roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
            rolesLen)].toString();
        while (usedRoles.contains(roleName)) {
          roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
              rolesLen)].toString();
        }
        usedRoles.add(roleName);
        oldR[j] = roleName;
        MemberRoleHistory obj = new MemberRoleHistory((int) oldHistory[i][0],
                                                      (long) oldHistory[i][1],
                                                      (int) oldHistory[i][2],
                                                      oldHistory[i][3].toString(),
                                                      roleName,
                                                      true);
        obj.setup(corpSyncAccount, testTime - 1);
        CachedData.update(obj);
      }
      usedRoles.clear();
      int newRoleCount = 3 + TestBase.getRandomInt(3);
      Object[] newR = new Object[newRoleCount];
      oldHistory[i][5] = newR;
      for (int j = 0; j < newRoleCount; j++) {
        String roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
            rolesLen)].toString();
        while (usedRoles.contains(roleName)) {
          roleName = GetCorporationsCorporationIdRoles200Ok.RolesEnum.values()[TestBase.getRandomInt(
              rolesLen)].toString();
        }
        usedRoles.add(roleName);
        newR[j] = roleName;
        MemberRoleHistory obj = new MemberRoleHistory((int) oldHistory[i][0],
                                                      (long) oldHistory[i][1],
                                                      (int) oldHistory[i][2],
                                                      oldHistory[i][3].toString(),
                                                      roleName,
                                                      false);
        obj.setup(corpSyncAccount, testTime - 1);
        CachedData.update(obj);
      }
    }

    // Perform the sync
    ESICorporationMembershipSync sync = new ESICorporationMembershipSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    verifyDataUpdate(testTime - 1, oldMembers, oldRoles, oldHistory);
    Object[][] totalHistory = new Object[oldHistory.length + roleHistoryTestData.length][6];
    System.arraycopy(oldHistory, 0, totalHistory, 0, oldHistory.length);
    System.arraycopy(roleHistoryTestData, 0, totalHistory, oldHistory.length, roleHistoryTestData.length);
    verifyDataUpdate(testTime, membersTestData, rolesTestData, totalHistory);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MEMBERSHIP);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MEMBERSHIP);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
