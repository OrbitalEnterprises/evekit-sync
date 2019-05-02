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

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
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
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        result = Pair.of(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS),
                         null);
        cacheHit();
      } else {
        // Any other error will be rethrown.
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
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        bkResult = Pair.of(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS),
                           null);
      } else {
        // Any other error will be rethrown.
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

  @SuppressWarnings({"Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<MiningLedgerData> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(3);

    if (data.getData().extractions != null) {
      // Compute and check hash
      List<MiningExtraction> retrievedExtractions = new ArrayList<>();
      for (GetCorporationCorporationIdMiningExtractions200Ok next : data.getData().extractions) {
        MiningExtraction nextExtraction = new MiningExtraction(next.getMoonId(),
                                                               next.getStructureId(),
                                                               next.getExtractionStartTime()
                                                                   .getMillis(),
                                                               next.getChunkArrivalTime()
                                                                   .getMillis(),
                                                               next.getNaturalDecayTime()
                                                                   .getMillis());
        retrievedExtractions.add(nextExtraction);
      }
      String hash = CachedData.dataHashHelper(retrievedExtractions.toArray());

      if (cachedHash[0] == null || !cachedHash[0].equals(hash)) {
        cacheMiss();
        cachedHash[0] = hash;

        // Update extractions
        Set<Triple<Integer, Long, Long>> seenExtractions = new HashSet<>();

        Map<Triple<Integer, Long, Long>, MiningExtraction> storedExtractions = CachedData.retrieveAll(time,
                                                                                                      (contid, at) -> MiningExtraction.accessQuery(
                                                                                                          account,
                                                                                                          contid,
                                                                                                          1000, false,
                                                                                                          at,
                                                                                                          AttributeSelector.any(),
                                                                                                          AttributeSelector.any(),
                                                                                                          AttributeSelector.any(),
                                                                                                          AttributeSelector.any(),
                                                                                                          AttributeSelector.any()))
                                                                                         .stream()
                                                                                         .map(
                                                                                             x -> new AbstractMap.SimpleEntry<>(
                                                                                                 Triple.of(
                                                                                                     x.getMoonID(),
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
      } else {
        cacheHit();
      }
    }

    if (data.getData().observers != null) {
      // Compute and check hash
      List<MiningObserver> retrievedObservers = new ArrayList<>();
      for (GetCorporationCorporationIdMiningObservers200Ok next : data.getData().observers) {
        MiningObserver nextObserver = new MiningObserver(next.getObserverId(),
                                                         next.getObserverType()
                                                             .toString(),
                                                         next.getLastUpdated()
                                                             .toDate()
                                                             .getTime());
        retrievedObservers.add(nextObserver);
      }
      String hash = CachedData.dataHashHelper(retrievedObservers.toArray());

      if (cachedHash[1] == null || !cachedHash[1].equals(hash)) {
        cacheMiss();
        cachedHash[1] = hash;

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
      } else {
        cacheHit();
      }
    }

    // Compute and check hash
    List<MiningObservation> retrievedObservations = new ArrayList<>();
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
        retrievedObservations.add(nextObservation);
      }
    }
    String hash = CachedData.dataHashHelper(retrievedObservations.toArray());

    if (cachedHash[2] == null || !cachedHash[2].equals(hash)) {
      cacheMiss();
      cachedHash[2] = hash;

      // Update observations
      Set<Triple<Long, Integer, Integer>> seenObservations = new HashSet<>();

      Map<Triple<Long, Integer, Integer>, MiningObservation> storedObservations = CachedData.retrieveAll(time,
                                                                                                         (contid, at) -> MiningObservation.accessQuery(
                                                                                                             account,
                                                                                                             contid,
                                                                                                             1000,
                                                                                                             false,
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
    } else {
      cacheHit();
    }

    // Save new hash
    currentETag = String.join("|", cachedHash);
  }

}
