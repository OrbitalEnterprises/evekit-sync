package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionTransactions200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletTransaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Logger;

public class ESICorporationWalletTransactionSync extends AbstractESIAccountSync<ESICorporationWalletTransactionSync.CorpWalletTransaction> {
  protected static final Logger log = Logger.getLogger(ESICorporationWalletTransactionSync.class.getName());
  private String context;

  public ESICorporationWalletTransactionSync(SynchronizedEveAccount account) {
    super(account);
  }

  class CorpWalletTransaction {
    Map<Integer, List<GetCorporationsCorporationIdWalletsDivisionTransactions200Ok>> txns = new HashMap<>();

    void ensureDivision(int division) {
      if (!this.txns.containsKey(division))
        this.txns.put(division, new ArrayList<>());
    }

    void appendDivision(int division, List<GetCorporationsCorporationIdWalletsDivisionTransactions200Ok> txns) {
      ensureDivision(division);
      this.txns.get(division)
               .addAll(txns);
    }
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_WALLET_TRANSACTIONS;
  }

  @Override
  protected String getNextSyncContext() {
    return context;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof WalletTransaction;
    WalletTransaction api = (WalletTransaction) item;

    if (WalletTransaction.get(account, time, api.getDivision(), api.getTransactionID()) != null) {
      // Item already exists. We don't need to check if it's changed because transactions are immutable.
      return;
    }

    // Otherwise, create entry
    evolveOrAdd(time, null, item);
  }

  @Override
  protected ESIAccountServerResult<CorpWalletTransaction> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    CorpWalletTransaction resultObject = new CorpWalletTransaction();
    long expiry = 0;

    for (int division = 1; division <= 7; division++) {
      long txnIdLimit = Long.MAX_VALUE;
      resultObject.ensureDivision(division);

      // Retrieve initial batch
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCorporationsCorporationIdWalletsDivisionTransactions200Ok>> result =
          apiInstance.getCorporationsCorporationIdWalletsDivisionTransactionsWithHttpInfo(
              (int) account.getEveCorporationID(),
              division,
              null,
              txnIdLimit,
              null,
              accessToken());
      checkCommonProblems(result);
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

      // Crawl transactions backwards until no more entries are retrieved
      while (!result.getData()
                    .isEmpty()) {
        resultObject.appendDivision(division, result.getData());
        //noinspection ConstantConditions
        txnIdLimit = result.getData()
                           .stream()
                           .min(Comparator.comparingLong(
                               GetCorporationsCorporationIdWalletsDivisionTransactions200Ok::getTransactionId))
                           .get()
                           .getTransactionId();
        ESIThrottle.throttle(endpoint().name(), account);
        result = apiInstance.getCorporationsCorporationIdWalletsDivisionTransactionsWithHttpInfo(
            (int) account.getEveCorporationID(),
            division,
            null,
            txnIdLimit,
            null,
            accessToken());
        checkCommonProblems(result);
        expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

        // Workaround for https://github.com/ccpgames/esi-issues/issues/715
        if (!result.getData()
                   .isEmpty()) {
          // Check whether min transaction ID is less than previous transaction ID.  If it's not
          // then we're seeing the bug and we need to empty the result set.
          @SuppressWarnings("ConstantConditions") long testLimit = result.getData()
                                                                         .stream()
                                                                         .min(Comparator.comparingLong(
                                     GetCorporationsCorporationIdWalletsDivisionTransactions200Ok::getTransactionId))
                                                                         .get()
                                                                         .getTransactionId();
          if (testLimit >= txnIdLimit) result.getData()
                                             .clear();
        }
      }

      // Sort division by transaction ID so we insert into the DB in order
      resultObject.txns.get(division)
                       .sort(Comparator.comparingLong(
                           GetCorporationsCorporationIdWalletsDivisionTransactions200Ok::getTransactionId));
    }

    return new ESIAccountServerResult<>(expiry, resultObject);
  }

  @SuppressWarnings({"RedundantThrows", "Duplicates"})
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<CorpWalletTransaction> data,
                                   List<CachedData> updates) throws IOException {
    // Check for existing tracker context.  If exists, this will be a txnID upper bound.  We can skip
    // enqueuing updates for any item with a txnID less than this bound.  Note that we require a
    // separate bound for each division.
    long[] txnIDBound = new long[7];
    long[] newTxnBound = new long[7];
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
      txnIDBound[i] = storedBound[i];
      newTxnBound[i] = Long.MIN_VALUE;
    }


    for (int division = 1; division <= 7; division++) {
      for (GetCorporationsCorporationIdWalletsDivisionTransactions200Ok next : data.getData().txns.get(division)) {
        // Items below the bound have already been processed
        if (next.getTransactionId() <= txnIDBound[division - 1])
          continue;

        updates.add(new WalletTransaction(division, next.getTransactionId(),
                                          next.getDate()
                                              .getMillis(),
                                          next.getQuantity(),
                                          next.getTypeId(),
                                          BigDecimal.valueOf(next.getUnitPrice())
                                                    .setScale(2, RoundingMode.HALF_UP),
                                          next.getClientId(),
                                          next.getLocationId(),
                                          next.getIsBuy(),
                                          false,
                                          next.getJournalRefId()));

        // Update the new bound for next sync
        newTxnBound[division - 1] = Math.max(newTxnBound[division - 1], next.getTransactionId());
      }
    }

    // Set next context to new ref bound
    StringBuilder contextString = new StringBuilder();
    for (long n : newTxnBound)
      contextString.append(n)
                   .append(",");
    contextString.setLength(contextString.length() - 1);
    context = contextString.toString();
  }


}
