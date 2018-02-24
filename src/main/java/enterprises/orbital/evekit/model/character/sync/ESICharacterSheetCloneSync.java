package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ClonesApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdClonesHomeLocation;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdClonesJumpClone;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdClonesOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheetClone;
import enterprises.orbital.evekit.model.character.JumpClone;
import enterprises.orbital.evekit.model.character.JumpCloneImplant;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterSheetCloneSync extends AbstractESIAccountSync<GetCharactersCharacterIdClonesOk> {
  protected static final Logger log = Logger.getLogger(ESICharacterSheetCloneSync.class.getName());

  public ESICharacterSheetCloneSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_CLONES;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CharacterSheetClone) ||
        (item instanceof JumpClone) ||
        (item instanceof JumpCloneImplant);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof CharacterSheetClone)
        existing = CharacterSheetClone.get(account, time);
      else if (item instanceof JumpClone)
        existing = JumpClone.get(account, time, ((JumpClone) item).getJumpCloneID());
      else
        existing = JumpCloneImplant.get(account,time, ((JumpCloneImplant) item).getJumpCloneID(), ((JumpCloneImplant) item).getTypeID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCharactersCharacterIdClonesOk> getServerData(ESIAccountClientProvider cp) throws ApiException, IOException {
    ClonesApi apiInstance = cp.getClonesApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdClonesOk> result = apiInstance.getCharactersCharacterIdClonesWithHttpInfo((int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()), result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCharactersCharacterIdClonesOk> data,
                                   List<CachedData> updates) throws IOException {
    GetCharactersCharacterIdClonesHomeLocation hl = data.getData().getHomeLocation();
    if (hl == null) {
      hl = new GetCharactersCharacterIdClonesHomeLocation();
      hl.setLocationId(null);
      hl.setLocationType(null);
    }
    // Add clone location information
    updates.add(new CharacterSheetClone(nullSafeDateTime(data.getData().getLastCloneJumpDate(), new DateTime(new Date(0))).getMillis(),
                                        nullSafeLong(hl.getLocationId(), 0L),
                                        nullSafeEnum(hl.getLocationType(), null),
                                        nullSafeDateTime(data.getData().getLastStationChangeDate(), new DateTime(new Date(0))).getMillis()));
    // Add jump clone information
    Set<Integer> seenJumpClones = new HashSet<>();
    Set<Pair<Integer, Integer>> seenJumpCloneImplants = new HashSet<>();
    for (GetCharactersCharacterIdClonesJumpClone nextClone : data.getData().getJumpClones()) {
      seenJumpClones.add(nextClone.getJumpCloneId());
      updates.add(new JumpClone(nextClone.getJumpCloneId(),
                                nextClone.getLocationId(),
                                nextClone.getName(),
                                nextClone.getLocationType().toString()));
      for (Integer nextImplant : nextClone.getImplants()) {
        seenJumpCloneImplants.add(Pair.of(nextClone.getJumpCloneId(), nextImplant));
        updates.add(new JumpCloneImplant(nextClone.getJumpCloneId(), nextImplant));
      }
    }
    // Delete any clones or implants no longer present
    for (JumpClone existing : CachedData.retrieveAll(time, (contid, at) -> JumpClone.accessQuery(account, contid, 1000, false, at, AttributeSelector.any(),
                                                                                                 AttributeSelector.any(), AttributeSelector.any(), AttributeSelector.any()))) {
      if (!seenJumpClones.contains(existing.getJumpCloneID())) {
        // Delete this clone
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
    for (JumpCloneImplant existing : CachedData.retrieveAll(time, (contid, at) -> JumpCloneImplant.accessQuery(account, contid, 1000, false, at, AttributeSelector.any(),
                                                                                                               AttributeSelector.any()))) {
      if (!seenJumpCloneImplants.contains(Pair.of(existing.getJumpCloneID(), existing.getTypeID()))) {
        // Delete this clone implant
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
