package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationCorporationIdMiningExtractions200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationCorporationIdMiningObservers200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationCorporationIdMiningObserversObserverId200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.MiningExtraction;
import enterprises.orbital.evekit.model.corporation.MiningObservation;
import enterprises.orbital.evekit.model.corporation.MiningObserver;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationMiningLedgerSync extends AbstractESIAccountSync<ESICorporationMiningLedgerSync.MiningLedgerData> {
  protected static final Logger log = Logger.getLogger(ESICorporationMiningLedgerSync.class.getName());

  class MiningLedgerData {
    List<GetCorporationCorporationIdMiningExtractions200Ok> extractions;
    List<GetCorporationCorporationIdMiningObservers200Ok> observers;
    Map<Long, List<GetCorporationCorporationIdMiningObserversObserverId200Ok>> observations;
  }

  public ESICorporationMiningLedgerSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_MINING;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof MiningExtraction) ||
        (item instanceof MiningObserver) ||
        (item instanceof MiningObservation);
    CachedData existing;

    if (item instanceof MiningExtraction) {
      MiningExtraction it = (MiningExtraction) item;
      existing = MiningExtraction.get(account, time, it.getMoonID(), it.getStructureID(), it.getExtractionStartTime());
    } else if (item instanceof MiningObserver) {
      MiningObserver it = (MiningObserver) item;
      existing = MiningObserver.get(account, time, it.getObserverID());
    } else {
      MiningObservation it = (MiningObservation) item;
      existing = MiningObservation.get(account, time, it.getObserverID(), it.getCharacterID(), it.getTypeID());
    }

    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<MiningLedgerData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    final String errTrap = "Character does not have required role";
    MiningLedgerData data = new MiningLedgerData();
    IndustryApi apiInstance = cp.getIndustryApi();

    Pair<Long, List<GetCorporationCorporationIdMiningExtractions200Ok>> result;
    try {
      result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationCorporationIdMiningExtractionsWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
    } catch (ApiException e) {
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        result = Pair.of(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS),
                         Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.extractions = result.getRight();

    Pair<Long, List<GetCorporationCorporationIdMiningObservers200Ok>> bkResult;
    try {
      bkResult = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationCorporationIdMiningObserversWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
    } catch (ApiException e) {
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        bkResult = Pair.of(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS),
                           Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
    expiry = Math.max(expiry,
                      bkResult.getLeft() > 0 ? bkResult.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay());
    data.observers = bkResult.getRight();

    Map<Long, List<GetCorporationCorporationIdMiningObserversObserverId200Ok>> observations = new HashMap<>();
    for (GetCorporationCorporationIdMiningObservers200Ok nextObserver : data.observers) {
      Pair<Long, List<GetCorporationCorporationIdMiningObserversObserverId200Ok>> observerResult =
          pagedResultRetriever((page) -> {
            ESIThrottle.throttle(endpoint().name(), account);
            return apiInstance.getCorporationCorporationIdMiningObserversObserverIdWithHttpInfo(
                (int) account.getEveCorporationID(),
                nextObserver.getObserverId(),
                null,
                null,
                page,
                accessToken());
          });
      expiry = Math.max(expiry,
                        observerResult.getLeft() > 0 ? observerResult.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay());
      observations.put(nextObserver.getObserverId(), observerResult.getRight());
    }
    data.observations = observations;

    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<MiningLedgerData> data,
                                   List<CachedData> updates) throws IOException {

    // Update extractions
    Set<Triple<Integer, Long, Long>> seenExtractions = new HashSet<>();

    Map<Triple<Integer, Long, Long>, MiningExtraction> storedExtractions = CachedData.retrieveAll(time,
                                                                                                  (contid, at) -> MiningExtraction.accessQuery(
                                                                                                      account, contid,
                                                                                                      1000, false, at,
                                                                                                      AttributeSelector.any(),
                                                                                                      AttributeSelector.any(),
                                                                                                      AttributeSelector.any(),
                                                                                                      AttributeSelector.any(),
                                                                                                      AttributeSelector.any()))
                                                                                     .stream()
                                                                                     .map(
                                                                                         x -> new AbstractMap.SimpleEntry<>(
                                                                                             Triple.of(x.getMoonID(),
                                                                                                       x.getStructureID(),
                                                                                                       x.getExtractionStartTime()),
                                                                                             x
                                                                                         ))
                                                                                     .collect(Collectors.toMap(
                                                                                         AbstractMap.SimpleEntry::getKey,
                                                                                         AbstractMap.SimpleEntry::getValue));

    for (GetCorporationCorporationIdMiningExtractions200Ok next : data.getData().extractions) {
      MiningExtraction nextExtraction = new MiningExtraction(next.getMoonId(),
                                                             next.getStructureId(),
                                                             next.getExtractionStartTime()
                                                                 .getMillis(),
                                                             next.getChunkArrivalTime()
                                                                 .getMillis(),
                                                             next.getNaturalDecayTime()
                                                                 .getMillis());
      Triple<Integer, Long, Long> key = Triple.of(nextExtraction.getMoonID(),
                                                  nextExtraction.getStructureID(),
                                                  nextExtraction.getExtractionStartTime());
      // Only update if there is a change to reduce DB contention
      if (!storedExtractions.containsKey(key) ||
          !nextExtraction.equivalent(storedExtractions.get(key)))
        updates.add(nextExtraction);
      seenExtractions.add(key);
    }

    for (MiningExtraction existing : storedExtractions.values()) {
      if (!seenExtractions.contains(
          Triple.of(existing.getMoonID(), existing.getStructureID(), existing.getExtractionStartTime()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    // Update observers
    Set<Long> seenObservers = new HashSet<>();

    Map<Long, MiningObserver> storedObservers = CachedData.retrieveAll(time,
                                                                       (contid, at) -> MiningObserver.accessQuery(
                                                                           account, contid,
                                                                           1000, false, at,
                                                                           AttributeSelector.any(),
                                                                           AttributeSelector.any(),
                                                                           AttributeSelector.any()))
                                                          .stream()
                                                          .map(
                                                              x -> new AbstractMap.SimpleEntry<>(
                                                                  x.getObserverID(),
                                                                  x
                                                              ))
                                                          .collect(Collectors.toMap(
                                                              AbstractMap.SimpleEntry::getKey,
                                                              AbstractMap.SimpleEntry::getValue));

    for (GetCorporationCorporationIdMiningObservers200Ok next : data.getData().observers) {
      MiningObserver nextObserver = new MiningObserver(next.getObserverId(),
                                                       next.getObserverType()
                                                           .toString(),
                                                       next.getLastUpdated()
                                                           .toDate()
                                                           .getTime());
      Long key = nextObserver.getObserverID();
      // Only update if there is a change to reduce DB contention
      if (!storedObservers.containsKey(key) ||
          !nextObserver.equivalent(storedObservers.get(key)))
        updates.add(nextObserver);
      seenObservers.add(key);
    }

    for (MiningObserver existing : storedObservers.values()) {
      if (!seenObservers.contains(existing.getObserverID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

    // Update observations
    Set<Triple<Long, Integer, Integer>> seenObservations = new HashSet<>();

    Map<Triple<Long, Integer, Integer>, MiningObservation> storedObservations = CachedData.retrieveAll(time,
                                                                                                       (contid, at) -> MiningObservation.accessQuery(
                                                                                                           account,
                                                                                                           contid,
                                                                                                           1000, false,
                                                                                                           at,
                                                                                                           AttributeSelector.any(),
                                                                                                           AttributeSelector.any(),
                                                                                                           AttributeSelector.any(),
                                                                                                           AttributeSelector.any(),
                                                                                                           AttributeSelector.any(),
                                                                                                           AttributeSelector.any()))
                                                                                          .stream()
                                                                                          .map(
                                                                                              x -> new AbstractMap.SimpleEntry<>(
                                                                                                  Triple.of(
                                                                                                      x.getObserverID(),
                                                                                                      x.getCharacterID(),
                                                                                                      x.getTypeID()),
                                                                                                  x
                                                                                              ))
                                                                                          .collect(Collectors.toMap(
                                                                                              AbstractMap.SimpleEntry::getKey,
                                                                                              AbstractMap.SimpleEntry::getValue));

    for (Long nextObsKey : data.getData().observations.keySet()) {
      for (GetCorporationCorporationIdMiningObserversObserverId200Ok next : data.getData().observations.get(
          nextObsKey)) {
        MiningObservation nextObservation = new MiningObservation(nextObsKey,
                                                                  next.getCharacterId(),
                                                                  next.getTypeId(),
                                                                  next.getRecordedCorporationId(),
                                                                  next.getQuantity(),
                                                                  next.getLastUpdated()
                                                                      .toDate()
                                                                      .getTime());
        Triple<Long, Integer, Integer> key = Triple.of(nextObservation.getObserverID(),
                                                       nextObservation.getCharacterID(),
                                                       nextObservation.getTypeID());
        // Only update if there is a change to reduce DB contention
        if (!storedObservations.containsKey(key) ||
            !nextObservation.equivalent(storedObservations.get(key)))
          updates.add(nextObservation);
        seenObservations.add(key);
      }
    }

    for (MiningObservation existing : storedObservations.values()) {
      if (!seenObservations.contains(Triple.of(existing.getObserverID(),
                                               existing.getCharacterID(),
                                               existing.getTypeID()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

  }

}
