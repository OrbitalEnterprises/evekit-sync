package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.ResearchAgent;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IResearchAgent;

public class CharacterResearchAgentSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterResearchAgentSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getResearchStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setResearchStatus(status);
    tracker.setResearchDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setResearchExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getResearchExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof ResearchAgent;

    ResearchAgent api = (ResearchAgent) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing ResearchAgent to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      ResearchAgent existing = ResearchAgent.get(accountKey, time, api.getAgentID());

      if (existing != null) {
        if (!existing.equivalent(api)) {
          // Evolve
          existing.evolve(api, time);
          super.commit(time, tracker, container, accountKey, existing);
          super.commit(time, tracker, container, accountKey, api);
        }
      } else {
        // New entity
        api.setup(accountKey, time);
        super.commit(time, tracker, container, accountKey, api);
      }
    }

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestResearchAgents();
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IResearchAgent> agents = (Collection<IResearchAgent>) data;

    Set<Integer> seenAgents = new HashSet<Integer>();

    // Create set of agents to update
    for (IResearchAgent next : agents) {
      ResearchAgent instance = new ResearchAgent(
          next.getAgentID(), next.getCurrentPoints(), next.getPointsPerDay(), next.getRemainderPoints(), ModelUtil.safeConvertDate(next.getResearchStartDate()),
          next.getSkillTypeID());
      seenAgents.add(instance.getAgentID());
      updates.add(instance);
    }

    // Remove agents no longer in the list
    // These should all be EOL
    int contid = -1;
    List<ResearchAgent> nextBatch = ResearchAgent.getAllAgents(syncAccount, time, 1000, contid);
    while (!nextBatch.isEmpty()) {
      for (ResearchAgent next : nextBatch) {
        int agentID = next.getAgentID();
        if (!seenAgents.contains(agentID)) {
          next.evolve(null, time);
          updates.add(next);
        }
        contid = Math.max(contid, agentID);
      }
      nextBatch = ResearchAgent.getAllAgents(syncAccount, time, 1000, contid);
    }

    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterResearchAgentSync syncher = new CharacterResearchAgentSync();

  public static SyncStatus syncResearchAgents(
                                              long time,
                                              SynchronizedEveAccount syncAccount,
                                              SynchronizerUtil syncUtil,
                                              ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "ResearchAgents");
  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "ResearchAgents", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "ResearchAgents", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
