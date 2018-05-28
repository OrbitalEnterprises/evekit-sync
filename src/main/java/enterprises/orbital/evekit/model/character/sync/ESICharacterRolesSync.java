package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdRolesOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterRole;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterRolesSync extends AbstractESIAccountSync<GetCharactersCharacterIdRolesOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterRolesSync.class.getName());

  public ESICharacterRolesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_CORP_ROLES;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterRole;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CharacterRole.get(account, time, ((CharacterRole) item).getRoleCategory(),
                                   ((CharacterRole) item).getRoleName());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdRolesOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdRolesOk> result = apiInstance.getCharactersCharacterIdRolesWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdRolesOk> data,
                                   List<CachedData> updates) throws IOException {
    Set<Pair<String, String>> seenRoles = new HashSet<>();
    for (GetCharactersCharacterIdRolesOk.RolesEnum next : data.getData()
                                                              .getRoles()) {
      updates.add(new CharacterRole(CharacterRole.CAT_CORPORATION, String.valueOf(next)));
      seenRoles.add(Pair.of(CharacterRole.CAT_CORPORATION, String.valueOf(next)));
    }
    for (GetCharactersCharacterIdRolesOk.RolesAtBaseEnum next : data.getData()
                                                                    .getRolesAtBase()) {
      updates.add(new CharacterRole(CharacterRole.CAT_CORPORATION_AT_BASE, String.valueOf(next)));
      seenRoles.add(Pair.of(CharacterRole.CAT_CORPORATION_AT_BASE, String.valueOf(next)));
    }
    for (GetCharactersCharacterIdRolesOk.RolesAtHqEnum next : data.getData()
                                                                  .getRolesAtHq()) {
      updates.add(new CharacterRole(CharacterRole.CAT_CORPORATION_AT_HQ, String.valueOf(next)));
      seenRoles.add(Pair.of(CharacterRole.CAT_CORPORATION_AT_HQ, String.valueOf(next)));
    }
    for (GetCharactersCharacterIdRolesOk.RolesAtOtherEnum next : data.getData()
                                                                     .getRolesAtOther()) {
      updates.add(new CharacterRole(CharacterRole.CAT_CORPORATION_AT_OTHER, String.valueOf(next)));
      seenRoles.add(Pair.of(CharacterRole.CAT_CORPORATION_AT_OTHER, String.valueOf(next)));
    }

    // Now check for roles that should be EOL
    for (CharacterRole existing : CachedData.retrieveAll(time,
                                                         (contid, at) -> CharacterRole.accessQuery(account, contid,
                                                                                                   1000, false, at,
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any()))) {
      if (!seenRoles.contains(Pair.of(existing.getRoleCategory(), existing.getRoleName()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
