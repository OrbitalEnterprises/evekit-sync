package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
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
import enterprises.orbital.evekit.model.character.CharacterMedal;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ICharacterMedal;

public class CharacterMedalSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterMedalSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getMedalsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setMedalsStatus(status);
    tracker.setMedalsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setMedalsExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getMedalsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof CharacterMedal;

    CharacterMedal api = (CharacterMedal) item;
    CharacterMedal existing = CharacterMedal.get(accountKey, time, api.getMedalID(), api.getIssued());

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
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestMedals();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<ICharacterMedal> medals = (Collection<ICharacterMedal>) data;

    for (ICharacterMedal next : medals) {
      CharacterMedal newMedal = new CharacterMedal(
          next.getDescription(), next.getMedalID(), next.getTitle(), next.getCorporationID(), ModelUtil.safeConvertDate(next.getIssued()), next.getIssuerID(),
          next.getReason(), next.getStatus());
      updates.add(newMedal);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterMedalSync syncher = new CharacterMedalSync();

  public static SyncStatus syncCharacterMedals(
                                               long time,
                                               SynchronizedEveAccount syncAccount,
                                               SynchronizerUtil syncUtil,
                                               ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterMedals");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterMedals", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterMedals", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
