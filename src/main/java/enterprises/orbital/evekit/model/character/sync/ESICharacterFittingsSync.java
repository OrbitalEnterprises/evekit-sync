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
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdFittings200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    Map<Integer, Fitting> existingFittingMap = CachedData.retrieveAll(time,
                                                                      (contid, at) -> Fitting.accessQuery(account,
                                                                                                          contid, 1000,
                                                                                                          false, at,
                                                                                                          AttributeSelector.any(),
                                                                                                          AttributeSelector.any(),
                                                                                                          AttributeSelector.any(),
                                                                                                          AttributeSelector.any()))
                                                         .stream()
                                                         .map(x -> new AbstractMap.SimpleEntry<>(x.getFittingID(), x))
                                                         .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey,
                                                                                   AbstractMap.SimpleEntry::getValue));

    Map<Pair<Integer, Integer>, FittingItem> existingItemMap = CachedData.retrieveAll(time,
                                                                                      (contid, at) -> FittingItem.accessQuery(
                                                                                          account,
                                                                                          contid, 1000,
                                                                                          false, at,
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any(),
                                                                                          AttributeSelector.any()))
                                                                         .stream()
                                                                         .map(x -> new AbstractMap.SimpleEntry<>(
                                                                             Pair.of(x.getFittingID(), x.getTypeID()),
                                                                             x))
                                                                         .collect(Collectors.toMap(
                                                                             AbstractMap.SimpleEntry::getKey,
                                                                             AbstractMap.SimpleEntry::getValue));

    Set<Integer> seenFittings = new HashSet<>();
    Set<Pair<Integer, Integer>> seenItems = new HashSet<>();

    for (GetCharactersCharacterIdFittings200Ok next : data.getData()) {
      Fitting nextFitting = new Fitting(next.getFittingId(),
                                        next.getName(),
                                        next.getDescription(),
                                        next.getShipTypeId());
      // Only update if there is a change to reduce DB contention
      if (!existingFittingMap.containsKey(nextFitting.getFittingID()) ||
          !nextFitting.equivalent(existingFittingMap.get(nextFitting.getFittingID())))
        updates.add(nextFitting);
      seenFittings.add(next.getFittingId());

      for (GetCharactersCharacterIdFittingsItem item : next.getItems()) {
        FittingItem nextItem = new FittingItem(next.getFittingId(),
                                               item.getTypeId(),
                                               item.getFlag(),
                                               item.getQuantity());
        // Only update if there is a change to reduce DB contention
        if (!existingItemMap.containsKey(Pair.of(nextItem.getFittingID(), nextItem.getTypeID())) ||
            !nextItem.equivalent(existingItemMap.get(Pair.of(nextItem.getFittingID(), nextItem.getTypeID()))))
          updates.add(nextItem);
        seenItems.add(Pair.of(next.getFittingId(), item.getTypeId()));
      }
    }

    // Check for deleted fittings
    for (Fitting stored : existingFittingMap.values()) {
      if (!seenFittings.contains(stored.getFittingID())) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

    // Check for deleted items
    for (FittingItem stored : existingItemMap.values()) {
      if (!seenItems.contains(Pair.of(stored.getFittingID(), stored.getTypeID()))) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

  }

}
