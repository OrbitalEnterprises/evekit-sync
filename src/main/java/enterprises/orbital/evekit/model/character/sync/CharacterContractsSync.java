package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.math.RoundingMode;
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
import enterprises.orbital.evekit.model.common.Contract;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IContract;

public class CharacterContractsSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterContractsSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getContractsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setContractsStatus(status);
    tracker.setContractsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) {
    container.setContractsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getContractsExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) {
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
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestContracts();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IContract> contracts = (Collection<IContract>) data;

    for (IContract next : contracts) {
      Contract newContract = new Contract(
          next.getContractID(), next.getIssuerID(), next.getIssuerCorpID(), next.getAssigneeID(), next.getAcceptorID(), next.getStartStationID(),
          next.getEndStationID(), next.getType(), next.getStatus(), next.getTitle(), next.isForCorp(), next.getAvailability(), next.getDateIssued().getTime(),
          next.getDateExpired().getTime(), next.getDateAccepted().getTime(), next.getNumDays(), next.getDateCompleted().getTime(),
          next.getPrice().setScale(2, RoundingMode.HALF_UP), next.getReward().setScale(2, RoundingMode.HALF_UP),
          next.getCollateral().setScale(2, RoundingMode.HALF_UP), next.getBuyout().setScale(2, RoundingMode.HALF_UP), next.getVolume());
      updates.add(newContract);
    }

    return charRequest.getCachedUntil().getTime();

  }

  private static final CharacterContractsSync syncher = new CharacterContractsSync();

  public static SyncStatus syncCharacterContracts(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterContracts");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContracts", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContracts", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
