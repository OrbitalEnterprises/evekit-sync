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
import enterprises.orbital.evekit.model.corporation.MemberSecurityLog;
import enterprises.orbital.evekit.model.corporation.SecurityRole;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberSecurityLog;
import enterprises.orbital.evexmlapi.crp.ISecurityRole;

public class CorporationMemberSecurityLogSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  // changeTime
  // changedCharacterID
  // changedCharacterName
  // issuerID
  // issuerName
  // roleLocationType
  // set of old roles
  // set of new roles
  static Object[][]      testData;

  static {
    // Set up test data
    int numMembers = 100 + TestBase.getRandomInt(100);
    testData = new Object[numMembers][];

    for (int member = 0; member < numMembers; member++) {
      testData[member] = new Object[8];
      testData[member][0] = TestBase.getUniqueRandomLong();
      testData[member][1] = TestBase.getUniqueRandomLong();
      testData[member][2] = TestBase.getRandomText(100);
      testData[member][3] = TestBase.getRandomLong();
      testData[member][4] = TestBase.getRandomText(100);
      testData[member][5] = TestBase.getRandomText(100);

      // Create a random set of roles and assign them randomly to the categories.
      int numRoles = 10 + TestBase.getRandomInt(10);
      Object[][] roles = new Object[numRoles][2];
      for (int role = 0; role < numRoles; role++) {
        roles[role][0] = TestBase.getUniqueRandomLong();
        roles[role][1] = TestBase.getRandomText(50);
      }

      // Two total role sets to generate data for.
      for (int i = 0; i < 2; i++) {
        int roleCount = 1 + TestBase.getRandomInt(numRoles - 1);
        testData[member][6 + i] = new Object[roleCount][];
        Set<Integer> choices = new HashSet<Integer>();
        while (choices.size() < roleCount) {
          choices.add(TestBase.getRandomInt(roleCount));
        }
        int j = 0;
        for (Integer x : choices) {
          ((Object[]) testData[member][6 + i])[j++] = roles[x];
        }
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

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IMemberSecurityLog> logs = new ArrayList<IMemberSecurityLog>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      logs.add(new IMemberSecurityLog() {

        @Override
        public Date getChangeTime() {
          return new Date((Long) instanceData[0]);
        }

        @Override
        public long getCharacterID() {
          return (Long) instanceData[1];
        }

        @Override
        public String getCharacterName() {
          return (String) instanceData[2];
        }

        @Override
        public long getIssuerID() {
          return (Long) instanceData[3];
        }

        @Override
        public String getIssuerName() {
          return (String) instanceData[4];
        }

        @Override
        public Collection<ISecurityRole> getNewRoles() {
          return genRoles(instanceData, 7);
        }

        @Override
        public Collection<ISecurityRole> getOldRoles() {
          return genRoles(instanceData, 6);
        }

        @Override
        public String getRoleLocationType() {
          return (String) instanceData[5];
        }
      });
    }

    EasyMock.expect(mockServer.requestMemberSecurityLog()).andReturn(logs);
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
      if (SecurityRole.get(owner, time, next.getRoleID()) == null) {
        SecurityRole newRole = new SecurityRole(next.getRoleID() + delta, next.getRoleName());
        newRole.setup(owner, time);
        newRole = CachedData.updateData(newRole);
      }
    }
  }

  // Test update with all new logs.
  @Test
  public void testCorporationMemberSecurityLogSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberSecurityLogSync.syncCorporationMemberSecurityLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify logs were added correctly.
    Collection<ISecurityRole> allRoles = new HashSet<ISecurityRole>();
    for (int i = 0; i < testData.length; i++) {
      MemberSecurityLog next = MemberSecurityLog.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getChangeTime());
      Assert.assertEquals(testData[i][1], next.getChangedCharacterID());
      Assert.assertEquals(testData[i][2], next.getChangedCharacterName());
      Assert.assertEquals(testData[i][3], next.getIssuerID());
      Assert.assertEquals(testData[i][4], next.getIssuerName());
      Assert.assertEquals(testData[i][5], next.getRoleLocationType());
      checkRoles("old roles", genRoles(testData[i], 6), next.getOldRoles(), 0);
      checkRoles("new roles", genRoles(testData[i], 7), next.getNewRoles(), 0);
      for (int j = 6; j < 8; j++) {
        allRoles.addAll(genRoles(testData[i], j));
      }
    }

    // Verify set of roles is identical to the roles included in the populated set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (SecurityRole check : SecurityRole.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());
    }
    checkRoles("all roles", allRoles, existingIDs, 0);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberSecurityLogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityLogStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityLogDetail());
  }

  // Test update with logs already populated. This should cause the existing logs to evolve.
  @Test
  public void testCorporationMemberSecurityLogSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing logs, slightly modified
    for (int i = 0; i < testData.length; i++) {
      MemberSecurityLog next = new MemberSecurityLog(
          (Long) testData[i][0], (Long) testData[i][1] + 5, (String) testData[i][2] + "A", (Long) testData[i][3] + 5, (String) testData[i][4] + "A",
          (String) testData[i][5] + "A");
      addRoles(testTime, syncAccount, genRoles(testData[i], 6), next.getOldRoles(), 0);
      addRoles(testTime, syncAccount, genRoles(testData[i], 7), next.getNewRoles(), 0);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberSecurityLogSync.syncCorporationMemberSecurityLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify logs were evolved
    Collection<ISecurityRole> allRoles = new HashSet<ISecurityRole>();
    for (int i = 0; i < testData.length; i++) {
      MemberSecurityLog next = MemberSecurityLog.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getChangeTime());
      Assert.assertEquals(((Long) testData[i][1]).longValue(), next.getChangedCharacterID());
      Assert.assertEquals(testData[i][2], next.getChangedCharacterName());
      Assert.assertEquals(((Long) testData[i][3]).longValue(), next.getIssuerID());
      Assert.assertEquals(testData[i][4], next.getIssuerName());
      Assert.assertEquals(testData[i][5], next.getRoleLocationType());
      checkRoles("old roles", genRoles(testData[i], 6), next.getOldRoles(), 0);
      checkRoles("new roles", genRoles(testData[i], 7), next.getNewRoles(), 0);
      for (int j = 6; j < 8; j++) {
        allRoles.addAll(genRoles(testData[i], j));
      }
    }

    // Verify set of roles is identical to the roles included in the populated set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (SecurityRole check : SecurityRole.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());
    }
    checkRoles("all roles", allRoles, existingIDs, 0);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberSecurityLogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityLogStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityLogDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationMemberSecurityLogSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing logs, slightly modified
    for (int i = 0; i < testData.length; i++) {
      MemberSecurityLog next = new MemberSecurityLog(
          (Long) testData[i][0], (Long) testData[i][1] + 5, (String) testData[i][2] + "A", (Long) testData[i][3] + 5, (String) testData[i][4] + "A",
          (String) testData[i][5] + "A");
      addRoles(testTime, syncAccount, genRoles(testData[i], 6), next.getOldRoles(), 5);
      addRoles(testTime, syncAccount, genRoles(testData[i], 7), next.getNewRoles(), 5);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setMemberSecurityLogStatus(SyncState.UPDATED);
    tracker.setMemberSecurityLogDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setMemberSecurityLogExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberSecurityLogSync.syncCorporationMemberSecurityLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify logs were added correctly.
    Collection<ISecurityRole> allRoles = new HashSet<ISecurityRole>();
    for (int i = 0; i < testData.length; i++) {
      MemberSecurityLog next = MemberSecurityLog.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getChangeTime());
      Assert.assertEquals((Long) testData[i][1] + 5, next.getChangedCharacterID());
      Assert.assertEquals((String) testData[i][2] + "A", next.getChangedCharacterName());
      Assert.assertEquals((Long) testData[i][3] + 5, next.getIssuerID());
      Assert.assertEquals((String) testData[i][4] + "A", next.getIssuerName());
      Assert.assertEquals((String) testData[i][5] + "A", next.getRoleLocationType());
      checkRoles("old roles", genRoles(testData[i], 6), next.getOldRoles(), 5);
      checkRoles("new roles", genRoles(testData[i], 7), next.getNewRoles(), 5);
      for (int j = 6; j < 8; j++) {
        genRoles(testData[i], j);
        allRoles.addAll(genRoles(testData[i], j));
      }
    }

    // Verify set of roles is identical to the roles included in the populated set.
    Set<Long> existingIDs = new HashSet<Long>();
    for (SecurityRole check : SecurityRole.getAll(syncAccount, testTime)) {
      existingIDs.add(check.getRoleID());
    }
    checkRoles("all roles", allRoles, existingIDs, 5);

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getMemberSecurityLogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityLogStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberSecurityLogDetail());
  }

}
