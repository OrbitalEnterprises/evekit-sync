package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ContractsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContracts200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContractsContractIdBids200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContractsContractIdItems200Ok;
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

public class ESICorporationContractsSync extends AbstractESIAccountSync<ESICorporationContractsSync.ContractData> {
  protected static final Logger log = Logger.getLogger(ESICorporationContractsSync.class.getName());

  class ContractData {
    List<GetCorporationsCorporationIdContracts200Ok> contracts;
    Map<Integer, List<GetCorporationsCorporationIdContractsContractIdItems200Ok>> contractItems = new HashMap<>();
    Map<Integer, List<GetCorporationsCorporationIdContractsContractIdBids200Ok>> contractBids = new HashMap<>();
  }

  public ESICorporationContractsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_CONTRACTS;
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
    // Retrieve bases info
    Pair<Long, List<GetCorporationsCorporationIdContracts200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdContractsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          page,
          accessToken());
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    // Retrieve contract items for bases with type: unknown, item_exchange, auction, courier
    // Retrieve contract bids for bases with type: auction
    resultData.contracts = result.getRight();
    for (GetCorporationsCorporationIdContracts200Ok nextContract : resultData.contracts) {
      switch (nextContract.getType()) {
        case UNKNOWN:
        case ITEM_EXCHANGE:
        case COURIER:
        case AUCTION:
          try {
            ESIThrottle.throttle(endpoint().name(), account);
            ApiResponse<List<GetCorporationsCorporationIdContractsContractIdItems200Ok>> items = apiInstance.getCorporationsCorporationIdContractsContractIdItemsWithHttpInfo(
                nextContract.getContractId(),
                (int) account.getEveCorporationID(),
                null,
                null,
                accessToken());
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
          if (nextContract.getType() == GetCorporationsCorporationIdContracts200Ok.TypeEnum.AUCTION) {
            try {
              Pair<Long, List<GetCorporationsCorporationIdContractsContractIdBids200Ok>> bids = pagedResultRetriever((page) -> {
                ESIThrottle.throttle(endpoint().name(), account);
                return apiInstance.getCorporationsCorporationIdContractsContractIdBidsWithHttpInfo(
                    nextContract.getContractId(),
                    (int) account.getEveCorporationID(),
                    null,
                    null,
                    page,
                    accessToken());
              });
              resultData.contractBids.put(nextContract.getContractId(), bids.getRight());
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
    // Add bases
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
    for (Map.Entry<Integer, List<GetCorporationsCorporationIdContractsContractIdItems200Ok>> nextItemList : data.getData().contractItems.entrySet()) {
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
    for (Map.Entry<Integer, List<GetCorporationsCorporationIdContractsContractIdBids200Ok>> nextBidList : data.getData().contractBids.entrySet()) {
      updates.addAll(nextBidList.getValue().stream().map((next) -> new ContractBid(
          next.getBidId(),
          nextBidList.getKey(),
          next.getBidderId(),
          next.getDateBid().getMillis(),
          BigDecimal.valueOf(next.getAmount()).setScale(2, RoundingMode.HALF_UP)
      )).collect(Collectors.toList()));
    }

    // Contracts can never be deleted, they can only change state.  So no reason to check for removed bases.
    // The same is true for the item list associated with a contract, as well as bids associated with an auction.

  }

}
