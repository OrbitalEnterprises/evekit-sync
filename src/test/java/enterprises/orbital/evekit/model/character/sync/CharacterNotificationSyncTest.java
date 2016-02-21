package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
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
import enterprises.orbital.evekit.model.character.CharacterNotification;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.INotification;

public class CharacterNotificationSyncTest extends SyncTestBase {

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
    // Generate random test data
    // 0 notificationID
    // 1 typeID
    // 2 senderID
    // 3 sentDate
    // 4 msgRead
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomInt();
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomBoolean();
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

  public INotification makeNotification(final Object[] instanceData, final long tweak) {
    return new INotification() {

      @Override
      public long getNotificationID() {
        return (Long) instanceData[0];
      }

      @Override
      public int getTypeID() {
        return (Integer) instanceData[1];
      }

      @Override
      public long getSenderID() {
        return (Long) instanceData[2] + tweak;
      }

      @Override
      public Date getSentDate() {
        return new Date((Long) instanceData[3]);
      }

      @Override
      public boolean isRead() {
        return (Boolean) instanceData[4];
      }
    };

  }

  public CharacterNotification makeNotificationObject(long time, final Object[] instanceData, final long tweak) throws Exception {
    long notificationID = (Long) instanceData[0];
    CharacterNotification note = new CharacterNotification(
        notificationID, (Integer) instanceData[1], (Long) instanceData[2] + tweak, (Long) instanceData[3], (Boolean) instanceData[4]);
    note.setup(syncAccount, time);
    return note;
  }

  public void checkNotification(CharacterNotification note, INotification check) {
    Assert.assertEquals(note.getNotificationID(), check.getNotificationID());
    Assert.assertEquals(note.getTypeID(), check.getTypeID());
    Assert.assertEquals(note.getSenderID(), check.getSenderID());
    Assert.assertEquals(note.getSentDate(), check.getSentDate().getTime());
    Assert.assertEquals(note.isMsgRead(), check.isRead());
  }

  public void setupOkMock(long tweak) throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    Collection<INotification> notes = new ArrayList<INotification>();
    for (int i = 0; i < testData.length; i++) {
      notes.add(makeNotification(testData[i], tweak));
    }

    EasyMock.expect(mockServer.requestNotifications()).andReturn(notes);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new notifications
  @Test
  public void testCharacterNotificationSyncUpdate() throws Exception {
    setupOkMock(0);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterNotificationSync.syncNotifications(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify notifications were added correctly.
    for (int i = 0; i < testData.length; i++) {
      INotification note = makeNotification(testData[i], 0);
      CharacterNotification check = CharacterNotification.get(syncAccount, testTime, note.getNotificationID());
      Assert.assertNotNull(check);
      checkNotification(check, note);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getNotificationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationsDetail());
  }

  // Test update with notifications already populated
  @Test
  public void testCharacterNotificationSyncUpdateExisting() throws Exception {
    setupOkMock(0);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing notifications with a slight tweak
    for (int i = 0; i < testData.length; i++) {
      CachedData.updateData(makeNotificationObject(testTime, testData[i], 25));
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterNotificationSync.syncNotifications(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify notifications are changed (evolved).
    for (int i = 0; i < testData.length; i++) {
      INotification note = makeNotification(testData[i], 0);
      CharacterNotification check = CharacterNotification.get(syncAccount, testTime, note.getNotificationID());
      Assert.assertNotNull(check);
      checkNotification(check, note);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getNotificationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterNotificationSyncUpdateSkip() throws Exception {
    setupOkMock(0);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing notifications with a slight tweak
    for (int i = 0; i < testData.length; i++) {
      CachedData.updateData(makeNotificationObject(testTime, testData[i], 25));
    }

    // Set the tracker as already updated and populate the container
    tracker.setNotificationsStatus(SyncState.UPDATED);
    tracker.setNotificationsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setNotificationsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterNotificationSync.syncNotifications(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify notifications are unchanged.
    for (int i = 0; i < testData.length; i++) {
      INotification note = makeNotification(testData[i], 25);
      CharacterNotification check = CharacterNotification.get(syncAccount, testTime, note.getNotificationID());
      Assert.assertNotNull(check);
      checkNotification(check, note);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getNotificationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationsDetail());
  }

}
