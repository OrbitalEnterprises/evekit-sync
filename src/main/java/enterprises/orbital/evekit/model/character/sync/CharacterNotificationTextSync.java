package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.CharacterNotificationBody;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.INotificationText;

public class CharacterNotificationTextSync extends AbstractCharacterSync {

  protected static final Logger log             = Logger.getLogger(CharacterNotificationTextSync.class.getName());
  protected static final int    NOTE_BATCH_SIZE = 20;

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getNotificationTextsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setNotificationTextsStatus(status);
    tracker.setNotificationTextsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setNotificationTextsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getNotificationTextsExpiry();
  }

  @Override
  public boolean prereqSatisfied(CapsuleerSyncTracker tracker) {
    // We require that notification headers have been retrieved first
    return tracker.getNotificationsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof CharacterNotificationBody;

    CharacterNotificationBody api = (CharacterNotificationBody) item;
    CharacterNotificationBody existing = CharacterNotificationBody.get(accountKey, time, api.getNotificationID());

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

  // Can't use generic sync for notification bodies
  @Override
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    throw new UnsupportedOperationException();
  }

  private static final CharacterNotificationTextSync syncher = new CharacterNotificationTextSync();

  public static SyncStatus syncNotificationTexts(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {

    try {
      // Run pre-check.
      String description = "NotificationTexts";
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, syncher);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // From old dps code
      log.fine("Starting refresh request for " + description + " for account " + syncAccount);

      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      try {
        List<Long> toRetrieve = CharacterNotificationBody.getUnretrievedNotificationIDs(syncAccount, time);

        if (toRetrieve.size() == 0) {
          // Nothing to update so just set our expiry
          Capsuleer cap = Capsuleer.getCapsuleer(syncAccount);
          if (cap == null) {
            log.warning("Can't find Capsuleer for " + syncAccount + ", expiry may be wrong but we're continuing anyway!");
          } else {
            nextExpiry = cap.getNotificationsExpiry();
          }
        } else {

          long[] idList = new long[toRetrieve.size()];
          int i = 0;
          for (long next : toRetrieve) {
            idList[i++] = next;
          }

          List<INotificationText> allTexts = new ArrayList<INotificationText>();

          for (i = 0; i < idList.length; i += NOTE_BATCH_SIZE) {
            long[] midList = new long[Math.min(NOTE_BATCH_SIZE, idList.length - i)];
            System.arraycopy(idList, i, midList, 0, midList.length);
            Collection<INotificationText> texts = charRequest.requestNotificationTexts(midList);
            if (charRequest.isError()) break;
            allTexts.addAll(texts);
          }

          if (charRequest.isError()) {
            StringBuilder errStr = new StringBuilder();
            status = handleServerError(charRequest, errStr);
            errorDetail = errStr.toString();
            if (status == SyncTracker.SyncState.SYNC_ERROR) log.warning("request failed: " + errorDetail);
          } else {

            // Add messages
            for (INotificationText next : allTexts) {
              CharacterNotificationBody msg = new CharacterNotificationBody(next.getNotificationID(), true, next.getText(), next.isMissing());
              updateList.add(msg);
            }

            // Set expiry based on notificaton headers. Not perfect, but guarantees we won't sync until we've retrieved new headers.
            Capsuleer cap = Capsuleer.getCapsuleer(syncAccount);
            if (cap == null) {
              log.warning("Can't find Capsuleer for " + syncAccount + ", expiry may be wrong but we're continuing anyway!");
            } else {
              nextExpiry = cap.getNotificationsExpiry();
            }
          }
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

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "NotificationTexts", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "NotificationTexts", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
