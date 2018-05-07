package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMining200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.MiningLedger;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterMiningLedgerSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdMining200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterMiningLedgerSync.class.getName());

  public ESICharacterMiningLedgerSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_MINING;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof MiningLedger;
    MiningLedger it = (MiningLedger) item;
    CachedData existing = MiningLedger.get(account, time, it.getDate(), it.getSolarSystemID(), it.getTypeID());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdMining200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    IndustryApi apiInstance = cp.getIndustryApi();

    Pair<Long, List<GetCharactersCharacterIdMining200Ok>> result = pagedResultRetriever(
        (page) -> {
          ESIThrottle.throttle(endpoint().name(), account);
          return apiInstance.getCharactersCharacterIdMiningWithHttpInfo(
              (int) account.getEveCharacterID(),
              null,
              null,
              page,
              accessToken(),
              null,
              null);
        });

    List<GetCharactersCharacterIdMining200Ok> results = result.getRight();
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();

    return new ESIAccountServerResult<>(expiry, results);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdMining200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    Map<Triple<Long, Integer, Integer>, MiningLedger> existingMLMap = CachedData.retrieveAll(time,
                                                                                             (contid, at) -> MiningLedger.accessQuery(
                                                                                                 account,
                                                                                                 contid, 1000,
                                                                                                 false, at,
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any(),
                                                                                                 AttributeSelector.any()))
                                                                                .stream()
                                                                                .map(x -> new AbstractMap.SimpleEntry<>(
                                                                                    Triple.of(x.getDate(),
                                                                                              x.getSolarSystemID(),
                                                                                              x.getTypeID()),
                                                                                    x))
                                                                                .collect(Collectors.toMap(
                                                                                    AbstractMap.SimpleEntry::getKey,
                                                                                    AbstractMap.SimpleEntry::getValue));

    Set<Triple<Long, Integer, Integer>> seenMLs = new HashSet<>();

    for (GetCharactersCharacterIdMining200Ok next : data.getData()) {
      MiningLedger nextML = new MiningLedger(next.getDate()
                                                 .toDate()
                                                 .getTime(),
                                             next.getSolarSystemId(),
                                             next.getTypeId(),
                                             next.getQuantity());
      Triple<Long, Integer, Integer> key = Triple.of(nextML.getDate(), nextML.getSolarSystemID(), nextML.getTypeID());
      // Only update if there is a change to reduce DB contention
      if (!existingMLMap.containsKey(key) ||
          !nextML.equivalent(existingMLMap.get(key)))
        updates.add(nextML);
      seenMLs.add(key);
    }

    // No need to check for deleted MLs.  The current day's ML may change, but they should never disappear.
    // They MAY fall off the update list after 30 days.
  }

}
