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
import enterprises.orbital.evekit.model.character.SkillInQueue;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ISkillInQueue;

public class CharacterSkillInQueueSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterSkillInQueueSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getSkillQueueStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setSkillQueueStatus(status);
    tracker.setSkillQueueDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setSkillQueueExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getSkillQueueExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    SkillInQueue api = (SkillInQueue) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing SkillInQueue to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      SkillInQueue existing = SkillInQueue.get(accountKey, time, api.getQueuePosition());
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
    return charRequest.requestSkillQueue();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<ISkillInQueue> skillQueue = (Collection<ISkillInQueue>) data;

    // Update against all elements currently in the queue. Delete any elements beyond the end of the queue. Queue positions are indexed from zero.
    int maxQueueIndex = skillQueue.size();
    for (SkillInQueue next : SkillInQueue.getAtOrAfterPosition(syncAccount, time, maxQueueIndex)) {
      next.evolve(null, time);
      updates.add(next);
    }

    for (ISkillInQueue next : skillQueue) {
      SkillInQueue newSkill = new SkillInQueue(
          next.getEndSP(), ModelUtil.safeConvertDate(next.getEndTime()), next.getLevel(), next.getQueuePosition(), next.getStartSP(),
          ModelUtil.safeConvertDate(next.getStartTime()), next.getTypeID());
      updates.add(newSkill);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterSkillInQueueSync syncher = new CharacterSkillInQueueSync();

  public static SyncStatus syncSkillQueue(
                                          long time,
                                          SynchronizedEveAccount syncAccount,
                                          SynchronizerUtil syncUtil,
                                          ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterSkillQueue");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterSkillQueue", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterSkillQueue", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
