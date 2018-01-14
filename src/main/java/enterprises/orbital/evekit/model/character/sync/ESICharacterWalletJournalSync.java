package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdWalletJournal200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdWalletJournalExtraInfo;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletJournal;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterWalletJournalSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdWalletJournal200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterWalletJournalSync.class.getName());

  public ESICharacterWalletJournalSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_WALLET_JOURNAL;
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
  protected ESIAccountServerResult<List<GetCharactersCharacterIdWalletJournal200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();
    List<GetCharactersCharacterIdWalletJournal200Ok> results = new ArrayList<>();
    long refIdLimit = Long.MAX_VALUE;

    // Retrieve initial batch
    ApiResponse<List<GetCharactersCharacterIdWalletJournal200Ok>> result = apiInstance.getCharactersCharacterIdWalletJournalWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        refIdLimit,
        accessToken(),
        null,
        null);
    checkCommonProblems(result);
    long expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

    // Crawl the journal backwards until no more entries are retrieved
    while (!result.getData()
                  .isEmpty()) {
      results.addAll(result.getData());
      refIdLimit = result.getData()
                         .stream()
                         .min(Comparator.comparingLong(GetCharactersCharacterIdWalletJournal200Ok::getRefId))
                         .get()
                         .getRefId();
      result = apiInstance.getCharactersCharacterIdWalletJournalWithHttpInfo((int) account.getEveCharacterID(),
                                                                             null,
                                                                             refIdLimit,
                                                                             accessToken(),
                                                                             null,
                                                                             null);
      checkCommonProblems(result);
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    // Sort results in increasing order by refID so we insert in order
    results.sort(Comparator.comparingLong(GetCharactersCharacterIdWalletJournal200Ok::getRefId));

    return new ESIAccountServerResult<>(expiry, results);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdWalletJournal200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    for (GetCharactersCharacterIdWalletJournal200Ok next : data.getData()) {
      GetCharactersCharacterIdWalletJournalExtraInfo extra = next.getExtraInfo();
      updates.add(new WalletJournal(1, next.getRefId(),
                                    next.getDate().getMillis(),
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
    }
  }


}
