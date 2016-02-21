package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.easymock.EasyMock;
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
import enterprises.orbital.evekit.model.corporation.MemberSecurity;
import enterprises.orbital.evekit.model.corporation.SecurityRole;
import enterprises.orbital.evekit.model.corporation.SecurityTitle;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberSecurity;
import enterprises.orbital.evexmlapi.crp.ISecurityRole;
import enterprises.orbital.evexmlapi.crp.ISecurityTitle;

public class CorporationMemberSecuritySyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  // characterID
  // name
  // list of grantable roles
  // list of grantable roles at base
  // list of grantable roles at hq
  // list of grantable roles at other
  // list of roles
  // list of roles at base
  // list of roles at hq
  // list of roles at other
  // list of security titles
  static Object[][]      testData;

  static {
    // Set up test data
    int numMembers = 100 + TestBase.getRandomInt(100);
    testData = new Object[numMembers][];

    for (int member = 0; member < numMembers; member++) {
      testData[member] = new Object[11];
      testData[member][0] = TestBase.getUniqueRandomLong();
      testData[member][1] = TestBase.getRandomText(100);

      // Create a random set of roles and assign them randomly to the categories.
      int numRoles = 10 + TestBase.getRandomInt(10);
      Object[][] roles = new Object[numRoles][2];
      for (int role = 0; role < numRoles; role++) {
        roles[role][0] = TestBase.getUniqueRandomLong();
        roles[role][1] = TestBase.getRandomText(50);
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

      // Generate random security titles
      int numTitles = 1 + TestBase.getRandomInt(5);
      testData[member][10] = new Object[numTitles][2];
      for (int title = 0; title < numTitles; title++) {
        ((Object[][]) testData[member][10])[title][0] = TestBase.getUniqueRandomLong();
        ((Object[][]) testData[member][10])[title][1] = TestBase.getRandomText(100);
      }
    }
  }

  protected static Collection<ISecurityRole> genRoles(Object[] instanceData, final int index) {
    final Object[][] roleData = (Object[][]) instanceData[index];
    Collection<ISecurityRole> roles = new HashSet<ISecurityRole>();
    for (int i = 0; i < roleData.length; i++) {
      final Object[] nextRole = roleData[i];
      roles.add(new ISecurityRole() {

        @Override
        public long getRoleID() {
          return (Long) nextRole[0];
        }

        @Override
        public String getRoleName() {
          return (String) nextRole[1];
        }

        @Override
        public boolean equals(Object other) {
          if (other instanceof ISecurityRole) {
            ISecurityRole check = (ISecurityRole) other;
            return getRoleID() == check.getRoleID()
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
          return code;
        }
      });
    }
    return roles;
  }

  protected static Collection<ISecurityTitle> genTitles(Object[] instanceData) {
    final Object[][] titleData = (Object[][]) instanceData[10];
    Collection<ISecurityTitle> titles = new HashSet<ISecurityTitle>();
    for (int i = 0; i < titleData.length; i++) {
      final Object[] nextTitle = titleData[i];
      titles.add(new ISecurityTitle() {

        @Override
        public long getTitleID() {
          return (Long) nextTitle[0];
        }

        @Override
        public String getTitleName() {
          return (String) nextTitle[1];
        }

        @Override
        public boolean equals(Object other) {
          if (other instanceof ISecurityTitle) {
            ISecurityTitle check = (ISecurityTitle) other;
            return getTitleID() == check.getTitleID()
                && (getTitleName() == check.getTitleName() || (getTitleName() != null && getTitleName().equals(check.getTitleName())));
          } else {
            return false;
          }
        }

        @Override
        public int hashCode() {
          int code = (int) getTitleID();
          if (getTitleName() != null) {
            code += getTitleName().hashCode();
          }
          return code;
        }
      });
    }
    return titles;
  }

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true, 1234, "abcd", 5678, "charname", 8765, "corpname");
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IMemberSecurity> notes = new ArrayList<IMemberSecurity>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      notes.add(new IMemberSecurity() {

        @Override
        public Collection<ISecurityTitle> getTitles() {
          return genTitles(instanceData);
        }

        @Override
        public Collection<ISecurityRole> getRolesAtOther() {
          return genRoles(instanceData, 9);
        }

        @Override
        public Collection<ISecurityRole> getRolesAtHQ() {
          return genRoles(instanceData, 8);
        }

        @Override
        public Collection<ISecurityRole> getRolesAtBase() {
          return genRoles(instanceData, 7);
        }

        @Override
        public Collection<ISecurityRole> getRoles() {
          return genRoles(instanceData, 6);
        }

        @Override
        public String getName() {
          return (String) instanceData[1];
        }

        @Override
        public Collection<ISecurityRole> getGrantableRolesAtOther() {
          return genRoles(instanceData, 5);
        }

        @Override
        public Collection<ISecurityRole> getGrantableRolesAtHQ() {
          return genRoles(instanceData, 4);
        }

        @Override
        public Collection<ISecurityRole> getGrantableRolesAtBase() {
          return genRoles(instanceData, 3);
        }

        @Override
        public Collection<ISecurityRole> getGrantableRoles() {
          return genRoles(instanceData, 2);
        }

        @Override
        public long getCharacterID() {
          return (Long) instanceData[0];
        }
      });
    }

    EasyMock.expect(mockServer.requestMemberSecurity()).andReturn(notes);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  static protected void checkRoles(String msg, Collection<ISecurityRole> ref, Collection<Long> stored, int delta) {
    Assert.assertEquals(msg, ref.size(), stored.size());
    for (ISecurityRole nextRole : ref) {
      Assert.assertTrue(msg + " roleID = " + (nextRole.getRoleID() + delta), stored.contains(nextRole.getRoleID() + delta));
    }
  }

  static protected void addRoles(long time, SynchronizedEveAccount owner, Collection<ISecurityRole> ref, Collection<Long> stored, int delta)
    throws IOException {
    assert stored.size() == 0;
    for (ISecurityRole next : ref) {
      stored.add(next.getRoleID() + delta);
      SecurityRole newRole = new SecurityRole(next.getRoleID() + delta, next.getRoleName());
      newRole.setup(owner, time);
      newRole = CachedData.updateData(newRole);
    }
  }

  static protected void checkTitles(String msg, Collection<ISecurityTitle> ref, Collection<Long> stored, int delta) {
    Assert.assertEquals(msg, ref.size(), stored.size());
    for (ISecurityTitle nextTitle : ref) {
      Assert.assertTrue(msg + " titleID = " + (nextTitle.getTitleID() + delta), stored.contains(nextTitle.getTitleID() + delta));
    }
  }

  static protected void addTitles(long time, SynchronizedEveAccount owner, Collection<ISecurityTitle> ref, Collection<Long> stored, int delta)
    throws IOException {
    assert stored.size() == 0;
    for (ISecurityTitle next : ref) {
      stored.add(next.getTitleID() + delta);
      SecurityTitle newTitle = new SecurityTitle(next.getTitleID() + delta, next.getTitleName());
      newTitle.setup(owner, time);
      newTitle = CachedData.updateData(newTitle);
    }
  }

  // Test update with all new security records.
  @Test
  public void testCorporationMemberSecuritySyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberSecuritySync.syncCorporationMemberSecurity(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify security records were added correctly.
    Collection<ISecurityRole> allRoles = new HashSet<ISecurityRole>();
    Collection<ISecurityTitle> allTitles = new HashSet<ISecurityTitle>();
    for (int i = 0; i < testData.length; i++) {
      MemberSecurity next = MemberSecurity.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getCharacterID());
      Assert.assertEquals(testData[i][1], next.getName());
      checkRoles("grantable roles", genRoles(testData[i], 2), next.getGrantableRoles(), 0);
      checkRoles("grantable roles at base", genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 0);
      checkRoles("grantable roles at hq", genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 0);
      checkRoles("grantable roles at other", genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 0);
      checkRoles("roles", genRoles(testData[i], 6), next.getRoles(), 0);
      checkRoles("roles at base", genRoles(testData[i], 7), next.getRolesAtBase(), 0);
      checkRoles("roles at hq", genRoles(testData[i], 8), next.getRolesAtHQ(), 0);
      checkRoles("roles at other", genRoles(testData[i], 9), next.getRolesAtOther(), 0);
      checkTitles("titles", genTitles(testData[i]), next.getTitles(), 0);
      for (int j = 2; j < 10; j++) {
        allRoles.addAll(genRoles(testData[i], j));
      }
      allTitles.addAll(genTitles(testData[i]));
    }

    // Verify set of roles is identical to the roles included in the populated set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (SecurityRole check : SecurityRole.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());
    }
    checkRoles("all roles", allRoles, existingIDs, 0);

    // Verify set of titles is identical to the titles included in the populated set.
    existingIDs.clear();
    for (SecurityTitle check : SecurityTitle.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getTitleID());
    }
    checkTitles("all titles", allTitles, existingIDs, 0);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberSecurityExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityDetail());
  }

  // Test update with security already populated
  @Test
  public void testCorporationMemberSecuritySyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing member security, slightly modified
    for (int i = 0; i < testData.length; i++) {
      MemberSecurity next = new MemberSecurity((Long) testData[i][0], (String) testData[i][1]);
      addRoles(testTime, syncAccount, genRoles(testData[i], 2), next.getGrantableRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 6), next.getRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 7), next.getRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 8), next.getRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 9), next.getRolesAtOther(), 5);
      addTitles(testTime, syncAccount, genTitles(testData[i]), next.getTitles(), 5);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberSecuritySync.syncCorporationMemberSecurity(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify security has been changed to down loaded version
    Collection<ISecurityRole> allRoles = new HashSet<ISecurityRole>();
    Collection<ISecurityTitle> allTitles = new HashSet<ISecurityTitle>();
    for (int i = 0; i < testData.length; i++) {
      MemberSecurity next = MemberSecurity.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getCharacterID());
      Assert.assertEquals(testData[i][1], next.getName());
      checkRoles("grantable roles", genRoles(testData[i], 2), next.getGrantableRoles(), 0);
      checkRoles("grantable roles at base", genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 0);
      checkRoles("grantable roles at hq", genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 0);
      checkRoles("grantable roles at other", genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 0);
      checkRoles("roles", genRoles(testData[i], 6), next.getRoles(), 0);
      checkRoles("roles at base", genRoles(testData[i], 7), next.getRolesAtBase(), 0);
      checkRoles("roles at hq", genRoles(testData[i], 8), next.getRolesAtHQ(), 0);
      checkRoles("roles at other", genRoles(testData[i], 9), next.getRolesAtOther(), 0);
      checkTitles("titles", genTitles(testData[i]), next.getTitles(), 0);
      for (int j = 2; j < 10; j++) {
        Collection<ISecurityRole> checkRoles = genRoles(testData[i], j);
        allRoles.addAll(checkRoles);
        for (ISecurityRole mod : checkRoles) {
          final ISecurityRole proxy = mod;
          allRoles.add(new ISecurityRole() {

            @Override
            public long getRoleID() {
              return proxy.getRoleID() + 5;
            }

            @Override
            public String getRoleName() {
              return proxy.getRoleName();
            }

            @Override
            public boolean equals(Object other) {
              if (other instanceof ISecurityRole) {
                ISecurityRole check = (ISecurityRole) other;
                return getRoleID() == check.getRoleID()
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
              return code;
            }
          });
        }
      }
      Collection<ISecurityTitle> checkTitles = genTitles(testData[i]);
      allTitles.addAll(checkTitles);
      for (ISecurityTitle mod : checkTitles) {
        final ISecurityTitle proxy = mod;
        allTitles.add(new ISecurityTitle() {

          @Override
          public long getTitleID() {
            return proxy.getTitleID() + 5;
          }

          @Override
          public String getTitleName() {
            return proxy.getTitleName();
          }

          @Override
          public boolean equals(Object other) {
            if (other instanceof ISecurityTitle) {
              ISecurityTitle check = (ISecurityTitle) other;
              return getTitleID() == check.getTitleID()
                  && (getTitleName() == check.getTitleName() || (getTitleName() != null && getTitleName().equals(check.getTitleName())));
            } else {
              return false;
            }
          }

          @Override
          public int hashCode() {
            int code = (int) getTitleID();
            if (getTitleName() != null) {
              code += getTitleName().hashCode();
            }
            return code;
          }
        });
      }
    }

    // There should now be two sets of roles since the initial role set has different role IDs than the sync'd set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (SecurityRole check : SecurityRole.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());

    }
    checkRoles("all roles", allRoles, existingIDs, 0);

    // Verify set of titles is identical to the titles included in the populated set.
    existingIDs.clear();
    for (SecurityTitle check : SecurityTitle.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getTitleID());
    }
    checkTitles("all titles", allTitles, existingIDs, 0);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberSecurityExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationMemberSecuritySyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing member security, slightly modified
    for (int i = 0; i < testData.length; i++) {
      MemberSecurity next = new MemberSecurity((Long) testData[i][0], (String) testData[i][1]);
      addRoles(testTime, syncAccount, genRoles(testData[i], 2), next.getGrantableRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 6), next.getRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 7), next.getRolesAtBase(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 8), next.getRolesAtHQ(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 9), next.getRolesAtOther(), 5);
      addTitles(testTime, syncAccount, genTitles(testData[i]), next.getTitles(), 5);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setMemberSecurityStatus(SyncState.UPDATED);
    tracker.setMemberSecurityDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setMemberSecurityExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberSecuritySync.syncCorporationMemberSecurity(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify member security unchanged
    Collection<ISecurityRole> allRoles = new HashSet<ISecurityRole>();
    Collection<ISecurityTitle> allTitles = new HashSet<ISecurityTitle>();
    for (int i = 0; i < testData.length; i++) {
      MemberSecurity next = MemberSecurity.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getCharacterID());
      Assert.assertEquals(testData[i][1], next.getName());
      checkRoles("grantable roles", genRoles(testData[i], 2), next.getGrantableRoles(), 5);
      checkRoles("grantable roles at base", genRoles(testData[i], 3), next.getGrantableRolesAtBase(), 5);
      checkRoles("grantable roles at hq", genRoles(testData[i], 4), next.getGrantableRolesAtHQ(), 5);
      checkRoles("grantable roles at other", genRoles(testData[i], 5), next.getGrantableRolesAtOther(), 5);
      checkRoles("roles", genRoles(testData[i], 6), next.getRoles(), 5);
      checkRoles("roles at base", genRoles(testData[i], 7), next.getRolesAtBase(), 5);
      checkRoles("roles at hq", genRoles(testData[i], 8), next.getRolesAtHQ(), 5);
      checkRoles("roles at other", genRoles(testData[i], 9), next.getRolesAtOther(), 5);
      checkTitles("titles", genTitles(testData[i]), next.getTitles(), 5);
      for (int j = 2; j < 10; j++) {
        allRoles.addAll(genRoles(testData[i], j));
      }
      allTitles.addAll(genTitles(testData[i]));
    }

    // Verify set of roles is identical to the roles included in the populated set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (SecurityRole check : SecurityRole.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());
    }
    checkRoles("all roles", allRoles, existingIDs, 5);

    // Verify set of titles is identical to the titles included in the populated set.
    existingIDs.clear();
    for (SecurityTitle check : SecurityTitle.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getTitleID());
    }
    checkTitles("all titles", allTitles, existingIDs, 5);

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getMemberSecurityExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityDetail());
  }

}
