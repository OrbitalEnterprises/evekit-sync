package enterprises.orbital.evekit.model;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evexmlapi.shared.IAsset;

public class AssetSyncUtil {

  private static class AssetFillPair {
    private final IAsset source;
    private final long   holderID;

    public AssetFillPair(IAsset s, long c) {
      source = s;
      holderID = c;
    }
  }

  public static void updateAssets(long time, SynchronizedEveAccount parent, Collection<IAsset> assets, List<CachedData> updateList) throws IOException {

    // 1. Retrieve keys of all live assets
    // 2. For each asset in API response:
    // a. if asset is live:
    // i. if unchanged, then skip
    // ii. if changed:
    // 1. Make a copy of the live object
    // 2. End of life the current object
    // 3. Set lifeStart to time and lifeEnd to Long.MAX for the copy
    // 4. Copy the changes to the new asset
    // 5. add to update set
    // iii. remove key for this asset since we've seen it
    // b. else, create new object
    // 3. For all live asset keys remaining:
    // a. End of life associated asset
    //
    // Step 1 - collect keys of all asset live at "time"
    Set<Long> allAssets = new HashSet<Long>();
    long contid = -1;
    List<Asset> nextBatch = Asset.getAllAssets(parent, time, 1000, contid);
    while (!nextBatch.isEmpty()) {
      for (Asset next : nextBatch) {
        allAssets.add(next.getItemID());
        contid = Math.max(contid, next.getItemID());
      }
      nextBatch = Asset.getAllAssets(parent, time, 1000, contid);
    }
    // Step 2 - process returned assets from EVE API
    Queue<AssetFillPair> containerQueue = new ArrayDeque<AssetFillPair>();
    for (IAsset topAsset : assets) {
      containerQueue.add(new AssetFillPair(topAsset, Asset.TOP_LEVEL));
    }
    // Now fill in contained assets
    while (!containerQueue.isEmpty()) {
      AssetFillPair next = containerQueue.remove();
      long item = next.source.getItemID();
      Asset api = new Asset(
          item, next.source.getLocationID(), next.source.getTypeID(), next.source.getQuantity(), next.source.getFlag(), next.source.isSingleton(),
          next.source.getRawQuantity(), next.holderID);
      // Asset live?
      if (allAssets.contains(item)) {
        // Retrieve and check whether this asset has changed
        Asset existing = Asset.get(parent, time, item);
        if (!existing.equivalent(api)) {
          // Evolve
          existing.evolve(api, time);
          updateList.add(existing);
          updateList.add(api);
        }
        // Remove this asset since we've seen it
        allAssets.remove(item);
      } else {
        // New entity
        api.setup(parent, time);
        updateList.add(api);
      }
      // Queue up items contained by the asset we just added
      for (IAsset toQueue : next.source.getContainedAssets()) {
        containerQueue.add(new AssetFillPair(toQueue, next.source.getItemID()));
      }
    }
    // Step 3 - anything left in allAssets should be end of life'd.
    for (Long eol : allAssets) {
      Asset toEOL = Asset.get(parent, time, eol);
      toEOL.evolve(null, time);
      updateList.add(toEOL);
    }

  }
}
