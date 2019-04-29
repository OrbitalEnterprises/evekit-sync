package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.OpportunitiesApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOpportunities200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.Opportunity;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterOpportunitiesSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdOpportunities200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterOpportunitiesSync.class.getName());

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

  public ESICharacterOpportunitiesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_OPPORTUNITIES;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Opportunity;

    if (Opportunity.get(account, time, ((Opportunity) item).getTaskID()) != null) {
      // Item already exists. We don't need to check if it's changed because opportunities are immutable.
      return;
    }

    // Otherwise, create entry
    evolveOrAdd(time, null, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdOpportunities200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    OpportunitiesApi apiInstance = cp.getOpportunitiesApi();

    // Check whether we have an ETAG to send for the skills call
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    try {
      long expiry;

      // Retrieve opportunities
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCharactersCharacterIdOpportunities200Ok>> result = apiInstance.getCharactersCharacterIdOpportunitiesWithHttpInfo(
          (int) account.getEveCharacterID(), null, currentETag, accessToken());
      checkCommonProblems(result);
      cacheMiss();
      currentETag = extractETag(result, null);
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

      return new ESIAccountServerResult<>(expiry, result.getData());
    } catch (ApiException e) {
      // Trap 304 which indicates there are no changes from the last call
      // Anything else is rethrown.
      if (e.getCode() != 304) throw e;
      cacheHit();
      currentETag = extractETag(e, null);
      return new ESIAccountServerResult<>(extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay()), null);
    }
  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdOpportunities200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    // Add opportunities
    for (GetCharactersCharacterIdOpportunities200Ok next : data.getData())
      updates.add(new Opportunity(next.getTaskId(), next.getCompletedAt()
                                                        .getMillis()));
  }

}
