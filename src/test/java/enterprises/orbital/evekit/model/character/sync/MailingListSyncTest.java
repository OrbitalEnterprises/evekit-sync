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
import enterprises.orbital.evekit.model.character.MailingList;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IMailList;

public class MailingListSyncTest extends SyncTestBase {

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
    testData = new Object[size][2];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getRandomText(50);
      testData[i][1] = TestBase.getUniqueRandomLong();
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
    Collection<IMailList> lists = new ArrayList<IMailList>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      lists.add(new IMailList() {

        @Override
        public String getDisplayName() {
          return (String) instanceData[0];
        }

        @Override
        public long getListID() {
          return (Long) instanceData[1];
        }
      });
    }

    EasyMock.expect(mockServer.requestMailingLists()).andReturn(lists);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new mailing lists
  @Test
  public void testMailingListSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailingListSync.syncMailingLists(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    for (int i = 0; i < testData.length; i++) {
      long listID = (Long) testData[i][1];
      MailingList next = MailingList.get(syncAccount, testTime, listID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][0], next.getDisplayName());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getMailingListsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsDetail());
  }

  // Test update with mailing lists already populated
  @Test
  public void testMailingListSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing mailing lists
    for (int i = 0; i < testData.length; i++) {
      long listID = (Long) testData[i][1];
      MailingList next = new MailingList((String) testData[i][0] + "foo", listID);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailingListSync.syncMailingLists(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify mailing lists are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      long listID = (Long) testData[i][1];
      MailingList next = MailingList.get(syncAccount, testTime, listID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][0], next.getDisplayName());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getMailingListsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testMailingListSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing mailing lists
    for (int i = 0; i < testData.length; i++) {
      long listID = (Long) testData[i][1];
      MailingList next = new MailingList((String) testData[i][0] + "foo", listID);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setMailingListsStatus(SyncState.UPDATED);
    tracker.setMailingListsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setMailingListsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailingListSync.syncMailingLists(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify mailing lists unchanged
    for (int i = 0; i < testData.length; i++) {
      long listID = (Long) testData[i][1];
      MailingList next = MailingList.get(syncAccount, testTime, listID);
      Assert.assertNotNull(next);
      Assert.assertEquals((String) testData[i][0] + "foo", next.getDisplayName());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getMailingListsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsDetail());
  }

  // Test update deletes mailing lists which no longer exist
  @Test
  public void testMailingListSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate mailing lists which should be deleted
    List<MailingList> toDelete = new ArrayList<MailingList>();
    for (int i = 0; i < 5; i++) {
      long listID = TestBase.getUniqueRandomLong();
      MailingList next = new MailingList(TestBase.getRandomText(50), listID);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      toDelete.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailingListSync.syncMailingLists(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted mailing lists are no longer present
    Assert.assertEquals(testData.length, MailingList.getAllListIDs(syncAccount, testTime).size());
    for (int i = 0; i < testData.length; i++) {
      long listID = (Long) testData[i][1];
      MailingList next = MailingList.get(syncAccount, testTime, listID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][0], next.getDisplayName());
    }
    for (MailingList i : toDelete) {
      Assert.assertNull(MailingList.get(syncAccount, testTime, i.getListID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getMailingListsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailingListsDetail());
  }

}
