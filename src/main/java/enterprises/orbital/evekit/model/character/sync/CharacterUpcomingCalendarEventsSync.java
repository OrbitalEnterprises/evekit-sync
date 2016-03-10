package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.UpcomingCalendarEvent;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IUpcomingCalendarEvent;

/**
 * Top level data object for character data.
 */
public class CharacterUpcomingCalendarEventsSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterUpcomingCalendarEventsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getUpcomingCalendarEventsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setUpcomingCalendarEventsStatus(status);
    tracker.setUpcomingCalendarEventsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setUpcomingCalendarEventsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getUpcomingCalendarEventsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    UpcomingCalendarEvent api = (UpcomingCalendarEvent) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing UpcomingCalendarEvent to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      UpcomingCalendarEvent existing = UpcomingCalendarEvent.get(accountKey, time, api.getEventID());
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
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestUpcomingCalendarEvents();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IUpcomingCalendarEvent> events = (Collection<IUpcomingCalendarEvent>) data;

    // Add and update events
    Set<Integer> eventSet = new HashSet<Integer>();

    // Prepare set of events to add/update
    for (IUpcomingCalendarEvent next : events) {
      UpcomingCalendarEvent newEvent = new UpcomingCalendarEvent(
          next.getDuration(), ModelUtil.safeConvertDate(next.getEventDate()), next.getEventID(), next.getEventText(), next.getEventTitle(), next.getOwnerID(),
          next.getOwnerName(), next.getResponse(), next.isImportant());
      updates.add(newEvent);
      eventSet.add(next.getEventID());
    }

    // Scan for events we need to remove
    for (UpcomingCalendarEvent next : UpcomingCalendarEvent.getAllUpcomingCalendarEvents(syncAccount, time)) {
      int eventID = next.getEventID();
      if (!eventSet.contains(eventID)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterUpcomingCalendarEventsSync syncher = new CharacterUpcomingCalendarEventsSync();

  public static SyncStatus syncUpcomingCalendarEvents(
                                                      long time,
                                                      SynchronizedEveAccount syncAccount,
                                                      SynchronizerUtil syncUtil,
                                                      ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "UpcomingCalendarEvents");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "UpcomingCalendarEvents", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "UpcomingCalendarEvents", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
