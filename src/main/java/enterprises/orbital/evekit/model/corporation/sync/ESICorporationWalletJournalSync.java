package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionJournal200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletJournal;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICorporationWalletJournalSync extends AbstractESIAccountSync<Map<Integer, List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>>> {
  protected static final Logger log = Logger.getLogger(ESICorporationWalletJournalSync.class.getName());
  private String context;

  public ESICorporationWalletJournalSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_WALLET_JOURNAL;
  }

  @Override
  protected String getNextSyncContext() {
    return context;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof WalletJournal;
    WalletJournal api = (WalletJournal) item;

    if (WalletJournal.get(account, time, api.getDivision(), api.getRefID()) != null) {
      // Item already exists. We don't need to check if it's changed because journal entries are immutable.
      return;
    }

    // Otherwise, create entry
    evolveOrAdd(time, null, item);
  }

  @Override
  protected ESIAccountServerResult<Map<Integer, List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    Map<Integer, List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>> resultMap = new HashMap<>();
    long expiry = 0;

    for (int division = 1; division <= 7; division++) {
      final int nextDivision = division;
      Pair<Long, List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>> result;
      try {
        result = pagedResultRetriever(
            (page) -> {
              ESIThrottle.throttle(endpoint().name(), account);
              return apiInstance.getCorporationsCorporationIdWalletsDivisionJournalWithHttpInfo(
                  (int) account.getEveCorporationID(),
                  nextDivision,
                  null,
                  null,
                  page,
                  accessToken());
            });
      } catch (ApiException e) {
        final String errTrap = "Character does not have required role";
        if (e.getCode() == 403 && e.getResponseBody() != null && e.getResponseBody()
                                                                  .contains(errTrap)) {
          // Trap 403 - Character does not have required role(s)
          log.info("Trapped 403 - Character does not have required role");
          result = Pair.of(OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS),
                           Collections.emptyList());
        } else {
          // Any other error will be rethrown.
          // Document other 403 error response bodies in case we should add these in the future.
          if (e.getCode() == 403) {
            log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
          }
          throw e;
        }
      }
      resultMap.put(division, result.getRight());
      expiry = Math.max(expiry,
                        result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay());

      // Sort division by refID so we insert into the DB in order
      resultMap.get(division)
               .sort(Comparator.comparingLong(GetCorporationsCorporationIdWalletsDivisionJournal200Ok::getId));
    }

    return new ESIAccountServerResult<>(expiry, resultMap);
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<Map<Integer, List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>>> data,
                                   List<CachedData> updates) throws IOException {
    // Check for existing tracker context.  If exists, this will be a refID upper bound.  We can skip
    // enqueuing updates for any item with a refID less than this bound.  Note that we require a
    // separate bound for each division.
    long[] refIDBound = new long[7];
    long[] newRefBound = new long[7];
    long[] storedBound = new long[7];
    try {
      for (int i = 0; i < 7; i++) storedBound[i] = Long.MIN_VALUE;
      String oldContext = getCurrentTracker().getContext();
      if (oldContext != null) {
        String[] stored = oldContext.split(",");
        for (int i = 0; i < 7 && i < stored.length; i++) {
          try {
            storedBound[i] = Long.valueOf(stored[i]);
          } catch (Exception f) {
            // Couldn't convert value, skip
          }
        }
      }
    } catch (Exception e) {
      // Ignore, no previous bound could be retrieved
    }
    for (int i = 0; i < 7; i++) {
      refIDBound[i] = storedBound[i];
      newRefBound[i] = Long.MIN_VALUE;
    }

    for (int division = 1; division <= 7; division++) {
      for (GetCorporationsCorporationIdWalletsDivisionJournal200Ok next : data.getData()
                                                                              .get(division)) {
        // Items below the bound have already been processed
        if (next.getId() <= refIDBound[division - 1])
          continue;

        updates.add(new WalletJournal(division,
                                      next.getId(),
                                      next.getDate()
                                          .getMillis(),
                                      nullSafeEnum(next.getRefType(), null),
                                      nullSafeInteger(next.getFirstPartyId(), 0),
                                      nullSafeInteger(next.getSecondPartyId(), 0),
                                      null, 0,
                                      BigDecimal.valueOf(nullSafeDouble(next.getAmount(), 0D))
                                                .setScale(2, RoundingMode.HALF_UP),
                                      BigDecimal.valueOf(nullSafeDouble(next.getBalance(), 0D))
                                                .setScale(2, RoundingMode.HALF_UP),
                                      next.getReason(),
                                      nullSafeInteger(next.getTaxReceiverId(), 0),
                                      BigDecimal.valueOf(nullSafeDouble(next.getTax(), 0D))
                                                .setScale(2, RoundingMode.HALF_UP),
                                      nullSafeLong(next.getContextId(), 0L),
                                      nullSafeEnum(next.getContextIdType(), null),
                                      next.getDescription()));

        // Update the new bound for next sync
        newRefBound[division - 1] = Math.max(newRefBound[division - 1], next.getId());
      }
    }

    // Set next context to new ref bound
    StringBuilder contextString = new StringBuilder();
    for (long n : newRefBound)
      contextString.append(n)
                   .append(",");
    contextString.setLength(contextString.length() - 1);
    context = contextString.toString();
  }


}
