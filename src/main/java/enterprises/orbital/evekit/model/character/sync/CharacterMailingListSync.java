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
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.MailingList;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IMailList;

public class CharacterMailingListSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterMailingListSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getMailingListsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setMailingListsStatus(status);
    tracker.setMailingListsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) throws IOException {
    container.setMailingListsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getMailingListsExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) throws IOException {
    assert item instanceof MailingList;

    MailingList api = (MailingList) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing MailingList to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      MailingList existing = MailingList.get(accountKey, time, api.getListID());
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
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestMailingLists();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IMailList> mailingLists = (Collection<IMailList>) data;

    // Add/update based on current list
    Set<Long> foundLists = new HashSet<Long>();
    for (IMailList next : mailingLists) {
      MailingList newList = new MailingList(next.getDisplayName(), next.getListID());
      foundLists.add(newList.getListID());
      updates.add(newList);
    }

    // Find lists which have been deleted
    for (long nextListID : MailingList.getAllListIDs(syncAccount, time)) {
      if (!foundLists.contains(nextListID)) {
        // EOL this list.
        MailingList ref = MailingList.get(syncAccount, time, nextListID);
        ref.evolve(null, time);
        updates.add(ref);
      }
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterMailingListSync syncher = new CharacterMailingListSync();

  public static SyncStatus syncMailingLists(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "MailingLists");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "MailingLists", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "MailingLists", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
