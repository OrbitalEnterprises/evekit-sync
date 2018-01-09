package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.Contract;
import enterprises.orbital.evekit.model.common.ContractItem;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IContractItem;

public class CharacterContractItemsSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterContractItemsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getContractItemsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setContractItemsStatus(status);
    tracker.setContractItemsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    // Always use expiry from Contracts for items expiry
    container.setContractItemsExpiry(container.getContractsExpiry());
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getContractItemsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    assert item instanceof ContractItem;

    ContractItem api = (ContractItem) item;
    ContractItem existing = ContractItem.get(accountKey, time, api.getContractID(), api.getRecordID());

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
  public boolean prereqSatisfied(
                                 CapsuleerSyncTracker tracker) {
    // We require that contracts have been retrieved first since we need contract IDs
    return tracker.getContractsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    // Must reset here since we may never call an API method and thus will inherit any existing error code or message.
    charRequest.reset();
    List<ContractItemPair> results = new ArrayList<ContractItemPair>();
    long contid = 0L;
    List<Contract> contractList = Contract.getAllItemRetrievableContracts(currentSyncAccount, syncTime, 1000, contid, syncTime);
    while (!contractList.isEmpty()) {
      for (Contract next : contractList) {
        // Contract still within the retrievable range so attempt to retrieve.
        Collection<IContractItem> items = charRequest.requestContractItems(next.getContractID());
        if (charRequest.isError()) return null;
        for (IContractItem nextItem : items) {
          results.add(new ContractItemPair(next.getContractID(), nextItem));
        }
        contid = Math.max(contid, next.getContractID());
      }
      contractList = Contract.getAllItemRetrievableContracts(currentSyncAccount, syncTime, 1000, contid, syncTime);
    }

    return results;
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<ContractItemPair> items = (Collection<ContractItemPair>) data;

    for (ContractItemPair next : items) {
      ContractItem newItem = new ContractItem(
          next.contractID, next.item.getRecordID(), next.item.getTypeID(), next.item.getQuantity(), next.item.getRawQuantity(), next.item.isSingleton(),
          next.item.isIncluded());
      updates.add(newItem);
    }

    return ModelUtil.safeConvertDate(charRequest.getCachedUntil());

  }

  protected SynchronizedEveAccount currentSyncAccount;
  protected long                   syncTime;

  public static SyncStatus syncCharacterContractItems(
                                                      long time,
                                                      SynchronizedEveAccount syncAccount,
                                                      SynchronizerUtil syncUtil,
                                                      ICharacterAPI charRequest) {
    // We allocate a new syncher since we use local state to store contract IDs we're tracking.
    CharacterContractItemsSync syncher = new CharacterContractItemsSync();
    syncher.currentSyncAccount = syncAccount;
    syncher.syncTime = time;
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterContractItems");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    CharacterContractItemsSync syncher = new CharacterContractItemsSync();
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContractItems", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    CharacterContractItemsSync syncher = new CharacterContractItemsSync();
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContractItems", SyncTracker.SyncState.NOT_ALLOWED);
  }

  private class ContractItemPair {
    long          contractID;
    IContractItem item;

    public ContractItemPair(long contractID, IContractItem item) {
      super();
      this.contractID = contractID;
      this.item = item;
    }
  }
}
