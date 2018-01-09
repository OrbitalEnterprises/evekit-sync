package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
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
import enterprises.orbital.evekit.model.character.CharacterSheet;
import enterprises.orbital.evekit.model.character.CharacterSkill;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ISkill;
import enterprises.orbital.evexmlapi.chr.ISkillInfo;

public class CharacterSkillsSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterSkillsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getSkillsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getSkillsExpiry();
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setSkillsStatus(status);
    tracker.setSkillsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setSkillsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    if (item instanceof CharacterSkill) {
      CharacterSkill api = (CharacterSkill) item;
      CharacterSkill existing = CharacterSkill.get(accountKey, time, api.getTypeID());
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
    } else if (item instanceof CharacterSheet) {
      CharacterSheet api = (CharacterSheet) item;
      CharacterSheet existing = CharacterSheet.get(accountKey, time);
      if (existing != null) {
        // Slightly more complicate check here since only freeSkillPoints can change in a Skills request
        if (!existing.equivalentSkillsCheck(api)) {
          // Evolve
          existing.evolve(api, time);
          // Copy over the non-affected settings to the new sheet
          api.copyForSkills(existing);
          super.commit(time, tracker, container, accountKey, existing);
          super.commit(time, tracker, container, accountKey, api);
        }
      } else {
        // New entity
        api.setup(accountKey, time);
        super.commit(time, tracker, container, accountKey, api);
      }
    } else {
      // Should never happen!
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest)
    throws IOException {
    return charRequest.requestSkills();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates)
    throws IOException {

    // Multiple items we need to process here:
    //
    // CharacterSheet
    // CharacterSkill

    // Update character sheet components
    ISkillInfo charSheet = (ISkillInfo) data;

    CharacterSheet sheet = new CharacterSheet(
        0, "", 0, "", "", 0, 0, "", 0, "", "", "", 0, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, charSheet.getFreeSkillPoints(), 0);
    updates.add(sheet);

    // Update skill set. We only need to add/update here.
    for (ISkill skill : charSheet.getSkills()) {
      CharacterSkill newSkill = new CharacterSkill(skill.getTypeID(), skill.getLevel(), skill.getSkillpoints(), skill.isPublished());
      updates.add(newSkill);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterSkillsSync syncher = new CharacterSkillsSync();

  public static SyncStatus syncCharacterSheet(
                                              long time,
                                              SynchronizedEveAccount syncAccount,
                                              SynchronizerUtil syncUtil,
                                              ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "Skills");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "Skills", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "Skills", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
