package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ClonesApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.Implant;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterSheetImplantsSync extends AbstractESIAccountSync<List<Integer>> {
  protected static final Logger log = Logger.getLogger(ESICharacterSheetImplantsSync.class.getName());

  public ESICharacterSheetImplantsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_IMPLANTS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Implant;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = Implant.get(account, time, ((Implant) item).getTypeID());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<Integer>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    ClonesApi apiInstance = cp.getClonesApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<Integer>> result = apiInstance.getCharactersCharacterIdImplantsWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<Integer>> data,
                                   List<CachedData> updates) throws IOException {
    Set<Integer> seenImplants = new HashSet<>();
    for (int next : data.getData()) {
      Implant nextImplant = new Implant(next);
      seenImplants.add(next);
      updates.add(nextImplant);
    }
    for (Implant stored : CachedData.retrieveAll(time,
                                                 (contid, at) -> Implant.accessQuery(account, contid, 1000, false, at,
                                                                                     AttributeSelector.any()))) {
      if (!seenImplants.contains(stored.getTypeID())) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

  }


}
