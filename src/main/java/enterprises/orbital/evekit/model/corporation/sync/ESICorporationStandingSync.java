package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStandings200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Standing;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationStandingSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdStandings200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationStandingSync.class.getName());

  public ESICorporationStandingSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_STANDINGS;
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Standing;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      Standing api = (Standing) item;
      existing = Standing.get(account, time, api.getStandingEntity(), api.getFromID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdStandings200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();
    Pair<Long, List<GetCorporationsCorporationIdStandings200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdStandingsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          page,
          accessToken());
    });
    return new ESIAccountServerResult<>(
        result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay(),
        result.getRight());
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdStandings200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record standings
    Set<Pair<String, Integer>> seenStandings = new HashSet<>();
    for (GetCorporationsCorporationIdStandings200Ok next : data.getData()) {
      Standing nextStanding = new Standing(next.getFromType()
                                               .toString(), next.getFromId(), next.getStanding());
      seenStandings.add(Pair.of(next.getFromType()
                                    .toString(), next.getFromId()));
      updates.add(nextStanding);
    }

    // Check for standings that no longer exist and schedule for EOL
    for (Standing existing : retrieveAll(time,
                                         (long contid, AttributeSelector at) -> Standing.accessQuery(account, contid,
                                                                                                     1000,
                                                                                                     false, at,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR))) {
      if (!seenStandings.contains(Pair.of(existing.getStandingEntity(), existing.getFromID()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

  }


}
