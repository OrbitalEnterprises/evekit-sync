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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterBlueprintsSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdBlueprints200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterBlueprintsSync.class.getName());

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

  @SuppressWarnings("Duplicates")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdBlueprints200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    // Assemble retrieved data and prepare hashes for comparison.
    List<Blueprint> retrievedBlueprints = new ArrayList<>();
    for (GetCharactersCharacterIdBlueprints200Ok next : data.getData()) {
      retrievedBlueprints.add(new Blueprint(next.getItemId(), next.getLocationId(), next.getLocationFlag()
                                                                                        .toString(),
                                            next.getTypeId(),
                                            next.getQuantity(), next.getTimeEfficiency(),
                                            next.getMaterialEfficiency(), next.getRuns()));
    }
    retrievedBlueprints.sort(Comparator.comparingLong(Blueprint::getItemID));
    String hashResult = CachedData.dataHashHelper(retrievedBlueprints.stream()
                                                                     .map(Blueprint::dataHash)
                                                                     .toArray());

    if (hashResult.equals(currentETag)) {
      // List hasn't changed, no need to update
      cacheHit();
      return;
    }

    // Otherwise, something changed so process.
    cacheMiss();
    currentETag = hashResult;

    // Process the latest blueprint list.
    Set<Long> currentBlueprintSet = retrievedBlueprints.stream()
                                                       .map(Blueprint::getItemID)
                                                       .collect(Collectors.toSet());
    updates.addAll(retrievedBlueprints);

    // Now EOL any blueprints which are not in the latest list.
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
      if (!currentBlueprintSet.contains(next.getItemID())) {
        next.evolve(null, time);
        updates.add(next);
      }
    }
  }

}
