package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import enterprises.orbital.evekit.model.character.PlanetaryColony;
import enterprises.orbital.evekit.model.character.PlanetaryLink;
import enterprises.orbital.evekit.model.character.PlanetaryPin;
import enterprises.orbital.evekit.model.character.PlanetaryRoute;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IPlanetaryColony;
import enterprises.orbital.evexmlapi.chr.IPlanetaryLink;
import enterprises.orbital.evexmlapi.chr.IPlanetaryPin;
import enterprises.orbital.evexmlapi.chr.IPlanetaryRoute;

public class CharacterPlanetaryColoniesSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterPlanetaryColoniesSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getPlanetaryColoniesStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setPlanetaryColoniesStatus(status);
    tracker.setPlanetaryColoniesDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setPlanetaryColoniesExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getPlanetaryColoniesExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    // Handle the four types of planetary info
    if (item instanceof PlanetaryColony) {
      PlanetaryColony api = (PlanetaryColony) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing PlanetaryColony to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        PlanetaryColony existing = PlanetaryColony.get(accountKey, time, api.getPlanetID());
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
    } else if (item instanceof PlanetaryLink) {
      PlanetaryLink api = (PlanetaryLink) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing PlanetaryLink to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        PlanetaryLink existing = PlanetaryLink.get(accountKey, time, api.getPlanetID(), api.getSourcePinID(), api.getDestinationPinID());
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
    } else if (item instanceof PlanetaryPin) {
      PlanetaryPin api = (PlanetaryPin) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing PlanetaryPin to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        PlanetaryPin existing = PlanetaryPin.get(accountKey, time, api.getPlanetID(), api.getPinID(), api.getContentTypeID());
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
    } else if (item instanceof PlanetaryRoute) {
      PlanetaryRoute api = (PlanetaryRoute) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing PlanetaryRoute to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        PlanetaryRoute existing = PlanetaryRoute.get(accountKey, time, api.getPlanetID(), api.getRouteID());
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

  // Can't use generic sync for planetary data
  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    throw new UnsupportedOperationException();
  }

  private static final CharacterPlanetaryColoniesSync syncher = new CharacterPlanetaryColoniesSync();

  private static class NaryKey {
    long[] keys;

    public NaryKey(long... k) {
      keys = new long[k.length];
      System.arraycopy(k, 0, keys, 0, k.length);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + Arrays.hashCode(keys);
      return result;
    }

    @Override
    public boolean equals(
                          Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      NaryKey other = (NaryKey) obj;
      if (!Arrays.equals(keys, other.keys)) return false;
      return true;
    }
  }

  public static SyncStatus syncPlanetaryColonies(
                                                 long time,
                                                 SynchronizedEveAccount syncAccount,
                                                 SynchronizerUtil syncUtil,
                                                 ICharacterAPI charRequest) {
    try {
      // Run pre-check.
      String description = "PlanetaryColonies";
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, syncher);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // Start server sync
      log.fine("Starting refresh request for " + description + "  for account " + syncAccount);

      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      try {
        // Load planetary colonies and all related pins, links and routes.
        // Schedule missing colonies, pins, links and routes for deletion.
        long minRefreshTime = Long.MAX_VALUE;
        Collection<IPlanetaryColony> colonies = charRequest.requestPlanetaryColonies();
        minRefreshTime = Math.min(ModelUtil.safeConvertDate(charRequest.getCachedUntil()), minRefreshTime);
        if (charRequest.isError()) {
          // erroneous loop termination
          StringBuilder errStr = new StringBuilder();
          status = handleServerError(charRequest, errStr);
          errorDetail = errStr.toString();
          if (status == SyncTracker.SyncState.SYNC_ERROR) log.warning("request failed: " + errorDetail);
        }

        get_colonies: while (status == SyncTracker.SyncState.UPDATED) {
          Map<Long, Collection<IPlanetaryLink>> linkSet = new HashMap<Long, Collection<IPlanetaryLink>>();
          Map<Long, Collection<IPlanetaryPin>> pinSet = new HashMap<Long, Collection<IPlanetaryPin>>();
          Map<Long, Collection<IPlanetaryRoute>> routeSet = new HashMap<Long, Collection<IPlanetaryRoute>>();

          // Collect all pins, links and routes for each colony.
          for (IPlanetaryColony next : colonies) {
            long pid = next.getPlanetID();
            linkSet.put(pid, charRequest.requestPlanetaryLinks(pid));
            if (charRequest.isError()) break;
            minRefreshTime = Math.min(ModelUtil.safeConvertDate(charRequest.getCachedUntil()), minRefreshTime);
            routeSet.put(pid, charRequest.requestPlanetaryRoutes(pid));
            if (charRequest.isError()) break;
            minRefreshTime = Math.min(ModelUtil.safeConvertDate(charRequest.getCachedUntil()), minRefreshTime);
            pinSet.put(pid, charRequest.requestPlanetaryPins(pid));
            if (charRequest.isError()) break;
            minRefreshTime = Math.min(ModelUtil.safeConvertDate(charRequest.getCachedUntil()), minRefreshTime);
          }

          if (charRequest.isError()) {
            // erroneous loop termination
            StringBuilder errStr = new StringBuilder();
            status = handleServerError(charRequest, errStr);
            errorDetail = errStr.toString();
            if (status != SyncTracker.SyncState.UPDATED) {
              log.warning("request exiting: " + errorDetail);
              break get_colonies;
            }
          }

          // Prepare colonies for population, EOL colonies which no longer exist.
          Set<NaryKey> usedPD = new HashSet<NaryKey>();
          for (IPlanetaryColony nextColony : colonies) {
            PlanetaryColony newC = new PlanetaryColony(
                nextColony.getPlanetID(), nextColony.getSolarSystemID(), nextColony.getSolarSystemName(), nextColony.getPlanetName(),
                nextColony.getPlanetTypeID(), nextColony.getPlanetTypeName(), nextColony.getOwnerID(), nextColony.getOwnerName(),
                ModelUtil.safeConvertDate(nextColony.getLastUpdate()), nextColony.getUpgradeLevel(), nextColony.getNumberOfPins());
            usedPD.add(new NaryKey(nextColony.getPlanetID()));
            updateList.add(newC);
          }
          for (PlanetaryColony check : PlanetaryColony.getAllPlanetaryColonies(syncAccount, time)) {
            if (!usedPD.contains(new NaryKey(check.getPlanetID()))) {
              check.evolve(null, time);
              updateList.add(check);
            }
          }

          // Prepare pins for population, EOL pins which no longer exist.
          usedPD.clear();
          for (Entry<Long, Collection<IPlanetaryPin>> nextPinMap : pinSet.entrySet()) {
            long pid = nextPinMap.getKey();
            for (IPlanetaryPin nextPin : nextPinMap.getValue()) {
              PlanetaryPin newP = new PlanetaryPin(
                  pid, nextPin.getPinID(), nextPin.getTypeID(), nextPin.getTypeName(), nextPin.getSchematicID(),
                  ModelUtil.safeConvertDate(nextPin.getLastLaunchTime()), nextPin.getCycleTime(), nextPin.getQuantityPerCycle(),
                  ModelUtil.safeConvertDate(nextPin.getInstallTime()), ModelUtil.safeConvertDate(nextPin.getExpiryTime()), nextPin.getContentTypeID(),
                  nextPin.getContentTypeName(), nextPin.getContentQuantity(), nextPin.getLongitude(), nextPin.getLatitude());
              usedPD.add(new NaryKey(pid, newP.getPinID(), newP.getContentTypeID()));
              updateList.add(newP);
            }
          }
          for (PlanetaryPin check : PlanetaryPin.getAllPlanetaryPins(syncAccount, time)) {
            long planetID = check.getPlanetID();
            long pinID = check.getPinID();
            int contentTypeID = check.getContentTypeID();
            if (!usedPD.contains(new NaryKey(planetID, pinID, contentTypeID))) {
              check.evolve(null, time);
              updateList.add(check);
            }
          }

          // Prepare links for population, EOL links which no longer exist.
          usedPD.clear();
          for (Entry<Long, Collection<IPlanetaryLink>> nextLinkMap : linkSet.entrySet()) {
            long pid = nextLinkMap.getKey();
            for (IPlanetaryLink nextLink : nextLinkMap.getValue()) {
              PlanetaryLink newP = new PlanetaryLink(pid, nextLink.getSourcePinID(), nextLink.getDestinationPinID(), nextLink.getLinkLevel());
              usedPD.add(new NaryKey(pid, newP.getSourcePinID(), newP.getDestinationPinID()));
              updateList.add(newP);
            }
          }
          for (PlanetaryLink check : PlanetaryLink.getAllPlanetaryLinks(syncAccount, time)) {
            long planetID = check.getPlanetID();
            long sourcePinID = check.getSourcePinID();
            long destPinID = check.getDestinationPinID();
            if (!usedPD.contains(new NaryKey(planetID, sourcePinID, destPinID))) {
              check.evolve(null, time);
              updateList.add(check);
            }
          }

          // Prepare routes for population, EOL routes which no longer exist.
          usedPD.clear();
          for (Entry<Long, Collection<IPlanetaryRoute>> nextRouteMap : routeSet.entrySet()) {
            long pid = nextRouteMap.getKey();
            for (IPlanetaryRoute nextRoute : nextRouteMap.getValue()) {
              PlanetaryRoute newP = new PlanetaryRoute(
                  pid, nextRoute.getRouteID(), nextRoute.getSourcePinID(), nextRoute.getDestinationPinID(), nextRoute.getContentTypeID(),
                  nextRoute.getContentTypeName(), nextRoute.getQuantity(), nextRoute.getWaypoint1(), nextRoute.getWaypoint2(), nextRoute.getWaypoint3(),
                  nextRoute.getWaypoint4(), nextRoute.getWaypoint5());
              usedPD.add(new NaryKey(pid, newP.getRouteID()));
              updateList.add(newP);
            }
          }
          for (PlanetaryRoute check : PlanetaryRoute.getAllPlanetaryRoutes(syncAccount, time)) {
            long planetID = check.getPlanetID();
            long routeID = check.getRouteID();
            if (!usedPD.contains(new NaryKey(planetID, routeID))) {
              check.evolve(null, time);
              updateList.add(check);
            }
          }

          nextExpiry = minRefreshTime;
          break get_colonies;
        }

      } catch (IOException e) {
        status = SyncTracker.SyncState.SYNC_ERROR;
        errorDetail = "request failed with IO error";
        log.warning("request failed with error " + e);
      }

      log.fine("Completed refresh request for " + description + " for account " + syncAccount);

      syncUtil.storeSynchResults(time, CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, status, errorDetail, nextExpiry, description, updateList,
                                 syncher);
      return SyncStatus.DONE;

    } catch (IOException e) {
      // Log but give us another shot in the queue
      log.warning("store failed with error " + e);
      return SyncStatus.ERROR;
    }
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "PlanetaryColonies", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "PlanetaryColonies", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
