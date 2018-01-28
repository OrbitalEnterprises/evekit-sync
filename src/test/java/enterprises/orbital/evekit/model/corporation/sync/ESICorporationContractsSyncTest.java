package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ContractsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContracts200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContractsContractIdBids200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContractsContractIdItems200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Contract;
import enterprises.orbital.evekit.model.common.ContractBid;
import enterprises.orbital.evekit.model.common.ContractItem;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICorporationContractsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private ContractsApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] contractsTestData;
  private static Object[][] itemsTestData;
  private static Object[][] bidsTestData;
  private static int[] pages;

  static {
    // Contracts test data
    // 0 int contractID;
    // 1 int issuerID;
    // 2 int issuerCorpID;
    // 3 int assigneeID;
    // 4 int acceptorID;
    // 5 long startStationID;
    // 6 long endStationID;
    // 7 String type; - ENUMERATED
    // 8 String status; - ENUMERATED
    // 9 String title;
    // 10 boolean forCorp;
    // 11 String availability; - ENUMERATED
    // 12 long dateIssued = -1;
    // 13 long dateExpired = -1;
    // 14 long dateAccepted = -1;
    // 15 int numDays;
    // 16 long dateCompleted = -1;
    // 17 BigDecimal price;
    // 18 BigDecimal reward;
    // 19 BigDecimal collateral;
    // 20 BigDecimal buyout;
    // 21 double volume;
    int size = 1000 + TestBase.getRandomInt(500);
    contractsTestData = new Object[size][22];
    int typeLen = GetCorporationsCorporationIdContracts200Ok.TypeEnum.values().length;
    int statusLen = GetCorporationsCorporationIdContracts200Ok.StatusEnum.values().length;
    int availLen = GetCorporationsCorporationIdContracts200Ok.AvailabilityEnum.values().length;
    for (int i = 0; i < size; i++) {
      contractsTestData[i][0] = TestBase.getUniqueRandomInteger();
      contractsTestData[i][1] = TestBase.getRandomInt();
      contractsTestData[i][2] = TestBase.getRandomInt();
      contractsTestData[i][3] = TestBase.getRandomInt();
      contractsTestData[i][4] = TestBase.getRandomInt();
      contractsTestData[i][5] = TestBase.getRandomLong();
      contractsTestData[i][6] = TestBase.getRandomLong();
      contractsTestData[i][7] = GetCorporationsCorporationIdContracts200Ok.TypeEnum.values()[TestBase.getRandomInt(typeLen)];
      contractsTestData[i][8] = GetCorporationsCorporationIdContracts200Ok.StatusEnum.values()[TestBase.getRandomInt(statusLen)];
      contractsTestData[i][9] = TestBase.getRandomText(50);
      contractsTestData[i][10] = TestBase.getRandomBoolean();
      contractsTestData[i][11] = GetCorporationsCorporationIdContracts200Ok.AvailabilityEnum.values()[TestBase.getRandomInt(availLen)];
      contractsTestData[i][12] = TestBase.getRandomLong();
      contractsTestData[i][13] = TestBase.getRandomLong();
      contractsTestData[i][14] = TestBase.getRandomLong();
      contractsTestData[i][15] = TestBase.getRandomInt();
      contractsTestData[i][16] = TestBase.getRandomLong();
      contractsTestData[i][17] = TestBase.getRandomBigDecimal(10000);
      contractsTestData[i][18] = TestBase.getRandomBigDecimal(10000);
      contractsTestData[i][19] = TestBase.getRandomBigDecimal(10000);
      contractsTestData[i][20] = TestBase.getRandomBigDecimal(10000);
      contractsTestData[i][21] = TestBase.getRandomDouble(10000);
    }

    // Contract Items test data
    // 0 int contractID;
    // 1 long recordID;
    // 2 int typeID;
    // 3 int quantity;
    // 4 int rawQuantity;
    // 5 boolean singleton;
    // 6 boolean included;
    Object[] nonLoanContracts = Arrays.stream(contractsTestData)
                                      .filter(x -> x[7] != GetCorporationsCorporationIdContracts200Ok.TypeEnum.LOAN)
                                      .toArray();
    int nonLoanLength = nonLoanContracts.length;
    int itemCount = size + TestBase.getRandomInt(1000);
    itemsTestData = new Object[itemCount][7];
    for (int i = 0, j = 0; i < itemCount; i++, j= (j+1) % nonLoanLength) {
      itemsTestData[i][0] = ((Object[]) nonLoanContracts[j])[0];
      itemsTestData[i][1] = TestBase.getUniqueRandomLong();
      itemsTestData[i][2] = TestBase.getRandomInt();
      itemsTestData[i][3] = TestBase.getRandomInt();
      itemsTestData[i][4] = TestBase.getRandomInt();
      itemsTestData[i][5] = TestBase.getRandomBoolean();
      itemsTestData[i][6] = TestBase.getRandomBoolean();
    }

    // Contract Bids test data
    // 0 int bidID;
    // 1 int contractID;
    // 2 int bidderID;
    // 3 long dateBid = -1;
    // 4 BigDecimal amount;
    Object[] auctionContracts = Arrays.stream(contractsTestData)
                                      .filter(x -> x[7] == GetCorporationsCorporationIdContracts200Ok.TypeEnum.AUCTION)
                                      .toArray();
    int auctionLength = auctionContracts.length;
    int bidCount = size + TestBase.getRandomInt(1000);
    bidsTestData = new Object[bidCount][5];
    for (int i = 0, j = 0; i < bidCount; i++, j= (j+1) % auctionLength) {
      bidsTestData[i][0] = TestBase.getUniqueRandomInteger();
      bidsTestData[i][1] = ((Object[]) auctionContracts[j])[0];
      bidsTestData[i][2] = TestBase.getRandomInt();
      bidsTestData[i][3] = TestBase.getRandomLong();
      bidsTestData[i][4] = TestBase.getRandomBigDecimal(10000);
    }

    // Configure contract retrieval pages
    int pageCount = 2 + TestBase.getRandomInt(4);
    pages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      pages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTRACTS, 1234L);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM Contract ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM ContractItem ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM ContractBid ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(ContractsApi.class);
    // Setup contract retrieval mock calls
    List<GetCorporationsCorporationIdContracts200Ok> contractList =
        Arrays.stream(contractsTestData)
              .map(x -> {
                GetCorporationsCorporationIdContracts200Ok nextContract = new GetCorporationsCorporationIdContracts200Ok();
                nextContract.setContractId((Integer) x[0]);
                nextContract.setIssuerId((Integer) x[1]);
                nextContract.setIssuerCorporationId((Integer) x[2]);
                nextContract.setAssigneeId((Integer) x[3]);
                nextContract.setAcceptorId((Integer) x[4]);
                nextContract.setStartLocationId((Long) x[5]);
                nextContract.setEndLocationId((Long) x[6]);
                nextContract.setType((GetCorporationsCorporationIdContracts200Ok.TypeEnum) x[7]);
                nextContract.setStatus((GetCorporationsCorporationIdContracts200Ok.StatusEnum) x[8]);
                nextContract.setTitle((String) x[9]);
                nextContract.setForCorporation((Boolean) x[10]);
                nextContract.setAvailability((GetCorporationsCorporationIdContracts200Ok.AvailabilityEnum) x[11]);
                nextContract.setDateIssued(new DateTime(new Date((Long) x[12])));
                nextContract.setDateExpired(new DateTime(new Date((Long) x[13])));
                nextContract.setDateAccepted(new DateTime(new Date((Long) x[14])));
                nextContract.setDaysToComplete((Integer) x[15]);
                nextContract.setDateCompleted(new DateTime(new Date((Long) x[16])));
                nextContract.setPrice(((BigDecimal) x[17]).doubleValue());
                nextContract.setReward(((BigDecimal) x[18]).doubleValue());
                nextContract.setCollateral(((BigDecimal) x[19]).doubleValue());
                nextContract.setBuyout(((BigDecimal) x[20]).doubleValue());
                nextContract.setVolume((Double) x[21]);
                return nextContract;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < pages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCorporationsCorporationIdContracts200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                      contractList.subList(last,
                                                                                                        pages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdContractsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = pages[i];
    }
    // Setup contract item retrieval mock calls
    List<Integer> contractsWithItems = Arrays.stream(contractsTestData)
                                      .filter(x -> x[7] != GetCorporationsCorporationIdContracts200Ok.TypeEnum.LOAN)
                                      .map(x -> (Integer) x[0])
                                      .collect(Collectors.toList());
    for (int contractID : contractsWithItems) {
      List<GetCorporationsCorporationIdContractsContractIdItems200Ok> itemList =
          Arrays.stream(itemsTestData).filter(x -> (Integer) x[0] == contractID)
          .map(x -> {
            GetCorporationsCorporationIdContractsContractIdItems200Ok nextItem = new GetCorporationsCorporationIdContractsContractIdItems200Ok();
            nextItem.setRecordId((Long) x[1]);
            nextItem.setTypeId((Integer) x[2]);
            nextItem.setQuantity((Integer) x[3]);
            nextItem.setRawQuantity((Integer) x[4]);
            nextItem.setIsSingleton((Boolean) x[5]);
            nextItem.setIsIncluded((Boolean) x[6]);
            return nextItem;
          })
          .collect(Collectors.toList());
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCorporationsCorporationIdContractsContractIdItems200Ok>> apir = new ApiResponse<>(200, headers, itemList);
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdContractsContractIdItemsWithHttpInfo(
          EasyMock.eq(contractID),
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }
    // Setup contract bid retrieval mock calls
    List<Integer> contractsWithBids = Arrays.stream(contractsTestData)
                                             .filter(x -> x[7] == GetCorporationsCorporationIdContracts200Ok.TypeEnum.AUCTION)
                                             .map(x -> (Integer) x[0])
                                             .collect(Collectors.toList());
    for (int contractID : contractsWithBids) {
      List<GetCorporationsCorporationIdContractsContractIdBids200Ok> bidList =
          Arrays.stream(bidsTestData).filter(x -> (Integer) x[1] == contractID)
                .map(x -> {
                  GetCorporationsCorporationIdContractsContractIdBids200Ok nextBid = new GetCorporationsCorporationIdContractsContractIdBids200Ok();
                  nextBid.setBidId((Integer) x[0]);
                  nextBid.setBidderId((Integer) x[2]);
                  nextBid.setDateBid(new DateTime(new Date((Long) x[3])));
                  nextBid.setAmount(((BigDecimal) x[4]).floatValue());
                  return nextBid;
                })
                .collect(Collectors.toList());
      int lastBidPage = 0;
      int bidPages = 2 + TestBase.getRandomInt(2);
      int[] bidPageOffsets = new int[bidPages];
      for (int j = bidPages - 1; j >= 0; j--)
        bidPageOffsets[j] = bidList.size() - (bidPages - 1 - j) * (bidList.size() / bidPages);

      for (int j = 0; j < bidPageOffsets.length; j++) {
        Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                          String.valueOf(bidPageOffsets.length));
        ApiResponse<List<GetCorporationsCorporationIdContractsContractIdBids200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                               bidList.subList(lastBidPage,
                                                                                                                    bidPageOffsets[j]));
        EasyMock.expect(mockEndpoint.getCorporationsCorporationIdContractsContractIdBidsWithHttpInfo(
            EasyMock.eq(contractID),
            EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
            EasyMock.isNull(),
            EasyMock.eq(j + 1),
            EasyMock.anyString(),
            EasyMock.isNull(),
            EasyMock.isNull()))
                .andReturn(apir);
        lastBidPage = bidPageOffsets[j];
      }
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getContractsApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Contract> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Contract.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR));

    List<ContractItem> storedItemData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        ContractItem.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    List<ContractBid> storedBidData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        ContractBid.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(contractsTestData.length, storedData.size());
    Assert.assertEquals(itemsTestData.length, storedItemData.size());
    Assert.assertEquals(bidsTestData.length, storedBidData.size());

    // Check stored data
    for (int i = 0; i < contractsTestData.length; i++) {
      Contract nextEl = storedData.get(i);
      Assert.assertEquals((int) (Integer) contractsTestData[i][0], nextEl.getContractID());
      Assert.assertEquals((int) (Integer) contractsTestData[i][1], nextEl.getIssuerID());
      Assert.assertEquals((int) (Integer) contractsTestData[i][2], nextEl.getIssuerCorpID());
      Assert.assertEquals((int) (Integer) contractsTestData[i][3], nextEl.getAssigneeID());
      Assert.assertEquals((int) (Integer) contractsTestData[i][4], nextEl.getAcceptorID());
      Assert.assertEquals((long) (Long) contractsTestData[i][5], nextEl.getStartStationID());
      Assert.assertEquals((long) (Long) contractsTestData[i][6], nextEl.getEndStationID());
      Assert.assertEquals(String.valueOf(contractsTestData[i][7]), nextEl.getType());
      Assert.assertEquals(String.valueOf(contractsTestData[i][8]), nextEl.getStatus());
      Assert.assertEquals(String.valueOf(contractsTestData[i][9]), nextEl.getTitle());
      Assert.assertEquals(contractsTestData[i][10], nextEl.isForCorp());
      Assert.assertEquals(String.valueOf(contractsTestData[i][11]), nextEl.getAvailability());
      Assert.assertEquals((long) (Long) contractsTestData[i][12], nextEl.getDateIssued());
      Assert.assertEquals((long) (Long) contractsTestData[i][13], nextEl.getDateExpired());
      Assert.assertEquals((long) (Long) contractsTestData[i][14], nextEl.getDateAccepted());
      Assert.assertEquals((int) (Integer) contractsTestData[i][15], nextEl.getNumDays());
      Assert.assertEquals((long) (Long) contractsTestData[i][16], nextEl.getDateCompleted());
      Assert.assertEquals(contractsTestData[i][17], nextEl.getPrice());
      Assert.assertEquals(contractsTestData[i][18], nextEl.getReward());
      Assert.assertEquals(contractsTestData[i][19], nextEl.getCollateral());
      Assert.assertEquals(contractsTestData[i][20], nextEl.getBuyout());
      Assert.assertEquals((Double) contractsTestData[i][21], nextEl.getVolume(), 0.001);
    }

    List<Integer> contractsWithItems = Arrays.stream(contractsTestData)
                                             .filter(x -> x[7] != GetCorporationsCorporationIdContracts200Ok.TypeEnum.LOAN)
                                             .map(x -> (Integer) x[0])
                                             .collect(Collectors.toList());
    for (int contractID : contractsWithItems) {
      List<ContractItem> storedItems = storedItemData.stream()
                                                     .filter(x -> x.getContractID() == contractID)
                                                     .sorted(Comparator.comparingLong(ContractItem::getRecordID))
                                                     .collect(Collectors.toList());
      List<Object[]> srcItems = Arrays.stream(itemsTestData)
                                      .filter(x -> (Integer) x[0] == contractID)
                                      .sorted(Comparator.comparingLong(x -> (Long) x[1]))
                                      .collect(Collectors.toList());
      for (int i = 0; i < srcItems.size(); i++) {
        ContractItem nextEl = storedItems.get(i);
        Object[] nextSrc = srcItems.get(i);

        Assert.assertEquals((int) (Integer) nextSrc[0], nextEl.getContractID());
        Assert.assertEquals((long) (Long) nextSrc[1], nextEl.getRecordID());
        Assert.assertEquals((int) (Integer) nextSrc[2], nextEl.getTypeID());
        Assert.assertEquals((int) (Integer) nextSrc[3], nextEl.getQuantity());
        Assert.assertEquals((int) (Integer) nextSrc[4], nextEl.getRawQuantity());
        Assert.assertEquals(nextSrc[5], nextEl.isSingleton());
        Assert.assertEquals(nextSrc[6], nextEl.isIncluded());
      }
    }

    List<Integer> contractsWithBids = Arrays.stream(contractsTestData)
                                            .filter(x -> x[7] == GetCorporationsCorporationIdContracts200Ok.TypeEnum.AUCTION)
                                            .map(x -> (Integer) x[0])
                                            .collect(Collectors.toList());
    for (int contractID : contractsWithBids) {
      List<ContractBid> storedBids = storedBidData.stream()
                                                  .filter(x -> x.getContractID() == contractID)
                                                  .sorted(Comparator.comparingInt(ContractBid::getBidID))
                                                  .collect(Collectors.toList());
      List<Object[]> srcBids = Arrays.stream(bidsTestData)
                                     .filter(x -> (Integer) x[1] == contractID)
                                     .sorted(Comparator.comparingInt(x -> (Integer) x[0]))
                                     .collect(Collectors.toList());
      for (int i = 0; i < srcBids.size(); i++) {
        ContractBid nextEl = storedBids.get(i);
        Object[] nextSrc = srcBids.get(i);

        Assert.assertEquals((int) (Integer) nextSrc[0], nextEl.getBidID());
        Assert.assertEquals((int) (Integer) nextSrc[1], nextEl.getContractID());
        Assert.assertEquals((int) (Integer) nextSrc[2], nextEl.getBidderID());
        Assert.assertEquals((long) (Long) nextSrc[3], nextEl.getDateBid());
        Assert.assertEquals(nextSrc[4], nextEl.getAmount());
      }
    }

  }

  private void verifyOldDataUpdated() throws Exception {
    // Retrieve all stored data
    List<Contract> storedData = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        Contract.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR));

    List<ContractItem> storedItemData = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        ContractItem.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                 AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    List<ContractBid> storedBidData = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        ContractBid.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(contractsTestData.length, storedData.size());
    Assert.assertEquals(itemsTestData.length, storedItemData.size());
    Assert.assertEquals(bidsTestData.length, storedBidData.size());

    // Check stored data
    for (int i = 0; i < contractsTestData.length; i++) {
      Contract nextEl = storedData.get(i);
      Assert.assertEquals((int) (Integer) contractsTestData[i][0], nextEl.getContractID());
      Assert.assertEquals((Integer) contractsTestData[i][1] + 1, nextEl.getIssuerID());
      Assert.assertEquals((Integer) contractsTestData[i][2] + 1, nextEl.getIssuerCorpID());
      Assert.assertEquals((Integer) contractsTestData[i][3] + 1, nextEl.getAssigneeID());
      Assert.assertEquals((Integer) contractsTestData[i][4] + 1, nextEl.getAcceptorID());
      Assert.assertEquals((Long) contractsTestData[i][5] + 1, nextEl.getStartStationID());
      Assert.assertEquals((Long) contractsTestData[i][6] + 1, nextEl.getEndStationID());
      Assert.assertEquals(String.valueOf(contractsTestData[i][7]), nextEl.getType());
      Assert.assertEquals(String.valueOf(contractsTestData[i][8]), nextEl.getStatus());
      Assert.assertEquals(String.valueOf(contractsTestData[i][9]), nextEl.getTitle());
      Assert.assertEquals(contractsTestData[i][10], nextEl.isForCorp());
      Assert.assertEquals(String.valueOf(contractsTestData[i][11]), nextEl.getAvailability());
      Assert.assertEquals((Long) contractsTestData[i][12] + 1, nextEl.getDateIssued());
      Assert.assertEquals((Long) contractsTestData[i][13] + 1, nextEl.getDateExpired());
      Assert.assertEquals((Long) contractsTestData[i][14] + 1, nextEl.getDateAccepted());
      Assert.assertEquals((Integer) contractsTestData[i][15] + 1, nextEl.getNumDays());
      Assert.assertEquals((Long) contractsTestData[i][16] + 1, nextEl.getDateCompleted());
      Assert.assertEquals(contractsTestData[i][17], nextEl.getPrice());
      Assert.assertEquals(contractsTestData[i][18], nextEl.getReward());
      Assert.assertEquals(contractsTestData[i][19], nextEl.getCollateral());
      Assert.assertEquals(contractsTestData[i][20], nextEl.getBuyout());
      Assert.assertEquals((Double) contractsTestData[i][21] + 1.0D, nextEl.getVolume(), 0.001);
    }

    List<Integer> contractsWithItems = Arrays.stream(contractsTestData)
                                             .filter(x -> x[7] != GetCorporationsCorporationIdContracts200Ok.TypeEnum.LOAN)
                                             .map(x -> (Integer) x[0])
                                             .collect(Collectors.toList());
    for (int contractID : contractsWithItems) {
      List<ContractItem> storedItems = storedItemData.stream()
                                                     .filter(x -> x.getContractID() == contractID)
                                                     .sorted(Comparator.comparingLong(ContractItem::getRecordID))
                                                     .collect(Collectors.toList());
      List<Object[]> srcItems = Arrays.stream(itemsTestData)
                                      .filter(x -> (Integer) x[0] == contractID)
                                      .sorted(Comparator.comparingLong(x -> (Long) x[1]))
                                      .collect(Collectors.toList());

      for (int i = 0; i < srcItems.size(); i++) {
        ContractItem nextEl = storedItems.get(i);
        Object[] nextSrc = srcItems.get(i);

        Assert.assertEquals((int) (Integer) nextSrc[0], nextEl.getContractID());
        Assert.assertEquals((long) (Long) nextSrc[1], nextEl.getRecordID());
        Assert.assertEquals((Integer) nextSrc[2] + 1, nextEl.getTypeID());
        Assert.assertEquals((Integer) nextSrc[3] + 1, nextEl.getQuantity());
        Assert.assertEquals((Integer) nextSrc[4] + 1, nextEl.getRawQuantity());
        Assert.assertEquals(nextSrc[5], nextEl.isSingleton());
        Assert.assertEquals(nextSrc[6], nextEl.isIncluded());
      }
    }

    List<Integer> contractsWithBids = Arrays.stream(contractsTestData)
                                            .filter(x -> x[7] == GetCorporationsCorporationIdContracts200Ok.TypeEnum.AUCTION)
                                            .map(x -> (Integer) x[0])
                                            .collect(Collectors.toList());
    for (int contractID : contractsWithBids) {
      List<ContractBid> storedBids = storedBidData.stream()
                                                  .filter(x -> x.getContractID() == contractID)
                                                  .sorted(Comparator.comparingInt(ContractBid::getBidID))
                                                  .collect(Collectors.toList());
      List<Object[]> srcBids = Arrays.stream(bidsTestData)
                                     .filter(x -> (Integer) x[1] == contractID)
                                     .sorted(Comparator.comparingInt(x -> (Integer) x[0]))
                                     .collect(Collectors.toList());

      for (int i = 0; i < srcBids.size(); i++) {
        ContractBid nextEl = storedBids.get(i);
        Object[] nextSrc = srcBids.get(i);

        Assert.assertEquals((int) (Integer) nextSrc[0], nextEl.getBidID());
        Assert.assertEquals((int) (Integer) nextSrc[1], nextEl.getContractID());
        Assert.assertEquals((Integer) nextSrc[2] + 1, nextEl.getBidderID());
        Assert.assertEquals((Long) nextSrc[3] + 1, nextEl.getDateBid());
        Assert.assertEquals(nextSrc[4], nextEl.getAmount());
      }
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationContractsSync sync = new ESICorporationContractsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CONTRACTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTRACTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // Contracts can never be deleted, no need to test
    for (Object[] d : contractsTestData) {
      Contract newEl = new Contract((Integer) d[0],
                                    (Integer) d[1] + 1,
                                    (Integer) d[2] + 1,
                                    (Integer) d[3] + 1,
                                    (Integer) d[4] + 1,
                                    (Long) d[5] + 1,
                                    (Long) d[6] + 1,
                                    String.valueOf(d[7]),
                                    String.valueOf(d[8]),
                                    String.valueOf(d[9]),
                                    (Boolean) d[10],
                                    String.valueOf(d[11]),
                                    (Long) d[12] + 1,
                                    (Long) d[13] + 1,
                                    (Long) d[14] + 1,
                                    (Integer) d[15] + 1,
                                    (Long) d[16] + 1,
                                    (BigDecimal) d[17],
                                    (BigDecimal) d[18],
                                    (BigDecimal) d[19],
                                    (BigDecimal) d[20],
                                    (Double) d[21] + 1.0D);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }
    for (Object[] d : itemsTestData) {
      ContractItem newEl = new ContractItem((Integer) d[0],
                                    (Long) d[1],
                                    (Integer) d[2] + 1,
                                    (Integer) d[3] + 1,
                                    (Integer) d[4] + 1,
                                    (Boolean) d[5],
                                    (Boolean) d[6]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }
    for (Object[] d : bidsTestData) {
      ContractBid newEl = new ContractBid((Integer) d[0],
                                    (Integer) d[1],
                                    (Integer) d[2] + 1,
                                    (Long) d[3] + 1,
                                    (BigDecimal) d[4]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationContractsSync sync = new ESICorporationContractsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyOldDataUpdated();

    // Verify updates
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CONTRACTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTRACTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
