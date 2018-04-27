package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.FittingsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdFittings200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdFittingsItem;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.Fitting;
import enterprises.orbital.evekit.model.character.FittingItem;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterFittingsSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdFittings200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterFittingsSync.class.getName());

  public ESICharacterFittingsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_FITTINGS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof Fitting) || (item instanceof FittingItem);

    CachedData existing;
    if (item instanceof Fitting) {
      existing = Fitting.get(account, time, ((Fitting) item).getFittingID());
    } else {
      existing = FittingItem.get(account, time, ((FittingItem) item).getFittingID(), ((FittingItem) item).getTypeID());
    }

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdFittings200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    FittingsApi apiInstance = cp.getFittingsApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdFittings200Ok>> result = apiInstance.getCharactersCharacterIdFittingsWithHttpInfo(
        (int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdFittings200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    Set<Integer> seenFittings = new HashSet<>();
    Set<Pair<Integer, Integer>> seenItems = new HashSet<>();
    for (GetCharactersCharacterIdFittings200Ok next : data.getData()) {
      updates.add(new Fitting(next.getFittingId(),
                              next.getName(),
                              next.getDescription(),
                              next.getShipTypeId()));
      seenFittings.add(next.getFittingId());

      for (GetCharactersCharacterIdFittingsItem item : next.getItems()) {
        updates.add(new FittingItem(next.getFittingId(),
                                    item.getTypeId(),
                                    item.getFlag(),
                                    item.getQuantity()));
        seenItems.add(Pair.of(next.getFittingId(), item.getTypeId()));
      }
    }

    // Check for fittings
    for (Fitting stored : CachedData.retrieveAll(time,
                                                 (contid, at) -> Fitting.accessQuery(account, contid, 1000,
                                                                                     false, at,
                                                                                     AttributeSelector.any(),
                                                                                     AttributeSelector.any(),
                                                                                     AttributeSelector.any(),
                                                                                     AttributeSelector.any()))) {
      if (!seenFittings.contains(stored.getFittingID())) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

    // Check for deleted items
    for (FittingItem stored : CachedData.retrieveAll(time, (contid, at) -> FittingItem.accessQuery(account,
                                                                                                   contid,
                                                                                                   1000,
                                                                                                   false,
                                                                                                   at,
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any()))) {
      if (!seenItems.contains(Pair.of(stored.getFittingID(), stored.getTypeID()))) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

  }

}
