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
import enterprises.orbital.evekit.model.character.CharacterMailMessage;
import enterprises.orbital.evekit.model.character.CharacterMailMessageBody;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IMailMessage;

public class CharacterMailMessageSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterMailMessageSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getMailMessagesStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getMailMessagesExpiry();
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setMailMessagesStatus(status);
    tracker.setMailMessagesDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setMailMessagesExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof CharacterMailMessage;

    CharacterMailMessage api = (CharacterMailMessage) item;
    CharacterMailMessage existing = CharacterMailMessage.get(accountKey, time, api.getMessageID());

    if (existing != null) {
      if (!existing.equivalent(api)) {
        // Evolve
        existing.evolve(api, time);
        super.commit(time, tracker, container, accountKey, existing);
        super.commit(time, tracker, container, accountKey, api);
      }
    } else {
      // New entity. Create a new empty message body at the same time so that we know to attempt to retrieve the body.
      api.setup(accountKey, time);
      super.commit(time, tracker, container, accountKey, api);
      CharacterMailMessageBody body = new CharacterMailMessageBody(api.getMessageID(), false, "");
      body.setup(accountKey, time);
      super.commit(time, tracker, container, accountKey, body);
    }

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestMailMessages();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {

    @SuppressWarnings("unchecked")
    Collection<IMailMessage> messages = (Collection<IMailMessage>) data;

    for (IMailMessage next : messages) {
      CharacterMailMessage msg = new CharacterMailMessage(
          next.getMessageID(), next.getSenderID(), next.getSenderName(), ModelUtil.safeConvertDate(next.getSentDate()), next.getTitle(),
          next.getToCorpOrAllianceID(), next.isRead(), next.getSenderTypeID());
      for (long nextTo : next.getToCharacterIDs()) {
        msg.getToCharacterID().add(nextTo);
      }
      for (long nextList : next.getToListID()) {
        msg.getToListID().add(nextList);
      }
      updates.add(msg);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterMailMessageSync syncher = new CharacterMailMessageSync();

  public static SyncStatus syncMailMessages(
                                            long time,
                                            SynchronizedEveAccount syncAccount,
                                            SynchronizerUtil syncUtil,
                                            ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "MailMessages");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "MailMessages", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "MailMessages", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
