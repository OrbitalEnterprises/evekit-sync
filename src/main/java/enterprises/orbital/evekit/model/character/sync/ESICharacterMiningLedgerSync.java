package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdMining200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.MiningLedger;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterMiningLedgerSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdMining200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterMiningLedgerSync.class.getName());

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
    if (existing != null && it.equivalent(existing))
      // No change, skip update.
      return;
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
              accessToken());
        });

    List<GetCharactersCharacterIdMining200Ok> results = result.getRight();
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();

    return new ESIAccountServerResult<>(expiry, results);
  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdMining200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(1);

    List<MiningLedger> retrieved = new ArrayList<>();
    for (GetCharactersCharacterIdMining200Ok next : data.getData()) {
      MiningLedger nextML = new MiningLedger(next.getDate()
                                                 .toDate()
                                                 .getTime(),
                                             next.getSolarSystemId(),
                                             next.getTypeId(),
                                             next.getQuantity());
      retrieved.add(nextML);
    }
    retrieved.sort((o1, o2) -> {
      int did = Comparator.comparingLong(MiningLedger::getDate).compare(o1, o2);
      if (did != 0) return did;
      did = Comparator.comparingInt(MiningLedger::getSolarSystemID).compare(o1, o2);
      if (did != 0) return did;
      return Comparator.comparingInt(MiningLedger::getTypeID).compare(o1, o2);
    });
    String hash = CachedData.dataHashHelper(retrieved.toArray());

    if (cachedHash[0] == null || !cachedHash[0].equals(hash)) {
      cacheMiss();
      cachedHash[0] = hash;
      updates.addAll(retrieved);
    } else {
      cacheHit();
    }

    // Save hashes for next execution
    currentETag = String.join("|", cachedHash);

    // No need to check for deleted MLs.  The current day's ML may change, but they should never disappear.
    // They MAY fall off the update list after 30 days.
  }

}
