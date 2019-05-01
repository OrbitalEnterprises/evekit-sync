package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContainersLogs200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.ContainerLog;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICorporationContainerLogSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdContainersLogs200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationContainerLogSync.class.getName());

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

  public ESICorporationContainerLogSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_CONTAINER_LOGS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof ContainerLog;
    ContainerLog api = (ContainerLog) item;

    if (ContainerLog.get(account, time, api.getContainerID(), api.getLogTime()) != null) {
      // Item already exists. We don't need to check if it's changed because container log entries are immutable.
      return;
    }

    // Otherwise, create entry
    evolveOrAdd(time, null, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdContainersLogs200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CorporationApi apiInstance = cp.getCorporationApi();
    try {
      Pair<Long, List<GetCorporationsCorporationIdContainersLogs200Ok>> result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdContainersLogsWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
      return new ESIAccountServerResult<>(
          result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay(),
          result.getRight());
    } catch (ApiException e) {
      if (e.getCode() == 403) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        cacheHit();
        return new ESIAccountServerResult<>(expiry, null);
      } else {
        // Any other error will be rethrown.
        throw e;
      }
    }
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdContainersLogs200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    if (data.getData() == null)
      // 403 so nothing to do
      return;

    // Compute and check hash

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(1);

    List<ContainerLog> retrievedLogs = new ArrayList<>();

    // Add container logs
    for (GetCorporationsCorporationIdContainersLogs200Ok next : data.getData()) {
      ContainerLog nextLog = new ContainerLog(next.getLoggedAt()
                                                  .getMillis(),
                                              next.getAction()
                                                  .toString(),
                                              next.getCharacterId(),
                                              next.getLocationFlag()
                                                  .toString(),
                                              next.getContainerId(),
                                              next.getContainerTypeId(),
                                              next.getLocationId(),
                                              nullSafeInteger(next.getNewConfigBitmask(), 0),
                                              nullSafeInteger(next.getOldConfigBitmask(), 0),
                                              next.getPasswordType() == null ? null : next.getPasswordType()
                                                                                          .toString(),
                                              nullSafeInteger(next.getQuantity(), 0),
                                              nullSafeInteger(next.getTypeId(), 0));
      retrievedLogs.add(nextLog);
    }
    retrievedLogs.sort(Comparator.comparingLong(ContainerLog::getLogTime));
    String hash = CachedData.dataHashHelper(retrievedLogs.toArray());

    if (cachedHash[0] == null || !cachedHash[0].equals(hash)) {
      cacheMiss();
      cachedHash[0] = hash;
      updates.addAll(retrievedLogs);
    } else {
      cacheHit();
    }

    // Save new hash
    currentETag = String.join("|", cachedHash);
  }

}
