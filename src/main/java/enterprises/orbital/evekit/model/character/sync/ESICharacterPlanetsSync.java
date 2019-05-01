package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.PlanetaryInteractionApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class ESICharacterPlanetsSync extends AbstractESIAccountSync<ESICharacterPlanetsSync.PlanetData> {
  protected static final Logger log = Logger.getLogger(ESICharacterPlanetsSync.class.getName());

  class PlanetData {
    List<GetCharactersCharacterIdPlanets200Ok> planets;
    Map<Integer, GetCharactersCharacterIdPlanetsPlanetIdOk> planetData = new HashMap<>();
  }

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  public ESICharacterPlanetsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_PLANETS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof PlanetaryColony) ||
        (item instanceof PlanetaryRoute) ||
        (item instanceof PlanetaryLink) ||
        (item instanceof PlanetaryPin);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof PlanetaryColony)
        existing = PlanetaryColony.get(account, time, ((PlanetaryColony) item).getPlanetID());
      else if (item instanceof PlanetaryRoute)
        existing = PlanetaryRoute.get(account, time, ((PlanetaryRoute) item).getPlanetID(),
                                      ((PlanetaryRoute) item).getRouteID());
      else if (item instanceof PlanetaryLink)
        existing = PlanetaryLink.get(account, time, ((PlanetaryLink) item).getPlanetID(),
                                     ((PlanetaryLink) item).getSourcePinID(),
                                     ((PlanetaryLink) item).getDestinationPinID());
      else
        existing = PlanetaryPin.get(account, time, ((PlanetaryPin) item).getPlanetID(),
                                    ((PlanetaryPin) item).getPinID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<PlanetData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    PlanetData data = new PlanetData();
    PlanetaryInteractionApi apiInstance = cp.getPlanetaryInteractionApi();

    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdPlanets200Ok>> planetResult = apiInstance.getCharactersCharacterIdPlanetsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        accessToken());
    checkCommonProblems(planetResult);
    data.planets = planetResult.getData();
    long expiry = extractExpiry(planetResult, OrbitalProperties.getCurrentTime() + maxDelay());

    for (GetCharactersCharacterIdPlanets200Ok next : data.planets) {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<GetCharactersCharacterIdPlanetsPlanetIdOk> piResult = apiInstance.getCharactersCharacterIdPlanetsPlanetIdWithHttpInfo(
          (int) account.getEveCharacterID(),
          next.getPlanetId(),
          null,
          null,
          accessToken());
      checkCommonProblems(piResult);
      data.planetData.put(next.getPlanetId(), piResult.getData());
      expiry = Math.max(expiry, extractExpiry(piResult, OrbitalProperties.getCurrentTime() + maxDelay()));
    }

    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<PlanetData> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(1);

    // Compute and check hash
    {
      List<PlanetaryColony> retrievedColonies = new ArrayList<>();
      List<PlanetaryRoute> retrievedRoutes = new ArrayList<>();
      List<PlanetaryPin> retrievedPins = new ArrayList<>();
      List<PlanetaryLink> retrievedLinks = new ArrayList<>();
      for (GetCharactersCharacterIdPlanets200Ok next : data.getData().planets) {
        retrievedColonies.add(new PlanetaryColony(
            next.getPlanetId(),
            next.getSolarSystemId(),
            next.getPlanetType()
                .toString(),
            next.getOwnerId(),
            next.getLastUpdate()
                .getMillis(),
            next.getUpgradeLevel(),
            next.getNumPins()
        ));
        GetCharactersCharacterIdPlanetsPlanetIdOk pInfo = data.getData().planetData.get(next.getPlanetId());

        // Create routes
        for (GetCharactersCharacterIdPlanetsPlanetIdRoute route : pInfo.getRoutes()) {
          PlanetaryRoute newRoute = new PlanetaryRoute(
              next.getPlanetId(),
              route.getRouteId(),
              route.getSourcePinId(),
              route.getDestinationPinId(),
              route.getContentTypeId(),
              route.getQuantity(),
              new ArrayList<>()
          );
          for (long waypoint : route.getWaypoints())
            newRoute.getWaypoints()
                    .add(waypoint);
          retrievedRoutes.add(newRoute);
        }

        // Create pins
        for (GetCharactersCharacterIdPlanetsPlanetIdPin pin : pInfo.getPins()) {
          GetCharactersCharacterIdPlanetsPlanetIdExtractorDetails extractor = pin.getExtractorDetails();
          PlanetaryPin newPin = new PlanetaryPin(
              next.getPlanetId(),
              pin.getPinId(),
              pin.getTypeId(),
              nullSafeInteger(pin.getSchematicId(), 0),
              nullSafeDateTime(pin.getLastCycleStart(), new DateTime(new Date(0L))).getMillis(),
              extractor == null ? 0 : nullSafeInteger(extractor.getCycleTime(), 0),
              extractor == null ? 0 : nullSafeInteger(extractor.getQtyPerCycle(), 0),
              nullSafeDateTime(pin.getInstallTime(), new DateTime(new Date(0L))).getMillis(),
              nullSafeDateTime(pin.getExpiryTime(), new DateTime(new Date(0L))).getMillis(),
              extractor == null ? 0 : nullSafeInteger(extractor.getProductTypeId(), 0),
              pin.getLongitude(),
              pin.getLatitude(),
              extractor == null ? 0 : nullSafeFloat(extractor.getHeadRadius(), 0),
              new HashSet<>(),
              new HashSet<>());
          for (GetCharactersCharacterIdPlanetsPlanetIdContent content : pin.getContents())
            newPin.getContents()
                  .add(new PlanetaryPinContent(content.getTypeId(), content.getAmount()));
          if (extractor != null) {
            for (GetCharactersCharacterIdPlanetsPlanetIdHead head : extractor.getHeads())
              newPin.getHeads()
                    .add(new PlanetaryPinHead(head.getHeadId(), head.getLatitude(), head.getLongitude()));
          }
          retrievedPins.add(newPin);
        }

        // Create links
        for (GetCharactersCharacterIdPlanetsPlanetIdLink link : pInfo.getLinks()) {
          retrievedLinks.add(new PlanetaryLink(
              next.getPlanetId(),
              link.getSourcePinId(),
              link.getDestinationPinId(),
              link.getLinkLevel()
          ));
        }
      }
      retrievedColonies.sort(Comparator.comparingInt(PlanetaryColony::getPlanetID));
      retrievedRoutes.sort((o1, o2) -> {
        int pid = Comparator.comparingInt(PlanetaryRoute::getPlanetID)
                            .compare(o1, o2);
        return pid != 0 ? pid : Comparator.comparingLong(PlanetaryRoute::getRouteID)
                                          .compare(o1, o2);
      });
      retrievedPins.sort((o1, o2) -> {
        int pid = Comparator.comparingInt(PlanetaryPin::getPlanetID)
                            .compare(o1, o2);
        return pid != 0 ? pid : Comparator.comparingLong(PlanetaryPin::getPinID)
                                          .compare(o1, o2);
      });
      retrievedLinks.sort((o1, o2) -> {
        int pid = Comparator.comparingInt(PlanetaryLink::getPlanetID)
                            .compare(o1, o2);
        if (pid != 0) return pid;
        pid = Comparator.comparingLong(PlanetaryLink::getSourcePinID)
                        .compare(o1, o2);
        return pid != 0 ? pid : Comparator.comparingLong(PlanetaryLink::getDestinationPinID)
                                          .compare(o1, o2);
      });

      String hash = CachedData.dataHashHelper(CachedData.dataHashHelper(retrievedColonies.toArray()),
                                              CachedData.dataHashHelper(retrievedRoutes.toArray()),
                                              CachedData.dataHashHelper(retrievedPins.toArray()),
                                              CachedData.dataHashHelper(retrievedLinks.toArray()));

      if (cachedHash[0] == null || !cachedHash[0].equals(hash)) {
        // New data to process
        cacheMiss();
        cachedHash[0] = hash;
      } else {
        // We've seen this data, short circuit
        cacheHit();
        currentETag = String.join("|", cachedHash);
        return;
      }

      // Save new hash
      currentETag = String.join("|", cachedHash);
    }

    // Save seen planets, routes, pins and links
    Set<Integer> seenPlanets = new HashSet<>(); // planetID
    Set<Pair<Integer, Long>> seenRoutes = new HashSet<>(); // planetID, routeID
    Set<Pair<Integer, Long>> seenPins = new HashSet<>(); // planetID, pinID
    Set<Triple<Integer, Long, Long>> seenLinks = new HashSet<>(); // planetID, srcPinID, destPinID

    // Create colonies
    for (GetCharactersCharacterIdPlanets200Ok next : data.getData().planets) {
      updates.add(new PlanetaryColony(
          next.getPlanetId(),
          next.getSolarSystemId(),
          next.getPlanetType()
              .toString(),
          next.getOwnerId(),
          next.getLastUpdate()
              .getMillis(),
          next.getUpgradeLevel(),
          next.getNumPins()
      ));
      seenPlanets.add(next.getPlanetId());
      GetCharactersCharacterIdPlanetsPlanetIdOk pInfo = data.getData().planetData.get(next.getPlanetId());

      // Create routes
      for (GetCharactersCharacterIdPlanetsPlanetIdRoute route : pInfo.getRoutes()) {
        PlanetaryRoute newRoute = new PlanetaryRoute(
            next.getPlanetId(),
            route.getRouteId(),
            route.getSourcePinId(),
            route.getDestinationPinId(),
            route.getContentTypeId(),
            route.getQuantity(),
            new ArrayList<>()
        );
        for (long waypoint : route.getWaypoints())
          newRoute.getWaypoints()
                  .add(waypoint);
        updates.add(newRoute);
        seenRoutes.add(Pair.of(next.getPlanetId(), route.getRouteId()));
      }

      // Create pins
      for (GetCharactersCharacterIdPlanetsPlanetIdPin pin : pInfo.getPins()) {
        GetCharactersCharacterIdPlanetsPlanetIdExtractorDetails extractor = pin.getExtractorDetails();
        // GetCharactersCharacterIdPlanetsPlanetIdFactoryDetails factory = pin.getFactoryDetails();
        PlanetaryPin newPin = new PlanetaryPin(
            next.getPlanetId(),
            pin.getPinId(),
            pin.getTypeId(),
            nullSafeInteger(pin.getSchematicId(), 0),
            nullSafeDateTime(pin.getLastCycleStart(), new DateTime(new Date(0L))).getMillis(),
            extractor == null ? 0 : nullSafeInteger(extractor.getCycleTime(), 0),
            extractor == null ? 0 : nullSafeInteger(extractor.getQtyPerCycle(), 0),
            nullSafeDateTime(pin.getInstallTime(), new DateTime(new Date(0L))).getMillis(),
            nullSafeDateTime(pin.getExpiryTime(), new DateTime(new Date(0L))).getMillis(),
            extractor == null ? 0 : nullSafeInteger(extractor.getProductTypeId(), 0),
            pin.getLongitude(),
            pin.getLatitude(),
            extractor == null ? 0 : nullSafeFloat(extractor.getHeadRadius(), 0),
            new HashSet<>(),
            new HashSet<>());
        for (GetCharactersCharacterIdPlanetsPlanetIdContent content : pin.getContents())
          newPin.getContents()
                .add(new PlanetaryPinContent(content.getTypeId(), content.getAmount()));
        if (extractor != null) {
          for (GetCharactersCharacterIdPlanetsPlanetIdHead head : extractor.getHeads())
            newPin.getHeads()
                  .add(new PlanetaryPinHead(head.getHeadId(), head.getLatitude(), head.getLongitude()));
        }
        updates.add(newPin);
        seenPins.add(Pair.of(next.getPlanetId(), pin.getPinId()));
      }

      // Create links
      for (GetCharactersCharacterIdPlanetsPlanetIdLink link : pInfo.getLinks()) {
        updates.add(new PlanetaryLink(
            next.getPlanetId(),
            link.getSourcePinId(),
            link.getDestinationPinId(),
            link.getLinkLevel()
        ));
        seenLinks.add(Triple.of(next.getPlanetId(), link.getSourcePinId(), link.getDestinationPinId()));
      }
    }

    // Remove non-existent colonies
    for (PlanetaryColony existing : retrieveAll(time,
                                                (long contid, AttributeSelector at) -> PlanetaryColony.accessQuery(
                                                    account, contid,
                                                    1000,
                                                    false, at,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR))) {
      if (!seenPlanets.contains(existing.getPlanetID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    // Remove non-existent routes
    for (PlanetaryRoute existing : retrieveAll(time,
                                               (long contid, AttributeSelector at) -> PlanetaryRoute.accessQuery(
                                                   account, contid,
                                                   1000,
                                                   false, at,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR,
                                                   ANY_SELECTOR))) {
      if (!seenRoutes.contains(Pair.of(existing.getPlanetID(), existing.getRouteID()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    // Remove non-existent pins
    for (PlanetaryPin existing : retrieveAll(time,
                                             (long contid, AttributeSelector at) -> PlanetaryPin.accessQuery(account,
                                                                                                             contid,
                                                                                                             1000,
                                                                                                             false, at,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR,
                                                                                                             ANY_SELECTOR))) {
      if (!seenPins.contains(Pair.of(existing.getPlanetID(), existing.getPinID()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    // Remove non-existent links
    for (PlanetaryLink existing : retrieveAll(time,
                                              (long contid, AttributeSelector at) -> PlanetaryLink.accessQuery(account,
                                                                                                               contid,
                                                                                                               1000,
                                                                                                               false,
                                                                                                               at,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR))) {
      if (!seenLinks.contains(
          Triple.of(existing.getPlanetID(), existing.getSourcePinID(), existing.getDestinationPinID()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
