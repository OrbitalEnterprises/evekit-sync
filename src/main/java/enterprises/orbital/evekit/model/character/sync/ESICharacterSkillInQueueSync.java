package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.SkillsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdSkillqueue200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.SkillInQueue;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterSkillInQueueSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdSkillqueue200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterSkillInQueueSync.class.getName());

  public ESICharacterSkillInQueueSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_SKILL_QUEUE;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof SkillInQueue;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = SkillInQueue.get(account, time, ((SkillInQueue) item).getQueuePosition());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdSkillqueue200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    SkillsApi apiInstance = cp.getSkillsApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdSkillqueue200Ok>> result = apiInstance.getCharactersCharacterIdSkillqueueWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdSkillqueue200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Add skills in queue
    Set<Integer> seenPositions = new HashSet<>();
    for (GetCharactersCharacterIdSkillqueue200Ok next : data.getData()) {
      seenPositions.add(next.getQueuePosition());
      updates.add(new SkillInQueue(nullSafeInteger(next.getLevelEndSp(), 0),
                                   nullSafeDateTime(next.getFinishDate(), new DateTime(new Date(0))).getMillis(),
                                   next.getFinishedLevel(),
                                   next.getQueuePosition(),
                                   nullSafeInteger(next.getLevelStartSp(), 0),
                                   nullSafeDateTime(next.getStartDate(), new DateTime(new Date(0))).getMillis(),
                                   next.getSkillId(),
                                   nullSafeInteger(next.getTrainingStartSp(), 0)));
    }
    // Delete skills no longer in the queue
    for (SkillInQueue existing : CachedData.retrieveAll(time,
                                                        (contid, at) -> SkillInQueue.accessQuery(account, contid, 1000,
                                                                                                 false, at,
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any()))) {
      if (!seenPositions.contains(existing.getQueuePosition())) {
        // Delete this clone
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
