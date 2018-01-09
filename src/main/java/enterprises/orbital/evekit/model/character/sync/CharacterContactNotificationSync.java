package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
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
import enterprises.orbital.evekit.model.character.CharacterContactNotification;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IContactNotification;

public class CharacterContactNotificationSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterContactNotificationSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getContactNotificationsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getContactNotificationsExpiry();
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setContactNotificationsStatus(status);
    tracker.setContactNotificationsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setContactNotificationsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof CharacterContactNotification;

    CharacterContactNotification api = (CharacterContactNotification) item;
    CharacterContactNotification existing = CharacterContactNotification.get(accountKey, time, api.getNotificationID());

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

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestContactNotifications();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {

    @SuppressWarnings("unchecked")
    Collection<IContactNotification> notes = (Collection<IContactNotification>) data;

    for (IContactNotification next : notes) {
      CharacterContactNotification newNote = new CharacterContactNotification(
          next.getNotificationID(), next.getSenderID(), next.getSenderName(), ModelUtil.safeConvertDate(next.getSentDate()), next.getMessageData());

      updates.add(newNote);
    }

    return charRequest.getCachedUntil().getTime();

  }

  private static final CharacterContactNotificationSync syncher = new CharacterContactNotificationSync();

  public static SyncStatus syncCharacterContactNotifications(
                                                             long time,
                                                             SynchronizedEveAccount syncAccount,
                                                             SynchronizerUtil syncUtil,
                                                             ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterContactNotifications");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContactNotifications", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContactNotifications", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
