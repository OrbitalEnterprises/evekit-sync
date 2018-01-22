package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.eve.esi.client.api.AssetsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdAssets200Ok;
import enterprises.orbital.eve.esi.client.model.PostCharactersCharacterIdAssetsLocations200Ok;
import enterprises.orbital.eve.esi.client.model.PostCharactersCharacterIdAssetsNames200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evekit.model.common.Location;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterAssetsSync extends AbstractESIAccountSync<ESICharacterAssetsSync.AssetData> {
  protected static final Logger log = Logger.getLogger(ESICharacterAssetsSync.class.getName());
  private static final String PROP_LOCATION_BATCH_SIZE = "enterprises.orbital.evekit.sync.location_batch_size";
  private static final int DEF_LOCATION_BATCH_SIZE = 500;

  class AssetData {
    List<GetCharactersCharacterIdAssets200Ok> assets;
    List<PostCharactersCharacterIdAssetsLocations200Ok> assetLocations;
    List<PostCharactersCharacterIdAssetsNames200Ok> assetNames;
  }

  public ESICharacterAssetsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_ASSETS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof Asset) || (item instanceof Location);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof Asset)
        existing = Asset.get(account, time, ((Asset) item).getItemID());
      else
        existing = Location.get(account, time, ((Location) item).getItemID());
    }
    evolveOrAdd(time, existing, item);
  }

  private void retrieveLocationBatch(AssetsApi apiInstance, List<Long> itemBatch,
                                     List<PostCharactersCharacterIdAssetsLocations200Ok> assetLocations,
                                     List<PostCharactersCharacterIdAssetsNames200Ok> assetNames) throws ApiException, IOException {
    ApiResponse<List<PostCharactersCharacterIdAssetsLocations200Ok>> nextLocationBatch = apiInstance.postCharactersCharacterIdAssetsLocationsWithHttpInfo(
      (int) account.getEveCharacterID(), itemBatch, null, accessToken(), null, null);
    checkCommonProblems(nextLocationBatch);
    assetLocations.addAll(nextLocationBatch.getData());
    ApiResponse<List<PostCharactersCharacterIdAssetsNames200Ok>> nextNameBatch = apiInstance.postCharactersCharacterIdAssetsNamesWithHttpInfo(
      (int) account.getEveCharacterID(), itemBatch, null, accessToken(), null, null);
    checkCommonProblems(nextNameBatch);
    assetNames.addAll(nextNameBatch.getData());
  }

  @Override
  protected ESIAccountServerResult<ESICharacterAssetsSync.AssetData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    AssetData resultData = new AssetData();
    AssetsApi apiInstance = cp.getAssetsApi();
    Pair<Long, List<GetCharactersCharacterIdAssets200Ok>> result = pagedResultRetriever((page) ->
                                                                                            apiInstance.getCharactersCharacterIdAssetsWithHttpInfo(
                                                                                                (int) account.getEveCharacterID(),
                                                                                                null,
                                                                                                page,
                                                                                                accessToken(),
                                                                                                null,
                                                                                                null));
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    resultData.assets = result.getRight();
    int BATCH_SIZE = PersistentProperty.getIntegerPropertyWithFallback(PROP_LOCATION_BATCH_SIZE,
                                                                       DEF_LOCATION_BATCH_SIZE);
    resultData.assetLocations = new ArrayList<>();
    resultData.assetNames = new ArrayList<>();
    for (int i = 0; i < resultData.assets.size(); ) {
      List<Long> itemBatch =
          resultData.assets.subList(i, Math.min(i + BATCH_SIZE, resultData.assets.size()))
                           .stream()
                           .map(GetCharactersCharacterIdAssets200Ok::getItemId)
                           .collect(Collectors.toList());
      try {
        retrieveLocationBatch(apiInstance, itemBatch, resultData.assetLocations, resultData.assetNames);
      } catch (ApiException e) {
        if (e.getCode() == HttpStatus.SC_NOT_FOUND) {
          // One of the items in this batch could not be found.  Iterate through the items one at a time and skip
          // offending items.  If we were smart we'd keep track of these problematic items for future calls.
          // We'll leave that for future work.
          for (long nextItem : itemBatch) {
            try {
              retrieveLocationBatch(apiInstance, Collections.singletonList(nextItem), resultData.assetLocations, resultData.assetNames);
            } catch (ApiException f) {
              if (f.getCode() == HttpStatus.SC_NOT_FOUND) {
                log.fine(getContext() + " Location or name for asset not found, skipping: " + nextItem);
              } else
                // Unexpected error looking up single item, throw
                throw f;
            }
          }
        } else
          // Anything else we rethrow
          throw e;
      }
      i += BATCH_SIZE;
    }
    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<ESICharacterAssetsSync.AssetData> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record seen assets
    Set<Long> seenAssets = new HashSet<>();
    for (GetCharactersCharacterIdAssets200Ok next : data.getData().assets) {
      Asset nextAsset = new Asset(next.getItemId(), next.getLocationId(), next.getLocationType()
                                                                              .toString(), next.getLocationFlag()
                                                                                               .toString(),
                                  next.getTypeId(), next.getQuantity(), next.getIsSingleton(), null);
      seenAssets.add(nextAsset.getItemID());
      updates.add(nextAsset);
    }

    // Add and record locations
    Map<Long, PostCharactersCharacterIdAssetsNames200Ok> nameMap =
        data.getData().assetNames.stream()
                                 .collect(Collectors.toMap(PostCharactersCharacterIdAssetsNames200Ok::getItemId,
                                                           Function.identity()));
    Map<Long, PostCharactersCharacterIdAssetsLocations200Ok> locationMap =
        data.getData().assetLocations.stream()
                                     .collect(Collectors.toMap(PostCharactersCharacterIdAssetsLocations200Ok::getItemId,
                                                               Function.identity()));
    for (long itemID : seenAssets) {
      PostCharactersCharacterIdAssetsNames200Ok name = nameMap.get(itemID);
      PostCharactersCharacterIdAssetsLocations200Ok location = locationMap.get(itemID);
      if (name != null && location != null) {
        Location nextLocation = new Location(itemID, name.getName(), location.getPosition()
                                                                             .getX(), location.getPosition()
                                                                                              .getY(),
                                             location.getPosition()
                                                     .getZ());
        updates.add(nextLocation);
      }
    }

    // Check for assets that no longer exist and schedule for EOL
    for (Asset existing : retrieveAll(time,
                                      (long contid, AttributeSelector at) -> Asset.accessQuery(account, contid, 1000,
                                                                                               false, at, ANY_SELECTOR,
                                                                                               ANY_SELECTOR,
                                                                                               ANY_SELECTOR,
                                                                                               ANY_SELECTOR,
                                                                                               ANY_SELECTOR,
                                                                                               ANY_SELECTOR,
                                                                                               ANY_SELECTOR,
                                                                                               ANY_SELECTOR))) {
      if (!seenAssets.contains(existing.getItemID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    // Check for locations that no longer exist and schedule for EOL
    for (Location existing : retrieveAll(time,
                                         (long contid, AttributeSelector at) -> Location.accessQuery(account, contid,
                                                                                                     1000,
                                                                                                     false, at,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR))) {
      if (!seenAssets.contains(existing.getItemID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
