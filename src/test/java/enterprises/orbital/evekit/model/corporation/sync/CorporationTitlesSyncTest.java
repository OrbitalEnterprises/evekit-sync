package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.CorporationTitle;
import enterprises.orbital.evekit.model.corporation.Role;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IRole;
import enterprises.orbital.evexmlapi.crp.ITitle;

public class CorporationTitlesSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  // 0 titleID
  // 1 titleName
  // 2 list of grantable roles
  // 3 list of grantable roles at base
  // 4 list of grantable roles at hq
  // 5 list of grantable roles at other
  // 6 list of roles
  // 7 list of roles at base
  // 8 list of roles at hq
  // 9 list of roles at other
  static Object[][]      testData;

  static {
    // Set up test data
    int numMembers = 100 + TestBase.getRandomInt(100);
    testData = new Object[numMembers][];

    for (int member = 0; member < numMembers; member++) {
      testData[member] = new Object[10];
      testData[member][0] = TestBase.getUniqueRandomLong();
      testData[member][1] = TestBase.getRandomText(100);

      // Create a random set of roles and assign them randomly to the categories.
      int numRoles = 10 + TestBase.getRandomInt(10);
      Object[][] roles = new Object[numRoles][3];
      for (int role = 0; role < numRoles; role++) {
        roles[role][0] = TestBase.getUniqueRandomLong();
        roles[role][1] = TestBase.getRandomText(1000);
        roles[role][2] = TestBase.getRandomText(50);
      }

      // Eight total role sets to generate data for.
      for (int i = 0; i < 8; i++) {
        int roleCount = 1 + TestBase.getRandomInt(numRoles - 1);
        testData[member][2 + i] = new Object[roleCount][];
        Set<Integer> choices = new HashSet<Integer>();
        while (choices.size() < roleCount) {
          choices.add(TestBase.getRandomInt(roleCount));
        }
        int j = 0;
        for (Integer x : choices) {
          ((Object[]) testData[member][2 + i])[j++] = roles[x];
        }
      }
    }
  }

  protected static Collection<IRole> genRoles(Object[] instanceData, final int index) {
    final Object[][] roleData = (Object[][]) instanceData[index];
    Collection<IRole> roles = new HashSet<IRole>();
    for (int i = 0; i < roleData.length; i++) {
      final Object[] nextRole = roleData[i];
      roles.add(new IRole() {

        @Override
        public long getRoleID() {
          return (Long) nextRole[0];
        }

        @Override
        public String getRoleDescription() {
          return (String) nextRole[1];
        }

        @Override
        public String getRoleName() {
          return (String) nextRole[2];
        }

        @Override
        public boolean equals(Object other) {
          if (other instanceof IRole) {
            IRole check = (IRole) other;
            return getRoleID() == check.getRoleID()
                && (getRoleDescription() == check.getRoleDescription()
                    || (getRoleDescription() != null && getRoleDescription().equals(check.getRoleDescription())))
                && (getRoleName() == check.getRoleName() || (getRoleName() != null && getRoleName().equals(check.getRoleName())));
          } else {
            return false;
          }
        }

        @Override
        public int hashCode() {
          int code = (int) getRoleID();
          if (getRoleName() != null) {
            code += getRoleName().hashCode();
          }
          if (getRoleDescription() != null) {
            code += getRoleDescription().hashCode();
          }
          return code;
        }
      });
    }
    return roles;
  }

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  @Override
  @After
  public void teardown() throws Exception {
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_GRANTABLEROLES")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_GRANTABLEROLESATBASE")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_GRANTABLEROLESATHQ")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_GRANTABLEROLESATOTHER")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_ROLES")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_ROLESATBASE")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_ROLESATHQ")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CORPORATIONTITLE_ROLESATOTHER")
                                                                            .executeUpdate());
    super.teardown();
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<ITitle> titles = new ArrayList<ITitle>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      titles.add(new ITitle() {

        @Override
        public Collection<IRole> getRolesAtOther() {
          return genRoles(instanceData, 9);
        }

        @Override
        public Collection<IRole> getRolesAtHQ() {
          return genRoles(instanceData, 8);
        }

        @Override
        public Collection<IRole> getRolesAtBase() {
          return genRoles(instanceData, 7);
        }

        @Override
        public Collection<IRole> getRoles() {
          return genRoles(instanceData, 6);
        }

        @Override
        public Collection<IRole> getGrantableRolesAtOther() {
          return genRoles(instanceData, 5);
        }

        @Override
        public Collection<IRole> getGrantableRolesAtHQ() {
          return genRoles(instanceData, 4);
        }

        @Override
        public Collection<IRole> getGrantableRolesAtBase() {
          return genRoles(instanceData, 3);
        }

        @Override
        public Collection<IRole> getGrantableRoles() {
          return genRoles(instanceData, 2);
        }

        @Override
        public long getTitleID() {
          return (Long) instanceData[0];
        }

        @Override
        public String getTitleName() {
          return (String) instanceData[1];
        }
      });
    }

    EasyMock.expect(mockServer.requestTitles()).andReturn(titles);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  static protected void checkRoles(String msg, Collection<IRole> ref, Collection<Long> stored, int delta) {
    Assert.assertEquals(msg, ref.size(), stored.size());
    for (IRole nextRole : ref) {
      Assert.assertTrue(msg + " roleID = " + (nextRole.getRoleID() + delta), stored.contains(nextRole.getRoleID() + delta));
    }
  }

  static protected void addRoles(long time, SynchronizedEveAccount owner, Collection<IRole> ref, Collection<Long> stored, int delta) throws IOException {
    assert stored.size() == 0;
    for (IRole next : ref) {
      stored.add(next.getRoleID() + delta);
      Role newRole = new Role(next.getRoleID() + delta, next.getRoleDescription(), next.getRoleName());
      newRole.setup(owner, time);
      newRole = CachedData.update(newRole);
    }
  }

  // Test update with all new titles.
  @Test
  public void testCorporationTitlesSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationTitlesSync.syncCorporationTitles(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify titles were added correctly.
    Collection<IRole> allRoles = new HashSet<IRole>();
    for (int i = 0; i < testData.length; i++) {
      CorporationTitle next = CorporationTitle.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getTitleID());
      Assert.assertEquals(testData[i][1], next.getTitleName());
      checkRoles("grantable roles", genRoles(testData[i], 2), next.getGrantableRoles(), 0);
      checkRoles("grantable roles at base", genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 0);
      checkRoles("grantable roles at hq", genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 0);
      checkRoles("grantable roles at other", genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 0);
      checkRoles("roles", genRoles(testData[i], 6), next.getRoles(), 0);
      checkRoles("roles at base", genRoles(testData[i], 7), next.getRolesAtBase(), 0);
      checkRoles("roles at hq", genRoles(testData[i], 8), next.getRolesAtHQ(), 0);
      checkRoles("roles at other", genRoles(testData[i], 9), next.getRolesAtOther(), 0);
      for (int j = 2; j < 10; j++) {
        allRoles.addAll(genRoles(testData[i], j));
      }
    }

    // Verify set of roles is identical to the roles included in the populated set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (Role check : Role.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());
    }
    checkRoles("all roles", allRoles, existingIDs, 0);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getTitlesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpTitlesStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpTitlesDetail());
  }

  // Test update with titles already populated.
  @Test
  public void testCorporationTitlesSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing titles, slightly modified.
    for (int i = 0; i < testData.length; i++) {
      CorporationTitle next = new CorporationTitle((Long) testData[i][0], (String) testData[i][1]);
      addRoles(testTime, syncAccount, genRoles(testData[i], 2), next.getGrantableRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 6), next.getRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 7), next.getRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 8), next.getRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 9), next.getRolesAtOther(), 5);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationTitlesSync.syncCorporationTitles(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify title has been changed to down loaded version
    Collection<IRole> allRoles = new HashSet<IRole>();
    for (int i = 0; i < testData.length; i++) {
      CorporationTitle next = CorporationTitle.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getTitleID());
      Assert.assertEquals(testData[i][1], next.getTitleName());
      checkRoles("grantable roles", genRoles(testData[i], 2), next.getGrantableRoles(), 0);
      checkRoles("grantable roles at base", genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 0);
      checkRoles("grantable roles at hq", genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 0);
      checkRoles("grantable roles at other", genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 0);
      checkRoles("roles", genRoles(testData[i], 6), next.getRoles(), 0);
      checkRoles("roles at base", genRoles(testData[i], 7), next.getRolesAtBase(), 0);
      checkRoles("roles at hq", genRoles(testData[i], 8), next.getRolesAtHQ(), 0);
      checkRoles("roles at other", genRoles(testData[i], 9), next.getRolesAtOther(), 0);
      for (int j = 2; j < 10; j++) {
        Collection<IRole> checkRoles = genRoles(testData[i], j);
        allRoles.addAll(checkRoles);
        for (IRole mod : checkRoles) {
          final IRole proxy = mod;
          allRoles.add(new IRole() {

            @Override
            public long getRoleID() {
              return proxy.getRoleID() + 5;
            }

            @Override
            public String getRoleName() {
              return proxy.getRoleName();
            }

            @Override
            public String getRoleDescription() {
              return proxy.getRoleDescription();
            }

            @Override
            public boolean equals(Object other) {
              if (other instanceof IRole) {
                IRole check = (IRole) other;
                return getRoleID() == check.getRoleID()
                    && (getRoleDescription() == check.getRoleDescription()
                        || (getRoleDescription() != null && getRoleDescription().equals(check.getRoleDescription())))
                    && (getRoleName() == check.getRoleName() || (getRoleName() != null && getRoleName().equals(check.getRoleName())));
              } else {
                return false;
              }
            }

            @Override
            public int hashCode() {
              int code = (int) getRoleID();
              if (getRoleName() != null) {
                code += getRoleName().hashCode();
              }
              if (getRoleDescription() != null) {
                code += getRoleDescription().hashCode();
              }
              return code;
            }
          });
        }
      }
    }

    // There should now be two sets of roles since the initial role set has different role IDs than the sync'd set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (Role check : Role.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());

    }
    checkRoles("all roles", allRoles, existingIDs, 0);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getTitlesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpTitlesStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpTitlesDetail());
  }

  // Test skips update when already updated.
  @Test
  public void testCorporationTitlesSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing titles, slightly modified.
    for (int i = 0; i < testData.length; i++) {
      CorporationTitle next = new CorporationTitle((Long) testData[i][0], (String) testData[i][1]);
      addRoles(testTime, syncAccount, genRoles(testData[i], 2), next.getGrantableRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 6), next.getRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 7), next.getRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 8), next.getRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 9), next.getRolesAtOther(), 5);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setCorpTitlesStatus(SyncState.UPDATED);
    tracker.setCorpTitlesDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setTitlesExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationTitlesSync.syncCorporationTitles(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify titles unchanged.
    Collection<IRole> allRoles = new HashSet<IRole>();
    for (int i = 0; i < testData.length; i++) {
      CorporationTitle next = CorporationTitle.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getTitleID());
      Assert.assertEquals(testData[i][1], next.getTitleName());
      checkRoles("grantable roles", genRoles(testData[i], 2), next.getGrantableRoles(), 5);
      checkRoles("grantable roles at base", genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 5);
      checkRoles("grantable roles at hq", genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 5);
      checkRoles("grantable roles at other", genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 5);
      checkRoles("roles", genRoles(testData[i], 6), next.getRoles(), 5);
      checkRoles("roles at base", genRoles(testData[i], 7), next.getRolesAtBase(), 5);
      checkRoles("roles at hq", genRoles(testData[i], 8), next.getRolesAtHQ(), 5);
      checkRoles("roles at other", genRoles(testData[i], 9), next.getRolesAtOther(), 5);
      for (int j = 2; j < 10; j++) {
        allRoles.addAll(genRoles(testData[i], j));
      }
    }

    // Verify set of roles is identical to the roles included in the populated set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (Role check : Role.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());
    }
    checkRoles("all roles", allRoles, existingIDs, 5);

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getTitlesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpTitlesStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorpTitlesDetail());
  }

}
