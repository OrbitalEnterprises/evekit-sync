package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CalendarApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdCalendar200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdCalendarEventIdAttendees200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdCalendarEventIdOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CalendarEventAttendee;
import enterprises.orbital.evekit.model.character.UpcomingCalendarEvent;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

public class ESICharacterCalendarSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CalendarApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] eventsTestData;
  private static int[] eventsPages;
  private static Object[][][] attendeesTestData;

  static {
    // Comparator for sorting test data in increasing order by eventID (testData[i][2])
    Comparator<Object[]> eventDataCompare = Comparator.comparingInt(x -> (Integer) x[2]);

    int size = 100 + TestBase.getRandomInt(100);
    eventsTestData = new Object[size][10];
    for (int i = 0; i < size; i++) {
      // Upcoming calendar event
      // 0 int duration
      // 1 long eventDate
      // 2 int eventID
      // 3 String eventText
      // 4 String eventTitle
      // 5 int ownerID
      // 6 String ownerName
      // 7 String response
      // 8 int importance
      // 9 String ownerType
      int responseLen = GetCharactersCharacterIdCalendar200Ok.EventResponseEnum.values().length;
      int ownerTypeLen = GetCharactersCharacterIdCalendarEventIdOk.OwnerTypeEnum.values().length;
      eventsTestData[i][0] = TestBase.getRandomInt();
      eventsTestData[i][1] = TestBase.getRandomLong();
      eventsTestData[i][2] = TestBase.getUniqueRandomInteger();
      eventsTestData[i][3] = TestBase.getRandomText(1000);
      eventsTestData[i][4] = TestBase.getRandomText(50);
      eventsTestData[i][5] = TestBase.getRandomInt();
      eventsTestData[i][6] = TestBase.getRandomText(50);
      eventsTestData[i][7] = GetCharactersCharacterIdCalendar200Ok.EventResponseEnum.values()[TestBase.getRandomInt(
          responseLen)];
      eventsTestData[i][8] = TestBase.getRandomInt();
      eventsTestData[i][9] = GetCharactersCharacterIdCalendarEventIdOk.OwnerTypeEnum.values()[TestBase.getRandomInt(
          ownerTypeLen)];
    }

    // Sort test data in increasing order by eventID
    Arrays.sort(eventsTestData, 0, eventsTestData.length, eventDataCompare);

    // Divide data into pages to test paging sync feature
    int pageCount = 3 + TestBase.getRandomInt(3);
    eventsPages = new int[pageCount];
    for (int i = 0; i < pageCount; i++) {
      eventsPages[i] = i * size / pageCount;
    }

    // Generate attendee list data
    attendeesTestData = new Object[size][][];
    int attendeeResponseLen = GetCharactersCharacterIdCalendarEventIdAttendees200Ok.EventResponseEnum.values().length;
    for (int i = 0; i < size; i++) {
      int eventID = (int) eventsTestData[i][2];
      int attendeeCount = 5 + TestBase.getRandomInt(10);
      Object[][] attendeeData = new Object[attendeeCount][3];
      attendeesTestData[i] = attendeeData;
      for (int j = 0; j < attendeeCount; j++) {
        // 0 int eventID;
        // 1 int characterID;
        // 2 String response;
        attendeeData[j][0] = eventID;
        attendeeData[j][1] = TestBase.getUniqueRandomInteger();
        attendeeData[j][2] = GetCharactersCharacterIdCalendarEventIdAttendees200Ok.EventResponseEnum.values()[TestBase.getRandomInt(
            attendeeResponseLen)];
      }
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CALENDAR, 1234L, null);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM UpcomingCalendarEvent ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CalendarEventAttendee ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CalendarApi.class);

    // Setup event list retrieval
    List<GetCharactersCharacterIdCalendar200Ok> events = new ArrayList<>();
    Map<Integer, GetCharactersCharacterIdCalendarEventIdOk> eventInfo = new HashMap<>();
    Map<Integer, List<GetCharactersCharacterIdCalendarEventIdAttendees200Ok>> eventAttendees = new HashMap<>();
    for (int i = 0; i < eventsTestData.length; i++) {
      Object[] nm = eventsTestData[i];
      // Add event
      GetCharactersCharacterIdCalendar200Ok nextEvent = new GetCharactersCharacterIdCalendar200Ok();
      nextEvent.setEventDate(new DateTime(new Date((long) nm[1])));
      nextEvent.setImportance((int) nm[8]);
      nextEvent.setTitle((String) nm[4]);
      nextEvent.setEventId((int) nm[2]);
      nextEvent.setEventResponse((GetCharactersCharacterIdCalendar200Ok.EventResponseEnum) nm[7]);
      events.add(nextEvent);

      // Add event info
      GetCharactersCharacterIdCalendarEventIdOk nextInfo = new GetCharactersCharacterIdCalendarEventIdOk();
      nextInfo.setDate(new DateTime(new Date((long) nm[1])));
      nextInfo.setDuration((int) nm[0]);
      nextInfo.setImportance((int) nm[8]);
      nextInfo.setOwnerId((int) nm[5]);
      nextInfo.setOwnerName((String) nm[6]);
      nextInfo.setResponse(String.valueOf(nm[7]));
      nextInfo.setTitle((String) nm[4]);
      nextInfo.setEventId((int) nm[2]);
      nextInfo.setOwnerType((GetCharactersCharacterIdCalendarEventIdOk.OwnerTypeEnum) nm[9]);
      nextInfo.setText((String) nm[3]);
      eventInfo.put(nextInfo.getEventId(), nextInfo);

      // Add attendees info
      Object[][] at = attendeesTestData[i];
      List<GetCharactersCharacterIdCalendarEventIdAttendees200Ok> nextAtList = new ArrayList<>();
      for (Object[] anAt : at) {
        GetCharactersCharacterIdCalendarEventIdAttendees200Ok nextAt = new GetCharactersCharacterIdCalendarEventIdAttendees200Ok();
        nextAt.setCharacterId((int) anAt[1]);
        nextAt.setEventResponse((GetCharactersCharacterIdCalendarEventIdAttendees200Ok.EventResponseEnum) anAt[2]);
        nextAtList.add(nextAt);
      }
      eventAttendees.put(nextInfo.getEventId(), nextAtList);
    }

    // Setup event list mock
    @SuppressWarnings("unchecked")
    List<GetCharactersCharacterIdCalendar200Ok>[] pages = new List[eventsPages.length];
    for (int i = 0; i < eventsPages.length; i++) {
      int limit = i + 1 == eventsPages.length ? eventsTestData.length : eventsPages[i + 1];
      pages[i] = events.subList(eventsPages[i], limit);
    }
    for (int i = 0; i < eventsPages.length + 1; i++) {
      Integer eventID = i > 0 ? pages[i - 1].get(pages[i - 1].size() - 1)
                                            .getEventId() : null;
      List<GetCharactersCharacterIdCalendar200Ok> data = i < eventsPages.length ? pages[i] : Collections.emptyList();
      ApiResponse<List<GetCharactersCharacterIdCalendar200Ok>> apir = new ApiResponse<>(200,
                                                                                        createHeaders("Expires",
                                                                                                      "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                        data);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdCalendarWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.eq(eventID),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Setup event info calls
    for (Map.Entry<Integer, GetCharactersCharacterIdCalendarEventIdOk> nb : eventInfo.entrySet()) {
      ApiResponse<GetCharactersCharacterIdCalendarEventIdOk> apir = new ApiResponse<>(200,
                                                                                      createHeaders("Expires",
                                                                                                    "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                      nb.getValue());
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdCalendarEventIdWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.eq(nb.getKey()
                        .intValue()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Setup event attendees calls
    for (Map.Entry<Integer, List<GetCharactersCharacterIdCalendarEventIdAttendees200Ok>> nb : eventAttendees.entrySet()) {
      ApiResponse<List<GetCharactersCharacterIdCalendarEventIdAttendees200Ok>> apir = new ApiResponse<>(200,
                                                                                                        createHeaders(
                                                                                                            "Expires",
                                                                                                            "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                                        nb.getValue());
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdCalendarEventIdAttendeesWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.eq(nb.getKey()
                        .intValue()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Complete mock with provider
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCalendarApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long atTime, Object[][] testEventData, Object[][][] testAttendeeData) throws Exception {

    // Retrieve stored events
    List<UpcomingCalendarEvent> storedEvents = AbstractESIAccountSync.retrieveAll(atTime,
                                                                                  (long contid, AttributeSelector at) ->
                                                                                      UpcomingCalendarEvent.accessQuery(
                                                                                          charSyncAccount, contid, 1000,
                                                                                          false, at,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testEventData.length, storedEvents.size());

    // Check stored data
    for (int i = 0; i < testEventData.length; i++) {
      UpcomingCalendarEvent nextEl = storedEvents.get(i);
      Object[] dt = testEventData[i];
      Assert.assertEquals((int) dt[0], nextEl.getDuration());
      Assert.assertEquals((long) dt[1], nextEl.getEventDate());
      Assert.assertEquals((int) dt[2], nextEl.getEventID());
      Assert.assertEquals(dt[3], nextEl.getEventText());
      Assert.assertEquals(dt[4], nextEl.getEventTitle());
      Assert.assertEquals((int) dt[5], nextEl.getOwnerID());
      Assert.assertEquals(dt[6], nextEl.getOwnerName());
      Assert.assertEquals(String.valueOf(dt[7]), nextEl.getResponse());
      Assert.assertEquals((int) dt[8], nextEl.getImportance());
      Assert.assertEquals(String.valueOf(dt[9]), nextEl.getOwnerType());
    }

    // Retrieve stored event attendees
    List<CalendarEventAttendee> storedAttendees = AbstractESIAccountSync.retrieveAll(atTime,
                                                                                     (long contid, AttributeSelector at) ->
                                                                                         CalendarEventAttendee.accessQuery(
                                                                                             charSyncAccount, contid,
                                                                                             1000, false, at,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    int attendeeCount = Arrays.stream(testAttendeeData)
                              .map(x -> x.length)
                              .reduce(0,
                                      (integer, integer2) -> integer + integer2);
    Assert.assertEquals(attendeeCount, storedAttendees.size());

    // Check stored data
    for (int i = 0, j = 0; i < testAttendeeData.length; i++) {
      Object[][] nextSet = testAttendeeData[i];
      for (Object[] aNextSet : nextSet) {
        CalendarEventAttendee nextEl = storedAttendees.get(j++);
        Assert.assertEquals((int) aNextSet[0], nextEl.getEventID());
        Assert.assertEquals((int) aNextSet[1], nextEl.getCharacterID());
        Assert.assertEquals(String.valueOf(aNextSet[2]), nextEl.getResponse());
      }
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterCalendarSync sync = new ESICharacterCalendarSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, eventsTestData, attendeesTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CALENDAR);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CALENDAR);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.  This is a copy of existing events with modified data.
    // Half the events are new and should be deleted in the update.
    int[] modifiedIDs = new int[eventsTestData.length];
    for (int i = 0; i < modifiedIDs.length; i++) {
      if (i % 2 == 0)
        modifiedIDs[i] = TestBase.getUniqueRandomInteger();
      else
        modifiedIDs[i] = (int) eventsTestData[i][2];
    }

    Object[][] newEventTestData = new Object[eventsTestData.length][10];
    for (int i = 0; i < eventsTestData.length; i++) {
      newEventTestData[i][0] = (int) eventsTestData[i][0] + 1;
      newEventTestData[i][1] = (long) eventsTestData[i][1] + 1;
      newEventTestData[i][2] = modifiedIDs[i];
      newEventTestData[i][3] = eventsTestData[i][3] + "1";
      newEventTestData[i][4] = eventsTestData[i][4] + "1";
      newEventTestData[i][5] = (int) eventsTestData[i][5] + 1;
      newEventTestData[i][6] = eventsTestData[i][6] + "1";
      newEventTestData[i][7] = eventsTestData[i][7];
      newEventTestData[i][8] = (int) eventsTestData[i][8] + 1;
      newEventTestData[i][9] = eventsTestData[i][9];

      UpcomingCalendarEvent existing = new UpcomingCalendarEvent((int) newEventTestData[i][0],
                                                                 (long) newEventTestData[i][1],
                                                                 (int) newEventTestData[i][2],
                                                                 (String) newEventTestData[i][3],
                                                                 (String) newEventTestData[i][4],
                                                                 (int) newEventTestData[i][5],
                                                                 (String) newEventTestData[i][6],
                                                                 String.valueOf(newEventTestData[i][7]),
                                                                 (int) newEventTestData[i][8],
                                                                 String.valueOf(newEventTestData[i][9]));
      existing.setup(charSyncAccount, testTime - 1);
      CachedData.update(existing);
    }

    Object[][][] newAttendeeTestData = new Object[attendeesTestData.length][][];
    for (int i = 0; i < attendeesTestData.length; i++) {
      Object[][] eAd = attendeesTestData[i];
      Object[][] nAd = new Object[eAd.length][3];
      newAttendeeTestData[i] = nAd;
      for (int j = 0; j < eAd.length; j++) {
        nAd[j][0] = modifiedIDs[i];
        nAd[j][1] = eAd[j][1];
        nAd[j][2] = String.valueOf(eAd[j][2]) + "1";

        CalendarEventAttendee existing = new CalendarEventAttendee((int) nAd[j][0],
                                                                   (int) nAd[j][1],
                                                                   String.valueOf(nAd[j][2]));
        existing.setup(charSyncAccount, testTime - 1);
        CachedData.update(existing);
      }
    }

    // Perform the sync
    ESICharacterCalendarSync sync = new ESICharacterCalendarSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old data updates
    verifyDataUpdate(testTime - 1, newEventTestData, newAttendeeTestData);

    // Verify new data updates
    verifyDataUpdate(testTime, eventsTestData, attendeesTestData);


    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CALENDAR);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CALENDAR);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
