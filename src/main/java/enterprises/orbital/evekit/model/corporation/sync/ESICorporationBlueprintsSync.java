package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdBlueprints200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Blueprint;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationBlueprintsSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdBlueprints200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationBlueprintsSync.class.getName());

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

  public ESICorporationBlueprintsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_BLUEPRINTS;
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

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdBlueprints200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    try {
      CorporationApi apiInstance = cp.getCorporationApi();
      Pair<Long, List<GetCorporationsCorporationIdBlueprints200Ok>> result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdBlueprintsWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
      return new ESIAccountServerResult<>(
          result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay(),
          result.getRight());
    } catch (ApiException e) {
      final String errTrap = "Character does not have required role";
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        return new ESIAccountServerResult<>(expiry, Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + e.getResponseBody());
        }
        throw e;
      }
    }
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdBlueprints200Ok>> data,
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
    for (GetCorporationsCorporationIdBlueprints200Ok next : data.getData()) {
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
