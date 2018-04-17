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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationKillMailSync extends AbstractESIAccountSync<List<GetKillmailsKillmailIdKillmailHashOk>> {
  protected static final Logger log = Logger.getLogger(ESICorporationKillMailSync.class.getName());
  private String context;

  public ESICorporationKillMailSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_KILL_MAIL;
  }

  @Override
  protected String getNextSyncContext() {
    return context;
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
    List<GetCorporationsCorporationIdKillmailsRecent200Ok> results = new ArrayList<>();
    int killIdLimit = Integer.MAX_VALUE;

    // Retrieve initial batch
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCorporationsCorporationIdKillmailsRecent200Ok>> result = apiInstance.getCorporationsCorporationIdKillmailsRecentWithHttpInfo(
        (int) account.getEveCorporationID(),
        null,
        killIdLimit,
        accessToken(),
        null,
        null);
    checkCommonProblems(result);
    long expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

    // Crawl kill mails backwards until no more entries are retrieved
    while (!result.getData()
                  .isEmpty()) {
      results.addAll(result.getData());
      //noinspection ConstantConditions
      killIdLimit = result.getData()
                         .stream()
                         .min(Comparator.comparingInt(GetCorporationsCorporationIdKillmailsRecent200Ok::getKillmailId))
                         .get()
                         .getKillmailId();
      ESIThrottle.throttle(endpoint().name(), account);
      result = apiInstance.getCorporationsCorporationIdKillmailsRecentWithHttpInfo((int) account.getEveCorporationID(),
                                                                               null,
                                                                                killIdLimit,
                                                                                accessToken(),
                                                                               null,
                                                                               null);
      checkCommonProblems(result);
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    // Sort results in increasing order by killID so we insert in order
    results.sort(Comparator.comparingInt(GetCorporationsCorporationIdKillmailsRecent200Ok::getKillmailId));

    // If a context is present, use it to filter out which kill mail we'll process.
    // We do this to batch the processing of kill mail which can be very large.
    int mailFilter;
    try {
      mailFilter = Integer.valueOf(getCurrentTracker().getContext());
      mailFilter = Math.max(mailFilter, 0);
    } catch (Exception e) {
      // No filter exists, assign a random filter
      mailFilter = (int) ((OrbitalProperties.getCurrentTime() / 1000) % 10);
    }

    // Filter headers by killmail ID to create a smaller processing batch.
    // Eventually, all headers will be processed as the filter cycles.
    final int mailBatch = mailFilter;
    results = results.stream()
                     .filter(x -> (x.getKillmailId() % 10) == mailBatch)
                     .collect(Collectors.toList());

    // Prepare filter and context for next tracker
    mailFilter = (mailFilter + 1) % 10;
    context = String.valueOf(mailFilter);

    // Retrieve detailed kill information
    List<GetKillmailsKillmailIdKillmailHashOk> data = new ArrayList<>();
    for (GetCorporationsCorporationIdKillmailsRecent200Ok next : results) {
      try {
        // Skip if we already have data for this kill.  Since kills are immutable, the
        // query time can be MAX_VALUE (kills never eol)
        if (Kill.get(account, Long.MAX_VALUE, next.getKillmailId()) != null) continue;
      } catch (IOException e) {
        // Log the error, but go ahead and retrieve the hash if possible
        log.log(Level.WARNING, "Error checking for existing kill, continuing", e);
      }

      try {
        ESIThrottle.throttle(endpoint().name(), account);
        ApiResponse<GetKillmailsKillmailIdKillmailHashOk> nextHash = apiInstance.getKillmailsKillmailIdKillmailHashWithHttpInfo(
            next.getKillmailHash(),
            next.getKillmailId(),
            null,
            null,
            null);
        checkCommonProblems(nextHash);
        data.add(nextHash.getData());
      } catch (ApiException | IOException e) {
        // Log the error, continue to next kill hash
        log.log(Level.WARNING, "Error retrieving kill information, continuing to next kill", e);
        if (e instanceof ApiException)
          ESIThrottle.throttle((ApiException) e);
      }
    }

    // Speed up next query time since we batch the downloads
    expiry -= TimeUnit.MILLISECONDS.convert(150, TimeUnit.SECONDS);
    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
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
      for (GetKillmailsKillmailIdKillmailHashItem1 nextTopItem : nextVictim.getItems()) {
        KillItem newTopItem = new KillItem(nextKill.getKillmailId(),
                                           nextTopItem.getItemTypeId(),
                                           nextTopItem.getFlag(),
                                           nullSafeLong(nextTopItem.getQuantityDestroyed(), 0L),
                                           nullSafeLong(nextTopItem.getQuantityDropped(), 0L),
                                           nextTopItem.getSingleton(),
                                           sequence++,
                                           KillItem.TOP_LEVEL);
        updates.add(newTopItem);
        for (GetKillmailsKillmailIdKillmailHashItem nextChildItem : nextTopItem.getItems()) {
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
