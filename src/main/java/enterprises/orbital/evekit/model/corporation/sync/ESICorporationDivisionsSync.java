package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsHangarHangar;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsOk;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdDivisionsWalletWallet;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.Division;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ESICorporationDivisionsSync extends AbstractESIAccountSync<GetCorporationsCorporationIdDivisionsOk> {
  protected static final Logger log = Logger.getLogger(ESICorporationDivisionsSync.class.getName());

  private CachedCorporationDivisions cacheUpdate;

  public ESICorporationDivisionsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_DIVISIONS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Division;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = Division.get(account, time, ((Division) item).isWallet(), ((Division) item).getDivision());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<GetCorporationsCorporationIdDivisionsOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCorporationsCorporationIdDivisionsOk> result = apiInstance.getCorporationsCorporationIdDivisionsWithHttpInfo(
        (int) account.getEveCorporationID(),
        null,
        null,
        accessToken());
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCorporationsCorporationIdDivisionsOk> data,
                                   List<CachedData> updates) throws IOException {
    // Prepare lists of updates
    List<Division> hangarDivs = new ArrayList<>();
    List<Division> walletDivs = new ArrayList<>();

    for (GetCorporationsCorporationIdDivisionsHangarHangar next : data.getData()
                                                                      .getHangar()) {
      hangarDivs.add(new Division(false,
                                  nullSafeInteger(next.getDivision(), 0),
                                  next.getName()));
    }

    for (GetCorporationsCorporationIdDivisionsWalletWallet next : data.getData()
                                                                      .getWallet()) {
      walletDivs.add(new Division(true,
                                  nullSafeInteger(next.getDivision(), 0),
                                  next.getName()));
    }

    // Retrieve or construct cache
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CORP_DIVISIONS);
    cacheUpdate = ref != null ? (CachedCorporationDivisions) ref.get() : null;
    if (cacheUpdate == null) {
      // No cache yet, create one from stored data.
      cacheInit();
      cacheUpdate = new CachedCorporationDivisions();
      for (Division next : retrieveAll(time,
                                       (long contid, AttributeSelector at) -> Division.accessQuery(account, contid,
                                                                                                   1000,
                                                                                                   false, at,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR))) {
        if (next.isWallet()) {
          cacheUpdate.addWalletDivision(next);
        } else {
          cacheUpdate.addHangarDivision(next);
        }
      }
    }

    // Process hangar divisions
    for (Division next : hangarDivs) {
      if (cacheUpdate.cachedHangarDivisions.containsKey(next.getDivision())) {
        if (!cacheUpdate.cachedHangarDivisions.get(next.getDivision())
                                              .equivalent(next)) {
          // Data changed, add update and update cache
          cacheMiss();
          updates.add(next);
          cacheUpdate.addHangarDivision(next);
        } else {
          // Nothing changed, cached value is still correct
          cacheHit();
        }
      } else {
        // New, add it and save in cache
        cacheMiss();
        updates.add(next);
        cacheUpdate.addHangarDivision(next);
      }
    }

    // Process wallet divisions
    for (Division next : walletDivs) {
      if (cacheUpdate.cachedWalletDivisions.containsKey(next.getDivision())) {
        if (!cacheUpdate.cachedWalletDivisions.get(next.getDivision())
                                              .equivalent(next)) {
          // Data changed, add update and update cache
          cacheMiss();
          updates.add(next);
          cacheUpdate.addWalletDivision(next);
        } else {
          // Nothing changed, cached value is still correct
          cacheHit();
        }
      } else {
        // New, add it and save in cache
        cacheMiss();
        updates.add(next);
        cacheUpdate.addWalletDivision(next);
      }
    }

  }

  @Override
  protected void commitComplete() {
    // Update the character sheet cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CORP_DIVISIONS, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCorporationDivisions implements ModelCacheData {
    Map<Integer, Division> cachedWalletDivisions = new HashMap<>();
    Map<Integer, Division> cachedHangarDivisions = new HashMap<>();

    void addWalletDivision(Division s) {
      cachedWalletDivisions.put(s.getDivision(), s);
    }

    void addHangarDivision(Division s) {
      cachedHangarDivisions.put(s.getDivision(), s);
    }

  }

}
