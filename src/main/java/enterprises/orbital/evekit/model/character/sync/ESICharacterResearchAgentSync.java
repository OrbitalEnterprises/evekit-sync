package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdAgentsResearch200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.ResearchAgent;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterResearchAgentSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdAgentsResearch200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterResearchAgentSync.class.getName());

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

  public ESICharacterResearchAgentSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_AGENTS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof ResearchAgent;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = ResearchAgent.get(account, time, ((ResearchAgent) item).getAgentID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdAgentsResearch200Ok>> getServerData(
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
      ApiResponse<List<GetCharactersCharacterIdAgentsResearch200Ok>> result = apiInstance.getCharactersCharacterIdAgentsResearchWithHttpInfo(
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

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdAgentsResearch200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    // Add and record seen agents
    Set<Integer> seenAgents = new HashSet<>();
    for (GetCharactersCharacterIdAgentsResearch200Ok next : data.getData()) {
      ResearchAgent nextAgent = new ResearchAgent(next.getAgentId(), next.getPointsPerDay(), next.getRemainderPoints(),
                                                  next.getStartedAt()
                                                      .getMillis(), next.getSkillTypeId());
      seenAgents.add(nextAgent.getAgentID());
      updates.add(nextAgent);
    }

    // Check for agents that no longer exist and schedule for EOL
    for (ResearchAgent existing : retrieveAll(time,
                                              (long contid, AttributeSelector at) -> ResearchAgent.accessQuery(account,
                                                                                                               contid,
                                                                                                               1000,
                                                                                                               false,
                                                                                                               at,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR,
                                                                                                               ANY_SELECTOR))) {
      if (!seenAgents.contains(existing.getAgentID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }


}
