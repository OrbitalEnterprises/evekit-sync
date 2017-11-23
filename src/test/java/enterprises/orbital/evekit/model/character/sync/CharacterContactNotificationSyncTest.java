package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.CharacterContactNotification;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IContactNotification;

public class CharacterContactNotificationSyncTest extends SyncTestBase {

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
                                          1234L, 4321L, "test_sender_one", "Nov 16, 2000", "notification data one"
                                        },
                                      {
                                          8215L, 5128L, "test_sender_two", "Mar 13, 2007", "notification data two"
                                        },
                                      {
                                          4277L, 7724L, "test_sender_three", "Sep 3, 2003", "notification data three"
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

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    Collection<IContactNotification> notes = new ArrayList<IContactNotification>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      notes.add(new IContactNotification() {

        @Override
        public long getNotificationID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getSenderID() {
          return (Long) instanceData[1];
        }

        @Override
        public String getSenderName() {
          return (String) instanceData[2];
        }

        @Override
        public Date getSentDate() {
          try {
            return DateFormat.getDateInstance().parse((String) instanceData[3]);
          } catch (ParseException e) {
            // Shouldn't happen!
            throw new RuntimeException(e);
          }
        }

        @Override
        public String getMessageData() {
          return (String) instanceData[4];
        }
      });
    }

    EasyMock.expect(mockServer.requestContactNotifications()).andReturn(notes);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new notifications
  @Test
  public void testCharacterContactNotificationSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterContactNotificationSync.syncCharacterContactNotifications(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify notifications were added correctly.
    for (int i = 0; i < testData.length; i++) {
      CharacterContactNotification next = CharacterContactNotification.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][0], next.getNotificationID());
      Assert.assertEquals(testData[i][1], next.getSenderID());
      Assert.assertEquals(testData[i][2], next.getSenderName());
      Assert.assertEquals(DateFormat.getDateInstance().parse((String) testData[i][3]).getTime(), next.getSentDate());
      Assert.assertEquals(testData[i][4], next.getMessageData());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getContactNotificationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContactNotificationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContactNotificationsDetail());
  }

  // Test update with notifications already populated
  @Test
  public void testCharacterContactNotificationsSyncUpdateExisting() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing notifications
    for (int i = 0; i < testData.length; i++) {
      CharacterContactNotification next = new CharacterContactNotification(
          (Long) testData[i][0], (Long) testData[i][1], (String) testData[i][2], DateFormat.getDateInstance().parse((String) testData[i][3]).getTime(),
          (String) testData[i][4]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterContactNotificationSync.syncCharacterContactNotifications(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify notifications are unchanged
    for (int i = 0; i < testData.length; i++) {
      CharacterContactNotification next = CharacterContactNotification.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][0], next.getNotificationID());
      Assert.assertEquals(testData[i][1], next.getSenderID());
      Assert.assertEquals(testData[i][2], next.getSenderName());
      Assert.assertEquals(DateFormat.getDateInstance().parse((String) testData[i][3]).getTime(), next.getSentDate());
      Assert.assertEquals(testData[i][4], next.getMessageData());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getContactNotificationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContactNotificationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContactNotificationsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterContactNotificationsSyncUpdateSkip() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing notifications
    for (int i = 0; i < testData.length; i++) {
      CharacterContactNotification next = new CharacterContactNotification(
          (Long) testData[i][0], (Long) testData[i][1], (String) testData[i][2], DateFormat.getDateInstance().parse((String) testData[i][3]).getTime(),
          (String) testData[i][4]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setContactNotificationsStatus(SyncState.UPDATED);
    tracker.setContactNotificationsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setContactNotificationsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterContactNotificationSync.syncCharacterContactNotifications(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify notifications unchanged
    for (int i = 0; i < testData.length; i++) {
      CharacterContactNotification next = CharacterContactNotification.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertNotNull(next);
      Assert.assertEquals(testData[i][0], next.getNotificationID());
      Assert.assertEquals(testData[i][1], next.getSenderID());
      Assert.assertEquals(testData[i][2], next.getSenderName());
      Assert.assertEquals(DateFormat.getDateInstance().parse((String) testData[i][3]).getTime(), next.getSentDate());
      Assert.assertEquals(testData[i][4], next.getMessageData());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getContactNotificationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContactNotificationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContactNotificationsDetail());
  }

}
