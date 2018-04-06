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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationCustomsOfficesSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdCustomsOffices200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationCustomsOfficesSync.class.getName());

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

  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdCustomsOffices200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    PlanetaryInteractionApi apiInstance = cp.getPlanetaryInteractionApi();

    Pair<Long, List<GetCorporationsCorporationIdCustomsOffices200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdCustomsOfficesWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });

    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    return new ESIAccountServerResult<>(expiry, result.getRight());
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdCustomsOffices200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    // Keep track of seen offices
    Set<Long> seenOffices = new HashSet<>();

    // Process offices
    for (GetCorporationsCorporationIdCustomsOffices200Ok next : data.getData()) {
      updates.add(new CustomsOffice(next.getOfficeId(),
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
      seenOffices.add(next.getOfficeId());
    }

    // Check for offices that no longer exist and schedule for EOL
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
  }

}
