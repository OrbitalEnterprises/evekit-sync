package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.ContractBid;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IContractBid;

public class CharacterContractBidsSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterContractBidsSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getContractBidsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setContractBidsStatus(status);
    tracker.setContractBidsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setContractBidsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getContractBidsExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
    assert item instanceof ContractBid;

    ContractBid api = (ContractBid) item;
    ContractBid existing = ContractBid.get(accountKey, time, api.getContractID(), api.getBidID());

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

    return true;
  }

  @Override
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    // Continue requesting bids until we receive empty list.
    Collection<IContractBid> bids = new ArrayList<IContractBid>();
    Collection<IContractBid> nextSet = null;

    do {
      nextSet = charRequest.requestContractBids();
      if (charRequest.isError()) return null;
      bids.addAll(nextSet);
    } while (nextSet.size() > 0);

    return bids;
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IContractBid> bids = (Collection<IContractBid>) data;

    for (IContractBid next : bids) {
      ContractBid newBid = new ContractBid(
          next.getBidID(), next.getContractID(), next.getBidderID(), next.getDateBid().getTime(), next.getAmount().setScale(2, RoundingMode.HALF_UP));
      updates.add(newBid);
    }

    return charRequest.getCachedUntil().getTime();

  }

  private static final CharacterContractBidsSync syncher = new CharacterContractBidsSync();

  public static SyncStatus syncCharacterContractBids(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterContractBids");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContractBids", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContractBids", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
