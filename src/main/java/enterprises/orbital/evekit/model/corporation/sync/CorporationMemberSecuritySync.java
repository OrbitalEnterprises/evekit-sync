package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.MemberSecurity;
import enterprises.orbital.evekit.model.corporation.SecurityRole;
import enterprises.orbital.evekit.model.corporation.SecurityTitle;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberSecurity;
import enterprises.orbital.evexmlapi.crp.ISecurityRole;
import enterprises.orbital.evexmlapi.crp.ISecurityTitle;

public class CorporationMemberSecuritySync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationMemberSecuritySync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getMemberSecurityStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setMemberSecurityStatus(status);
    tracker.setMemberSecurityDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) throws IOException {
    container.setMemberSecurityExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getMemberSecurityExpiry();
  }

  /**
   * Update a set of ID references if necessary.
   * 
   * @param refSet
   *          the ID set to update from.
   * @param setToUpdate
   *          the ID set to be updated.
   * @return true if the set was updated, false otherwise.
   */
  protected boolean updateIDSet(Set<Long> refSet, Set<Long> setToUpdate) {
    if (refSet.containsAll(setToUpdate) && setToUpdate.containsAll(refSet)) return false;
    setToUpdate.clear();
    setToUpdate.addAll(refSet);
    return true;
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) throws IOException {
    // Item is one of MemberSecurity, SecurityRole or SecurityTitle
    if (item instanceof MemberSecurity) {
      MemberSecurity api = (MemberSecurity) item;
      MemberSecurity existing = MemberSecurity.get(accountKey, time, api.getCharacterID());

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
    } else if (item instanceof SecurityRole) {
      SecurityRole api = (SecurityRole) item;
      SecurityRole existing = SecurityRole.get(accountKey, time, api.getRoleID());

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
    } else if (item instanceof SecurityTitle) {
      SecurityTitle api = (SecurityTitle) item;
      SecurityTitle existing = SecurityTitle.get(accountKey, time, api.getTitleID());

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
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestMemberSecurity();
  }

  /**
   * Update role set mapping based on latest download data. Create any new roles we haven't seen yet.
   * 
   * @param memberSet
   *          download set to update from.
   * @param usedRoles
   *          map of roles we've seen so far.
   * @param roleSet
   *          role set to update.
   */
  protected void updateRoleSet(Collection<ISecurityRole> memberSet, Map<Long, SecurityRole> usedRoles, Set<Long> roleSet) {
    assert roleSet.size() == 0;
    for (ISecurityRole role : memberSet) {
      roleSet.add(role.getRoleID());
      if (!usedRoles.containsKey(role.getRoleID())) {
        SecurityRole newRole = new SecurityRole(role.getRoleID(), role.getRoleName());
        usedRoles.put(role.getRoleID(), newRole);
      }
    }
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IMemberSecurity> memSecurity = (Collection<IMemberSecurity>) data;

    // Collections of security roles and titles in use.
    Map<Long, SecurityRole> roleMap = new HashMap<Long, SecurityRole>();
    Map<Long, SecurityTitle> titleMap = new HashMap<Long, SecurityTitle>();

    for (IMemberSecurity next : memSecurity) {
      // Add this security entry. We'll only update if the role set actually changed.
      MemberSecurity newSecurity = new MemberSecurity(next.getCharacterID(), next.getName());

      // Update all role sets.
      updateRoleSet(next.getGrantableRoles(), roleMap, newSecurity.getGrantableRoles());
      updateRoleSet(next.getGrantableRolesAtBase(), roleMap, newSecurity.getGrantableRolesAtBase());
      updateRoleSet(next.getGrantableRolesAtHQ(), roleMap, newSecurity.getGrantableRolesAtHQ());
      updateRoleSet(next.getGrantableRolesAtOther(), roleMap, newSecurity.getGrantableRolesAtOther());
      updateRoleSet(next.getRoles(), roleMap, newSecurity.getRoles());
      updateRoleSet(next.getRolesAtBase(), roleMap, newSecurity.getRolesAtBase());
      updateRoleSet(next.getRolesAtHQ(), roleMap, newSecurity.getRolesAtHQ());
      updateRoleSet(next.getRolesAtOther(), roleMap, newSecurity.getRolesAtOther());

      // Update titles.
      assert newSecurity.getTitles().size() == 0;
      for (ISecurityTitle title : next.getTitles()) {
        newSecurity.getTitles().add(title.getTitleID());
        if (!titleMap.containsKey(title.getTitleID())) {
          SecurityTitle newTitle = new SecurityTitle(title.getTitleID(), title.getTitleName());
          titleMap.put(title.getTitleID(), newTitle);
        }
      }

      // Save the new security for adding
      updates.add(newSecurity);
    }

    // Add all roles and titles to the update set
    updates.addAll(roleMap.values());
    updates.addAll(titleMap.values());

    // Schedule any no longer used roles or titles for deletion? We don't delete security roles or titles so that we can keep a history of previous roles. This
    // is similar to history such as that contained in MemberSecurityLog which records history role changes.

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationMemberSecuritySync syncher = new CorporationMemberSecuritySync();

  public static SyncStatus syncCorporationMemberSecurity(
                                                         long time,
                                                         SynchronizedEveAccount syncAccount,
                                                         SynchronizerUtil syncUtil,
                                                         ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationMemberSecurity");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberSecurity", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberSecurity", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
