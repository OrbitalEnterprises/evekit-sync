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

public class ESICorporationStructuresSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdStructures200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationStructuresSync.class.getName());

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
      final String errTrap = "Character does not have required role";
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        return new ESIAccountServerResult<>(expiry, Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdStructures200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Add structures and structure services
    Set<Long> seenStructures = new HashSet<>();
    Set<Pair<Long, String>> seenServices = new HashSet<>();
    for (GetCorporationsCorporationIdStructures200Ok nextStruct : data.getData()) {
      updates.add(new Structure(nextStruct.getStructureId(),
                                nextStruct.getCorporationId(),
                                nullSafeDateTime(nextStruct.getFuelExpires(), new DateTime(new Date(0))).getMillis(),
                                nullSafeDateTime(nextStruct.getNextReinforceApply(),
                                                 new DateTime(new Date(0))).getMillis(),
                                nullSafeInteger(nextStruct.getNextReinforceHour(), -1),
                                nullSafeInteger(nextStruct.getNextReinforceWeekday(), -1),
                                nextStruct.getProfileId(),
                                nextStruct.getReinforceHour(),
                                nextStruct.getReinforceWeekday(),
                                nextStruct.getState()
                                          .toString(),
                                nullSafeDateTime(nextStruct.getStateTimerEnd(), new DateTime(new Date(0))).getMillis(),
                                nullSafeDateTime(nextStruct.getStateTimerStart(),
                                                 new DateTime(new Date(0))).getMillis(),
                                nextStruct.getSystemId(),
                                nextStruct.getTypeId(),
                                nullSafeDateTime(nextStruct.getUnanchorsAt(), new DateTime(new Date(0))).getMillis()));
      seenStructures.add(nextStruct.getStructureId());
      for (GetCorporationsCorporationIdStructuresService s : nextStruct.getServices()) {
        updates.add(new StructureService(nextStruct.getStructureId(),
                                         s.getName(),
                                         s.getState()
                                          .toString()));
        seenServices.add(Pair.of(nextStruct.getStructureId(), s.getName()));
      }
    }

    // Clean up removed structures and services
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
    for (StructureService s : CachedData.retrieveAll(time,
                                                     (contid, at) -> StructureService.accessQuery(account, contid, 1000,
                                                                                                  false, at,
                                                                                                  AttributeSelector.any(),
                                                                                                  AttributeSelector.any(),
                                                                                                  AttributeSelector.any()))) {
      if (!seenServices.contains(Pair.of(s.getStructureID(), s.getName()))) {
        s.evolve(null, time);
        updates.add(s);
      }
    }

  }

}
