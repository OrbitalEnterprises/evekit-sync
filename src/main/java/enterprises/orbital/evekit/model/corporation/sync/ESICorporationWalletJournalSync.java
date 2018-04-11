package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionJournal200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionJournalExtraInfo;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletJournal;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;

public class ESICorporationWalletJournalSync extends AbstractESIAccountSync<ESICorporationWalletJournalSync.CorpWalletJournal> {
  protected static final Logger log = Logger.getLogger(ESICorporationWalletJournalSync.class.getName());
  private String context;

  public ESICorporationWalletJournalSync(SynchronizedEveAccount account) {
    super(account);
  }

  class CorpWalletJournal {
    Map<Integer, List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>> journals = new HashMap<>();

    void ensureDivision(int division) {
      if (!journals.containsKey(division))
        journals.put(division, new ArrayList<>());
    }

    void appendDivision(int division, List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok> journal) {
      ensureDivision(division);
      journals.get(division)
              .addAll(journal);
    }
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
  protected ESIAccountServerResult<CorpWalletJournal> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    CorpWalletJournal resultObject = new CorpWalletJournal();
    long expiry = 0;

    for (int division = 1; division <= 7; division++) {
      long refIdLimit = Long.MAX_VALUE;
      resultObject.ensureDivision(division);
      
      // Retrieve initial batch
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>> result =
          apiInstance.getCorporationsCorporationIdWalletsDivisionJournalWithHttpInfo(
              (int) account.getEveCorporationID(),
              division,
              null,
              refIdLimit,
              accessToken(),
              null,
              null);
      checkCommonProblems(result);
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

      // Crawl the journal backwards until no more entries are retrieved
      while (!result.getData()
                    .isEmpty()) {
        resultObject.appendDivision(division, result.getData());
        //noinspection ConstantConditions
        refIdLimit = result.getData()
                           .stream()
                           .min(Comparator.comparingLong(
                               GetCorporationsCorporationIdWalletsDivisionJournal200Ok::getRefId))
                           .get()
                           .getRefId();
        ESIThrottle.throttle(endpoint().name(), account);
        result = apiInstance.getCorporationsCorporationIdWalletsDivisionJournalWithHttpInfo(
            (int) account.getEveCorporationID(),
            division,
            null,
            refIdLimit,
            accessToken(),
            null,
            null);
        checkCommonProblems(result);
        expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());
      }

      // Sort division by refID so we insert into the DB in order
      resultObject.journals.get(division).sort(Comparator.comparingLong(GetCorporationsCorporationIdWalletsDivisionJournal200Ok::getRefId));
    }

    return new ESIAccountServerResult<>(expiry, resultObject);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<CorpWalletJournal> data,
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
      for (GetCorporationsCorporationIdWalletsDivisionJournal200Ok next : data.getData().journals.get(division)) {
        // Items below the bound have already been processed
        if (next.getRefId() <= refIDBound[division - 1])
          continue;

        GetCorporationsCorporationIdWalletsDivisionJournalExtraInfo extra = next.getExtraInfo();
        updates.add(new WalletJournal(division, next.getRefId(),
                                      next.getDate()
                                          .getMillis(),
                                      nullSafeEnum(next.getRefType(), null),
                                      nullSafeInteger(next.getFirstPartyId(), 0),
                                      nullSafeEnum(next.getFirstPartyType(), null),
                                      nullSafeInteger(next.getSecondPartyId(), 0),
                                      nullSafeEnum(next.getSecondPartyType(), null),
                                      null, 0,
                                      BigDecimal.valueOf(nullSafeDouble(next.getAmount(), 0D))
                                                .setScale(2, RoundingMode.HALF_UP),
                                      BigDecimal.valueOf(nullSafeDouble(next.getBalance(), 0D))
                                                .setScale(2, RoundingMode.HALF_UP),
                                      next.getReason(),
                                      nullSafeInteger(next.getTaxReceiverId(), 0),
                                      BigDecimal.valueOf(nullSafeDouble(next.getTax(), 0D))
                                                .setScale(2, RoundingMode.HALF_UP),
                                      extra != null ? nullSafeLong(extra.getLocationId(), 0L) : 0,
                                      extra != null ? nullSafeLong(extra.getTransactionId(), 0L) : 0,
                                      extra != null ? extra.getNpcName() : null,
                                      extra != null ? nullSafeInteger(extra.getNpcId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getDestroyedShipTypeId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getCharacterId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getCorporationId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getAllianceId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getJobId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getContractId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getSystemId(), 0) : 0,
                                      extra != null ? nullSafeInteger(extra.getPlanetId(), 0) : 0));

        // Update the new bound for next sync
        newRefBound[division - 1] = Math.max(newRefBound[division - 1], next.getRefId());
      }
    }

    // Set next context to new ref bound
    StringBuilder contextString = new StringBuilder();
    for (long n : newRefBound)
      contextString.append(n).append(",");
    contextString.setLength(contextString.length() - 1);
    context = contextString.toString();
  }


}
