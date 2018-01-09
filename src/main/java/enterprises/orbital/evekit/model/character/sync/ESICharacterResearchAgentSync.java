package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdAgentsResearch200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.ResearchAgent;
import enterprises.orbital.evekit.model.common.AccountBalance;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterResearchAgentSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdAgentsResearch200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterResearchAgentSync.class.getName());

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
  protected ESIAccountServerResult<List<GetCharactersCharacterIdAgentsResearch200Ok>> getServerData(ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    ApiResponse<List<GetCharactersCharacterIdAgentsResearch200Ok>> result = apiInstance.getCharactersCharacterIdAgentsResearchWithHttpInfo((int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()), result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdAgentsResearch200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // And and record seen agents
    Set<Integer> seenAgents = new HashSet<>();
    for (GetCharactersCharacterIdAgentsResearch200Ok next : data.getData()) {
      ResearchAgent nextAgent = new ResearchAgent(next.getAgentId(), next.getPointsPerDay(), next.getRemainderPoints(), next.getStartedAt().getMillis(), next.getSkillTypeId());
      seenAgents.add(nextAgent.getAgentID());
      updates.add(nextAgent);
    }

    // Check for agents that no longer exist and schedule for EOL
    for (ResearchAgent existing : retrieveAll(time, (long contid, AttributeSelector at) -> ResearchAgent.accessQuery(account, contid, 1000, false, at, ANY_SELECTOR, ANY_SELECTOR, ANY_SELECTOR, ANY_SELECTOR, ANY_SELECTOR))) {
      if (!seenAgents.contains(existing.getAgentID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }


}
