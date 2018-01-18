package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdWalletTransactions200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletTransaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterWalletTransactionSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdWalletTransactions200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterWalletTransactionSync.class.getName());

  public ESICharacterWalletTransactionSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_WALLET_TRANSACTIONS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof WalletTransaction;
    WalletTransaction api = (WalletTransaction) item;

    if (WalletTransaction.get(account, time, api.getDivision(), api.getTransactionID()) != null) {
      // Item already exists. We don't need to check if it's changed because transaction entries are immutable.
      return;
    }

    // Otherwise, create entry
    evolveOrAdd(time, null, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdWalletTransactions200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    List<GetCharactersCharacterIdWalletTransactions200Ok> results = new ArrayList<>();
    long txnIdLimit = Long.MAX_VALUE;

    // Retrieve initial batch
    ApiResponse<List<GetCharactersCharacterIdWalletTransactions200Ok>> result = apiInstance.getCharactersCharacterIdWalletTransactionsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        txnIdLimit,
        accessToken(),
        null,
        null);
    checkCommonProblems(result);
    long expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

    // Crawl transactions backwards until no more entries are retrieved
    while (!result.getData()
                  .isEmpty()) {
      results.addAll(result.getData());
      txnIdLimit = result.getData()
                         .stream()
                         .min(Comparator.comparingLong(
                             GetCharactersCharacterIdWalletTransactions200Ok::getTransactionId))
                         .get()
                         .getTransactionId();
      result = apiInstance.getCharactersCharacterIdWalletTransactionsWithHttpInfo((int) account.getEveCharacterID(),
                                                                                  null,
                                                                                  txnIdLimit,
                                                                                  accessToken(),
                                                                                  null,
                                                                                  null);
      checkCommonProblems(result);
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

      // Workaround for https://github.com/ccpgames/esi-issues/issues/715
      if (!result.getData().isEmpty()) {
        // Check whether min transaction ID is less than previous transaction ID.  If it's not
        // then we're seeing the bug and we need to empty the result set.
        long testLimit = result.getData()
                               .stream()
                               .min(Comparator.comparingLong(
                                   GetCharactersCharacterIdWalletTransactions200Ok::getTransactionId))
                               .get()
                               .getTransactionId();
        if (testLimit >= txnIdLimit) result.getData().clear();
      }
    }

    // Sort results by transaction ID so we insert into the DB in order
    results.sort(Comparator.comparingLong(GetCharactersCharacterIdWalletTransactions200Ok::getTransactionId));
    
    return new ESIAccountServerResult<>(expiry, results);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdWalletTransactions200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    for (GetCharactersCharacterIdWalletTransactions200Ok next : data.getData()) {
      updates.add(new WalletTransaction(1, next.getTransactionId(),
                                        next.getDate()
                                            .getMillis(),
                                        next.getQuantity(),
                                        next.getTypeId(),
                                        BigDecimal.valueOf(next.getUnitPrice())
                                                  .setScale(2, RoundingMode.HALF_UP),
                                        next.getClientId(),
                                        next.getLocationId(),
                                        next.getIsBuy(),
                                        next.getIsPersonal(),
                                        next.getJournalRefId()));
    }
  }


}