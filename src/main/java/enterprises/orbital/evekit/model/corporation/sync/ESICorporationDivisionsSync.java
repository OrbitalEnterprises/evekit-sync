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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICorporationDivisionsSync extends AbstractESIAccountSync<GetCorporationsCorporationIdDivisionsOk> {
  protected static final Logger log = Logger.getLogger(ESICorporationDivisionsSync.class.getName());

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

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<GetCorporationsCorporationIdDivisionsOk> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();

    // Check whether we have an ETAG to send for the skills call
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    try {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<GetCorporationsCorporationIdDivisionsOk> result = apiInstance.getCorporationsCorporationIdDivisionsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          currentETag,
          accessToken());
      checkCommonProblems(result);
      cacheMiss();
      currentETag = extractETag(result, null);
      return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                          result.getData());
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        cacheHit();
        return new ESIAccountServerResult<>(expiry, null);
      } else if (e.getCode() == 304) {
        // ETag hit
        cacheHit();
        currentETag = extractETag(e, null);
        return new ESIAccountServerResult<>(extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay()), null);
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }
  }

  @Override
  protected void processServerData(long time, ESIAccountServerResult<GetCorporationsCorporationIdDivisionsOk> data,
                                   List<CachedData> updates) throws IOException {
    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    for (GetCorporationsCorporationIdDivisionsHangarHangar next : data.getData()
                                                                      .getHangar()) {
      updates.add(new Division(false,
                               nullSafeInteger(next.getDivision(), 0),
                               next.getName()));
    }

    for (GetCorporationsCorporationIdDivisionsWalletWallet next : data.getData()
                                                                      .getWallet()) {
      updates.add(new Division(true,
                               nullSafeInteger(next.getDivision(), 0),
                               next.getName()));
    }
  }

}
