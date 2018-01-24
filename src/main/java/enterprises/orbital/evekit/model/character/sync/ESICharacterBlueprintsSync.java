package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBlueprints200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evekit.model.common.Blueprint;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterBlueprintsSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdBlueprints200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterBlueprintsSync.class.getName());

  public ESICharacterBlueprintsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_BLUEPRINTS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Blueprint;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = Blueprint.get(account, time, ((Blueprint) item).getItemID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdBlueprints200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    Pair<Long, List<GetCharactersCharacterIdBlueprints200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdBlueprintsWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });
    return new ESIAccountServerResult<>(
        result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay(),
        result.getRight());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdBlueprints200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record seen blueprints
    Set<Long> seenBlueprints = new HashSet<>();
    for (GetCharactersCharacterIdBlueprints200Ok next : data.getData()) {
      Blueprint nextBlueprint = new Blueprint(next.getItemId(), next.getLocationId(), next.getLocationFlag()
                                                                                          .toString(), next.getTypeId(),
                                              next.getQuantity(), next.getTimeEfficiency(),
                                              next.getMaterialEfficiency(), next.getRuns());
      seenBlueprints.add(nextBlueprint.getItemID());
      updates.add(nextBlueprint);
    }

    // Check for blueprints that no longer exist and schedule for EOL
    for (Blueprint existing : retrieveAll(time,
                                          (long contid, AttributeSelector at) -> Blueprint.accessQuery(account, contid,
                                                                                                       1000,
                                                                                                       false, at,
                                                                                                       ANY_SELECTOR,
                                                                                                       ANY_SELECTOR,
                                                                                                       ANY_SELECTOR,
                                                                                                       ANY_SELECTOR,
                                                                                                       ANY_SELECTOR,
                                                                                                       ANY_SELECTOR,
                                                                                                       ANY_SELECTOR,
                                                                                                       ANY_SELECTOR))) {
      if (!seenBlueprints.contains(existing.getItemID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
