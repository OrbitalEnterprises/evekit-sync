package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CorporationApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMedals200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdMedalsIssued200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.CorporationMedal;
import enterprises.orbital.evekit.model.corporation.CorporationMemberMedal;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICorporationMedalsSync extends AbstractESIAccountSync<ESICorporationMedalsSync.MedalsData> {
  protected static final Logger log = Logger.getLogger(ESICorporationMedalsSync.class.getName());

  class MedalsData {
    List<GetCorporationsCorporationIdMedals200Ok> medals;
    List<GetCorporationsCorporationIdMedalsIssued200Ok> issued;
  }

  public ESICorporationMedalsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_MEDALS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CorporationMedal) || (item instanceof CorporationMemberMedal);
    CachedData existing = null;

    if (item instanceof CorporationMedal && item.getLifeStart() == 0) {
      // Only CorporationMedals can be modified.  If this is an update, then check
      // for an existing CorporationMedal.
      existing = CorporationMedal.get(account, time, ((CorporationMedal) item).getMedalID());
    } else if (item instanceof CorporationMemberMedal) {
      // CorporationMemberMedals can NOT be modified.  If this awarded medal already exists,
      // then we can skip this update.  Otherwise, we're creating the awarded medal for the
      // first time.
      CorporationMemberMedal m = (CorporationMemberMedal) item;
      if (CorporationMemberMedal.get(account, time, m.getMedalID(), m.getCharacterID(), m.getIssued()) != null)
        return;
    }

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<MedalsData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    MedalsData data = new MedalsData();
    CorporationApi apiInstance = cp.getCorporationApi();

    Pair<Long, List<GetCorporationsCorporationIdMedals200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdMedalsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          page,
          accessToken());
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.medals = result.getRight();

    long bkExpiry;
    try {
      Pair<Long, List<GetCorporationsCorporationIdMedalsIssued200Ok>> bkResult = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdMedalsIssuedWithHttpInfo(
            (int) account.getEveCorporationID(),
            null,
            null,
            page,
            accessToken());
      });
      bkExpiry = bkResult.getLeft() > 0 ? bkResult.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
      data.issued = bkResult.getRight();
    } catch (ApiException e) {
      final String errTrap = "Character does not have required role";
      if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                .contains(errTrap)) {
        // Trap 403 - Character does not have required role(s)
        log.info("Trapped 403 - Character does not have required role");
        bkExpiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        data.issued = Collections.emptyList();
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + e.getResponseBody());
        }
        throw e;
      }
    }

    return new ESIAccountServerResult<>(Math.max(expiry, bkExpiry), data);
  }

  @SuppressWarnings({"Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<MedalsData> data,
                                   List<CachedData> updates) throws IOException {

    // Track seen medals.  Issued medals can't be removed, so no point in tracking those.
    Set<Integer> seenMedals = new HashSet<>();

    for (GetCorporationsCorporationIdMedals200Ok next : data.getData().medals) {
      updates.add(new CorporationMedal(next.getMedalId(),
                                       next.getDescription(),
                                       next.getTitle(),
                                       next.getCreatedAt()
                                           .getMillis(),
                                       next.getCreatorId()));
      seenMedals.add(next.getMedalId());
    }

    for (GetCorporationsCorporationIdMedalsIssued200Ok next : data.getData().issued) {
      updates.add(new CorporationMemberMedal(next.getMedalId(),
                                             next.getCharacterId(),
                                             next.getIssuedAt()
                                                 .getMillis(),
                                             next.getIssuerId(),
                                             next.getReason(),
                                             next.getStatus()
                                                 .toString()));
    }

    // Check for medals that no longer exist and schedule for EOL
    for (CorporationMedal existing : retrieveAll(time,
                                                 (long contid, AttributeSelector at) -> CorporationMedal.accessQuery(
                                                     account, contid,
                                                     1000,
                                                     false, at,
                                                     ANY_SELECTOR,
                                                     ANY_SELECTOR,
                                                     ANY_SELECTOR,
                                                     ANY_SELECTOR,
                                                     ANY_SELECTOR))) {
      if (!seenMedals.contains(existing.getMedalID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
