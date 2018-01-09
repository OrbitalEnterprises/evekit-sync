package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.RoundingMode;
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
import enterprises.orbital.evekit.model.common.Contract;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IContract;

public class CorporationContractsSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationContractsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getContractsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setContractsStatus(status);
    tracker.setContractsDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) throws IOException {
    container.setContractsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getContractsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof Contract;

    Contract api = (Contract) item;
    Contract existing = Contract.get(accountKey, time, api.getContractID());

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
    return corpRequest.requestContracts();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IContract> contracts = (Collection<IContract>) data;

    for (IContract next : contracts) {
      Contract newContract = new Contract(
          next.getContractID(), next.getIssuerID(), next.getIssuerCorpID(), next.getAssigneeID(), next.getAcceptorID(), next.getStartStationID(),
          next.getEndStationID(), next.getType(), next.getStatus(), next.getTitle(), next.isForCorp(), next.getAvailability(),
          ModelUtil.safeConvertDate(next.getDateIssued()), ModelUtil.safeConvertDate(next.getDateExpired()), ModelUtil.safeConvertDate(next.getDateAccepted()),
          next.getNumDays(), ModelUtil.safeConvertDate(next.getDateCompleted()), next.getPrice().setScale(2, RoundingMode.HALF_UP),
          next.getReward().setScale(2, RoundingMode.HALF_UP), next.getCollateral().setScale(2, RoundingMode.HALF_UP),
          next.getBuyout().setScale(2, RoundingMode.HALF_UP), next.getVolume());
      updates.add(newContract);
    }

    return corpRequest.getCachedUntil().getTime();

  }

  private static final CorporationContractsSync syncher = new CorporationContractsSync();

  public static SyncStatus syncCorporationContracts(
                                                    long time,
                                                    SynchronizedEveAccount syncAccount,
                                                    SynchronizerUtil syncUtil,
                                                    ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationContracts");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContracts", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContracts", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
