package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdStandings200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Standing;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterStandingSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdStandings200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterStandingSync.class.getName());

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

  public ESICharacterStandingSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_STANDINGS;
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
  protected ESIAccountServerResult<List<GetCharactersCharacterIdStandings200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();

    // Check whether we have an ETAG to send for the skills call
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    try {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCharactersCharacterIdStandings200Ok>> result = apiInstance.getCharactersCharacterIdStandingsWithHttpInfo(
          (int) account.getEveCharacterID(), null, currentETag, accessToken());
      checkCommonProblems(result);
      cacheMiss();
      currentETag = extractETag(result, null);
      return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                          result.getData());
    } catch (ApiException e) {
      // Trap 304 which indicates there are no changes from the last call
      // Anything else is rethrown.
      if (e.getCode() != 304) throw e;
      cacheHit();
      currentETag = extractETag(e, null);
      return new ESIAccountServerResult<>(extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay()), null);
    }

  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdStandings200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    // Add and record standings
    Set<Pair<String, Integer>> seenStandings = new HashSet<>();
    for (GetCharactersCharacterIdStandings200Ok next : data.getData()) {
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
