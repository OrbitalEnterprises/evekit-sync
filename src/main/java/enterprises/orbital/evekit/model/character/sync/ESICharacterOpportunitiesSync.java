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
    long expiry;

    // Retrieve opportunities
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdOpportunities200Ok>> result = apiInstance.getCharactersCharacterIdOpportunitiesWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

    return new ESIAccountServerResult<>(expiry, result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdOpportunities200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    // Add opportunities
    for (GetCharactersCharacterIdOpportunities200Ok next : data.getData())
      updates.add(new Opportunity(next.getTaskId(), next.getCompletedAt()
                                                        .getMillis()));
  }

}
