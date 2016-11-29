package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.CalendarEventAttendee;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.CharacterSheet;
import enterprises.orbital.evekit.model.character.UpcomingCalendarEvent;
import enterprises.orbital.evexmlapi.chr.ICalendarEventAttendee;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;

public class CharacterCalendarEventAttendeeSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterCalendarEventAttendeeSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getCalendarEventAttendeesStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getCalendarEventAttendeesExpiry();
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setCalendarEventAttendeesStatus(status);
    tracker.setCalendarEventAttendeesDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setCalendarEventAttendeesExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof CalendarEventAttendee;

    CalendarEventAttendee api = (CalendarEventAttendee) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing CalendarEventAttendee to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      CalendarEventAttendee existing = CalendarEventAttendee.get(accountKey, time, api.getEventID(), api.getCharacterID());
      if (existing != null) {
        if (!existing.equivalent(api)) {
          // Evolve
          existing.evolve(api, time);
          super.commit(time, tracker, container, accountKey, existing);
          super.commit(time, tracker, container, accountKey, api);
        }
      } else {
        // New entity
        api.setup(accountKey, time);
        super.commit(time, tracker, container, accountKey, api);
      }
    }

    return true;

  }

  @Override
  public boolean prereqSatisfied(
                                 CapsuleerSyncTracker tracker) {
    // We require that UpcomingCalendarEvents and CharacterSheet be updated first
    return tracker.getUpcomingCalendarEventsStatus() != SyncTracker.SyncState.NOT_PROCESSED
        && tracker.getCharacterSheetStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  // We can't use the template for synching calendar events so these methods are unsupported.
  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest)
    throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates)
    throws IOException {
    throw new UnsupportedOperationException();
  }

  private static final CharacterCalendarEventAttendeeSync syncher = new CharacterCalendarEventAttendeeSync();

  public static SyncStatus syncCalendarEventAttendees(
                                                      long time,
                                                      SynchronizedEveAccount syncAccount,
                                                      SynchronizerUtil syncUtil,
                                                      ICharacterAPI charRequest) {

    try {
      // Run pre-check.
      String description = "CalendarEventAttendees";
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, syncher);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // From old dps code
      log.fine("Starting refresh request for " + description + " for account " + syncAccount);

      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      // Assemble set of events we need to query. These are all events in which:
      // 1. this account is the owner.
      // 2. the owner is the corp and this account is in the same corp
      // 3. the owner is the alliance and this account is in the same alliance
      Capsuleer cap = Capsuleer.getCapsuleer(syncAccount);
      CharacterSheet sheet = CharacterSheet.get(syncAccount, time);
      List<UpcomingCalendarEvent> events = UpcomingCalendarEvent.getAllUpcomingCalendarEvents(syncAccount, time);
      List<Long> eventsToQuery = new ArrayList<Long>();

      for (UpcomingCalendarEvent nextEvent : events) {
        if (nextEvent.getOwnerID() == cap.getCharacterID() || nextEvent.getOwnerID() == sheet.getCorporationID()
            || nextEvent.getOwnerID() == sheet.getAllianceID()) {
          eventsToQuery.add(nextEvent.getEventID());
        }
      }

      try {

        // Update attendee list. Two attendees are identical if they have the same eventID and characterID. We have to query each event individually because the
        // API doesn't indicate which events correspond to which attendees.
        Set<Long> attendeeSet = new HashSet<Long>();

        // Process events one at a time
        for (long nextEventID : eventsToQuery) {
          // Retrieve attendee list from server
          Collection<ICalendarEventAttendee> attendees = charRequest.requestCalendarEventAttendees(nextEventID);

          if (charRequest.isError()) {
            // Short circuit in this case. This means that if any one event fails to be retrieved then we'll stop processing all events
            StringBuilder errStr = new StringBuilder();
            status = handleServerError(charRequest, errStr);
            errorDetail = errStr.toString();
            if (status == SyncTracker.SyncState.SYNC_ERROR) log.warning("request failed: " + errorDetail);
            break;
          }

          // Process attendees
          attendeeSet.clear();

          // Create objects for all these attendees. We'll add/update at the commit.
          for (ICalendarEventAttendee nextAttendee : attendees) {
            CalendarEventAttendee obj = new CalendarEventAttendee(
                nextAttendee.getEventID(), nextAttendee.getCharacterID(), nextAttendee.getCharacterName(), nextAttendee.getResponse());

            updateList.add(obj);
            attendeeSet.add(obj.getCharacterID());
          }

          // Now find all attendees which are no longer in the list.
          // These should all be EOL
          for (CalendarEventAttendee nextCurrent : CalendarEventAttendee.getByEventID(syncAccount, time, nextEventID)) {
            if (!attendeeSet.contains(nextCurrent.getCharacterID())) {
              nextCurrent.evolve(null, time);
              updateList.add(nextCurrent);
            }
          }

          // Set expiry based on UpcomingCalendarEvent. This way, we won't attempt to look up more attendees until we've updated calendar events. Not perfect
          // since the tweo can change independently but probably good enough.
          nextExpiry = cap.getUpcomingCalendarEventsExpiry();
        }

      } catch (IOException e) {
        status = SyncTracker.SyncState.SYNC_ERROR;
        errorDetail = "request failed with IO error";
        log.warning("request failed with error " + e);
      }

      log.fine("Completed refresh request for " + description + " for account " + syncAccount);

      // Finished sync, store result if needed
      syncUtil.storeSynchResults(time, CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, status, errorDetail, nextExpiry, description, updateList,
                                 syncher);

      return SyncStatus.DONE;

    } catch (IOException e) {
      // Log but give us another shot in the queue
      log.warning("store failed with error " + e);
      return SyncStatus.ERROR;
    }

  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CalendarEventAttendees", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CalendarEventAttendees", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
