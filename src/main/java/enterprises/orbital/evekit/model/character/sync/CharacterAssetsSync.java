package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
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
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getAssetListStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getAssetListExpiry();
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setAssetListStatus(status);
    tracker.setAssetListDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setAssetListExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestAssets();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IAsset> assetList = (Collection<IAsset>) data;
    AssetSyncUtil.updateAssets(time, syncAccount, assetList, updates);
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterAssetsSync syncher = new CharacterAssetsSync();

  public static SyncStatus syncAssets(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterAssets");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAssets", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterAssets", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
