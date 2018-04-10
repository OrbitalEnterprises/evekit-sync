package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionTransactions200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletTransaction;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class ESICorporationWalletTransactionSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private WalletApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][][] txnTestData;
  private static int[][] txnPages;

  static {
    // Comparator for sorting test data in decreasing order by refID (testData[i][1])
    Comparator<Object[]> journalDataCompare = Comparator.comparingLong(x -> (Long) x[1]);

    // Generate large batch of test data which we will split into pages to ensure sync handles
    // paging correctly.
    //
    // 0 int division;
    // 1 long transactionID;
    // 2 long date = -1;
    // 3 int quantity;
    // 4 int typeID;
    // 5 BigDecimal price;
    // 6 int clientID;
    // 7 long locationID;
    // 8 boolean isBuy;
    // 9 boolean isPersonal;
    // 10 long journalTransactionID;

    // Generate separate batches and page offsets for each division
    txnTestData = new Object[7][][];
    txnPages = new int[7][];

    for (int division = 0; division < 7; division++) {
      int size = 1000 + TestBase.getRandomInt(1000);
      txnTestData[division] = new Object[size][11];
      for (int i = 0; i < size; i++) {
        txnTestData[division][i][0] = division + 1;
        txnTestData[division][i][1] = TestBase.getUniqueRandomLong();
        txnTestData[division][i][2] = txnTestData[division][i][1];
        txnTestData[division][i][3] = TestBase.getRandomInt();
        txnTestData[division][i][4] = TestBase.getRandomInt();
        txnTestData[division][i][5] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2,
                                                                                                   RoundingMode.HALF_UP);
        txnTestData[division][i][6] = TestBase.getRandomInt();
        txnTestData[division][i][7] = TestBase.getRandomLong();
        txnTestData[division][i][8] = TestBase.getRandomBoolean();
        txnTestData[division][i][9] = false; // corporation isPersonal is always false
        txnTestData[division][i][10] = TestBase.getRandomLong();
      }

      // Sort test data in increasing order by refID (txnTestData[i][1])
      Arrays.sort(txnTestData[division], 0, txnTestData[division].length, journalDataCompare);

      // Divide data into pages to test paging sync feature
      int pageCount = 3 + TestBase.getRandomInt(3);
      txnPages[division] = new int[pageCount];
      for (int i = 0; i < pageCount; i++) {
        txnPages[division][i] = i * size / pageCount;
      }
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_TRANSACTIONS,
                                                        1234L, null);

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
                                                        .createQuery("DELETE FROM WalletTransaction ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    @SuppressWarnings("unchecked")
    List<GetCorporationsCorporationIdWalletsDivisionTransactions200Ok>[][] pages = new List[7][];
    for (int division = 0; division < 7; division++) {
      //noinspection unchecked
      pages[division] = new List[txnPages[division].length];
      for (int i = 0; i < txnPages[division].length; i++) {
        pages[division][i] = new ArrayList<>();
        int limit = i + 1 == txnPages[division].length ? txnTestData[division].length : txnPages[division][i + 1];
        for (int j = txnPages[division][i]; j < limit; j++) {
          GetCorporationsCorporationIdWalletsDivisionTransactions200Ok nextTxn = new GetCorporationsCorporationIdWalletsDivisionTransactions200Ok();
          Object[] dt = txnTestData[division][j];
          nextTxn.setTransactionId((Long) dt[1]);
          nextTxn.setDate(new DateTime(new Date((Long) dt[2])));
          nextTxn.setQuantity((Integer) dt[3]);
          nextTxn.setTypeId((Integer) dt[4]);
          nextTxn.setUnitPrice(((BigDecimal) dt[5]).doubleValue());
          nextTxn.setClientId((Integer) dt[6]);
          nextTxn.setLocationId((Long) dt[7]);
          nextTxn.setIsBuy((Boolean) dt[8]);
          nextTxn.setJournalRefId((Long) dt[10]);
          pages[division][i].add(nextTxn);
        }
      }
    }

    mockEndpoint = EasyMock.createMock(WalletApi.class);
    for (int division = 0; division < 7; division++) {
      for (int i = txnPages[division].length; i >= 0; i--) {
        Long txnID = i < txnPages[division].length ? pages[division][i].get(0)
                                                                       .getTransactionId() : Long.MAX_VALUE;
        List<GetCorporationsCorporationIdWalletsDivisionTransactions200Ok> data = i > 0 ? pages[division][i - 1] : Collections.emptyList();
        ApiResponse<List<GetCorporationsCorporationIdWalletsDivisionTransactions200Ok>> apir = new ApiResponse<>(200,
                                                                                                                 createHeaders(
                                                                                                                     "Expires",
                                                                                                                     "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                                                 data);
        EasyMock.expect(mockEndpoint.getCorporationsCorporationIdWalletsDivisionTransactionsWithHttpInfo(
            EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
            EasyMock.eq(division + 1),
            EasyMock.isNull(),
            EasyMock.eq(txnID),
            EasyMock.anyString(),
            EasyMock.isNull(),
            EasyMock.isNull()))
                .andReturn(apir);
      }
    }

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getWalletApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(Object[][] testData, int division) throws Exception {
    // Retrieve all stored data
    List<WalletTransaction> storedData = AbstractESIAccountSync.retrieveAll(testTime,
                                                                            (long contid, AttributeSelector at) ->
                                                                                WalletTransaction.accessQuery(
                                                                                    corpSyncAccount, contid, 1000,
                                                                                    false, at,
                                                                                    new AttributeSelector(
                                                                                        "{ values: [" + division + "]}"),
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      WalletTransaction nextEl = storedData.get(i);
      Object[] dt = testData[i];
      Assert.assertEquals((int) (Integer) dt[0], nextEl.getDivision());
      Assert.assertEquals((long) (Long) dt[1], nextEl.getTransactionID());
      Assert.assertEquals((long) (Long) dt[2], nextEl.getDate());
      Assert.assertEquals((int) (Integer) dt[3], nextEl.getQuantity());
      Assert.assertEquals((int) (Integer) dt[4], nextEl.getTypeID());
      Assert.assertEquals(dt[5], nextEl.getPrice());
      Assert.assertEquals((int) (Integer) dt[6], nextEl.getClientID());
      Assert.assertEquals((long) (Long) dt[7], nextEl.getLocationID());
      Assert.assertEquals(dt[8], nextEl.isBuy());
      Assert.assertEquals(dt[9], nextEl.isPersonal());
      Assert.assertEquals((long) (Long) dt[10], nextEl.getJournalTransactionID());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationWalletTransactionSync sync = new ESICorporationWalletTransactionSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    for (int division = 0; division < 7; division++)
      verifyDataUpdate(txnTestData[division], division + 1);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_WALLET_TRANSACTIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount,
                                                              ESISyncEndpoint.CORP_WALLET_TRANSACTIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.  These should not be modified.  This is just a copy of the existing test data with
    // lower ref IDs and slightly adjusted data values.
    Object[][][] allData = new Object[7][][];
    for (int division = 0; division < 7; division++) {
      allData[division] = new Object[txnTestData[division].length * 2][27];
      System.arraycopy(txnTestData[division], 0, allData[division], txnTestData[division].length,
                       txnTestData[division].length);
      long firstDate = (Long) txnTestData[division][0][1] - txnTestData[division].length - 1;
      for (int i = 0; i < allData[division].length / 2; i++) {
        System.arraycopy(allData[division][i + txnTestData[division].length], 0, allData[division][i], 0, 11);
        allData[division][i][1] = firstDate + i;
        Object[] dt = allData[division][i];
        WalletTransaction newEl = new WalletTransaction(division + 1,
                                                        (Long) dt[1],
                                                        (Long) dt[2],
                                                        (Integer) dt[3],
                                                        (Integer) dt[4],
                                                        (BigDecimal) dt[5],
                                                        (Integer) dt[6],
                                                        (Long) dt[7],
                                                        (Boolean) dt[8],
                                                        (Boolean) dt[9],
                                                        (Long) dt[10]);
        newEl.setup(corpSyncAccount, testTime - 1);
        CachedData.update(newEl);
      }
    }

    // Perform the sync
    ESICorporationWalletTransactionSync sync = new ESICorporationWalletTransactionSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    for (int division = 0; division < 7; division++)
      verifyDataUpdate(allData[division], division + 1);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_WALLET_TRANSACTIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount,
                                                              ESISyncEndpoint.CORP_WALLET_TRANSACTIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
