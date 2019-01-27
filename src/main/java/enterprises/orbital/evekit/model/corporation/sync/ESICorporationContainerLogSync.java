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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICorporationContainerLogSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdContainersLogs200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationContainerLogSync.class.getName());

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
      final String errTrap = "Character does not have required role";
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        return new ESIAccountServerResult<>(expiry, Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdContainersLogs200Ok>> data,
                                   List<CachedData> updates) throws IOException {
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
      updates.add(nextLog);
    }
  }

}
