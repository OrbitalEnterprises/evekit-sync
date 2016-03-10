package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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
import enterprises.orbital.evekit.model.common.Kill;
import enterprises.orbital.evekit.model.common.KillAttacker;
import enterprises.orbital.evekit.model.common.KillItem;
import enterprises.orbital.evekit.model.common.KillVictim;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IKill;
import enterprises.orbital.evexmlapi.shared.IKillAttacker;
import enterprises.orbital.evexmlapi.shared.IKillItem;
import enterprises.orbital.evexmlapi.shared.IKillVictim;

public class CharacterKillLogSync extends AbstractCharacterSync {
  protected static final Logger log = Logger.getLogger(CharacterKillLogSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getKilllogStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setKilllogStatus(status);
    tracker.setKilllogDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setKilllogExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getKilllogExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    // Handle the four types of kill info
    if (item instanceof Kill) {
      Kill api = (Kill) item;
      Kill existing = Kill.get(accountKey, time, api.getKillID());

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
    } else if (item instanceof KillAttacker) {
      KillAttacker api = (KillAttacker) item;
      KillAttacker existing = KillAttacker.get(accountKey, time, api.getKillID(), api.getAttackerCharacterID());

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
    } else if (item instanceof KillItem) {
      KillItem api = (KillItem) item;
      KillItem existing = KillItem.get(accountKey, time, api.getKillID(), api.getSequence());

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
    } else if (item instanceof KillVictim) {
      KillVictim api = (KillVictim) item;
      KillVictim existing = KillVictim.get(accountKey, time, api.getKillID());

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
    } else {
      // Should never happen!
      assert false;
    }

    return true;
  }

  // Can't use generic sync for the kill log
  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    throw new UnsupportedOperationException();
  }

  private static final CharacterKillLogSync syncher = new CharacterKillLogSync();

  private static class KillItemPair {
    private final KillItem  container;
    private final IKillItem entry;

    public KillItemPair(KillItem container, IKillItem entry) {
      super();
      this.container = container;
      this.entry = entry;
    }
  }

  public static SyncStatus syncCharacterKillLog(
                                                long time,
                                                SynchronizedEveAccount syncAccount,
                                                SynchronizerUtil syncUtil,
                                                ICharacterAPI charRequest) {
    try {
      // Run pre-check.
      String description = "CharacterKillLog";
      SyncStatus preCheck = syncUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, description, syncher);
      if (preCheck != SyncStatus.CONTINUE) return preCheck;

      // Start server sync
      log.fine("Starting refresh request for " + description + "  for account " + syncAccount);

      SyncTracker.SyncState status = SyncTracker.SyncState.UPDATED;
      String errorDetail = null;
      long nextExpiry = -1;
      List<CachedData> updateList = new ArrayList<CachedData>();

      try {
        // On each synchronization, load as many kill records as the
        // server will provide. Server will terminate us with error code
        // 119.
        List<IKill> toPopulate = new ArrayList<IKill>();
        long minKillID = Long.MAX_VALUE;
        Collection<IKill> kills = charRequest.requestKillMails();

        if (charRequest.isError() && charRequest.getErrorCode() != 119) {
          // erroneous loop termination
          StringBuilder errStr = new StringBuilder();
          status = handleServerError(charRequest, errStr);
          errorDetail = errStr.toString();
          if (status == SyncTracker.SyncState.SYNC_ERROR) log.warning("request failed: " + errorDetail);
        }

        while (status == SyncTracker.SyncState.UPDATED && kills != null && kills.size() > 0) {
          // Copy over next kill set, keep track of min kill ID
          for (IKill next : kills) {
            toPopulate.add(next);
            minKillID = Math.min(next.getKillID(), minKillID);
          }

          kills = charRequest.requestKillMails(minKillID);

          if (charRequest.isError() && charRequest.getErrorCode() != 119) {
            // erroneous loop termination
            errorDetail = "Error " + charRequest.getErrorCode() + ": " + charRequest.getErrorString();
            status = SyncTracker.SyncState.SYNC_ERROR;
            log.warning("request failed: " + errorDetail);
          }
        }

        if (status == SyncTracker.SyncState.UPDATED) {
          // Prepare kills for population
          for (IKill nextKill : toPopulate) {
            long killID = nextKill.getKillID();

            Kill makeKill = new Kill(killID, ModelUtil.safeConvertDate(nextKill.getKillTime()), nextKill.getMoonID(), nextKill.getSolarSystemID());
            updateList.add(makeKill);

            IKillVictim nextVictim = nextKill.getVictim();
            KillVictim makeVictim = new KillVictim(
                killID, nextVictim.getAllianceID(), nextVictim.getAllianceName(), nextVictim.getCharacterID(), nextVictim.getCharacterName(),
                nextVictim.getCorporationID(), nextVictim.getCorporationName(), nextVictim.getDamageTaken(), nextVictim.getFactionID(),
                nextVictim.getFactionName(), nextVictim.getShipTypeID());
            updateList.add(makeVictim);

            for (IKillAttacker attacker : nextKill.getAttackers()) {
              KillAttacker makeAttacker = new KillAttacker(
                  killID, attacker.getCharacterID(), attacker.getAllianceID(), attacker.getAllianceName(), attacker.getCharacterName(),
                  attacker.getCorporationID(), attacker.getCorporationName(), attacker.getDamageDone(), attacker.getFactionID(), attacker.getFactionName(),
                  attacker.getSecurityStatus(), attacker.getShipTypeID(), attacker.getWeaponTypeID(), attacker.isFinalBlow());
              updateList.add(makeAttacker);
            }

            // Kill item assembly is a bit involved due to nesting. We reproduce the kill item tree here with appropriate container relationships. Sequence ids
            // are generated based on a breadth first traversal of the kill item tree.
            int sequence = KillItem.TOP_LEVEL + 1;
            Queue<KillItemPair> itemQueue = new LinkedList<KillItemPair>();
            for (IKillItem item : nextKill.getItems()) {
              itemQueue.add(new KillItemPair(null, item));
            }
            while (!itemQueue.isEmpty()) {
              KillItemPair nextPair = itemQueue.remove();
              KillItem makeItem = new KillItem(
                  killID, nextPair.entry.getTypeID(), nextPair.entry.getFlag(), nextPair.entry.getQtyDestroyed(), nextPair.entry.getQtyDropped(),
                  nextPair.entry.isSingleton(), sequence++, nextPair.container == null ? KillItem.TOP_LEVEL : nextPair.container.getContainerSequence());
              updateList.add(makeItem);
              for (IKillItem subitem : nextPair.entry.getContainedItems()) {
                itemQueue.add(new KillItemPair(makeItem, subitem));
              }
            }

          }

          nextExpiry = charRequest.getCachedUntil().getTime();
        }

      } catch (IOException e) {
        status = SyncTracker.SyncState.SYNC_ERROR;
        errorDetail = "request failed with IO error";
        log.warning("request failed with error " + e);
      }

      log.fine("Completed refresh request for " + description + " for account " + syncAccount);

      syncUtil.storeSynchResults(time, CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, status, errorDetail, nextExpiry, description, updateList,
                                 syncher);
      return SyncStatus.DONE;

    } catch (IOException e) {
      // Log but give us another shot in the queue
      log.warning("store failed with error " + e);
      return SyncStatus.ERROR;
    }
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterKillLog", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterKillLog", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
