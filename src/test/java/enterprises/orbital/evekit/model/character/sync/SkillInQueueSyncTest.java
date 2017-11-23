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
import enterprises.orbital.evekit.model.character.SkillInQueue;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ISkillInQueue;

public class SkillInQueueSyncTest extends SyncTestBase {

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
    testData = new Object[size][7];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getRandomInt();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomInt(5);
      testData[i][3] = i;
      testData[i][4] = TestBase.getRandomInt();
      testData[i][5] = TestBase.getRandomLong();
      testData[i][6] = TestBase.getRandomInt();
    }
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
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    Collection<ISkillInQueue> skillqueue = new ArrayList<ISkillInQueue>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      skillqueue.add(new ISkillInQueue() {

        @Override
        public int getTypeID() {
          return (Integer) instanceData[6];
        }

        @Override
        public Date getStartTime() {
          return new Date((Long) instanceData[5]);
        }

        @Override
        public int getStartSP() {
          return (Integer) instanceData[4];
        }

        @Override
        public int getQueuePosition() {
          return (Integer) instanceData[3];
        }

        @Override
        public int getLevel() {
          return (Integer) instanceData[2];
        }

        @Override
        public Date getEndTime() {
          return new Date((Long) instanceData[1]);
        }

        @Override
        public int getEndSP() {
          return (Integer) instanceData[0];
        }
      });
    }

    EasyMock.expect(mockServer.requestSkillQueue()).andReturn(skillqueue);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all skill queue
  @Test
  public void testSkillInQueueSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillInQueueSync.syncSkillQueue(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    for (int i = 0; i < testData.length; i++) {
      int queuePosition = (Integer) testData[i][3];
      SkillInQueue next = SkillInQueue.get(syncAccount, testTime, queuePosition);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getEndSP());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEndTime());
      Assert.assertEquals((int) ((Integer) testData[i][2]), next.getLevel());
      Assert.assertEquals((int) ((Integer) testData[i][4]), next.getStartSP());
      Assert.assertEquals((long) ((Long) testData[i][5]), next.getStartTime());
      Assert.assertEquals((int) ((Integer) testData[i][6]), next.getTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getSkillQueueExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueDetail());
  }

  // Test update with skill queue already populated
  @Test
  public void testSkilInQueueSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing skills
    for (int i = 0; i < testData.length; i++) {
      int queuePosition = (Integer) testData[i][3];
      SkillInQueue next = new SkillInQueue(
          (Integer) testData[i][0], (Long) testData[i][1], (Integer) testData[i][2], queuePosition, (Integer) testData[i][4], (Long) testData[i][5] + 27L,
          (Integer) testData[i][6]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillInQueueSync.syncSkillQueue(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify skills are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      int queuePosition = (Integer) testData[i][3];
      SkillInQueue next = SkillInQueue.get(syncAccount, testTime, queuePosition);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getEndSP());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEndTime());
      Assert.assertEquals((int) ((Integer) testData[i][2]), next.getLevel());
      Assert.assertEquals((int) ((Integer) testData[i][4]), next.getStartSP());
      Assert.assertEquals((long) ((Long) testData[i][5]), next.getStartTime());
      Assert.assertEquals((int) ((Integer) testData[i][6]), next.getTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getSkillQueueExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueDetail());
  }

  // Test skips update when already updated
  @Test
  public void testSkillInQueueSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing skills
    for (int i = 0; i < testData.length; i++) {
      int queuePosition = (Integer) testData[i][3];
      SkillInQueue next = new SkillInQueue(
          (Integer) testData[i][0], (Long) testData[i][1], (Integer) testData[i][2], queuePosition, (Integer) testData[i][4], (Long) testData[i][5] + 27L,
          (Integer) testData[i][6]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setSkillQueueStatus(SyncState.UPDATED);
    tracker.setSkillQueueDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setSkillQueueExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillInQueueSync.syncSkillQueue(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify skills unchanged
    for (int i = 0; i < testData.length; i++) {
      int queuePosition = (Integer) testData[i][3];
      SkillInQueue next = SkillInQueue.get(syncAccount, testTime, queuePosition);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getEndSP());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEndTime());
      Assert.assertEquals((int) ((Integer) testData[i][2]), next.getLevel());
      Assert.assertEquals((int) ((Integer) testData[i][4]), next.getStartSP());
      Assert.assertEquals(((Long) testData[i][5]) + 27L, next.getStartTime());
      Assert.assertEquals((int) ((Integer) testData[i][6]), next.getTypeID());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getSkillQueueExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueDetail());
  }

  // Test update deletes skills beyond the length of the latest queue
  @Test
  public void testSkillInQueueSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate skills which should be deleted
    List<SkillInQueue> toDelete = new ArrayList<SkillInQueue>();
    for (int i = 0; i < 5; i++) {
      int queuePosition = testData.length + i;
      SkillInQueue next = new SkillInQueue(
          TestBase.getRandomInt(), TestBase.getRandomLong(), TestBase.getRandomInt(5), queuePosition, TestBase.getRandomInt(), TestBase.getRandomLong(),
          TestBase.getRandomInt());
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
      toDelete.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillInQueueSync.syncSkillQueue(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted skills are no longer present
    Assert.assertEquals(testData.length, SkillInQueue.getAtOrAfterPosition(syncAccount, testTime, 0).size());
    for (int i = 0; i < testData.length; i++) {
      int queuePosition = (Integer) testData[i][3];
      SkillInQueue next = SkillInQueue.get(syncAccount, testTime, queuePosition);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getEndSP());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEndTime());
      Assert.assertEquals((int) ((Integer) testData[i][2]), next.getLevel());
      Assert.assertEquals((int) ((Integer) testData[i][4]), next.getStartSP());
      Assert.assertEquals((long) ((Long) testData[i][5]), next.getStartTime());
      Assert.assertEquals((int) ((Integer) testData[i][6]), next.getTypeID());
    }
    for (SkillInQueue i : toDelete) {
      Assert.assertNull(SkillInQueue.get(syncAccount, testTime, i.getQueuePosition()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getSkillQueueExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillQueueDetail());
  }

}
