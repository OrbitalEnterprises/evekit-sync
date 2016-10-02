package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;

import org.easymock.EasyMock;
import org.hsqldb.rights.User;
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
import enterprises.orbital.evekit.model.character.CalendarEventAttendee;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.UpcomingCalendarEvent;
import enterprises.orbital.evexmlapi.chr.ICalendarEventAttendee;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;

public class CharacterCalendarEventAttendeeSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  User                   testUser;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  // eventID
  // characterID
  // characterName
  // response
  Object[][]             testData = new Object[][] {
      {
          1234L, 8711L, "test char one", "response one"
      }, {
          1234L, 4522L, "test char two", "response two"
      }, {
          4321L, 8711L, "test char three", "response three"
      }
  };

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
    Collection<ICalendarEventAttendee> attendees_1234 = new ArrayList<ICalendarEventAttendee>();
    Collection<ICalendarEventAttendee> attendees_4321 = new ArrayList<ICalendarEventAttendee>();
    for (int i = 0; i < 3; i++) {
      final Object[] instanceData = testData[i];
      ICalendarEventAttendee attendee = new ICalendarEventAttendee() {

        @Override
        public long getEventID() {
          return (Long) instanceData[0];
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
        public String getResponse() {
          return (String) instanceData[3];
        }
      };
      if (i < 2) {
        attendees_1234.add(attendee);
      } else {
        attendees_4321.add(attendee);
      }
    }

    // No call to getCachedUntil because this sync uses the expiry time from UpcomingCalendarEvents
    EasyMock.expect(mockServer.requestCalendarEventAttendees(1234)).andReturn(attendees_1234);
    EasyMock.expect(mockServer.requestCalendarEventAttendees(4321)).andReturn(attendees_4321);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expectLastCall().times(2);
  }

  // Test update with new events
  @Test
  public void testCharacterCalendarEventAttendeeSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate two calendar events
    UpcomingCalendarEvent event = new UpcomingCalendarEvent(0, 0, (Long) testData[0][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);
    event = new UpcomingCalendarEvent(0, 0, (Long) testData[2][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);

    // This sync requires character sheet and upcoming calendar events to already be processed.
    tracker.setUpcomingCalendarEventsStatus(SyncState.UPDATED);
    tracker.setUpcomingCalendarEventsDetail(null);
    tracker.setCharacterSheetStatus(SyncState.UPDATED);
    tracker.setCharacterSheetDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setUpcomingCalendarEventsExpiry(prevDate);
    container.setCharacterSheetExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterCalendarEventAttendeeSync.syncCalendarEventAttendees(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify notifications were added correctly.
    for (int i = 0; i < testData.length; i++) {
      CalendarEventAttendee next = CalendarEventAttendee.get(syncAccount, testTime, (Long) testData[i][0], (Long) testData[i][1]);
      Assert.assertEquals(testData[i][2], next.getCharacterName());
      Assert.assertEquals(testData[i][3], next.getResponse());
    }

    // Verify tracker and container were updated properly. Note that this sync uses the expiry time from UpcomingCalendarEvents.
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getCalendarEventAttendeesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesDetail());
  }

  // Test update with events already populated
  @Test
  public void testCharacterCalendarEventAttendeeSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate two calendar events
    UpcomingCalendarEvent event = new UpcomingCalendarEvent(0, 0, (Long) testData[0][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);
    event = new UpcomingCalendarEvent(0, 0, (Long) testData[2][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);

    // Populate attendees
    for (int i = 0; i < testData.length; i++) {
      CalendarEventAttendee next = new CalendarEventAttendee((Long) testData[i][0], (Long) testData[i][1], (String) testData[i][2], (String) testData[i][3]);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
    }

    // This sync requires character sheet and upcoming calendar events to already be processed.
    tracker.setUpcomingCalendarEventsStatus(SyncState.UPDATED);
    tracker.setUpcomingCalendarEventsDetail(null);
    tracker.setCharacterSheetStatus(SyncState.UPDATED);
    tracker.setCharacterSheetDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setUpcomingCalendarEventsExpiry(prevDate);
    container.setCharacterSheetExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterCalendarEventAttendeeSync.syncCalendarEventAttendees(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify attendees are unchanged
    for (int i = 0; i < testData.length; i++) {
      CalendarEventAttendee next = CalendarEventAttendee.get(syncAccount, testTime, (Long) testData[i][0], (Long) testData[i][1]);
      Assert.assertEquals(testData[i][2], next.getCharacterName());
      Assert.assertEquals(testData[i][3], next.getResponse());
    }

    // Verify tracker and container were updated properly. Note that this sync uses the expiry time from UpcomingCalendarEvents.
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getCalendarEventAttendeesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesDetail());
  }

  // Test fails when prereqs not met
  @Test
  public void testCharacterCalendarEventAttendeeSyncUpdateNoPreqs() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterCalendarEventAttendeeSync.syncCalendarEventAttendees(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.ERROR, syncOutcome);
    // Skip the verify here since the calls should never be made
  }

  // Test skips update when already updated
  @Test
  public void testCharacterCalendarEventAttendeeSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate two calendar events
    UpcomingCalendarEvent event = new UpcomingCalendarEvent(0, 0, (Long) testData[0][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);
    event = new UpcomingCalendarEvent(0, 0, (Long) testData[2][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);

    // This sync requires character sheet and upcoming calendar events to already be processed.
    tracker.setUpcomingCalendarEventsStatus(SyncState.UPDATED);
    tracker.setUpcomingCalendarEventsDetail(null);
    tracker.setCharacterSheetStatus(SyncState.UPDATED);
    tracker.setCharacterSheetDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setUpcomingCalendarEventsExpiry(prevDate);
    container.setCharacterSheetExpiry(prevDate);
    container = CachedData.updateData(container);

    // Set the tracker as already updated and populate the container
    tracker.setCalendarEventAttendeesStatus(SyncState.UPDATED);
    tracker.setCalendarEventAttendeesDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setCalendarEventAttendeesExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterCalendarEventAttendeeSync.syncCalendarEventAttendees(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify no attendees added
    Assert.assertTrue(CalendarEventAttendee.getByEventID(syncAccount, testTime, (Long) testData[0][0]).isEmpty());
    Assert.assertTrue(CalendarEventAttendee.getByEventID(syncAccount, testTime, (Long) testData[2][0]).isEmpty());

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getCalendarEventAttendeesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesDetail());
  }

  // Verify attendees no longer on the invite list are deleted.
  @Test
  public void testCharacterCalendarEventAttendeeSyncDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate two calendar events
    UpcomingCalendarEvent event = new UpcomingCalendarEvent(0, 0, (Long) testData[0][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);
    event = new UpcomingCalendarEvent(0, 0, (Long) testData[2][0], "", "", syncAccount.getEveCharacterID(), "", "", true);
    event.setup(syncAccount, testTime);
    event = CachedData.updateData(event);

    // Populate attendees, these should be deleted
    CalendarEventAttendee next = new CalendarEventAttendee((Long) testData[0][0], 5678L, "del char one", "del response one");
    next.setup(syncAccount, testTime);
    next = CachedData.updateData(next);
    next = new CalendarEventAttendee((Long) testData[2][0], 567822L, "del char two", "del response two");
    next.setup(syncAccount, testTime);
    next = CachedData.updateData(next);

    // This sync requires character sheet and upcoming calendar events to already be processed.
    tracker.setUpcomingCalendarEventsStatus(SyncState.UPDATED);
    tracker.setUpcomingCalendarEventsDetail(null);
    tracker.setCharacterSheetStatus(SyncState.UPDATED);
    tracker.setCharacterSheetDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setUpcomingCalendarEventsExpiry(prevDate);
    container.setCharacterSheetExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterCalendarEventAttendeeSync.syncCalendarEventAttendees(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify new attendees are added.
    for (int i = 0; i < testData.length; i++) {
      next = CalendarEventAttendee.get(syncAccount, testTime, (Long) testData[i][0], (Long) testData[i][1]);
      Assert.assertEquals(testData[i][2], next.getCharacterName());
      Assert.assertEquals(testData[i][3], next.getResponse());
    }

    // Verify old attendees are deleted.
    for (CalendarEventAttendee i : CalendarEventAttendee.getByEventID(syncAccount, testTime, 1234)) {
      Assert.assertTrue(i.getCharacterID() != 5678L);
    }
    for (CalendarEventAttendee i : CalendarEventAttendee.getByEventID(syncAccount, testTime, 4321)) {
      Assert.assertTrue(i.getCharacterID() != 567822L);
    }

    // Verify tracker and container were updated properly. Note that this sync uses the expiry time from UpcomingCalendarEvents.
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getCalendarEventAttendeesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getCalendarEventAttendeesDetail());
  }
}
