package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CalendarApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdCalendar200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdCalendarEventIdAttendees200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdCalendarEventIdOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CalendarEventAttendee;
import enterprises.orbital.evekit.model.character.UpcomingCalendarEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ESICharacterCalendarSync extends AbstractESIAccountSync<ESICharacterCalendarSync.CalendarData> {
  protected static final Logger log = Logger.getLogger(ESICharacterCalendarSync.class.getName());

  // Capture data for mail headers, mail bodies, mail labels and mailing lists
  static class CalendarData {
    List<GetCharactersCharacterIdCalendar200Ok> events = new ArrayList<>();
    Map<Integer, GetCharactersCharacterIdCalendarEventIdOk> eventInfo = new HashMap<>();
    Map<Integer, List<GetCharactersCharacterIdCalendarEventIdAttendees200Ok>> attendees = new HashMap<>();
  }

  public ESICharacterCalendarSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_CALENDAR;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof UpcomingCalendarEvent) ||
        (item instanceof CalendarEventAttendee);

    CachedData existing;
    if (item instanceof UpcomingCalendarEvent) {
      existing = UpcomingCalendarEvent.get(account, time, ((UpcomingCalendarEvent) item).getEventID());
    } else {
      existing = CalendarEventAttendee.get(account, time, ((CalendarEventAttendee) item).getEventID(),
                                           ((CalendarEventAttendee) item).getCharacterID());
    }

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<CalendarData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {

    CalendarApi apiInstance = cp.getCalendarApi();
    CalendarData resultData = new CalendarData();
    int eventIdLimit;

    // Retrieve calendar events
    List<GetCharactersCharacterIdCalendar200Ok> prelimResults = new ArrayList<>();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdCalendar200Ok>> result = apiInstance.getCharactersCharacterIdCalendarWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        accessToken(),
        null,
        null);
    checkCommonProblems(result);
    long expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

    // Crawl calendar events forward until no more entries are retrieved
    while (!result.getData()
                  .isEmpty()) {
      prelimResults.addAll(result.getData());
      //noinspection ConstantConditions
      eventIdLimit = result.getData()
                           .stream()
                           .max(Comparator.comparingLong(GetCharactersCharacterIdCalendar200Ok::getEventId))
                           .get()
                           .getEventId();
      ESIThrottle.throttle(endpoint().name(), account);
      result = apiInstance.getCharactersCharacterIdCalendarWithHttpInfo((int) account.getEveCharacterID(),
                                                                        null,
                                                                        eventIdLimit,
                                                                        accessToken(),
                                                                        null,
                                                                        null);
      checkCommonProblems(result);
      expiry = Math.max(expiry, extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    // Retrieve event and attendees details
    for (GetCharactersCharacterIdCalendar200Ok next : prelimResults) {
      try {
        ESIThrottle.throttle(endpoint().name(), account);
        ApiResponse<GetCharactersCharacterIdCalendarEventIdOk> eventResponse = apiInstance.getCharactersCharacterIdCalendarEventIdWithHttpInfo(
            (int) account.getEveCharacterID(),
            next.getEventId(),
            null,
            accessToken(),
            null,
            null);
        checkCommonProblems(eventResponse);
        expiry = Math.max(expiry, extractExpiry(eventResponse, OrbitalProperties.getCurrentTime() + maxDelay()));

        // "Not found" is common for attendee lists so safely eat those exceptions
        try {
          ESIThrottle.throttle(endpoint().name(), account);
          ApiResponse<List<GetCharactersCharacterIdCalendarEventIdAttendees200Ok>> attendeesResponse = apiInstance.getCharactersCharacterIdCalendarEventIdAttendeesWithHttpInfo(
              (int) account.getEveCharacterID(),
              next.getEventId(),
              null,
              accessToken(),
              null,
              null);
          checkCommonProblems(attendeesResponse);
          expiry = Math.max(expiry, extractExpiry(attendeesResponse, OrbitalProperties.getCurrentTime() + maxDelay()));
          resultData.attendees.put(next.getEventId(), attendeesResponse.getData());
        } catch (ApiException f) {
          // Possibly an exception we expect so carry on.
          log.log(Level.FINE, getContext() + " Ignoring attendee list retrieval exception " + next, f);
        }

        // If we succeed then record event, event info and attendee list
        resultData.events.add(next);
        resultData.eventInfo.put(next.getEventId(), eventResponse.getData());
      } catch (ApiException | IOException e) {
        // Skip this event, try to make progress with what is left
        log.log(Level.FINE, getContext() + " Skipping failed event " + next, e);
      }
    }

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<CalendarData> data,
                                   List<CachedData> updates) throws IOException {

    Set<Integer> seenEvents = new HashSet<>();
    Set<Pair<Integer, Integer>> seenAttendees = new HashSet<>();

    // Assemble events and attendees
    for (GetCharactersCharacterIdCalendar200Ok nm : data.getData().events) {
      GetCharactersCharacterIdCalendarEventIdOk info = data.getData().eventInfo.get(nm.getEventId());
      updates.add(new UpcomingCalendarEvent(
          info.getDuration(),
          info.getDate()
              .getMillis(),
          info.getEventId(),
          info.getText(),
          info.getTitle(),
          info.getOwnerId(),
          info.getOwnerName(),
          info.getResponse(),
          info.getImportance(),
          info.getOwnerType()
              .toString()
      ));
      seenEvents.add(info.getEventId());

      if (data.getData().attendees.containsKey(nm.getEventId())) {
        List<GetCharactersCharacterIdCalendarEventIdAttendees200Ok> attendees = data.getData().attendees.get(
            nm.getEventId());
        for (GetCharactersCharacterIdCalendarEventIdAttendees200Ok a : attendees) {
          updates.add(new CalendarEventAttendee(nm.getEventId(),
                                                a.getCharacterId(),
                                                a.getEventResponse()
                                                 .toString()));
          seenAttendees.add(Pair.of(nm.getEventId(), a.getCharacterId()));
        }
      }
    }

    // Look for events which no longer exist and end of life
    CachedData.SimpleStreamExceptionHandler handler = new CachedData.SimpleStreamExceptionHandler();
    CachedData.stream(time, (contid, at) -> UpcomingCalendarEvent.accessQuery(account, contid, 1000, false, at,
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any()), true, handler)
              .filter(upcomingCalendarEvent -> {
                // Retain events which are live but which were not in the download set
                return !seenEvents.contains(upcomingCalendarEvent.getEventID());
              })
              .forEach(upcomingCalendarEvent -> {
                // Event is live but not in the download set, so end of life
                upcomingCalendarEvent.evolve(null, time);
                updates.add(upcomingCalendarEvent);
              });
    if (handler.hit()) throw handler.getFirst();

    // Look for attendees which no longer exist and end of life
    handler = new CachedData.SimpleStreamExceptionHandler();
    CachedData.stream(time, (contid, at) -> CalendarEventAttendee.accessQuery(account, contid, 1000, false, at,
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any(),
                                                                              AttributeSelector.any()), true, handler)
              .filter(attendee -> {
                // Retain attendees which are live but which were not in the download set
                return !seenAttendees.contains(Pair.of(attendee.getEventID(), attendee.getCharacterID()));
              })
              .forEach(attendee -> {
                // Attendee is live but not in the download set, so end of life
                attendee.evolve(null, time);
                updates.add(attendee);
              });
    if (handler.hit()) throw handler.getFirst();

  }

}
