package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ContractsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdContracts200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdContractsContractIdBids200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdContractsContractIdItems200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Contract;
import enterprises.orbital.evekit.model.common.ContractBid;
import enterprises.orbital.evekit.model.common.ContractItem;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.joda.time.DateTime;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterContractsSync extends AbstractESIAccountSync<ESICharacterContractsSync.ContractData> {
  protected static final Logger log = Logger.getLogger(ESICharacterContractsSync.class.getName());

  class ContractData {
    List<GetCharactersCharacterIdContracts200Ok> contracts;
    Map<Integer, List<GetCharactersCharacterIdContractsContractIdItems200Ok>> contractItems = new HashMap<>();
    Map<Integer, List<GetCharactersCharacterIdContractsContractIdBids200Ok>> contractBids = new HashMap<>();
  }

  public ESICharacterContractsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_CONTRACTS;
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof Contract) || (item instanceof ContractItem) || (item instanceof ContractBid);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof Contract)
        existing = Contract.get(account, time, ((Contract) item).getContractID());
      else if (item instanceof ContractItem)
        existing = ContractItem.get(account, time, ((ContractItem) item).getContractID(), ((ContractItem) item).getRecordID());
      else
        existing = ContractBid.get(account, time, ((ContractBid) item).getContractID(), ((ContractBid) item).getBidID());
    }
    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<ContractData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    ContractData resultData = new ContractData();
    ContractsApi apiInstance = cp.getContractsApi();
    // Retrieve contracts info
    Pair<Long, List<GetCharactersCharacterIdContracts200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdContractsWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          null,
          page,
          accessToken(),
          null,
          null);
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    // Retrieve contract items for contracts with type: unknown, item_exchange, auction, courier
    // Retrieve contract bids for contracts with type: auction
    resultData.contracts = result.getRight();
    for (GetCharactersCharacterIdContracts200Ok nextContract : resultData.contracts) {
      switch (nextContract.getType()) {
        case UNKNOWN:
        case ITEM_EXCHANGE:
        case COURIER:
        case AUCTION:
          try {
            ESIThrottle.throttle(endpoint().name(), account);
            ApiResponse<List<GetCharactersCharacterIdContractsContractIdItems200Ok>> items = apiInstance.getCharactersCharacterIdContractsContractIdItemsWithHttpInfo(
                (int) account.getEveCharacterID(),
                nextContract.getContractId(),
                null,
                null,
                accessToken(),
                null,
                null);
            checkCommonProblems(items);
            resultData.contractItems.put(nextContract.getContractId(), items.getData());
          } catch (ApiException e) {
            // Throttle in case we're about to exhaust the error limit
            ESIThrottle.throttle(e);
            if (e.getCode() == HttpStatus.SC_NOT_FOUND)
              // If not found, log and continue
              log.fine(getContext() + " Can't find items for contract: " + nextContract.getContractId() + ", continuing");
            else
              // Anything else is unexpected and is thrown
              throw e;
          }

          // If this is an action, also retrieve any bids
          if (nextContract.getType() == GetCharactersCharacterIdContracts200Ok.TypeEnum.AUCTION) {
            try {
              ESIThrottle.throttle(endpoint().name(), account);
              ApiResponse<List<GetCharactersCharacterIdContractsContractIdBids200Ok>> bids = apiInstance.getCharactersCharacterIdContractsContractIdBidsWithHttpInfo(
                  (int) account.getEveCharacterID(),
                  nextContract.getContractId(),
                  null,
                  null,
                  accessToken(),
                  null,
                  null);
              checkCommonProblems(bids);
              resultData.contractBids.put(nextContract.getContractId(), bids.getData());
            } catch (ApiException e) {
              // Throttle in case we're about to exhaust the error limit
              ESIThrottle.throttle(e);
              if (e.getCode() == HttpStatus.SC_NOT_FOUND)
                // If not found, log and continue
                log.fine(getContext() + " Can't find bids for contract: " + nextContract.getContractId() + ", continuing");
              else
                // Anything else is unexpected and is thrown
                throw e;
            }
          }
      }
    }
    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<ContractData> data,
                                   List<CachedData> updates) throws IOException {
    // Add contracts
    updates.addAll(data.getData().contracts.stream().map((next) -> new Contract(
        next.getContractId(),
        next.getIssuerId(),
        next.getIssuerCorporationId(),
        next.getAssigneeId(),
        next.getAcceptorId(),
        nullSafeLong(next.getStartLocationId(), 0L),
        nullSafeLong(next.getEndLocationId(), 0L),
        next.getType().toString(),
        next.getStatus().toString(),
        next.getTitle(),
        next.getForCorporation(),
        next.getAvailability().toString(),
        next.getDateIssued().getMillis(),
        next.getDateExpired().getMillis(),
        nullSafeDateTime(next.getDateAccepted(), new DateTime(new Date(0))).getMillis(),
        nullSafeInteger(next.getDaysToComplete(), 0),
        nullSafeDateTime(next.getDateCompleted(), new DateTime(new Date(0))).getMillis(),
        BigDecimal.valueOf(nullSafeDouble(next.getPrice(), 0D)).setScale(2, RoundingMode.HALF_UP),
        BigDecimal.valueOf(nullSafeDouble(next.getReward(), 0D)).setScale(2, RoundingMode.HALF_UP),
        BigDecimal.valueOf(nullSafeDouble(next.getCollateral(), 0D)).setScale(2, RoundingMode.HALF_UP),
        BigDecimal.valueOf(nullSafeDouble(next.getBuyout(), 0D)).setScale(2, RoundingMode.HALF_UP),
        nullSafeDouble(next.getVolume(), 0D)
    )).collect(Collectors.toList()));

    // Add contract items
    for (Map.Entry<Integer, List<GetCharactersCharacterIdContractsContractIdItems200Ok>> nextItemList : data.getData().contractItems.entrySet()) {
      updates.addAll(nextItemList.getValue().stream().map((next) -> new ContractItem(
          nextItemList.getKey(),
          next.getRecordId(),
          next.getTypeId(),
          next.getQuantity(),
          nullSafeInteger(next.getRawQuantity(), 0),
          next.getIsSingleton(),
          next.getIsIncluded()
      )).collect(Collectors.toList()));
    }

    // Add contract bids
    for (Map.Entry<Integer, List<GetCharactersCharacterIdContractsContractIdBids200Ok>> nextBidList : data.getData().contractBids.entrySet()) {
      updates.addAll(nextBidList.getValue().stream().map((next) -> new ContractBid(
          next.getBidId(),
          nextBidList.getKey(),
          next.getBidderId(),
          next.getDateBid().getMillis(),
          BigDecimal.valueOf(next.getAmount()).setScale(2, RoundingMode.HALF_UP)
      )).collect(Collectors.toList()));
    }

    // Contracts can never be deleted, they can only change state.  So no reason to check for removed contracts.
    // The same is true for the item list associated with a contract, as well as bids associated with an auction.

  }

}
