package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdIndustryJobs200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.IndustryJob;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICorporationIndustryJobSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdIndustryJobs200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationIndustryJobSync.class.getName());

  public ESICorporationIndustryJobSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_INDUSTRY;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof IndustryJob;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = IndustryJob.get(account, time, ((IndustryJob) item).getJobID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdIndustryJobs200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    IndustryApi apiInstance = cp.getIndustryApi();
    Pair<Long, List<GetCorporationsCorporationIdIndustryJobs200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdIndustryJobsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          true,
          page,
          accessToken());
    });
    return new ESIAccountServerResult<>(
        result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay(),
        result.getRight());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdIndustryJobs200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Add and record orders
    for (GetCorporationsCorporationIdIndustryJobs200Ok next : data.getData()) {
      IndustryJob nextJob = new IndustryJob(next.getJobId(),
                                            next.getInstallerId(),
                                            next.getFacilityId(),
                                            next.getLocationId(),
                                            next.getActivityId(),
                                            next.getBlueprintId(),
                                            next.getBlueprintTypeId(),
                                            next.getBlueprintLocationId(),
                                            next.getOutputLocationId(),
                                            next.getRuns(),
                                            BigDecimal.valueOf(nullSafeDouble(next.getCost(), 0D))
                                                      .setScale(2, RoundingMode.HALF_UP),
                                            nullSafeInteger(next.getLicensedRuns(), 0),
                                            nullSafeFloat(next.getProbability(), 0F),
                                            nullSafeInteger(next.getProductTypeId(), 0),
                                            next.getStatus()
                                                .toString(),
                                            next.getDuration(),
                                            next.getStartDate()
                                                .getMillis(),
                                            next.getEndDate()
                                                .getMillis(),
                                            nullSafeDateTime(next.getPauseDate(),
                                                             new DateTime(new Date(0L))).getMillis(),
                                            nullSafeDateTime(next.getCompletedDate(),
                                                             new DateTime(new Date(0L))).getMillis(),
                                            nullSafeInteger(next.getCompletedCharacterId(), 0),
                                            nullSafeInteger(next.getSuccessfulRuns(), 0));
      updates.add(nextJob);
    }
  }


}
