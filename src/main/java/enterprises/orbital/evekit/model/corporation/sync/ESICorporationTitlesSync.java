package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMembersTitles200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdTitles200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CorporationTitle;
import enterprises.orbital.evekit.model.corporation.CorporationTitleRole;
import enterprises.orbital.evekit.model.corporation.MemberTitle;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationTitlesSync extends AbstractESIAccountSync<ESICorporationTitlesSync.TitleData> {
  protected static final Logger log = Logger.getLogger(ESICorporationTitlesSync.class.getName());

  static class TitleData {
    List<GetCorporationsCorporationIdTitles200Ok> titles;
    List<GetCorporationsCorporationIdMembersTitles200Ok> members;
  }

  public ESICorporationTitlesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_TITLES;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CorporationTitle) ||
        (item instanceof CorporationTitleRole) ||
        (item instanceof MemberTitle);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof CorporationTitle)
        existing = CorporationTitle.get(account, time, ((CorporationTitle) item).getTitleID());
      else if (item instanceof CorporationTitleRole) {
        CorporationTitleRole ctr = (CorporationTitleRole) item;
        existing = CorporationTitleRole.get(account, time, ctr.getTitleID(), ctr.getRoleName(),
                                            ctr.isGrantable(), ctr.isAtHQ(), ctr.isAtBase(),
                                            ctr.isAtOther());
      } else
        existing = MemberTitle.get(account, time, ((MemberTitle) item).getCharacterID(),
                                   ((MemberTitle) item).getTitleID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<TitleData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();
    TitleData resultData = new TitleData();
    long expiry;

    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCorporationsCorporationIdTitles200Ok>> apir = apiInstance.getCorporationsCorporationIdTitlesWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          accessToken());
      checkCommonProblems(apir);
      resultData.titles = apir.getData();
      expiry = extractExpiry(apir, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCorporationsCorporationIdMembersTitles200Ok>> apir = apiInstance.getCorporationsCorporationIdMembersTitlesWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          accessToken());
      checkCommonProblems(apir);
      resultData.members = apir.getData();
      expiry = Math.max(expiry, extractExpiry(apir, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<TitleData> data,
                                   List<CachedData> updates) throws IOException {

    // Convenient masks
    final int GRANTABLE = 1;
    final int AT_HQ = 1 << 1;
    final int AT_BASE = 1 << 2;
    final int AT_OTHER = 1 << 3;

    // Keep track of seen titles and assignments
    Set<Integer> seenTitles = new HashSet<>();
    Set<Triple<Integer, String, Integer>> seenRoles = new HashSet<>();
    Set<Pair<Integer, Integer>> seenMembers = new HashSet<>();

    // Process data
    for (GetCorporationsCorporationIdTitles200Ok next : data.getData().titles) {
      seenTitles.add(nullSafeInteger(next.getTitleId(), 0));
      updates.add(new CorporationTitle(nullSafeInteger(next.getTitleId(), 0), next.getName()));
      for (GetCorporationsCorporationIdTitles200Ok.RolesEnum role : next.getRoles()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), 0));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             false,
                                             false,
                                             false,
                                             false));
      }
      for (GetCorporationsCorporationIdTitles200Ok.RolesAtBaseEnum role : next.getRolesAtBase()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), AT_BASE));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             false,
                                             false,
                                             true,
                                             false));
      }
      for (GetCorporationsCorporationIdTitles200Ok.RolesAtHqEnum role : next.getRolesAtHq()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), AT_HQ));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             false,
                                             true,
                                             false,
                                             false));
      }
      for (GetCorporationsCorporationIdTitles200Ok.RolesAtOtherEnum role : next.getRolesAtOther()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), AT_OTHER));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             false,
                                             false,
                                             false,
                                             true));
      }
      for (GetCorporationsCorporationIdTitles200Ok.GrantableRolesEnum role : next.getGrantableRoles()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), GRANTABLE));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             true,
                                             false,
                                             false,
                                             false));
      }
      for (GetCorporationsCorporationIdTitles200Ok.GrantableRolesAtBaseEnum role : next.getGrantableRolesAtBase()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), GRANTABLE | AT_BASE));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             true,
                                             false,
                                             true,
                                             false));
      }
      for (GetCorporationsCorporationIdTitles200Ok.GrantableRolesAtHqEnum role : next.getGrantableRolesAtHq()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), GRANTABLE | AT_HQ));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             true,
                                             true,
                                             false,
                                             false));
      }
      for (GetCorporationsCorporationIdTitles200Ok.GrantableRolesAtOtherEnum role : next.getGrantableRolesAtOther()) {
        seenRoles.add(Triple.of(nullSafeInteger(next.getTitleId(), 0), role.toString(), GRANTABLE | AT_OTHER));
        updates.add(new CorporationTitleRole(nullSafeInteger(next.getTitleId(), 0),
                                             role.toString(),
                                             true,
                                             false,
                                             false,
                                             true));
      }
    }

    for (GetCorporationsCorporationIdMembersTitles200Ok next : data.getData().members) {
      for (Integer t : next.getTitles()) {
        seenMembers.add(Pair.of(next.getCharacterId(), t));
        updates.add(new MemberTitle(next.getCharacterId(), t));
      }
    }

    // Check for data that no longer exists and schedule for EOL
    for (CorporationTitle existing : retrieveAll(time,
                                                 (long contid, AttributeSelector at) -> CorporationTitle.accessQuery(
                                                     account, contid,
                                                     1000,
                                                     false, at,
                                                     ANY_SELECTOR,
                                                     ANY_SELECTOR))) {
      if (!seenTitles.contains(existing.getTitleID())) {
        // Mark for EOL.  Note that role deletion will be handled below.
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    for (CorporationTitleRole existing : retrieveAll(time,
                                                     (contid, at) -> CorporationTitleRole.accessQuery(account, contid,
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
      if (!seenRoles.contains(Triple.of(existing.getTitleID(), existing.getRoleName(), mask))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    for (MemberTitle existing : retrieveAll(time,
                                            (long contid, AttributeSelector at) -> MemberTitle.accessQuery(
                                                account, contid,
                                                1000,
                                                false, at,
                                                ANY_SELECTOR,
                                                ANY_SELECTOR))) {
      if (!seenMembers.contains(Pair.of(existing.getCharacterID(), existing.getTitleID()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

  }

}
