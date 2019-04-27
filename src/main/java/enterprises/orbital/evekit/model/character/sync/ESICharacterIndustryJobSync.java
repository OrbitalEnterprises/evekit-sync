package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdIndustryJobs200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.IndustryJob;
import org.joda.time.DateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterIndustryJobSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdIndustryJobs200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterIndustryJobSync.class.getName());

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

  public ESICharacterIndustryJobSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_INDUSTRY;
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
  protected ESIAccountServerResult<List<GetCharactersCharacterIdIndustryJobs200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    IndustryApi apiInstance = cp.getIndustryApi();

    // Check whether we have an ETAG to send for the skills call
    try {
      currentETag = getCurrentTracker().getContext();
    } catch (TrackerNotFoundException e) {
      currentETag = null;
    }

    try {
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCharactersCharacterIdIndustryJobs200Ok>> result = apiInstance.getCharactersCharacterIdIndustryJobsWithHttpInfo(
          (int) account.getEveCharacterID(), null, currentETag, true, accessToken());
      checkCommonProblems(result);
      cacheMiss();
      currentETag = extractETag(result, null);
      return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                          result.getData());
    } catch (ApiException e) {
      // Trap 304 which indicates there are no changes from the last call
      // Anything else is rethrown.
      if (e.getCode() != 304) throw e;
      cacheHit();
      currentETag = extractETag(e, null);
      return new ESIAccountServerResult<>(extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay()), null);
    }

  }

  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdIndustryJobs200Ok>> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData() == null)
      // Cache hit, no need to update
      return;

    // Add and record jobs
    for (GetCharactersCharacterIdIndustryJobs200Ok next : data.getData()) {
      IndustryJob nextJob = new IndustryJob(next.getJobId(),
                                            next.getInstallerId(),
                                            next.getFacilityId(),
                                            next.getStationId(),
                                            next.getActivityId(),
                                            next.getBlueprintId(),
                                            next.getBlueprintTypeId(),
                                            next.getBlueprintLocationId(),
                                            next.getOutputLocationId(),
                                            next.getRuns(),
                                            BigDecimal.valueOf(nullSafeDouble(next.getCost(), 0D)).setScale(2, RoundingMode.HALF_UP),
                                            nullSafeInteger(next.getLicensedRuns(), 0),
                                            nullSafeFloat(next.getProbability(), 0F),
                                            nullSafeInteger(next.getProductTypeId(), 0),
                                            next.getStatus().toString(),
                                            next.getDuration(),
                                            next.getStartDate().getMillis(),
                                            next.getEndDate().getMillis(),
                                            nullSafeDateTime(next.getPauseDate(), new DateTime(new Date(0L))).getMillis(),
                                            nullSafeDateTime(next.getCompletedDate(), new DateTime(new Date(0L))).getMillis(),
                                            nullSafeInteger(next.getCompletedCharacterId(), 0),
                                            nullSafeInteger(next.getSuccessfulRuns(), 0));
      updates.add(nextJob);
    }
  }


}
