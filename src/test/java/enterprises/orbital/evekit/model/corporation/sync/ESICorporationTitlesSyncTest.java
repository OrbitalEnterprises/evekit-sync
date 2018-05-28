package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMembersTitles200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdRoles200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdRolesHistory200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdTitles200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.*;
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
public class ESICorporationTitlesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CorporationApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] titlesTestData;
  private static Object[][] memberTitlesTestData;

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

    int size = 100 + TestBase.getRandomInt(100);
    int rolesLen = GetCorporationsCorporationIdTitles200Ok.RolesEnum.values().length;
    titlesTestData = new Object[size][3];
    for (int i = 0; i < size; i++) {
      // CorporationTitle test data
      // 0 int titleID
      // 1 String titleName
      // 2 Object[][] roles
      titlesTestData[i][0] = TestBase.getUniqueRandomInteger();
      titlesTestData[i][1] = TestBase.getRandomText(50);
      int roleCount = 10 + TestBase.getRandomInt(10);
      Object[][] roleList = new Object[roleCount][2];
      titlesTestData[i][2] = roleList;
      Set<Pair<String, Integer>> usedRoles = new HashSet<>();
      for (int j = 0; j < roleCount; j++) {
        String nextRoleName = GetCorporationsCorporationIdTitles200Ok.RolesEnum.values()[TestBase.getRandomInt(
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
    memberTitlesTestData = new Object[size][2];
    for (int i = 0; i < size; i++) {
      // MemberTitle test data
      // 0 int characterID
      // 1 Object[] titles
      memberTitlesTestData[i][0] = TestBase.getUniqueRandomInteger();
      int titleCount = 3 + TestBase.getRandomInt(3);
      Object[] titleList = new Object[titleCount];
      memberTitlesTestData[i][1] = titleList;
      Set<Integer> seenTitles = new HashSet<>();
      for (int j = 0; j < titleCount; j++) {
        int nextTitleID = TestBase.getRandomInt();
        while (seenTitles.contains(nextTitleID)) {
          nextTitleID = TestBase.getRandomInt();
        }
        titleList[j] = nextTitleID;
        seenTitles.add(nextTitleID);
      }
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_TITLES, 1234L, null);

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
                                                        .createQuery("DELETE FROM CorporationTitle ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CorporationTitleRole ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MemberTitle ")
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

    // Setup Corporation Title list
    List<GetCorporationsCorporationIdTitles200Ok> titleList = new ArrayList<>();
    for (Object[] next : titlesTestData) {
      GetCorporationsCorporationIdTitles200Ok obj = new GetCorporationsCorporationIdTitles200Ok();
      obj.setTitleId((int) next[0]);
      obj.setName((String) next[1]);
      for (Object[] role : (Object[][]) next[2]) {
        String roleName = (String) role[0];
        int mask = (int) role[1];
        switch (mask) {
          case AT_BASE:
            obj.getRolesAtBase()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.RolesAtBaseEnum.values(), roleName));
            break;
          case AT_HQ:
            obj.getRolesAtHq()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.RolesAtHqEnum.values(), roleName));
            break;
          case AT_OTHER:
            obj.getRolesAtOther()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.RolesAtOtherEnum.values(), roleName));
            break;
          case GRANTABLE:
            obj.getGrantableRoles()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.GrantableRolesEnum.values(), roleName));
            break;
          case GRANTABLE | AT_BASE:
            obj.getGrantableRolesAtBase()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.GrantableRolesAtBaseEnum.values(), roleName));
            break;
          case GRANTABLE | AT_HQ:
            obj.getGrantableRolesAtHq()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.GrantableRolesAtHqEnum.values(), roleName));
            break;
          case GRANTABLE | AT_OTHER:
            obj.getGrantableRolesAtOther()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.GrantableRolesAtOtherEnum.values(), roleName));
            break;
          default:
            obj.getRoles()
               .add(findEnum(GetCorporationsCorporationIdTitles200Ok.RolesEnum.values(), roleName));
        }
      }
      titleList.add(obj);
    }

    {
      ApiResponse<List<GetCorporationsCorporationIdTitles200Ok>> apir = new ApiResponse<>(200,
                                                                                         createHeaders("Expires",
                                                                                                       "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                         titleList);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdTitlesWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }

    // Setup MemberTitle list
    List<GetCorporationsCorporationIdMembersTitles200Ok> memberList = new ArrayList<>();
    for (Object[] next : memberTitlesTestData) {
      GetCorporationsCorporationIdMembersTitles200Ok obj = new GetCorporationsCorporationIdMembersTitles200Ok();
      obj.setCharacterId((int) next[0]);
      for (Object tt : (Object[]) next[1]) {
        obj.getTitles().add((int) tt);
      }
      memberList.add(obj);
    }

    {
      ApiResponse<List<GetCorporationsCorporationIdMembersTitles200Ok>> apir = new ApiResponse<>(200,
                                                                                          createHeaders("Expires",
                                                                                                        "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                          memberList);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdMembersTitlesWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCorporationApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] tData, Object[][] mData) throws Exception {

    // Verify corporation titles data
    List<CorporationTitle> storedTitles = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        CorporationTitle.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR));

    List<CorporationTitleRole> storedTitleRoles = AbstractESIAccountSync.retrieveAll(time, (long contid, AttributeSelector at) ->
        CorporationTitleRole.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                     AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                     AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                     AbstractESIAccountSync.ANY_SELECTOR));

    Set<Pair<Integer, String>> titleSet = new HashSet<>();
    Set<Triple<Integer, String, Integer>> titleRoleSet = new HashSet<>();
    for (Object[] next : tData) {
      titleSet.add(Pair.of((int) next[0], (String) next[1]));
      for (Object[] role : (Object[][]) next[2]) {
        titleRoleSet.add(Triple.of((int) next[0], (String) role[0], (int) role[1]));
      }
    }

    Assert.assertEquals(titleSet.size(), storedTitles.size());
    Assert.assertEquals(titleRoleSet.size(), storedTitleRoles.size());

    for (CorporationTitle next : storedTitles) {
      Assert.assertTrue(titleSet.contains(Pair.of(next.getTitleID(), next.getTitleName())));
    }

    for (CorporationTitleRole next : storedTitleRoles) {
      int mask = next.isGrantable() ? GRANTABLE : 0;
      mask |= next.isAtBase() ? AT_BASE : 0;
      mask |= next.isAtHQ() ? AT_HQ : 0;
      mask |= next.isAtOther() ? AT_OTHER : 0;
      Assert.assertTrue(titleRoleSet.contains(Triple.of(next.getTitleID(), next.getRoleName(), mask)));
    }

    // Verify mmeber title data
    List<MemberTitle> storedMembers = AbstractESIAccountSync.retrieveAll(time,
                                                                               (long contid, AttributeSelector at) ->
                                                                                   MemberTitle.accessQuery(
                                                                                       corpSyncAccount, contid, 1000,
                                                                                       false, at,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR));

    Set<Pair<Integer, Integer>> memberTitleSet = new HashSet<>();
    for (Object[] next : mData) {
      int charID = (int) next[0];
      for (Object tt : (Object[]) next[1]) {
        memberTitleSet.add(Pair.of(charID, (int) tt));
      }
    }

    Assert.assertEquals(memberTitleSet.size(), storedMembers.size());

    for (MemberTitle next : storedMembers) {
      Assert.assertTrue(memberTitleSet.contains(Pair.of(next.getCharacterID(), next.getTitleID())));
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationTitlesSync sync = new ESICorporationTitlesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, titlesTestData, memberTitlesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_TITLES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_TITLES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Modify half the corporation so we can test both updates and EOL.
    Object[][] oldTitles = new Object[titlesTestData.length][3];
    int rolesLen = GetCorporationsCorporationIdTitles200Ok.RolesEnum.values().length;
    for (int i = 0; i < oldTitles.length; i++) {
      oldTitles[i][0] = i % 2 == 0 ? titlesTestData[i][0] : TestBase.getUniqueRandomInteger();
      oldTitles[i][1] = TestBase.getRandomText(50);
      int roleCount = 10 + TestBase.getRandomInt(10);
      Object[][] roleList = new Object[roleCount][2];
      oldTitles[i][2] = roleList;
      Set<Pair<String, Integer>> usedRoles = new HashSet<>();

      for (int j = 0; j < roleCount; j++) {
        String nextRoleName = GetCorporationsCorporationIdTitles200Ok.RolesEnum.values()[TestBase.getRandomInt(
            rolesLen)].toString();
        int nextMask = makeMask();
        while (usedRoles.contains(Pair.of(nextRoleName, nextMask))) {
          nextRoleName = GetCorporationsCorporationIdTitles200Ok.RolesEnum.values()[TestBase.getRandomInt(
              rolesLen)].toString();
          nextMask = makeMask();
        }
        usedRoles.add(Pair.of(nextRoleName, nextMask));
        roleList[j][0] = nextRoleName;
        roleList[j][1] = nextMask;

        CorporationTitleRole obj = new CorporationTitleRole((int) oldTitles[i][0],
                                                            nextRoleName,
                                        (nextMask & GRANTABLE) > 0,
                                        (nextMask & AT_HQ) > 0,
                                        (nextMask & AT_BASE) > 0,
                                        (nextMask & AT_OTHER) > 0);
        obj.setup(corpSyncAccount, testTime - 1);
        CachedData.update(obj);
      }

      CorporationTitle obj = new CorporationTitle((int) oldTitles[i][0], (String) oldTitles[i][1]);
      obj.setup(corpSyncAccount, testTime - 1);
      CachedData.update(obj);
    }

    // Modify half the member titles so we can test both updates and EOL
    Object[][] oldMembers = new Object[memberTitlesTestData.length][2];
    for (int i = 0; i < oldMembers.length; i++) {
      oldMembers[i][0] = i % 2 == 0 ? memberTitlesTestData[i][0] : TestBase.getUniqueRandomInteger();
      int titleCount = 3 + TestBase.getRandomInt(3);
      Object[] titleList = new Object[titleCount];
      oldMembers[i][1] = titleList;
      Set<Integer> usedTitles = new HashSet<>();

      for (int j = 0; j < titleCount; j++) {
        int nextTitleID = TestBase.getRandomInt();
        while (usedTitles.contains(nextTitleID)) {
          nextTitleID = TestBase.getRandomInt();
        }
        usedTitles.add(nextTitleID);
        titleList[j] = nextTitleID;

        MemberTitle obj = new MemberTitle((int) oldMembers[i][0], nextTitleID);
        obj.setup(corpSyncAccount, testTime - 1);
        CachedData.update(obj);
      }
    }


    // Perform the sync
    ESICorporationTitlesSync sync = new ESICorporationTitlesSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    verifyDataUpdate(testTime - 1, oldTitles, oldMembers);
    verifyDataUpdate(testTime, titlesTestData, memberTitlesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_TITLES);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_TITLES);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
