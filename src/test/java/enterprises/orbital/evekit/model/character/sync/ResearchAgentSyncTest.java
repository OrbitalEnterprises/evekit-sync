package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.ResearchAgent;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IResearchAgent;

public class ResearchAgentSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  static Object[][]      testData;

  static {
    // Generate test data
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][6];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomInteger();
      testData[i][1] = TestBase.getRandomDouble(1000);
      testData[i][2] = TestBase.getRandomDouble(1000);
      testData[i][3] = TestBase.getRandomDouble(1000);
      testData[i][4] = TestBase.getRandomLong();
      testData[i][5] = TestBase.getRandomInt();
    }
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
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    Collection<IResearchAgent> agents = new ArrayList<IResearchAgent>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      agents.add(new IResearchAgent() {

        @Override
        public int getSkillTypeID() {
          return (Integer) instanceData[5];
        }

        @Override
        public Date getResearchStartDate() {
          return new Date((Long) instanceData[4]);
        }

        @Override
        public double getRemainderPoints() {
          return (Double) instanceData[3];
        }

        @Override
        public double getPointsPerDay() {
          return (Double) instanceData[2];
        }

        @Override
        public double getCurrentPoints() {
          return (Double) instanceData[1];
        }

        @Override
        public int getAgentID() {
          return (Integer) instanceData[0];
        }
      });
    }

    EasyMock.expect(mockServer.requestResearchAgents()).andReturn(agents);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new agents
  @Test
  public void testResearchAgentSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterResearchAgentSync.syncResearchAgents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    for (int i = 0; i < testData.length; i++) {
      int agentID = (Integer) testData[i][0];
      ResearchAgent next = ResearchAgent.get(syncAccount, testTime, agentID);
      Assert.assertNotNull(next);
      Assert.assertEquals(((Double) testData[i][1]), next.getCurrentPoints(), 0.01);
      Assert.assertEquals(((Double) testData[i][2]), next.getPointsPerDay(), 0.01);
      Assert.assertEquals(((Double) testData[i][3]), next.getRemainderPoints(), 0.01);
      Assert.assertEquals((long) ((Long) testData[i][4]), next.getResearchStartDate());
      Assert.assertEquals((int) ((Integer) testData[i][5]), next.getSkillTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getResearchExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchDetail());
  }

  // Test update with research agents already populated
  @Test
  public void testResearchAgentSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing agents
    for (int i = 0; i < testData.length; i++) {
      int agentID = (Integer) testData[i][0];
      ResearchAgent next = new ResearchAgent(
          agentID, (Double) testData[i][1], (Double) testData[i][2], (Double) testData[i][3], (Long) testData[i][4] + 27L, (Integer) testData[i][5]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterResearchAgentSync.syncResearchAgents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify agents are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      int agentID = (Integer) testData[i][0];
      ResearchAgent next = ResearchAgent.get(syncAccount, testTime, agentID);
      Assert.assertNotNull(next);
      Assert.assertEquals(((Double) testData[i][1]), next.getCurrentPoints(), 0.01);
      Assert.assertEquals(((Double) testData[i][2]), next.getPointsPerDay(), 0.01);
      Assert.assertEquals(((Double) testData[i][3]), next.getRemainderPoints(), 0.01);
      Assert.assertEquals((long) ((Long) testData[i][4]), next.getResearchStartDate());
      Assert.assertEquals((int) ((Integer) testData[i][5]), next.getSkillTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getResearchExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchDetail());
  }

  // Test skips update when already updated
  @Test
  public void testResearchAgentSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing research agents
    for (int i = 0; i < testData.length; i++) {
      int agentID = (Integer) testData[i][0];
      ResearchAgent next = new ResearchAgent(
          agentID, (Double) testData[i][1], (Double) testData[i][2], (Double) testData[i][3], (Long) testData[i][4] + 27L, (Integer) testData[i][5]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setResearchStatus(SyncState.UPDATED);
    tracker.setResearchDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setResearchExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterResearchAgentSync.syncResearchAgents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify agents unchanged
    for (int i = 0; i < testData.length; i++) {
      int agentID = (Integer) testData[i][0];
      ResearchAgent next = ResearchAgent.get(syncAccount, testTime, agentID);
      Assert.assertNotNull(next);
      Assert.assertEquals(((Double) testData[i][1]), next.getCurrentPoints(), 0.01);
      Assert.assertEquals(((Double) testData[i][2]), next.getPointsPerDay(), 0.01);
      Assert.assertEquals(((Double) testData[i][3]), next.getRemainderPoints(), 0.01);
      Assert.assertEquals(((Long) testData[i][4]) + 27L, next.getResearchStartDate());
      Assert.assertEquals((int) ((Integer) testData[i][5]), next.getSkillTypeID());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getResearchExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchDetail());
  }

  // Test update deletes agents which no longer exist
  @Test
  public void testResearchAgentSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate agents which should be deleted
    List<ResearchAgent> toDelete = new ArrayList<ResearchAgent>();
    for (int i = 0; i < 5; i++) {
      int agentID = TestBase.getUniqueRandomInteger();
      ResearchAgent next = new ResearchAgent(
          agentID, TestBase.getRandomDouble(1000), TestBase.getRandomDouble(1000), TestBase.getRandomDouble(1000), TestBase.getRandomLong(),
          TestBase.getRandomInt());
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
      toDelete.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterResearchAgentSync.syncResearchAgents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted agents are no longer present
    Assert.assertEquals(testData.length, ResearchAgent.getAllAgents(syncAccount, testTime, -1, -1).size());
    for (int i = 0; i < testData.length; i++) {
      int agentID = (Integer) testData[i][0];
      ResearchAgent next = ResearchAgent.get(syncAccount, testTime, agentID);
      Assert.assertNotNull(next);
      Assert.assertEquals(((Double) testData[i][1]), next.getCurrentPoints(), 0.01);
      Assert.assertEquals(((Double) testData[i][2]), next.getPointsPerDay(), 0.01);
      Assert.assertEquals(((Double) testData[i][3]), next.getRemainderPoints(), 0.01);
      Assert.assertEquals((long) ((Long) testData[i][4]), next.getResearchStartDate());
      Assert.assertEquals((int) ((Integer) testData[i][5]), next.getSkillTypeID());
    }
    for (ResearchAgent i : toDelete) {
      Assert.assertNull(ResearchAgent.get(syncAccount, testTime, i.getAgentID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getResearchExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getResearchDetail());
  }

}
