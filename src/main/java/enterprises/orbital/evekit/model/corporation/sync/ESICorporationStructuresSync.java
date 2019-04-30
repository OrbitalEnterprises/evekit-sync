package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStructures200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStructuresService;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Structure;
import enterprises.orbital.evekit.model.corporation.StructureService;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationStructuresSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdStructures200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationStructuresSync.class.getName());

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

  public ESICorporationStructuresSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_STRUCTURES;
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof Structure) || (item instanceof StructureService);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof Structure)
        existing = Structure.get(account, time, ((Structure) item).getStructureID());
      else
        existing = StructureService.get(account, time, ((StructureService) item).getStructureID(),
                                        ((StructureService) item).getName());
    }
    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdStructures200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    try {
      // Retrieve structures info
      Pair<Long, List<GetCorporationsCorporationIdStructures200Ok>> result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdStructuresWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            null,
            null,
            page,
            accessToken());
      });
      long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();

      return new ESIAccountServerResult<>(expiry, result.getRight());
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        cacheHit();
        return new ESIAccountServerResult<>(expiry, Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }
  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdStructures200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(2);

    // Compute hash
    List<Structure> retrievedStructures = new ArrayList<>();
    List<StructureService> retrievedServices = new ArrayList<>();
    for (GetCorporationsCorporationIdStructures200Ok nextStruct : data.getData()) {
      retrievedStructures.add(new Structure(nextStruct.getStructureId(),
                                            nextStruct.getCorporationId(),
                                            nullSafeDateTime(nextStruct.getFuelExpires(),
                                                             new DateTime(new Date(0))).getMillis(),
                                            nullSafeDateTime(nextStruct.getNextReinforceApply(),
                                                             new DateTime(new Date(0))).getMillis(),
                                            nullSafeInteger(nextStruct.getNextReinforceHour(), -1),
                                            nullSafeInteger(nextStruct.getNextReinforceWeekday(), -1),
                                            nextStruct.getProfileId(),
                                            nextStruct.getReinforceHour(),
                                            nextStruct.getReinforceWeekday(),
                                            nextStruct.getState()
                                                      .toString(),
                                            nullSafeDateTime(nextStruct.getStateTimerEnd(),
                                                             new DateTime(new Date(0))).getMillis(),
                                            nullSafeDateTime(nextStruct.getStateTimerStart(),
                                                             new DateTime(new Date(0))).getMillis(),
                                            nextStruct.getSystemId(),
                                            nextStruct.getTypeId(),
                                            nullSafeDateTime(nextStruct.getUnanchorsAt(),
                                                             new DateTime(new Date(0))).getMillis()));
      for (GetCorporationsCorporationIdStructuresService s : nextStruct.getServices()) {
        retrievedServices.add(new StructureService(nextStruct.getStructureId(),
                                                   s.getName(),
                                                   s.getState()
                                                    .toString()));
      }
    }
    retrievedStructures.sort(Comparator.comparingLong(Structure::getStructureID));
    retrievedServices.sort((o1, o2) -> {
      int sid = Comparator.comparingLong(StructureService::getStructureID)
                          .compare(o1, o2);
      return sid != 0 ? sid : o1.getName()
                                .compareTo(o2.getName());
    });
    String structureHashResult = CachedData.dataHashHelper(retrievedStructures.stream()
                                                                              .map(Structure::dataHash)
                                                                              .toArray());
    String serviceHashResult = CachedData.dataHashHelper(retrievedServices.stream()
                                                                          .map(StructureService::dataHash)
                                                                          .toArray());

    // Check hash
    if (cachedHash[0] == null || !cachedHash[0].equals(structureHashResult)) {
      cacheMiss();
      cachedHash[0] = structureHashResult;
      updates.addAll(retrievedStructures);

      // Clean up removed structures
      Set<Long> seenStructures = retrievedStructures.stream()
                                                    .map(Structure::getStructureID)
                                                    .collect(Collectors.toSet());
      for (Structure s : CachedData.retrieveAll(time,
                                                (contid, at) -> Structure.accessQuery(account, contid, 1000, false, at,
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any(),
                                                                                      AttributeSelector.any()))) {
        if (!seenStructures.contains(s.getStructureID())) {
          s.evolve(null, time);
          updates.add(s);
        }
      }
    } else {
      cacheHit();
    }

    // Check hash
    if (cachedHash[1] == null || !cachedHash[1].equals(serviceHashResult)) {
      cacheMiss();
      cachedHash[1] = serviceHashResult;
      updates.addAll(retrievedServices);

      // Clean up removed services
      Set<Pair<Long, String>> seenServices = retrievedServices.stream()
                                                              .map(x -> Pair.of(x.getStructureID(), x.getName()))
                                                              .collect(
                                                                  Collectors.toSet());
      for (StructureService s : CachedData.retrieveAll(time,
                                                       (contid, at) -> StructureService.accessQuery(account, contid,
                                                                                                    1000,
                                                                                                    false, at,
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any()))) {
        if (!seenServices.contains(Pair.of(s.getStructureID(), s.getName()))) {
          s.evolve(null, time);
          updates.add(s);
        }
      }
    } else {
      cacheHit();
    }

    // Save hashes for next execution
    currentETag = String.join("|", cachedHash);
  }

}
