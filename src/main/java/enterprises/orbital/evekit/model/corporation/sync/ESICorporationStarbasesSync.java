package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStarbases200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStarbasesStarbaseIdFuel;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdStarbasesStarbaseIdOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Fuel;
import enterprises.orbital.evekit.model.corporation.Starbase;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationStarbasesSync extends AbstractESIAccountSync<ESICorporationStarbasesSync.StarbaseData> {
  protected static final Logger log = Logger.getLogger(ESICorporationStarbasesSync.class.getName());

  class StarbaseData {
    List<GetCorporationsCorporationIdStarbases200Ok> bases;
    Map<Long, GetCorporationsCorporationIdStarbasesStarbaseIdOk> baseInfo = new HashMap<>();
  }

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  public ESICorporationStarbasesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_STARBASES;
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof Starbase) || (item instanceof Fuel);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof Starbase)
        existing = Starbase.get(account, time, ((Starbase) item).getStarbaseID());
      else
        existing = Fuel.get(account, time, ((Fuel) item).getStarbaseID(), ((Fuel) item).getTypeID());
    }
    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<StarbaseData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    StarbaseData resultData = new StarbaseData();
    CorporationApi apiInstance = cp.getCorporationApi();
    Pair<Long, List<GetCorporationsCorporationIdStarbases200Ok>> result;

    try {
      // Retrieve bases info
      result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdStarbasesWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        cacheHit();
        result = Pair.of(expiry, null);
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();

    // Retrieve base info for each base
    resultData.bases = result.getRight();
    if (resultData.bases != null) {
      for (GetCorporationsCorporationIdStarbases200Ok nextBase : resultData.bases) {
        ESIThrottle.throttle(endpoint().name(), account);
        ApiResponse<GetCorporationsCorporationIdStarbasesStarbaseIdOk> item = apiInstance.getCorporationsCorporationIdStarbasesStarbaseIdWithHttpInfo(
            (int) account.getEveCorporationID(),
            nextBase.getStarbaseId(),
            nextBase.getSystemId(),
            null,
            null,
            accessToken());
        checkCommonProblems(item);
        resultData.baseInfo.put(nextBase.getStarbaseId(), item.getData());
        expiry = Math.max(expiry, extractExpiry(item, OrbitalProperties.getCurrentTime() + maxDelay()));
      }
    }
    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<StarbaseData> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData().bases == null)
      // 403 - nothing to do
      return;

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(1);

    // Compute and check hash
    List<Starbase> retrievedBases = new ArrayList<>();
    List<Fuel> retrievedFuel = new ArrayList<>();

    for (GetCorporationsCorporationIdStarbases200Ok nextBase : data.getData().bases) {
      GetCorporationsCorporationIdStarbasesStarbaseIdOk info = data.getData().baseInfo.get(nextBase.getStarbaseId());
      retrievedBases.add(new Starbase(nextBase.getStarbaseId(),
                               nextBase.getTypeId(),
                               nextBase.getSystemId(),
                               nullSafeInteger(nextBase.getMoonId(), 0),
                               nullSafeEnum(nextBase.getState(), null),
                               nullSafeDateTime(nextBase.getUnanchorAt(), new DateTime(new Date(0L))).getMillis(),
                               nullSafeDateTime(nextBase.getReinforcedUntil(), new DateTime(new Date(0L))).getMillis(),
                               nullSafeDateTime(nextBase.getOnlinedSince(), new DateTime(new Date(0L))).getMillis(),
                               info.getFuelBayView()
                                   .toString(),
                               info.getFuelBayTake()
                                   .toString(),
                               info.getAnchor()
                                   .toString(),
                               info.getUnanchor()
                                   .toString(),
                               info.getOnline()
                                   .toString(),
                               info.getOffline()
                                   .toString(),
                               info.getAllowCorporationMembers(),
                               info.getAllowAllianceMembers(),
                               info.getUseAllianceStandings(),
                               nullSafeFloat(info.getAttackStandingThreshold(), 0),
                               nullSafeFloat(info.getAttackSecurityStatusThreshold(), 0),
                               info.getAttackIfOtherSecurityStatusDropping(),
                               info.getAttackIfAtWar()));
      for (GetCorporationsCorporationIdStarbasesStarbaseIdFuel f : info.getFuels()) {
        retrievedFuel.add(new Fuel(nextBase.getStarbaseId(),
                             f.getTypeId(),
                             f.getQuantity()));
      }
    }

    String hash = CachedData.dataHashHelper(CachedData.dataHashHelper(retrievedBases.toArray()),
                                            CachedData.dataHashHelper(retrievedFuel.toArray()));

    if (cachedHash[0] == null || !cachedHash[0].equals(hash)) {
      cacheMiss();
      cachedHash[0] = hash;

      // Add bases and fuel
      updates.addAll(retrievedBases);
      updates.addAll(retrievedFuel);
      Set<Long> seenBases = retrievedBases.stream().map(Starbase::getStarbaseID).collect(Collectors.toSet());
      Set<Pair<Long, Integer>> seenFuels = retrievedFuel.stream()
                                                        .map(x -> Pair.of(x.getStarbaseID(), x.getTypeID()))
                                                        .collect(Collectors.toSet());

      // Clean up removed starbases and fuels
      for (Starbase s : CachedData.retrieveAll(time,
                                               (contid, at) -> Starbase.accessQuery(account, contid, 1000, false, at,
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any(),
                                                                                    AttributeSelector.any()))) {
        if (!seenBases.contains(s.getStarbaseID())) {
          s.evolve(null, time);
          updates.add(s);
        }
      }
      for (Fuel f : CachedData.retrieveAll(time, (contid, at) -> Fuel.accessQuery(account, contid, 1000, false, at,
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any(),
                                                                                  AttributeSelector.any()))) {
        if (!seenFuels.contains(Pair.of(f.getStarbaseID(), f.getTypeID()))) {
          f.evolve(null, time);
          updates.add(f);
        }
      }
    } else {
      cacheHit();
    }

    // Save new hash
    currentETag = String.join("|", cachedHash);
  }

}
