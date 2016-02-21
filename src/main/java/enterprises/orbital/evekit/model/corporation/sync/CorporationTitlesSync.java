package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import enterprises.orbital.evekit.model.corporation.CorporationTitle;
import enterprises.orbital.evekit.model.corporation.Role;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IRole;
import enterprises.orbital.evexmlapi.crp.ITitle;

public class CorporationTitlesSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationTitlesSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getCorpTitlesStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setCorpTitlesStatus(status);
    tracker.setCorpTitlesDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setTitlesExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getTitlesExpiry();
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
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {

    if (item instanceof CorporationTitle) {
      CorporationTitle api = (CorporationTitle) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing CorporationTitle to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        CorporationTitle existing = CorporationTitle.get(accountKey, time, api.getTitleID());
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
    } else if (item instanceof Role) {
      Role api = (Role) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing Role to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        Role existing = Role.get(accountKey, time, api.getRoleID());
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
    } else {
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestTitles();
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
  protected void updateRoleSet(Collection<IRole> memberSet, Map<Long, Role> usedRoles, Set<Long> roleSet) {
    assert roleSet.size() == 0;
    for (IRole role : memberSet) {
      roleSet.add(role.getRoleID());
      if (!usedRoles.containsKey(role.getRoleID())) {
        Role newRole = new Role(role.getRoleID(), role.getRoleDescription(), role.getRoleName());
        usedRoles.put(role.getRoleID(), newRole);
      }
    }
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<ITitle> titles = (Collection<ITitle>) data;

    // Collection of roles in use.
    Map<Long, Role> roleMap = new HashMap<Long, Role>();
    Set<Long> usedTitles = new HashSet<Long>();

    for (ITitle next : titles) {
      // Add this title. We'll only update if the role set actually changed.
      CorporationTitle newTitle = new CorporationTitle(next.getTitleID(), next.getTitleName());
      usedTitles.add(next.getTitleID());

      // Update all role sets.
      updateRoleSet(next.getGrantableRoles(), roleMap, newTitle.getGrantableRoles());
      updateRoleSet(next.getGrantableRolesAtBase(), roleMap, newTitle.getGrantableRolesAtBase());
      updateRoleSet(next.getGrantableRolesAtHQ(), roleMap, newTitle.getGrantableRolesAtHQ());
      updateRoleSet(next.getGrantableRolesAtOther(), roleMap, newTitle.getGrantableRolesAtOther());
      updateRoleSet(next.getRoles(), roleMap, newTitle.getRoles());
      updateRoleSet(next.getRolesAtBase(), roleMap, newTitle.getRolesAtBase());
      updateRoleSet(next.getRolesAtHQ(), roleMap, newTitle.getRolesAtHQ());
      updateRoleSet(next.getRolesAtOther(), roleMap, newTitle.getRolesAtOther());

      // Save the new title.
      updates.add(newTitle);
    }

    // Add all roles to the update set.
    updates.addAll(roleMap.values());

    // Cleanup any titles no longer in use. We don't bother cleaning up roles so that we maintain a history of roles used for the corp.
    for (CorporationTitle existing : CorporationTitle.getAll(syncAccount, time)) {
      if (!usedTitles.contains(existing.getTitleID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationTitlesSync syncher = new CorporationTitlesSync();

  public static SyncStatus syncCorporationTitles(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationTitles");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationTitles", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationTitles", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
