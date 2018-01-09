package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
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
import enterprises.orbital.evekit.model.character.CharacterSkillInTraining;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ISkillInTraining;

public class CharacterSkillInTrainingSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterSkillInTrainingSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getSkillInTrainingStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getSkillInTrainingExpiry();
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setSkillInTrainingStatus(status);
    tracker.setSkillInTrainingDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setSkillInTrainingExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestSkillInTraining();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof CharacterSkillInTraining;

    CharacterSkillInTraining api = (CharacterSkillInTraining) item;
    CharacterSkillInTraining existing = CharacterSkillInTraining.get(accountKey, time);
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
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    ISkillInTraining skillTraining = (ISkillInTraining) data;
    CharacterSkillInTraining skill = new CharacterSkillInTraining(
        skillTraining.isSkillInTraining(), ModelUtil.safeConvertDate(skillTraining.getCurrentTrainingQueueTime()),
        ModelUtil.safeConvertDate(skillTraining.getTrainingStartTime()), ModelUtil.safeConvertDate(skillTraining.getTrainingEndTime()),
        skillTraining.getTrainingStartSP(), skillTraining.getTrainingDestinationSP(), skillTraining.getTrainingToLevel(), skillTraining.getSkillTypeID());
    updates.add(skill);
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterSkillInTrainingSync syncher = new CharacterSkillInTrainingSync();

  public static SyncStatus syncSkillInTraining(
                                               long time,
                                               SynchronizedEveAccount syncAccount,
                                               SynchronizerUtil syncUtil,
                                               ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterSkillInTraining");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterSkillInTraining", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterSkillInTraining", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
