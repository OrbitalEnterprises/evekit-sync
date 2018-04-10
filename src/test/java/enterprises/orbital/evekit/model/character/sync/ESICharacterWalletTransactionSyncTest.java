package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdWalletTransactions200Ok;
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

public class ESICharacterWalletTransactionSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private WalletApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] txnTestData;
  private static int[] txnPages;

  static {
    // Comparator for sorting test data in increasing order by transactionID (testData[i][1])
    Comparator<Object[]> txnDataCompare = Comparator.comparingLong(x -> (Long) x[1]);

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

    int size = 1000 + TestBase.getRandomInt(1000);
    txnTestData = new Object[size][11];
    for (int i = 0; i < size; i++) {
      txnTestData[i][0] = 1; // fixed for character accounts
      txnTestData[i][1] = TestBase.getUniqueRandomLong();
      txnTestData[i][2] = txnTestData[i][1];
      txnTestData[i][3] = TestBase.getRandomInt();
      txnTestData[i][4] = TestBase.getRandomInt();
      txnTestData[i][5] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
      txnTestData[i][6] = TestBase.getRandomInt();
      txnTestData[i][7] = TestBase.getRandomLong();
      txnTestData[i][8] = TestBase.getRandomBoolean();
      txnTestData[i][9] = TestBase.getRandomBoolean();
      txnTestData[i][10] = TestBase.getRandomLong();
    }

    // Sort test data in decreasing order by refID (txnTestData[i][1])
    Arrays.sort(txnTestData, 0, txnTestData.length, txnDataCompare);

    // Divide data into pages to test paging sync feature
    int pageCount = 3 + TestBase.getRandomInt(3);
    txnPages = new int[pageCount];
    for (int i = 0; i < pageCount; i++) {
      txnPages[i] = i * size / pageCount;
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_WALLET_TRANSACTIONS,
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
    List<GetCharactersCharacterIdWalletTransactions200Ok>[] pages = new List[txnPages.length];
    for (int i = 0; i < txnPages.length; i++) {
      pages[i] = new ArrayList<>();
      int limit = i + 1 == txnPages.length ? txnTestData.length : txnPages[i + 1];
      for (int j = txnPages[i]; j < limit; j++) {
        GetCharactersCharacterIdWalletTransactions200Ok nextTxn = new GetCharactersCharacterIdWalletTransactions200Ok();
        Object[] dt = txnTestData[j];
        nextTxn.setTransactionId((Long) dt[1]);
        nextTxn.setDate(new DateTime(new Date((Long) dt[2])));
        nextTxn.setQuantity((Integer) dt[3]);
        nextTxn.setTypeId((Integer) dt[4]);
        nextTxn.setUnitPrice(((BigDecimal) dt[5]).doubleValue());
        nextTxn.setClientId((Integer) dt[6]);
        nextTxn.setLocationId((Long) dt[7]);
        nextTxn.setIsBuy((Boolean) dt[8]);
        nextTxn.setIsPersonal((Boolean) dt[9]);
        nextTxn.setJournalRefId((Long) dt[10]);
        pages[i].add(nextTxn);
      }
    }

    mockEndpoint = EasyMock.createMock(WalletApi.class);
    for (int i = txnPages.length; i >= 0; i--) {
      Long txnID = i < txnPages.length ? pages[i].get(0)
                                                 .getTransactionId() : Long.MAX_VALUE;
      List<GetCharactersCharacterIdWalletTransactions200Ok> data = i > 0 ? pages[i - 1] : Collections.emptyList();
      ApiResponse<List<GetCharactersCharacterIdWalletTransactions200Ok>> apir = new ApiResponse<>(200,
                                                                                                  createHeaders(
                                                                                                      "Expires",
                                                                                                      "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                                  data);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdWalletTransactionsWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.eq(txnID),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getWalletApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<WalletTransaction> storedData = AbstractESIAccountSync.retrieveAll(testTime,
                                                                            (long contid, AttributeSelector at) ->
                                                                                WalletTransaction.accessQuery(
                                                                                    charSyncAccount, contid, 1000,
                                                                                    false, at,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
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
    ESICharacterWalletTransactionSync sync = new ESICharacterWalletTransactionSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(txnTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_WALLET_TRANSACTIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount,
                                                              ESISyncEndpoint.CHAR_WALLET_TRANSACTIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.  These should not be modified.  This is just a copy of the existing test data with
    // lower ref IDs and slightly adjusted data values.
    Object[][] allData = new Object[txnTestData.length * 2][11];
    System.arraycopy(txnTestData, 0, allData, txnTestData.length, txnTestData.length);
    long firstDate = (Long) txnTestData[0][1] - txnTestData.length - 1;
    for (int i = 0; i < allData.length / 2; i++) {
      System.arraycopy(allData[i + txnTestData.length], 0, allData[i], 0, 11);
      allData[i][1] = firstDate + i;
      Object[] dt = allData[i];
      WalletTransaction newEl = new WalletTransaction(1,
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
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterWalletTransactionSync sync = new ESICharacterWalletTransactionSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    verifyDataUpdate(allData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_WALLET_TRANSACTIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount,
                                                              ESISyncEndpoint.CHAR_WALLET_TRANSACTIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
