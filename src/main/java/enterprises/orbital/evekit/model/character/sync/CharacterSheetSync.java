package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.Iterator;
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
import enterprises.orbital.evekit.model.character.CharacterRole;
import enterprises.orbital.evekit.model.character.CharacterSheet;
import enterprises.orbital.evekit.model.character.CharacterSheetBalance;
import enterprises.orbital.evekit.model.character.CharacterSheetClone;
import enterprises.orbital.evekit.model.character.CharacterSheetJump;
import enterprises.orbital.evekit.model.character.CharacterSkill;
import enterprises.orbital.evekit.model.character.CharacterTitle;
import enterprises.orbital.evekit.model.character.Implant;
import enterprises.orbital.evekit.model.character.JumpClone;
import enterprises.orbital.evekit.model.character.JumpCloneImplant;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ICharacterRole;
import enterprises.orbital.evexmlapi.chr.ICharacterSheet;
import enterprises.orbital.evexmlapi.chr.ICharacterTitle;
import enterprises.orbital.evexmlapi.chr.IImplant;
import enterprises.orbital.evexmlapi.chr.IJumpClone;
import enterprises.orbital.evexmlapi.chr.IJumpCloneImplant;
import enterprises.orbital.evexmlapi.chr.ISkill;

public class CharacterSheetSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterSheetSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getCharacterSheetStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getCharacterSheetExpiry();
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setCharacterSheetStatus(status);
    tracker.setCharacterSheetDetail(detail);
    if (status == SyncState.UPDATED || status == SyncState.NOT_EXPIRED) {
      // If we succeed in updating the entire sheet (or skip because the timer hasn't expired), then we can mark partial sheet downloads as completed as well.
      // This avoids two extra sync's we don't need
      // to do.
      tracker.setPartialCharacterSheetStatus(status);
      tracker.setPartialCharacterSheetDetail(detail);
      tracker.setSkillsStatus(status);
      tracker.setSkillsDetail(detail);
    }
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setCharacterSheetExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    // Things which can be EOL:
    //
    // CharacterRole
    // CharacerTitle
    // Implant
    // JumpClone
    // JumpCloneImplant
    //
    // Everything else should evolve as usual.

    if (item instanceof Implant) {
      Implant api = (Implant) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing Implant to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        Implant existing = Implant.get(accountKey, time, api.getTypeID());
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
    } else if (item instanceof JumpClone) {
      JumpClone api = (JumpClone) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing JumpClone to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        JumpClone existing = JumpClone.get(accountKey, time, api.getJumpCloneID());
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
    } else if (item instanceof JumpCloneImplant) {
      JumpCloneImplant api = (JumpCloneImplant) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing JumpCloneImplant to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        JumpCloneImplant existing = JumpCloneImplant.get(accountKey, time, api.getJumpCloneID(), api.getTypeID());
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
    } else if (item instanceof CharacterSkill) {
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
    } else if (item instanceof CharacterRole) {
      CharacterRole api = (CharacterRole) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing CharacterRole to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        CharacterRole existing = CharacterRole.get(accountKey, time, api.getRoleCategory(), api.getRoleID());
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
    } else if (item instanceof CharacterTitle) {
      CharacterTitle api = (CharacterTitle) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing CharacterTitle to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        CharacterTitle existing = CharacterTitle.get(accountKey, time, api.getTitleID());
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
    } else if (item instanceof CharacterSheet) {
      CharacterSheet api = (CharacterSheet) item;
      CharacterSheet existing = CharacterSheet.get(accountKey, time);
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
    } else if (item instanceof CharacterSheetBalance) {
      CharacterSheetBalance api = (CharacterSheetBalance) item;
      CharacterSheetBalance existing = CharacterSheetBalance.get(accountKey, time);
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
    } else if (item instanceof CharacterSheetJump) {
      CharacterSheetJump api = (CharacterSheetJump) item;
      CharacterSheetJump existing = CharacterSheetJump.get(accountKey, time);
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
    } else if (item instanceof CharacterSheetClone) {
      CharacterSheetClone api = (CharacterSheetClone) item;
      CharacterSheetClone existing = CharacterSheetClone.get(accountKey, time);
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
    return charRequest.requestCharacterSheet();
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
    // CharacterSheetBalance
    // CharacterSheetJump
    // CharacterSheetClone
    // CharacterSkill
    // CharacterRole
    // CharacterTitle
    // Implant
    // JumpClone
    // JumpCloneImplant

    // Update character sheet components
    ICharacterSheet charSheet = (ICharacterSheet) data;

    CharacterSheetBalance balance = new CharacterSheetBalance(charSheet.getBalance().setScale(2, RoundingMode.HALF_UP));
    updates.add(balance);

    CharacterSheetClone clone = new CharacterSheetClone(ModelUtil.safeConvertDate(charSheet.getCloneJumpDate()));
    updates.add(clone);

    CharacterSheetJump jump = new CharacterSheetJump(
        ModelUtil.safeConvertDate(charSheet.getJumpActivation()), ModelUtil.safeConvertDate(charSheet.getJumpFatigue()),
        ModelUtil.safeConvertDate(charSheet.getJumpLastUpdate()));
    updates.add(jump);

    CharacterSheet sheet = new CharacterSheet(
        charSheet.getCharacterID(), charSheet.getName(), charSheet.getCorporationID(), charSheet.getCorporationName(), charSheet.getRace(),
        ModelUtil.safeConvertDate(charSheet.getDoB()), charSheet.getBloodlineID(), charSheet.getBloodline(), charSheet.getAncestryID(), charSheet.getAncestry(),
        charSheet.getGender(), charSheet.getAllianceName(), charSheet.getAllianceID(), charSheet.getFactionName(), charSheet.getFactionID(),
        charSheet.getIntelligence(), charSheet.getMemory(), charSheet.getCharisma(), charSheet.getPerception(), charSheet.getWillpower(),
        charSheet.getHomeStationID(), ModelUtil.safeConvertDate(charSheet.getLastRespecDate()), ModelUtil.safeConvertDate(charSheet.getLastTimedRespec()),
        charSheet.getFreeRespecs(), charSheet.getFreeSkillPoints(), ModelUtil.safeConvertDate(charSheet.getRemoteStationDate()));
    updates.add(sheet);

    // Update skill set. We only need to add/update here.
    for (ISkill skill : charSheet.getSkills()) {
      CharacterSkill newSkill = new CharacterSkill(skill.getTypeID(), skill.getLevel(), skill.getSkillpoints(), skill.isPublished());
      updates.add(newSkill);
    }

    // Update character roles. Roles which no longer exist are EOL.
    List<CharacterRole> existingRoles = CharacterRole.getAllRoles(syncAccount, time);
    for (ICharacterRole next : charSheet.getRoles()) {
      // Add role for update check
      CharacterRole role = new CharacterRole(next.getRoleCategory().name(), next.getRoleID(), next.getRoleName());
      updates.add(role);

      // Remove this role from our list if we've already seen it.
      for (Iterator<CharacterRole> it = existingRoles.iterator(); it.hasNext();) {
        CharacterRole check = it.next();
        long roleID = check.getRoleID();
        String roleCategory = check.getRoleCategory();
        if (roleID == role.getRoleID() && roleCategory.equals(role.getRoleCategory())) {
          it.remove();
          break;
        }
      }
    }
    // Anything left in existing roles should be EOL.
    for (CharacterRole next : existingRoles) {
      next.evolve(null, time);
      updates.add(next);
    }

    // Update character title. Titles which no longer exist are EOL.
    List<CharacterTitle> existingTitles = CharacterTitle.getAllTitles(syncAccount, time);
    for (ICharacterTitle next : charSheet.getTitles()) {
      // Add title for update check
      CharacterTitle title = new CharacterTitle(next.getTitleID(), next.getTitleName());
      updates.add(title);

      // Remove this title from our list if we've already seen it
      for (Iterator<CharacterTitle> it = existingTitles.iterator(); it.hasNext();) {
        CharacterTitle check = it.next();
        if (check.getTitleID() == title.getTitleID()) {
          it.remove();
          break;
        }
      }
    }
    // Anything left in existing titles should be EOL.
    for (CharacterTitle next : existingTitles) {
      next.evolve(null, time);
      updates.add(next);
    }

    // Update implants. Implants which no longer exist are deleted.
    List<Implant> implants = Implant.getAll(syncAccount, time);
    for (IImplant next : charSheet.getImplants()) {
      // Add implant for update check
      Implant implant = new Implant(next.getTypeID(), next.getTypeName());
      updates.add(implant);

      // Remove this implant from our list if we've already seen it
      for (Iterator<Implant> it = implants.iterator(); it.hasNext();) {
        Implant check = it.next();
        if (check.getTypeID() == next.getTypeID()) {
          it.remove();
          break;
        }
      }
    }
    // Anything left should be EOL.
    for (Implant next : implants) {
      next.evolve(null, time);
      updates.add(next);
    }

    // Update jump clones. Jump clones which no longer exist are EOL.
    List<JumpClone> clones = JumpClone.getAll(syncAccount, time);
    for (IJumpClone next : charSheet.getJumpClones()) {
      // Add jump clone for update check
      JumpClone jc = new JumpClone(next.getJumpCloneID(), next.getTypeID(), next.getLocationID(), next.getCloneName());
      updates.add(jc);

      // Remove this jump clone from our list if we've already seen it
      for (Iterator<JumpClone> it = clones.iterator(); it.hasNext();) {
        JumpClone check = it.next();
        if (check.getJumpCloneID() == next.getJumpCloneID()) {
          it.remove();
          break;
        }
      }
    }
    // Anything left should be EOL.
    for (JumpClone next : clones) {
      next.evolve(null, time);
      updates.add(next);
    }

    // Update jump clone implants. Jump clone implants which no longer exist are EOL.
    List<JumpCloneImplant> cloneImplants = JumpCloneImplant.getAll(syncAccount, time);
    for (IJumpCloneImplant next : charSheet.getJumpCloneImplants()) {
      // Add jump clone implant for update check
      JumpCloneImplant jci = new JumpCloneImplant(next.getJumpCloneID(), next.getTypeID(), next.getTypeName());
      updates.add(jci);

      // Remove this jump clone implant from our list if we've already seen it
      for (Iterator<JumpCloneImplant> it = cloneImplants.iterator(); it.hasNext();) {
        JumpCloneImplant check = it.next();
        int jumpCloneID = check.getJumpCloneID();
        int typeID = check.getTypeID();
        if (jumpCloneID == next.getJumpCloneID() && typeID == next.getTypeID()) {
          it.remove();
          break;
        }
      }
    }
    // Anything left should be EOL.
    for (JumpCloneImplant next : cloneImplants) {
      next.evolve(null, time);
      updates.add(next);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterSheetSync syncher = new CharacterSheetSync();

  public static SyncStatus syncCharacterSheet(
                                              long time,
                                              SynchronizedEveAccount syncAccount,
                                              SynchronizerUtil syncUtil,
                                              ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterSheet");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterSheet", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterSheet", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
