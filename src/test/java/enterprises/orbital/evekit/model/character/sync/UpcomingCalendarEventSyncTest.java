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
import enterprises.orbital.evekit.model.character.UpcomingCalendarEvent;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IUpcomingCalendarEvent;

public class UpcomingCalendarEventSyncTest extends SyncTestBase {

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
    testData = new Object[size][10];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getRandomInt();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getUniqueRandomLong();
      testData[i][3] = TestBase.getRandomText(500);
      testData[i][4] = TestBase.getRandomText(50);
      testData[i][5] = TestBase.getRandomLong();
      testData[i][6] = TestBase.getRandomText(50);
      testData[i][7] = TestBase.getRandomText(50);
      testData[i][8] = TestBase.getRandomBoolean();
      testData[i][9] = TestBase.getRandomInt();
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
    Collection<IUpcomingCalendarEvent> events = new ArrayList<IUpcomingCalendarEvent>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      events.add(new IUpcomingCalendarEvent() {

        @Override
        public boolean isImportant() {
          return (Boolean) instanceData[8];
        }

        @Override
        public String getResponse() {
          return (String) instanceData[7];
        }

        @Override
        public String getOwnerName() {
          return (String) instanceData[6];
        }

        @Override
        public long getOwnerID() {
          return (Long) instanceData[5];
        }

        @Override
        public String getEventTitle() {
          return (String) instanceData[4];
        }

        @Override
        public String getEventText() {
          return (String) instanceData[3];
        }

        @Override
        public long getEventID() {
          return (Long) instanceData[2];
        }

        @Override
        public Date getEventDate() {
          return new Date((Long) instanceData[1]);
        }

        @Override
        public int getDuration() {
          return (Integer) instanceData[0];
        }

        @Override
        public int getOwnerTypeID() {
          return (Integer) instanceData[9];
        }

      });
    }

    EasyMock.expect(mockServer.requestUpcomingCalendarEvents()).andReturn(events);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new events
  @Test
  public void testUpcomingCalendarEventSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterUpcomingCalendarEventsSync.syncUpcomingCalendarEvents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify data was populated correctly
    for (int i = 0; i < testData.length; i++) {
      long eventID = (Long) testData[i][2];
      UpcomingCalendarEvent next = UpcomingCalendarEvent.get(syncAccount, testTime, eventID);
      Assert.assertNotNull(next);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getDuration());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEventDate());
      Assert.assertEquals(testData[i][3], next.getEventText());
      Assert.assertEquals(testData[i][4], next.getEventTitle());
      Assert.assertEquals((long) ((Long) testData[i][5]), next.getOwnerID());
      Assert.assertEquals(testData[i][6], next.getOwnerName());
      Assert.assertEquals(testData[i][7], next.getResponse());
      Assert.assertEquals((boolean) ((Boolean) testData[i][8]), next.isImportant());
      Assert.assertEquals((int) ((Integer) testData[i][9]), next.getOwnerTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getUpcomingCalendarEventsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsDetail());
  }

  // Test update with events already populated
  @Test
  public void testUpcomingCalendarEventSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing events
    for (int i = 0; i < testData.length; i++) {
      long eventID = (Long) testData[i][2];
      UpcomingCalendarEvent next = new UpcomingCalendarEvent(
          (Integer) testData[i][0], (Long) testData[i][1], eventID, (String) testData[i][3], (String) testData[i][4], (Long) testData[i][5],
          (String) testData[i][6], (String) testData[i][7] + "foo", (Boolean) testData[i][8], (Integer) testData[i][9]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterUpcomingCalendarEventsSync.syncUpcomingCalendarEvents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify events are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      long eventID = (Long) testData[i][2];
      UpcomingCalendarEvent next = UpcomingCalendarEvent.get(syncAccount, testTime, eventID);
      Assert.assertNotNull(next);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getDuration());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEventDate());
      Assert.assertEquals(testData[i][3], next.getEventText());
      Assert.assertEquals(testData[i][4], next.getEventTitle());
      Assert.assertEquals((long) ((Long) testData[i][5]), next.getOwnerID());
      Assert.assertEquals(testData[i][6], next.getOwnerName());
      Assert.assertEquals(testData[i][7], next.getResponse());
      Assert.assertEquals((boolean) ((Boolean) testData[i][8]), next.isImportant());
      Assert.assertEquals((int) ((Integer) testData[i][9]), next.getOwnerTypeID());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getUpcomingCalendarEventsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testUpcomingCalendarEventSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing events
    for (int i = 0; i < testData.length; i++) {
      long eventID = (Long) testData[i][2];
      UpcomingCalendarEvent next = new UpcomingCalendarEvent(
          (Integer) testData[i][0], (Long) testData[i][1], eventID, (String) testData[i][3], (String) testData[i][4], (Long) testData[i][5],
          (String) testData[i][6], (String) testData[i][7] + "foo", (Boolean) testData[i][8], (Integer) testData[i][9]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setUpcomingCalendarEventsStatus(SyncState.UPDATED);
    tracker.setUpcomingCalendarEventsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setUpcomingCalendarEventsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterUpcomingCalendarEventsSync.syncUpcomingCalendarEvents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify events unchanged
    for (int i = 0; i < testData.length; i++) {
      long eventID = (Long) testData[i][2];
      UpcomingCalendarEvent next = UpcomingCalendarEvent.get(syncAccount, testTime, eventID);
      Assert.assertNotNull(next);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getDuration());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEventDate());
      Assert.assertEquals(testData[i][3], next.getEventText());
      Assert.assertEquals(testData[i][4], next.getEventTitle());
      Assert.assertEquals((long) ((Long) testData[i][5]), next.getOwnerID());
      Assert.assertEquals(testData[i][6], next.getOwnerName());
      Assert.assertEquals((String) testData[i][7] + "foo", next.getResponse());
      Assert.assertEquals((boolean) ((Boolean) testData[i][8]), next.isImportant());
      Assert.assertEquals((int) ((Integer) testData[i][9]), next.getOwnerTypeID());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getUpcomingCalendarEventsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsDetail());
  }

  // Test update deletes events which no longer exist
  @Test
  public void testUpcomingCalendarEventSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate events which should be deleted
    List<UpcomingCalendarEvent> toDelete = new ArrayList<UpcomingCalendarEvent>();
    for (int i = 0; i < 5; i++) {
      int eventID = TestBase.getUniqueRandomInteger();
      UpcomingCalendarEvent next = new UpcomingCalendarEvent(
          TestBase.getRandomInt(), TestBase.getRandomLong(), eventID, TestBase.getRandomText(500), TestBase.getRandomText(50), TestBase.getRandomLong(),
          TestBase.getRandomText(50), TestBase.getRandomText(50), TestBase.getRandomBoolean(), TestBase.getRandomInt());
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
      toDelete.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterUpcomingCalendarEventsSync.syncUpcomingCalendarEvents(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted events are no longer present
    Assert.assertEquals(testData.length, UpcomingCalendarEvent.getAllUpcomingCalendarEvents(syncAccount, testTime).size());
    for (int i = 0; i < testData.length; i++) {
      long eventID = (Long) testData[i][2];
      UpcomingCalendarEvent next = UpcomingCalendarEvent.get(syncAccount, testTime, eventID);
      Assert.assertNotNull(next);
      Assert.assertEquals((int) ((Integer) testData[i][0]), next.getDuration());
      Assert.assertEquals((long) ((Long) testData[i][1]), next.getEventDate());
      Assert.assertEquals(testData[i][3], next.getEventText());
      Assert.assertEquals(testData[i][4], next.getEventTitle());
      Assert.assertEquals((long) ((Long) testData[i][5]), next.getOwnerID());
      Assert.assertEquals(testData[i][6], next.getOwnerName());
      Assert.assertEquals(testData[i][7], next.getResponse());
      Assert.assertEquals((boolean) ((Boolean) testData[i][8]), next.isImportant());
      Assert.assertEquals((int) ((Integer) testData[i][9]), next.getOwnerTypeID());
    }
    for (UpcomingCalendarEvent i : toDelete) {
      Assert.assertNull(UpcomingCalendarEvent.get(syncAccount, testTime, i.getEventID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getUpcomingCalendarEventsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getUpcomingCalendarEventsDetail());
  }

}
