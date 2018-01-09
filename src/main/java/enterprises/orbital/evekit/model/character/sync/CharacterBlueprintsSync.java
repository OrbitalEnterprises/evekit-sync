package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.Blueprint;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IBlueprint;

public class CharacterBlueprintsSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterBlueprintsSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getBlueprintsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setBlueprintsStatus(status);
    tracker.setBlueprintsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) throws IOException {
    container.setBlueprintsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getBlueprintsExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) throws IOException {
    assert item instanceof Blueprint;

    Blueprint api = (Blueprint) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Blueprint to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Blueprint existing = Blueprint.get(accountKey, time, api.getItemID());
      if (existing != null) {
        // Existing, evolve if changed
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

    return true;
  }

  @Override
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestBlueprints();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IBlueprint> blueprints = (Collection<IBlueprint>) data;
    // All all retrieved blueprints to update set and prepare membership to check for blueprints no longer present
    Set<Long> blueprintSet = new HashSet<Long>();
    for (IBlueprint next : blueprints) {
      Blueprint newBlueprint = new Blueprint(
          next.getItemID(), next.getLocationID(), next.getTypeID(), next.getTypeName(), next.getFlagID(), next.getQuantity(), next.getTimeEfficiency(),
          next.getMaterialEfficiency(), next.getRuns());
      updates.add(newBlueprint);
      blueprintSet.add(newBlueprint.getItemID());
    }
    // From the set of all blueprints, find blueprints no longer in the list.
    // These should all be EOL
    long contid = -1;
    List<Blueprint> nextBatch = Blueprint.getAllBlueprints(syncAccount, time, 1000, contid);
    while (!nextBatch.isEmpty()) {
      for (Blueprint next : nextBatch) {
        long itemID = next.getItemID();
        if (!blueprintSet.contains(itemID)) {
          // Blueprint no longer present, retire
          next.evolve(null, time);
          updates.add(next);
        }
        contid = Math.max(contid, itemID);
      }
      nextBatch = Blueprint.getAllBlueprints(syncAccount, time, 1000, contid);
    }
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterBlueprintsSync syncher = new CharacterBlueprintsSync();

  public static SyncStatus syncCharacterBlueprints(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterBlueprints");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterBlueprints", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterBlueprints", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
