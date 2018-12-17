package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBlueprints200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Blueprint;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.logging.Logger;

public class ESICharacterBlueprintsSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdBlueprints200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterBlueprintsSync.class.getName());

  private CachedCharBlueprints cacheUpdate;

  public ESICharacterBlueprintsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_BLUEPRINTS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Blueprint;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = Blueprint.get(account, time, ((Blueprint) item).getItemID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdBlueprints200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    Pair<Long, List<GetCharactersCharacterIdBlueprints200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdBlueprintsWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          null,
          page,
          accessToken());
    });
    return new ESIAccountServerResult<>(
        result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay(),
        result.getRight());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdBlueprints200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Prepare list of latest blueprints
    List<Blueprint> toAdd = new ArrayList<>();
    for (GetCharactersCharacterIdBlueprints200Ok next : data.getData()) {
      Blueprint nextBlueprint = new Blueprint(next.getItemId(), next.getLocationId(), next.getLocationFlag()
                                                                                          .toString(), next.getTypeId(),
                                              next.getQuantity(), next.getTimeEfficiency(),
                                              next.getMaterialEfficiency(), next.getRuns());
      toAdd.add(nextBlueprint);
    }

    // Retrieve blueprint cache and check for changed blueprints, or blueprints that no longer exist and can be
    // deleted.
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CHAR_BLUEPRINTS);
    cacheUpdate = ref != null ? (CachedCharBlueprints) ref.get() : null;
    if (cacheUpdate == null) {
      // If we don't have a cache yet, then create one from all active stored blueprints.
      cacheInit();
      cacheUpdate = new CachedCharBlueprints();
      for (Blueprint next : retrieveAll(time,
                                        (long contid, AttributeSelector at) -> Blueprint.accessQuery(account, contid,
                                                                                                     1000,
                                                                                                     false, at,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR))) {
        cacheUpdate.addBlueprint(next);
      }
    }

    // Created a stored collection from the current cached blueprints
    // We make a copy here to avoid concurrent modification exceptions below.
    Collection<Blueprint> stored = new ArrayList<>(cacheUpdate.cachedBlueprints.values());

    // Process the latest blueprint list.  Any new blueprint should be added
    // and cached.  If the blueprint already exists, but the new copy has changed,
    // then it should also be updated and replace the cached copy.  We also keep
    // track of the ID of all new blueprints so we can detect when an existing
    // blueprint should be EOL'd.
    Set<Long> currentBlueprintSet = new HashSet<>();
    //noinspection Duplicates
    for (Blueprint next : toAdd) {
      currentBlueprintSet.add(next.getItemID());
      if (cacheUpdate.cachedBlueprints.containsKey(next.getItemID())) {
        Blueprint compare = cacheUpdate.cachedBlueprints.get(next.getItemID());
        if (!compare.equivalent(next)) {
          // Update and replace in cache
          updates.add(next);
          cacheUpdate.addBlueprint(next);
          cacheMiss();
        } else {
          // Cached value is still correct
          cacheHit();
        }
      } else {
        // A blueprint we've never seen before
        cacheMiss();
        updates.add(next);
        cacheUpdate.addBlueprint(next);
      }
    }

    // Now EOL any blueprints which are not in the latest list.
    //noinspection Duplicates
    for (Blueprint next : stored) {
      if (!currentBlueprintSet.contains(next.getItemID())) {
        next.evolve(null, time);
        updates.add(next);
        cacheUpdate.cachedBlueprints.remove(next.getItemID());
      }
    }

  }

  @Override
  protected void commitComplete() {
    // Update the blueprint cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CHAR_BLUEPRINTS, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCharBlueprints implements ModelCacheData {
    Map<Long, Blueprint> cachedBlueprints = new HashMap<>();

    void addBlueprint(Blueprint bp) {
      cachedBlueprints.put(bp.getItemID(), bp);
    }
  }
}
