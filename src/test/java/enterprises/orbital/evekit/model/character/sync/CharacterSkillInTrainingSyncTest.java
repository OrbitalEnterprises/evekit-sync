package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.Date;

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
import enterprises.orbital.evekit.model.character.CharacterSkillInTraining;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ISkillInTraining;

public class CharacterSkillInTrainingSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  Object[][]             testData = new Object[][] {
                                      {
                                          TestBase.getRandomBoolean(), TestBase.getRandomLong(), TestBase.getRandomLong(), TestBase.getRandomLong(),
                                          TestBase.getRandomInt(), TestBase.getRandomInt(), TestBase.getRandomInt(), TestBase.getRandomInt()
                                        }
                                    };

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

  public void compareWithTestData(CharacterSkillInTraining skill) {
    Assert.assertEquals(skill.isSkillInTraining(), (boolean) ((Boolean) testData[0][0]));
    Assert.assertEquals(skill.getCurrentTrainingQueueTime(), (long) ((Long) testData[0][1]));
    Assert.assertEquals(skill.getTrainingStartTime(), (long) ((Long) testData[0][2]));
    Assert.assertEquals(skill.getTrainingEndTime(), (long) ((Long) testData[0][3]));
    Assert.assertEquals(skill.getTrainingStartSP(), (int) ((Integer) testData[0][4]));
    Assert.assertEquals(skill.getTrainingDestinationSP(), (int) ((Integer) testData[0][5]));
    Assert.assertEquals(skill.getTrainingToLevel(), (int) ((Integer) testData[0][6]));
    Assert.assertEquals(skill.getSkillTypeID(), (int) ((Integer) testData[0][7]));
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    final Object[] instanceData = testData[0];
    ISkillInTraining skill = new ISkillInTraining() {

      @Override
      public boolean isSkillInTraining() {
        return (Boolean) instanceData[0];
      }

      @Override
      public int getTrainingToLevel() {
        return (Integer) instanceData[6];
      }

      @Override
      public Date getTrainingStartTime() {
        return new Date((Long) instanceData[2]);
      }

      @Override
      public int getTrainingStartSP() {
        return (Integer) instanceData[4];
      }

      @Override
      public Date getTrainingEndTime() {
        return new Date((Long) instanceData[3]);
      }

      @Override
      public int getTrainingDestinationSP() {
        return (Integer) instanceData[5];
      }

      @Override
      public int getSkillTypeID() {
        return (Integer) instanceData[7];
      }

      @Override
      public Date getCurrentTrainingQueueTime() {
        return new Date((Long) instanceData[1]);
      }
    };

    EasyMock.expect(mockServer.requestSkillInTraining()).andReturn(skill);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with new skill in training
  @Test
  public void testCharacterSkillInTrainingSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillInTrainingSync.syncSkillInTraining(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify skill was added correctly.
    compareWithTestData(CharacterSkillInTraining.get(syncAccount, testTime));

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getSkillInTrainingExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillInTrainingStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillInTrainingDetail());
  }

  // Test update with skill already populated
  @Test
  public void testCharacterSkillInTrainingSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate skill
    CharacterSkillInTraining skill = new CharacterSkillInTraining(
        (Boolean) testData[0][0], (Long) testData[0][1], (Long) testData[0][2], (Long) testData[0][3], (Integer) testData[0][4], (Integer) testData[0][5],
        (Integer) testData[0][6], (Integer) testData[0][7]);
    skill.setup(syncAccount, testTime);
    skill = CachedData.updateData(skill);

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillInTrainingSync.syncSkillInTraining(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify skill is unchanged
    compareWithTestData(CharacterSkillInTraining.get(syncAccount, testTime));

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getSkillInTrainingExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillInTrainingStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillInTrainingDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterSkillInTrainingSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    CharacterSkillInTraining skill = new CharacterSkillInTraining(
        (Boolean) testData[0][0], (Long) testData[0][1], (Long) testData[0][2], (Long) testData[0][3], (Integer) testData[0][4], (Integer) testData[0][5],
        (Integer) testData[0][6], (Integer) testData[0][7]);
    skill.setup(syncAccount, testTime);
    skill = CachedData.updateData(skill);

    // Set the tracker as already updated and populate the container
    tracker.setSkillInTrainingStatus(SyncState.UPDATED);
    tracker.setSkillInTrainingDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setSkillInTrainingExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillInTrainingSync.syncSkillInTraining(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify skill unchanged
    compareWithTestData(CharacterSkillInTraining.get(syncAccount, testTime));

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getSkillInTrainingExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillInTrainingStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillInTrainingDetail());
  }

}
