package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdWalletJournal200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletJournal;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterWalletJournalSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdWalletJournal200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterWalletJournalSync.class.getName());
  private String context;

  public ESICharacterWalletJournalSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_WALLET_JOURNAL;
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
  protected ESIAccountServerResult<List<GetCharactersCharacterIdWalletJournal200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    WalletApi apiInstance = cp.getWalletApi();

    Pair<Long, List<GetCharactersCharacterIdWalletJournal200Ok>> result = pagedResultRetriever(
        (page) -> {
          ESIThrottle.throttle(endpoint().name(), account);
          return apiInstance.getCharactersCharacterIdWalletJournalWithHttpInfo(
              (int) account.getEveCharacterID(),
              null,
              null,
              page,
              accessToken(),
              null,
              null);
        });

    List<GetCharactersCharacterIdWalletJournal200Ok> results = result.getRight();
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();

    // Sort results in increasing order by refID so we insert in order
    results.sort(Comparator.comparingLong(GetCharactersCharacterIdWalletJournal200Ok::getId));

    return new ESIAccountServerResult<>(expiry, results);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdWalletJournal200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Check for existing tracker context.  If exists, this will be a refID upper bound.  We can skip
    // enqueuing updates for any item with a refID less than this bound.
    long refIDBound = Long.MIN_VALUE;
    long newRefBound = Long.MIN_VALUE;
    try {
      refIDBound = Long.valueOf(getCurrentTracker().getContext());
    } catch (Exception e) {
      // ignore, not able to use the bound;
    }

    for (GetCharactersCharacterIdWalletJournal200Ok next : data.getData()) {
      // Items below the bound have already been processed
      if (next.getId() <= refIDBound)
        continue;

      updates.add(new WalletJournal(1,
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
      newRefBound = Math.max(newRefBound, next.getId());
    }

    // Set next context to new ref bound
    context = String.valueOf(newRefBound);
  }

}
