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
import enterprises.orbital.evekit.model.character.CharacterMailMessageBody;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IMailBody;

public class CharacterMailMessageBodiesSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterMailMessageBodiesSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getMailBodiesStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getMailBodiesExpiry();
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setMailBodiesStatus(status);
    tracker.setMailBodiesDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setMailBodiesExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof CharacterMailMessageBody;

    CharacterMailMessageBody api = (CharacterMailMessageBody) item;
    CharacterMailMessageBody existing = CharacterMailMessageBody.get(accountKey, time, api.getMessageID());

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
  public boolean prereqSatisfied(CapsuleerSyncTracker tracker) {
    // We require that mail headers have been retrieved first
    return tracker.getMailMessagesStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  // Can't use generic sync for mail bodies.
  @Override
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    throw new UnsupportedOperationException();
  }

  private static final CharacterMailMessageBodiesSync syncher = new CharacterMailMessageBodiesSync();

  public static SyncStatus syncMailMessageBodies(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {

    try {
      // Run pre-check.
      String description = "MailMessageBodies";
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, syncher);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // From old dps code
      log.fine("Starting refresh request for " + description + " for account " + syncAccount);

      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      try {
        List<Long> toRetrieve = CharacterMailMessageBody.getUnretrievedMessageIDs(syncAccount, time);

        if (toRetrieve.size() == 0) {
          // Nothing to update so just set our expiry based on the expiry of the headers.
          Capsuleer cap = Capsuleer.getCapsuleer(syncAccount);
          if (cap == null) {
            log.warning("Can't find Capsuleer for " + syncAccount + ", expiry may be wrong but we're continuing anyway!");
          } else {
            nextExpiry = cap.getMailMessagesExpiry();
          }
        } else {
          long[] idList = new long[toRetrieve.size()];
          int i = 0;
          for (Long next : toRetrieve) {
            idList[i++] = next;
          }

          Collection<IMailBody> bodies = charRequest.requestMailBodies(idList);

          if (charRequest.isError()) {
            StringBuilder errStr = new StringBuilder();
            status = handleServerError(charRequest, errStr);
            errorDetail = errStr.toString();
            if (status == SyncTracker.SyncState.SYNC_ERROR) log.warning("request failed: " + errorDetail);
          } else {

            // Add messages
            for (IMailBody next : bodies) {
              CharacterMailMessageBody msg = new CharacterMailMessageBody(next.getMessageID(), true, next.getBody());
              updateList.add(msg);
              log.fine("Queued update for msg ID: " + next.getMessageID());
            }

            // Set expiry based on mail message headers. Not perfect, but guarantees we won't sync until we've retrieved new headers. The expiry of the mail
            // messages themselves is useless here because all messages are set to expire in 10 years.
            Capsuleer cap = Capsuleer.getCapsuleer(syncAccount);
            if (cap == null) {
              log.warning("Can't find Capsuleer for " + syncAccount + ", expiry may be wrong but we're continuing anyway!");
            } else {
              nextExpiry = cap.getMailMessagesExpiry();
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
    return syncher.excludeState(syncAccount, syncUtil, "MailMessageBodies", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "MailMessageBodies", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
