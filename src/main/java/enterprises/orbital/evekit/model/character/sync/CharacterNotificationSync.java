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
import enterprises.orbital.evekit.model.character.CharacterNotification;
import enterprises.orbital.evekit.model.character.CharacterNotificationBody;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.INotification;

public class CharacterNotificationSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterNotificationSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getNotificationsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setNotificationsStatus(status);
    tracker.setNotificationsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setNotificationsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getNotificationsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof CharacterNotification;

    CharacterNotification api = (CharacterNotification) item;
    CharacterNotification existing = CharacterNotification.get(accountKey, time, api.getNotificationID());

    if (existing != null) {
      if (!existing.equivalent(api)) {
        // Evolve
        existing.evolve(api, time);
        super.commit(time, tracker, container, accountKey, existing);
        super.commit(time, tracker, container, accountKey, api);
      }
    } else {
      // New entity. Create a new empty notification body at the same time so that we know to attempt to retrieve the body.
      api.setup(accountKey, time);
      super.commit(time, tracker, container, accountKey, api);
      CharacterNotificationBody body = new CharacterNotificationBody(api.getNotificationID(), false, "", false);
      body.setup(accountKey, time);
      super.commit(time, tracker, container, accountKey, body);
    }

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestNotifications();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<INotification> notes = (Collection<INotification>) data;

    for (INotification next : notes) {
      CharacterNotification note = new CharacterNotification(
          next.getNotificationID(), next.getTypeID(), next.getSenderID(), ModelUtil.safeConvertDate(next.getSentDate()), next.isRead());
      updates.add(note);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterNotificationSync syncher = new CharacterNotificationSync();

  public static SyncStatus syncNotifications(
                                             long time,
                                             SynchronizedEveAccount syncAccount,
                                             SynchronizerUtil syncUtil,
                                             ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "Notifications");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "Notifications", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "Notifications", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
