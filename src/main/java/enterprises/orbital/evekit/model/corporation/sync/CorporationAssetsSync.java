package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.AssetSyncUtil;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IAsset;

public class CorporationAssetsSync extends AbstractCorporationSync {
  protected static final Logger log = Logger.getLogger(CorporationAssetsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getAssetListStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getAssetListExpiry();
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setAssetListStatus(status);
    tracker.setAssetListDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setAssetListExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  protected Object getServerData(
                                 ICorporationAPI corpRequest)
    throws IOException {
    // First retrieve nested assets
    Collection<IAsset> assetList = corpRequest.requestAssets();
    // Then retrieve flat asset list and add in any assets that were not present in the initial request
    Collection<IAsset> flat = corpRequest.requestAssets(true);
    // Build a map of all assets contained in the nested call
    Set<Long> itemSet = new HashSet<>();
    Queue<IAsset> processQueue = new ArrayDeque<>();
    processQueue.addAll(assetList);
    while (!processQueue.isEmpty()) {
      IAsset next = processQueue.remove();
      itemSet.add(next.getItemID());
      processQueue.addAll(next.getContainedAssets());
    }
    // Add any assets in the flat list that we haven't seen yet
    for (IAsset next : flat) {
      if (!itemSet.contains(next.getItemID())) assetList.add(next);
    }
    return assetList;
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IAsset> assetList = (Collection<IAsset>) data;
    AssetSyncUtil.updateAssets(time, syncAccount, assetList, updates);
    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationAssetsSync syncher = new CorporationAssetsSync();

  public static SyncStatus syncAssets(
                                      long time,
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil,
                                      ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationAssets");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationAssets", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationAssets", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
