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
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICorporationContractsSync extends AbstractESIAccountSync<ESICorporationContractsSync.ContractData> {
  protected static final Logger log = Logger.getLogger(ESICorporationContractsSync.class.getName());

  private CachedCorpContracts cacheUpdate;

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
        existing = ContractItem.get(account, time, ((ContractItem) item).getContractID(),
                                    ((ContractItem) item).getRecordID());
      else
        existing = ContractBid.get(account, time, ((ContractBid) item).getContractID(),
                                   ((ContractBid) item).getBidID());
    }
    evolveOrAdd(time, existing, item);
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected ESIAccountServerResult<ContractData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    ContractData resultData = new ContractData();
    ContractsApi apiInstance = cp.getContractsApi();
    Pair<Long, List<GetCorporationsCorporationIdContracts200Ok>> result;
    try {
      // Retrieve bases info
      result = pagedResultRetriever((page) -> {
        ESIThrottle.throttle(endpoint().name(), account);
        return apiInstance.getCorporationsCorporationIdContractsWithHttpInfo(
            (int) account.getEveCorporationID(),
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
        long expiry = OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        result = Pair.of(expiry, Collections.emptyList());
      } else {
        // Any other error will be rethrown.
        // Document other 403 error response bodies in case we should add these in the future.
        if (e.getCode() == 403) {
          log.warning("403 code with unmatched body: " + String.valueOf(e.getResponseBody()));
        }
        throw e;
      }
    }
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
              log.fine(
                  getContext() + " Can't find items for contract: " + nextContract.getContractId() + ", continuing");
            else
              // Anything else is unexpected and is thrown
              throw e;
          }

          // If this is an action, also retrieve any bids
          if (nextContract.getType() == GetCorporationsCorporationIdContracts200Ok.TypeEnum.AUCTION) {
            try {
              Pair<Long, List<GetCorporationsCorporationIdContractsContractIdBids200Ok>> bids = pagedResultRetriever(
                  (page) -> {
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
                log.fine(
                    getContext() + " Can't find bids for contract: " + nextContract.getContractId() + ", continuing");
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

    // Construct arrays of values to add
    List<Contract> currentContracts = data.getData().contracts.stream()
                                                              .map((next) -> new Contract(
                                                                  next.getContractId(),
                                                                  next.getIssuerId(),
                                                                  next.getIssuerCorporationId(),
                                                                  next.getAssigneeId(),
                                                                  next.getAcceptorId(),
                                                                  nullSafeLong(
                                                                      next.getStartLocationId(), 0L),
                                                                  nullSafeLong(next.getEndLocationId(),
                                                                               0L),
                                                                  next.getType()
                                                                      .toString(),
                                                                  next.getStatus()
                                                                      .toString(),
                                                                  next.getTitle(),
                                                                  next.getForCorporation(),
                                                                  next.getAvailability()
                                                                      .toString(),
                                                                  next.getDateIssued()
                                                                      .getMillis(),
                                                                  next.getDateExpired()
                                                                      .getMillis(),
                                                                  nullSafeDateTime(
                                                                      next.getDateAccepted(),
                                                                      new DateTime(
                                                                          new Date(0))).getMillis(),
                                                                  nullSafeInteger(
                                                                      next.getDaysToComplete(), 0),
                                                                  nullSafeDateTime(
                                                                      next.getDateCompleted(),
                                                                      new DateTime(
                                                                          new Date(0))).getMillis(),
                                                                  BigDecimal.valueOf(
                                                                      nullSafeDouble(next.getPrice(),
                                                                                     0D))
                                                                            .setScale(2,
                                                                                      RoundingMode.HALF_UP),
                                                                  BigDecimal.valueOf(
                                                                      nullSafeDouble(next.getReward(),
                                                                                     0D))
                                                                            .setScale(2,
                                                                                      RoundingMode.HALF_UP),
                                                                  BigDecimal.valueOf(nullSafeDouble(
                                                                      next.getCollateral(), 0D))
                                                                            .setScale(2,
                                                                                      RoundingMode.HALF_UP),
                                                                  BigDecimal.valueOf(
                                                                      nullSafeDouble(next.getBuyout(),
                                                                                     0D))
                                                                            .setScale(2,
                                                                                      RoundingMode.HALF_UP),
                                                                  nullSafeDouble(next.getVolume(), 0D)
                                                              ))
                                                              .collect(Collectors.toList());

    List<ContractItem> currentItems = new ArrayList<>();
    for (Map.Entry<Integer, List<GetCorporationsCorporationIdContractsContractIdItems200Ok>> nextItemList : data.getData().contractItems.entrySet()) {
      currentItems.addAll(nextItemList.getValue()
                                      .stream()
                                      .map((next) -> new ContractItem(
                                          nextItemList.getKey(),
                                          next.getRecordId(),
                                          next.getTypeId(),
                                          next.getQuantity(),
                                          nullSafeInteger(next.getRawQuantity(), 0),
                                          next.getIsSingleton(),
                                          next.getIsIncluded()
                                      ))
                                      .collect(Collectors.toList()));
    }

    List<ContractBid> currentBids = new ArrayList<>();
    for (Map.Entry<Integer, List<GetCorporationsCorporationIdContractsContractIdBids200Ok>> nextBidList : data.getData().contractBids.entrySet()) {
      currentBids.addAll(nextBidList.getValue()
                                    .stream()
                                    .map((next) -> new ContractBid(
                                        next.getBidId(),
                                        nextBidList.getKey(),
                                        next.getBidderId(),
                                        next.getDateBid()
                                            .getMillis(),
                                        BigDecimal.valueOf(next.getAmount())
                                                  .setScale(2, RoundingMode.HALF_UP)
                                    ))
                                    .collect(Collectors.toList()));
    }

    // Retrieve contract cache and check for changed contracts.  Although contracts can not be deleted
    // (they can only be changed), we do remove them from the cache when they no longer appear in the latest
    // contract list.
    WeakReference<ModelCacheData> ref = ModelCache.get(account, ESISyncEndpoint.CORP_CONTRACTS);
    cacheUpdate = ref != null ? (CachedCorpContracts) ref.get() : null;
    if (cacheUpdate == null) {
      // If we don't have a cache yet, then create one from stored contracts.
      // Since contracts are never deleted, we don't actually want to retrieve all existing contracts
      // (and items and bids).  Instead, we retrieve all contracts with a dateIssued no more than 5 weeks
      // before the current time.  This works because no contract can have an expiry of more than 4 weeks.
      cacheInit();
      cacheUpdate = new CachedCorpContracts();
      long boundary = time - 5 * 7 * 24 * 60 * 60 * 1000L;
      for (Contract next : retrieveAll(time,
                                       (long contid, AttributeSelector at) -> Contract.accessQuery(account, contid,
                                                                                                   1000,
                                                                                                   false, at,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   AttributeSelector.range(
                                                                                                       boundary, time),
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR))) {
        cacheUpdate.addContract(next);
      }

      // Retrieve items and bids as needed
      for (Contract next : cacheUpdate.cachedContractMap.values()) {
        for (ContractItem nextItem : retrieveAll(time, (long contid, AttributeSelector at) -> ContractItem.accessQuery(
            account, contid,
            1000, false, at,
            AttributeSelector.values(next.getContractID()),
            ANY_SELECTOR,
            ANY_SELECTOR,
            ANY_SELECTOR,
            ANY_SELECTOR,
            ANY_SELECTOR,
            ANY_SELECTOR))) {
          cacheUpdate.addContractItem(nextItem);
        }
        if (next.getType()
                .equals("auction")) {
          for (ContractBid nextBid : retrieveAll(time,
                                                 (long contid, AttributeSelector at) -> ContractBid.accessQuery(account,
                                                                                                                contid,
                                                                                                                1000,
                                                                                                                false,
                                                                                                                at,
                                                                                                                ANY_SELECTOR,
                                                                                                                AttributeSelector.values(
                                                                                                                    next.getContractID()),
                                                                                                                ANY_SELECTOR,
                                                                                                                ANY_SELECTOR,
                                                                                                                ANY_SELECTOR))) {
            cacheUpdate.addContractBid(nextBid);
          }
        }
      }
    }

    // Contracts can never be deleted, they can only change state.  So no reason to check for removed contracts.
    // The same is true for the item list associated with a contract, as well as bids associated with an auction.

    // Process the latest contracts list.  Any new contract should be added and cached.
    // If a contract already exists, but the new copy has changed, then it should also be
    // updated and replace the cached copy.  We also keep track of the ID of the latest
    // contracts and remove any from the cache that no longer appear in the latest list.
    Set<Integer> seenContracts = new HashSet<>();
    for (Contract next : currentContracts) {
      seenContracts.add(next.getContractID());
      if (cacheUpdate.cachedContractMap.containsKey(next.getContractID())) {
        Contract compare = cacheUpdate.cachedContractMap.get(next.getContractID());
        if (!compare.equivalent(next)) {
          // Update and replace in cache
          updates.add(next);
          cacheUpdate.addContract(next);
          cacheMiss();
        } else {
          // Cached value is still correct
          cacheHit();
        }
      } else {
        // A contract we've never seen before
        cacheMiss();
        updates.add(next);
        cacheUpdate.addContract(next);
      }
    }

    // Process items
    for (ContractItem next : currentItems) {
      cacheUpdate.cachedContractItems.computeIfAbsent(next.getContractID(), k -> new HashMap<>());
      if (cacheUpdate.cachedContractItems.get(next.getContractID())
                                         .containsKey(next.getRecordID())) {
        ContractItem compare = cacheUpdate.cachedContractItems.get(next.getContractID())
                                                              .get(next.getRecordID());
        if (!compare.equivalent(next)) {
          // Update and replace in cache
          updates.add(next);
          cacheUpdate.addContractItem(next);
          cacheMiss();
        } else {
          // Cached value is still correct
          cacheHit();
        }
      } else {
        // A contract item we've never seen before
        cacheMiss();
        updates.add(next);
        cacheUpdate.addContractItem(next);
      }
    }

    // Process bids
    for (ContractBid next : currentBids) {
      cacheUpdate.cachedContractBids.computeIfAbsent(next.getContractID(), k -> new HashMap<>());
      if (cacheUpdate.cachedContractBids.get(next.getContractID())
                                        .containsKey(next.getBidID())) {
        ContractBid compare = cacheUpdate.cachedContractBids.get(next.getContractID())
                                                            .get(next.getBidID());
        if (!compare.equivalent(next)) {
          // Update and replace in cache
          updates.add(next);
          cacheUpdate.addContractBid(next);
          cacheMiss();
        } else {
          // Cached value is still correct
          cacheHit();
        }
      } else {
        // A contract bid we've never seen before
        cacheMiss();
        updates.add(next);
        cacheUpdate.addContractBid(next);
      }
    }

    // Clean the cache of any contracts no longer in the latest list.
    Set<Integer> toDelete = new HashSet<>();
    for (Integer next : cacheUpdate.cachedContractMap.keySet()) {
      if (!seenContracts.contains(next)) {
        toDelete.add(next);
      }
    }
    for (Integer toDel : toDelete) {
      cacheUpdate.cachedContractMap.remove(toDel);
      cacheUpdate.cachedContractItems.remove(toDel);
      cacheUpdate.cachedContractBids.remove(toDel);
    }

  }

  @Override
  protected void commitComplete() {
    // Update the contracts cache if we updated the value
    if (cacheUpdate != null) ModelCache.set(account, ESISyncEndpoint.CORP_CONTRACTS, cacheUpdate);
    super.commitComplete();
  }

  private static class CachedCorpContracts implements ModelCacheData {
    Map<Integer, Contract> cachedContractMap = new HashMap<>();
    Map<Integer, Map<Long, ContractItem>> cachedContractItems = new HashMap<>();
    Map<Integer, Map<Integer, ContractBid>> cachedContractBids = new HashMap<>();

    void addContract(Contract ct) {
      cachedContractMap.put(ct.getContractID(), ct);
    }

    void addContractItem(ContractItem it) {
      cachedContractItems.computeIfAbsent(it.getContractID(), k -> new HashMap<>());
      cachedContractItems.get(it.getContractID())
                         .put(it.getRecordID(), it);
    }

    void addContractBid(ContractBid bd) {
      cachedContractBids.computeIfAbsent(bd.getContractID(), k -> new HashMap<>());
      cachedContractBids.get(bd.getContractID())
                        .put(bd.getBidID(), bd);
    }
  }

}
