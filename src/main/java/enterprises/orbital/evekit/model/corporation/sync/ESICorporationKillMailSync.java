package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.KillmailsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Kill;
import enterprises.orbital.evekit.model.common.KillAttacker;
import enterprises.orbital.evekit.model.common.KillItem;
import enterprises.orbital.evekit.model.common.KillVictim;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationKillMailSync extends AbstractESIAccountSync<List<GetKillmailsKillmailIdKillmailHashOk>> {
  protected static final Logger log = Logger.getLogger(ESICorporationKillMailSync.class.getName());

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  public ESICorporationKillMailSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_KILL_MAIL;
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof Kill) ||
        (item instanceof KillItem) ||
        (item instanceof KillVictim) ||
        (item instanceof KillAttacker);

    // Kill entries are immutable.  Therefore, we can skip updates if the specified items already exist.
    if (item instanceof Kill) {
      if (Kill.get(account, time, ((Kill) item).getKillID()) != null) return;
    } else if (item instanceof KillItem) {
      if (KillItem.get(account, time, ((KillItem) item).getKillID(), ((KillItem) item).getSequence()) != null) return;
    } else if (item instanceof KillVictim) {
      if (KillVictim.get(account, time, ((KillVictim) item).getKillID()) != null) return;
    } else {
      if (KillAttacker.get(account, time, ((KillAttacker) item).getKillID(), ((KillAttacker) item).getAttackerCharacterID()) != null) return;
    }

    // Otherwise, create entry
    evolveOrAdd(time, null, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetKillmailsKillmailIdKillmailHashOk>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    KillmailsApi apiInstance = cp.getKillmailsApi();
    Pair<Long, List<GetCorporationsCorporationIdKillmailsRecent200Ok>> result;

    try {
      // Retrieve recent kill mails
      result = pagedResultRetriever(
          (page) -> {
            ESIThrottle.throttle(endpoint().name(), account);
            return apiInstance.getCorporationsCorporationIdKillmailsRecentWithHttpInfo(
                (int) account.getEveCorporationID(),
                null,
                null,
                page,
                accessToken());
          });
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        cacheHit();
        result = Pair.of(expiry, Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }

    List<GetCorporationsCorporationIdKillmailsRecent200Ok> results = result.getRight();
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();

    // Sort results in increasing order by killID so we insert in order
    results.sort(Comparator.comparingInt(GetCorporationsCorporationIdKillmailsRecent200Ok::getKillmailId));

    // Extract context.
    String[] cachedHash = splitCachedContext(1);

    // If a context is present, then it stores the highest killID that we have processed so far.
    // We can therefore only insert kill IDs beyond this bound.  In order to avoid excessive
    // delay, we also bound the number of inserts we'll do in one shot.  Since the kill endpoint
    // has a short endtime (5 minutes), it won't take too long to catch up.
    int maxKillID;
    try {
      maxKillID = Integer.valueOf(cachedHash[0]);
      maxKillID = Math.max(maxKillID, 0);
    } catch (Exception e) {
      // no context, use 0 and we'll set a bound after processing
      maxKillID = 0;
    }

    // We'll never retrieve more than this many records in one cycle.
    final int BATCH_SIZE = 100;


    // Retrieve detailed kill information
    List<GetKillmailsKillmailIdKillmailHashOk> data = new ArrayList<>();
    for (GetCorporationsCorporationIdKillmailsRecent200Ok next : results) {
      if (next.getKillmailId() <= maxKillID) {
        cacheHit();
        continue;
      }

      cacheMiss();
      try {
        ESIThrottle.throttle(endpoint().name(), account);
        ApiResponse<GetKillmailsKillmailIdKillmailHashOk> nextHash = apiInstance.getKillmailsKillmailIdKillmailHashWithHttpInfo(
            next.getKillmailHash(),
            next.getKillmailId(),
            null,
            null);
        checkCommonProblems(nextHash);
        data.add(nextHash.getData());
        maxKillID = Math.max(maxKillID, next.getKillmailId());
      } catch (ApiException | IOException e) {
        // Log the error, but short circuit so maxKillID remains correct
        log.log(Level.WARNING, "Error retrieving kill information, continuing to next kill", e);
        if (e instanceof ApiException)
          ESIThrottle.throttle((ApiException) e);
        break;
      }

      // Short circuit if we reach the batch limit
      if (data.size() >= BATCH_SIZE)
        break;
    }

    // Save maxKillID for next execution
    cachedHash[0] = String.valueOf(maxKillID);
    currentETag = String.join("|", cachedHash);

    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings({"Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetKillmailsKillmailIdKillmailHashOk>> data,
                                   List<CachedData> updates) throws IOException {

    for (GetKillmailsKillmailIdKillmailHashOk nextKill : data.getData()) {
      updates.add(new Kill(nextKill.getKillmailId(),
                           nextKill.getKillmailTime().getMillis(),
                           nullSafeInteger(nextKill.getMoonId(), 0),
                           nextKill.getSolarSystemId(),
                           nullSafeInteger(nextKill.getWarId(), 0)));

      for (GetKillmailsKillmailIdKillmailHashAttacker nextAttacker : nextKill.getAttackers()) {
        updates.add(new KillAttacker(nextKill.getKillmailId(),
                                     nullSafeInteger(nextAttacker.getCharacterId(), 0),
                                     nullSafeInteger(nextAttacker.getAllianceId(), 0),
                                     nullSafeInteger(nextAttacker.getCorporationId(), 0),
                                     nextAttacker.getDamageDone(),
                                     nullSafeInteger(nextAttacker.getFactionId(), 0),
                                     nextAttacker.getSecurityStatus(),
                                     nullSafeInteger(nextAttacker.getShipTypeId(), 0),
                                     nullSafeInteger(nextAttacker.getWeaponTypeId(), 0),
                                     nextAttacker.getFinalBlow()));
      }

      GetKillmailsKillmailIdKillmailHashVictim nextVictim = nextKill.getVictim();
      GetKillmailsKillmailIdKillmailHashPosition victimPosition = nextVictim.getPosition();
      if (victimPosition == null) {
        victimPosition = new GetKillmailsKillmailIdKillmailHashPosition();
        victimPosition.setX(0D);
        victimPosition.setY(0D);
        victimPosition.setZ(0D);
      }
      updates.add(new KillVictim(nextKill.getKillmailId(),
                                 nullSafeInteger(nextVictim.getAllianceId(), 0),
                                 nullSafeInteger(nextVictim.getCharacterId(), 0),
                                 nullSafeInteger(nextVictim.getCorporationId(), 0),
                                 nextVictim.getDamageTaken(),
                                 nullSafeInteger(nextVictim.getFactionId(), 0),
                                 nextVictim.getShipTypeId(),
                                 victimPosition.getX(),
                                 victimPosition.getY(),
                                 victimPosition.getZ()
                                 ));

      int sequence = 0;
      for (GetKillmailsKillmailIdKillmailHashItem nextTopItem : nextVictim.getItems()) {
        KillItem newTopItem = new KillItem(nextKill.getKillmailId(),
                                           nextTopItem.getItemTypeId(),
                                           nextTopItem.getFlag(),
                                           nullSafeLong(nextTopItem.getQuantityDestroyed(), 0L),
                                           nullSafeLong(nextTopItem.getQuantityDropped(), 0L),
                                           nextTopItem.getSingleton(),
                                           sequence++,
                                           KillItem.TOP_LEVEL);
        updates.add(newTopItem);
        for (GetKillmailsKillmailIdKillmailHashItemsItem nextChildItem : nextTopItem.getItems()) {
          updates.add(new KillItem(nextKill.getKillmailId(),
                                             nextChildItem.getItemTypeId(),
                                             nextChildItem.getFlag(),
                                             nullSafeLong(nextChildItem.getQuantityDestroyed(), 0L),
                                             nullSafeLong(nextChildItem.getQuantityDropped(), 0L),
                                             nextChildItem.getSingleton(),
                                             sequence++,
                                             newTopItem.getSequence()));

        }
      }
    }
  }

}
