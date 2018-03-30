package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdRoles200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdRolesHistory200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Member;
import enterprises.orbital.evekit.model.corporation.MemberRole;
import enterprises.orbital.evekit.model.corporation.MemberRoleHistory;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationMembershipSync extends AbstractESIAccountSync<ESICorporationMembershipSync.MembershipData> {
  protected static final Logger log = Logger.getLogger(ESICorporationMembershipSync.class.getName());

  static class MembershipData {
    List<Integer> members;
    List<GetCorporationsCorporationIdRoles200Ok> roles;
    List<GetCorporationsCorporationIdRolesHistory200Ok> history;
  }

  public ESICorporationMembershipSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_MEMBERSHIP;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof MemberRole) ||
        (item instanceof MemberRoleHistory) ||
        (item instanceof Member);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof MemberRole) {
        MemberRole mr = (MemberRole) item;
        existing = MemberRole.get(account, time, mr.getCharacterID(), mr.getRoleName(), mr.isGrantable(), mr.isAtHQ(),
                                  mr.isAtBase(), mr.isAtOther());
      } else if (item instanceof MemberRoleHistory) {
        MemberRoleHistory mrh = (MemberRoleHistory) item;
        existing = MemberRoleHistory.get(account, time, mrh.getCharacterID(), mrh.getChangedAt(), mrh.getIssuerID(),
                                         mrh.getRoleType(), mrh.getRoleName(), mrh.isOld());
      } else
        existing = Member.get(account, time, ((Member) item).getCharacterID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<MembershipData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();
    MembershipData resultData = new MembershipData();
    long expiry;

    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<Integer>> apir = apiInstance.getCorporationsCorporationIdMembersWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          accessToken(),
          null,
          null);
      checkCommonProblems(apir);
      resultData.members = apir.getData();
      expiry = extractExpiry(apir, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCorporationsCorporationIdRoles200Ok>> apir = apiInstance.getCorporationsCorporationIdRolesWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          accessToken(),
          null,
          null);
      checkCommonProblems(apir);
      resultData.roles = apir.getData();
      expiry = Math.max(expiry, extractExpiry(apir, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    {
      Pair<Long, List<GetCorporationsCorporationIdRolesHistory200Ok>> result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdRolesHistoryWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            page,
            accessToken(),
            null,
            null);
      });
      expiry = Math.max(expiry,
                        result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay());
      resultData.history = result.getRight();
    }

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<MembershipData> data,
                                   List<CachedData> updates) throws IOException {

    // Convenient masks
    final int GRANTABLE = 1;
    final int AT_HQ = 1 << 1;
    final int AT_BASE = 1 << 2;
    final int AT_OTHER = 1 << 3;

    // Keep track of seen members and roles.  History is immutable, so we don't need
    // to track deletions.
    Set<Integer> seenMembers = new HashSet<>();
    Set<Triple<Integer, String, Integer>> seenRoles = new HashSet<>();

    // Process data
    for (Integer next : data.getData().members) {
      seenMembers.add(next);
      updates.add(new Member(next));
    }

    for (GetCorporationsCorporationIdRoles200Ok next : data.getData().roles) {
      for (GetCorporationsCorporationIdRoles200Ok.RolesEnum role : next.getRoles()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), 0));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   false,
                                   false,
                                   false,
                                   false));
      }
      for (GetCorporationsCorporationIdRoles200Ok.RolesAtBaseEnum role : next.getRolesAtBase()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), AT_BASE));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   false,
                                   false,
                                   true,
                                   false));
      }
      for (GetCorporationsCorporationIdRoles200Ok.RolesAtHqEnum role : next.getRolesAtHq()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), AT_HQ));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   false,
                                   true,
                                   false,
                                   false));
      }
      for (GetCorporationsCorporationIdRoles200Ok.RolesAtOtherEnum role : next.getRolesAtOther()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), AT_OTHER));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   false,
                                   false,
                                   false,
                                   true));
      }
      for (GetCorporationsCorporationIdRoles200Ok.GrantableRolesEnum role : next.getGrantableRoles()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), GRANTABLE));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   true,
                                   false,
                                   false,
                                   false));
      }
      for (GetCorporationsCorporationIdRoles200Ok.GrantableRolesAtBaseEnum role : next.getGrantableRolesAtBase()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), GRANTABLE | AT_BASE));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   true,
                                   false,
                                   true,
                                   false));
      }
      for (GetCorporationsCorporationIdRoles200Ok.GrantableRolesAtHqEnum role : next.getGrantableRolesAtHq()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), GRANTABLE | AT_HQ));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   true,
                                   true,
                                   false,
                                   false));
      }
      for (GetCorporationsCorporationIdRoles200Ok.GrantableRolesAtOtherEnum role : next.getGrantableRolesAtOther()) {
        seenRoles.add(Triple.of(next.getCharacterId(), role.toString(), GRANTABLE | AT_OTHER));
        updates.add(new MemberRole(next.getCharacterId(),
                                   role.toString(),
                                   true,
                                   false,
                                   false,
                                   true));
      }
    }

    for (GetCorporationsCorporationIdRolesHistory200Ok next : data.getData().history) {
      for (GetCorporationsCorporationIdRolesHistory200Ok.OldRolesEnum oo : next.getOldRoles()) {
        updates.add(new MemberRoleHistory(next.getCharacterId(),
                                          next.getChangedAt()
                                              .getMillis(),
                                          next.getIssuerId(),
                                          next.getRoleType()
                                              .toString(),
                                          oo.toString(),
                                          true));
      }
      for (GetCorporationsCorporationIdRolesHistory200Ok.NewRolesEnum nn : next.getNewRoles()) {
        updates.add(new MemberRoleHistory(next.getCharacterId(),
                                          next.getChangedAt()
                                              .getMillis(),
                                          next.getIssuerId(),
                                          next.getRoleType()
                                              .toString(),
                                          nn.toString(),
                                          false));
      }
    }

    // Check for data that no longer exists and schedule for EOL
    for (Member existing : retrieveAll(time,
                                       (long contid, AttributeSelector at) -> Member.accessQuery(
                                           account, contid,
                                           1000,
                                           false, at,
                                           ANY_SELECTOR))) {
      if (!seenMembers.contains(existing.getCharacterID())) {
        // Mark for EOL.
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    for (MemberRole existing : retrieveAll(time,
                                           (contid, at) -> MemberRole.accessQuery(account, contid,
                                                                                  1000, false, at,
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any()))) {
      int mask = (existing.isGrantable() ? GRANTABLE : 0) |
          (existing.isAtBase() ? AT_BASE : 0) |
          (existing.isAtHQ() ? AT_HQ : 0) |
          (existing.isAtOther() ? AT_OTHER : 0);
      if (!seenRoles.contains(Triple.of(existing.getCharacterID(), existing.getRoleName(), mask))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

  }

}
