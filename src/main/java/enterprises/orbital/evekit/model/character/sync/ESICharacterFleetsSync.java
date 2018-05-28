package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.FleetsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICharacterFleetsSync extends AbstractESIAccountSync<ESICharacterFleetsSync.FleetData> {
  protected static final Logger log = Logger.getLogger(ESICharacterFleetsSync.class.getName());

  static class FleetData {
    GetCharactersCharacterIdFleetOk charFleet;
    GetFleetsFleetIdOk fleetInfo;
    List<GetFleetsFleetIdMembers200Ok> fleetMembers = Collections.emptyList();
    List<GetFleetsFleetIdWings200Ok> fleetWings = Collections.emptyList();
  }

  public ESICharacterFleetsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_FLEETS;
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CharacterFleet) ||
        (item instanceof FleetInfo) ||
        (item instanceof FleetMember) ||
        (item instanceof FleetWing) ||
        (item instanceof FleetSquad);

    // Fleet information is mutable while a character is still in the fleet.  Once the character
    // leaves the fleet, we retire all associated data.  It is possible we can miss leaving one
    // fleet and joining another.  When this happens existing fleet information will migrate.
    CachedData existing = null;

    if (item instanceof CharacterFleet && item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = CharacterFleet.get(account, time, ((CharacterFleet) item).getFleetID());
    else if (item instanceof FleetInfo && item.getLifeStart() == 0)
      existing = FleetInfo.get(account, time, ((FleetInfo) item).getFleetID());
    else if (item instanceof FleetMember && item.getLifeStart() == 0)
      existing = FleetMember.get(account, time, ((FleetMember) item).getFleetID(),
                                 ((FleetMember) item).getCharacterID());
    else if (item instanceof FleetWing && item.getLifeStart() == 0)
      existing = FleetWing.get(account, time, ((FleetWing) item).getFleetID(), ((FleetWing) item).getWingID());
    else if (item instanceof FleetSquad && item.getLifeStart() == 0)
      existing = FleetSquad.get(account, time, ((FleetSquad) item).getFleetID(), ((FleetSquad) item).getWingID(),
                                ((FleetSquad) item).getSquadID());

    // Otherwise, create entry
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<FleetData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    FleetsApi apiInstance = cp.getFleetsApi();
    FleetData data = new FleetData();

    // Retrieve current character fleet
    long expiry;
    ESIThrottle.throttle(endpoint().name(), account);
    {
      try {
        ApiResponse<GetCharactersCharacterIdFleetOk> result = apiInstance.getCharactersCharacterIdFleetWithHttpInfo(
            (int) account.getEveCharacterID(),
            null,
            null,
            accessToken());
        // Otherwise, check for other problems and continue
        checkCommonProblems(result);
        data.charFleet = result.getData();
        expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());
      } catch (ApiException e) {
        // This call will 404 if the character is not in a fleet, in which case we can immediately return
        // data and exit.
        if (e.getCode() == HttpStatus.SC_NOT_FOUND)
          return new ESIAccountServerResult<>(
              OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(60, TimeUnit.SECONDS), data);

        // Otherwise, something we didn't expect so throw it
        throw e;
      }
    }

    // Retrieve fleet info
    ESIThrottle.throttle(endpoint().name(), account);
    {
      try {
        ApiResponse<GetFleetsFleetIdOk> result = apiInstance.getFleetsFleetIdWithHttpInfo(
            data.charFleet.getFleetId(),
            null,
            null,
            accessToken());
        checkCommonProblems(result);
        data.fleetInfo = result.getData();
        expiry = Math.max(expiry, extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()));
      } catch (ApiException e) {
        // This call will 404 if the character is in a fleet, but we're not allowed to access fleet info.
        // This is benign, so just return our results so far.
        if (e.getCode() == HttpStatus.SC_NOT_FOUND)
          return new ESIAccountServerResult<>(expiry, data);

        // Otherwise, something we didn't expect so throw it
        throw e;
      }
    }

    // Retrieve fleet members
    ESIThrottle.throttle(endpoint().name(), account);
    {
      ApiResponse<List<GetFleetsFleetIdMembers200Ok>> result = apiInstance.getFleetsFleetIdMembersWithHttpInfo(
          data.charFleet.getFleetId(),
          null,
          null,
          null,
          null,
          accessToken());
      checkCommonProblems(result);
      data.fleetMembers = result.getData();
      expiry = Math.max(expiry, extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    // Retrieve fleet wings
    ESIThrottle.throttle(endpoint().name(), account);
    {
      ApiResponse<List<GetFleetsFleetIdWings200Ok>> result = apiInstance.getFleetsFleetIdWingsWithHttpInfo(
          data.charFleet.getFleetId(),
          null,
          null,
          null,
          null,
          accessToken());
      checkCommonProblems(result);
      data.fleetWings = result.getData();
      expiry = Math.max(expiry, extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<FleetData> data,
                                   List<CachedData> updates) throws IOException {

    FleetData source = data.getData();

    // Only set if it turns out the character is currently in a fleet.
    long currentFleet = -1;

    // Only populate if the character is actually in a fleet
    if (source.charFleet != null) {
      currentFleet = source.charFleet.getFleetId();
      updates.add(new CharacterFleet(source.charFleet.getFleetId(),
                                     source.charFleet.getRole()
                                                     .toString(),
                                     source.charFleet.getSquadId(),
                                     source.charFleet.getWingId()));

      Set<Integer> seenMembers = new HashSet<>();
      Set<Long> seenWings = new HashSet<>();
      Set<Pair<Long, Long>> seenSquads = new HashSet<>();

      if (source.fleetInfo != null) {
        // We may be in a fleet, but not have access to the details.  Only populate
        // info and wings if we have that info.
        updates.add(new FleetInfo(currentFleet,
                                  source.fleetInfo.getIsFreeMove(),
                                  source.fleetInfo.getIsRegistered(),
                                  source.fleetInfo.getIsVoiceEnabled(),
                                  source.fleetInfo.getMotd()));

        for (GetFleetsFleetIdMembers200Ok mem : source.fleetMembers) {
          updates.add(new FleetMember(currentFleet,
                                      mem.getCharacterId(),
                                      mem.getJoinTime()
                                         .getMillis(),
                                      mem.getRole()
                                         .toString(),
                                      mem.getRoleName(),
                                      mem.getShipTypeId(),
                                      mem.getSolarSystemId(),
                                      mem.getSquadId(),
                                      nullSafeLong(mem.getStationId(), 0),
                                      mem.getTakesFleetWarp(),
                                      mem.getWingId()));
          seenMembers.add(mem.getCharacterId());
        }

        for (GetFleetsFleetIdWings200Ok wing : source.fleetWings) {
          updates.add(new FleetWing(currentFleet,
                                    wing.getId(),
                                    wing.getName()));
          seenWings.add(wing.getId());
          for (GetFleetsFleetIdWingsSquad squad : wing.getSquads()) {
            updates.add(new FleetSquad(currentFleet,
                                       wing.getId(),
                                       squad.getId(),
                                       squad.getName()));
            seenSquads.add(Pair.of(wing.getId(), squad.getId()));
          }
        }
      }

      // Evolve any members which have dropped out of the current fleet
      final long fleetID = currentFleet;
      for (FleetMember f : CachedData.retrieveAll(time,
                                                  (contid, at) -> FleetMember.accessQuery(account, contid, 1000, false,
                                                                                          at,
                                                                                          AttributeSelector.values(
                                                                                              fleetID),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any()))) {
        if (!seenMembers.contains(f.getCharacterID())) {
          f.evolve(null, time);
          updates.add(f);
        }
      }

      // Evolve any wings which have dropped out of the current fleet
      for (FleetWing f : CachedData.retrieveAll(time,
                                                (contid, at) -> FleetWing.accessQuery(account, contid, 1000, false,
                                                                                      at,
                                                                                      AttributeSelector.values(fleetID),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any()))) {
        if (!seenWings.contains(f.getWingID())) {
          f.evolve(null, time);
          updates.add(f);
        }
      }

      // Evolve any squads which have dropped out of the current fleet
      for (FleetSquad f : CachedData.retrieveAll(time,
                                                 (contid, at) -> FleetSquad.accessQuery(account, contid, 1000, false,
                                                                                        at,
                                                                                        AttributeSelector.values(
                                                                                            fleetID),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any()))) {
        if (!seenSquads.contains(Pair.of(f.getWingID(), f.getSquadID()))) {
          f.evolve(null, time);
          updates.add(f);
        }
      }

    }

    // Evolve any live objects which are not for the current fleet.
    for (CharacterFleet f : CachedData.retrieveAll(time,
                                                   (contid, at) -> CharacterFleet.accessQuery(account, contid, 1000,
                                                                                              false, at,
                                                                                              AttributeSelector.any(),
                                                                                              AttributeSelector.any(),
                                                                                              AttributeSelector.any(),
                                                                                              AttributeSelector.any()))) {
      if (currentFleet != f.getFleetID()) {
        f.evolve(null, time);
        updates.add(f);
      }
    }

    for (FleetInfo f : CachedData.retrieveAll(time,
                                              (contid, at) -> FleetInfo.accessQuery(account, contid, 1000, false, at,
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any()))) {
      if (currentFleet != f.getFleetID()) {
        f.evolve(null, time);
        updates.add(f);
      }
    }

    for (FleetMember f : CachedData.retrieveAll(time,
                                                (contid, at) -> FleetMember.accessQuery(account, contid, 1000, false,
                                                                                        at,
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any(),
                                                                                        AttributeSelector.any()))) {
      if (currentFleet != f.getFleetID()) {
        f.evolve(null, time);
        updates.add(f);
      }
    }

    for (FleetWing f : CachedData.retrieveAll(time,
                                              (contid, at) -> FleetWing.accessQuery(account, contid, 1000, false, at,
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any()))) {
      if (currentFleet != f.getFleetID()) {
        f.evolve(null, time);
        updates.add(f);
      }
    }

    for (FleetSquad f : CachedData.retrieveAll(time,
                                               (contid, at) -> FleetSquad.accessQuery(account, contid, 1000, false, at,
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any()))) {
      if (currentFleet != f.getFleetID()) {
        f.evolve(null, time);
        updates.add(f);
      }
    }

  }

}
