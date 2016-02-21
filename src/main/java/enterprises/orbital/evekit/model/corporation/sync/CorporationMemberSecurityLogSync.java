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
import enterprises.orbital.evekit.model.corporation.MemberSecurityLog;
import enterprises.orbital.evekit.model.corporation.SecurityRole;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberSecurityLog;
import enterprises.orbital.evexmlapi.crp.ISecurityRole;

public class CorporationMemberSecurityLogSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationMemberSecurityLogSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getMemberSecurityLogStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setMemberSecurityLogStatus(status);
    tracker.setMemberSecurityLogDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setMemberSecurityLogExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getMemberSecurityLogExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    // Item is one of MemberSecurityLog or SecurityRole
    if (item instanceof MemberSecurityLog) {
      MemberSecurityLog api = (MemberSecurityLog) item;
      MemberSecurityLog existing = MemberSecurityLog.get(accountKey, time, api.getChangeTime());

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
    } else {
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestMemberSecurityLog();
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
    Collection<IMemberSecurityLog> memSecurity = (Collection<IMemberSecurityLog>) data;

    // Collections of security roles in use.
    Map<Long, SecurityRole> roleMap = new HashMap<Long, SecurityRole>();

    for (IMemberSecurityLog next : memSecurity) {
      // Add this security entry.
      MemberSecurityLog newLog = new MemberSecurityLog(
          next.getChangeTime().getTime(), next.getCharacterID(), next.getCharacterName(), next.getIssuerID(), next.getIssuerName(), next.getRoleLocationType());

      // Update all role sets.
      updateRoleSet(next.getOldRoles(), roleMap, newLog.getOldRoles());
      updateRoleSet(next.getNewRoles(), roleMap, newLog.getNewRoles());

      // Save the new log for adding
      updates.add(newLog);
    }

    // Add all roles to the update set
    updates.addAll(roleMap.values());

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationMemberSecurityLogSync syncher = new CorporationMemberSecurityLogSync();

  public static SyncStatus syncCorporationMemberSecurityLog(
                                                            long time,
                                                            SynchronizedEveAccount syncAccount,
                                                            SynchronizerUtil syncUtil,
                                                            ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationMemberSecurityLog");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberSecurityLog", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationMemberSecurityLog", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
