package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.AssetSyncUtil;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IAsset;

public class CharacterAssetsSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterAssetsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getAssetListStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getAssetListExpiry();
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setAssetListStatus(status);
    tracker.setAssetListDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setAssetListExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest)
    throws IOException {
    // First retrieve nested assets
    Collection<IAsset> assetList = charRequest.requestAssets();
    if (charRequest.isError()) return Collections.emptyList();
    // Then retrieve flat asset list and add in any assets that were not present in the initial request
    Collection<IAsset> flat = charRequest.requestAssets(true);
    if (charRequest.isError()) return Collections.emptyList();
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
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IAsset> assetList = (Collection<IAsset>) data;
    AssetSyncUtil.updateAssets(time, syncAccount, assetList, updates);
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterAssetsSync syncher = new CharacterAssetsSync();

  public static SyncStatus syncAssets(
                                      long time,
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil,
                                      ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterAssets");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAssets", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAssets", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
