package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.PlanetaryInteractionApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdCustomsOffices200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CustomsOffice;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationCustomsOfficesSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdCustomsOffices200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationCustomsOfficesSync.class.getName());

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

  public ESICorporationCustomsOfficesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_CUSTOMS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CustomsOffice;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = CustomsOffice.get(account, time, ((CustomsOffice) item).getOfficeID());
    }
    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdCustomsOffices200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    PlanetaryInteractionApi apiInstance = cp.getPlanetaryInteractionApi();

    try {
      Pair<Long, List<GetCorporationsCorporationIdCustomsOffices200Ok>> result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdCustomsOfficesWithHttpInfo(
            (int) account.getEveCorporationID(),
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
        return new ESIAccountServerResult<>(expiry, null);
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }
  }

  @SuppressWarnings({"Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdCustomsOffices200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData() == null)
      // Incorrect role, nothing to do.
      return;

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(1);

    // Compute hash
    List<CustomsOffice> retrievedOffices = new ArrayList<>();
    for (GetCorporationsCorporationIdCustomsOffices200Ok next : data.getData()) {
      retrievedOffices.add(new CustomsOffice(next.getOfficeId(),
                                             next.getSystemId(),
                                             next.getReinforceExitStart(),
                                             next.getReinforceExitEnd(),
                                             next.getAllowAllianceAccess(),
                                             next.getAllowAccessWithStandings(),
                                             nullSafeEnum(next.getStandingLevel(), null),
                                             nullSafeFloat(next.getAllianceTaxRate(), 0F),
                                             nullSafeFloat(next.getCorporationTaxRate(), 0F),
                                             nullSafeFloat(next.getExcellentStandingTaxRate(), 0F),
                                             nullSafeFloat(next.getGoodStandingTaxRate(), 0F),
                                             nullSafeFloat(next.getNeutralStandingTaxRate(), 0F),
                                             nullSafeFloat(next.getBadStandingTaxRate(), 0F),
                                             nullSafeFloat(next.getTerribleStandingTaxRate(), 0F)));
    }
    retrievedOffices.sort(Comparator.comparingLong(CustomsOffice::getOfficeID));
    String officeHashResult = CachedData.dataHashHelper(retrievedOffices.stream()
                                                                        .map(CustomsOffice::dataHash)
                                                                        .toArray());

    // Check hash
    if (cachedHash[0] == null || !cachedHash[0].equals(officeHashResult)) {
      cacheMiss();
      cachedHash[0] = officeHashResult;
      updates.addAll(retrievedOffices);

      // Check for offices that no longer exist and schedule for EOL
      Set<Long> seenOffices = retrievedOffices.stream()
                                              .map(CustomsOffice::getOfficeID)
                                              .collect(Collectors.toSet());

      for (CustomsOffice existing : retrieveAll(time,
                                                (long contid, AttributeSelector at) -> CustomsOffice.accessQuery(
                                                    account, contid,
                                                    1000,
                                                    false, at,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR,
                                                    ANY_SELECTOR))) {
        if (!seenOffices.contains(existing.getOfficeID())) {
          existing.evolve(null, time);
          updates.add(existing);
        }
      }
    } else {
      cacheHit();
    }

    // Save hashes for next execution
    currentETag = String.join("|", cachedHash);
  }

}
