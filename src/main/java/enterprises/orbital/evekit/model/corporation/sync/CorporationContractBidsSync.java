package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.ContractBid;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IContractBid;

public class CorporationContractBidsSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationContractBidsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getContractBidsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setContractBidsStatus(status);
    tracker.setContractBidsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) throws IOException {
    container.setContractBidsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getContractBidsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
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
  protected Object getServerData(
                                 ICorporationAPI corpRequest) throws IOException {
    // Continue requesting bids until we receive empty list.
    Collection<IContractBid> bids = new ArrayList<IContractBid>();
    Collection<IContractBid> nextSet = null;

    do {
      nextSet = corpRequest.requestContractBids();
      if (corpRequest.isError()) return null;
      bids.addAll(nextSet);
    } while (nextSet.size() > 0);

    return bids;
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IContractBid> bids = (Collection<IContractBid>) data;

    for (IContractBid next : bids) {
      ContractBid newBid = new ContractBid(
          next.getBidID(), next.getContractID(), next.getBidderID(), ModelUtil.safeConvertDate(next.getDateBid()),
          next.getAmount().setScale(2, RoundingMode.HALF_UP));
      updates.add(newBid);
    }

    return ModelUtil.safeConvertDate(corpRequest.getCachedUntil());

  }

  private static final CorporationContractBidsSync syncher = new CorporationContractBidsSync();

  public static SyncStatus syncCorporationContractBids(
                                                       long time,
                                                       SynchronizedEveAccount syncAccount,
                                                       SynchronizerUtil syncUtil,
                                                       ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationContractBids");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContractBids", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContractBids", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
