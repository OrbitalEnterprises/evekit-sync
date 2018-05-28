package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionJournal200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.WalletJournal;
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
import java.util.stream.Collectors;

public class ESICorporationWalletJournalSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private WalletApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][][] journalTestData;
  private static int[][] journalPages;

  static {
    // Comparator for sorting test data in decreasing order by refID (testData[i][1])
    Comparator<Object[]> journalDataCompare = Comparator.comparingLong(x -> (Long) x[1]);

    // Generate large batch of test data which we will split into pages to ensure sync handles
    // paging correctly.
    //
    // 0 int division;
    // 1 long refID;
    // 2 long date = -1;
    // 3 String refType;
    // 4 int firstPartyID;
    // 5 int secondPartyID;
    // 6 String argName1;
    // 7 long argID1;
    // 8 BigDecimal amount;
    // 9 BigDecimal balance;
    // 10 String reason;
    // 11 int taxReceiverID;
    // 12 BigDecimal taxAmount;
    // 13 long contextID;
    // 14 String contextType;
    // 15 String description;

    // Generate separate batches and page offsets for each division
    journalTestData = new Object[7][][];
    journalPages = new int[7][];

    for (int division = 0; division < 7; division++) {
      int size = 1000 + TestBase.getRandomInt(1000);
      journalTestData[division] = new Object[size][16];
      int refTypeLen = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.RefTypeEnum.values().length;
      int ctxTypeLen = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.ContextIdTypeEnum.values().length;
      for (int i = 0; i < size; i++) {
        journalTestData[division][i][0] = division + 1;
        journalTestData[division][i][1] = TestBase.getUniqueRandomLong();
        journalTestData[division][i][2] = journalTestData[division][i][1];
        journalTestData[division][i][3] = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.RefTypeEnum.values()[TestBase.getRandomInt(
            refTypeLen)];
        journalTestData[division][i][4] = TestBase.getRandomInt();
        journalTestData[division][i][5] = TestBase.getRandomInt();
        journalTestData[division][i][6] = null;
        journalTestData[division][i][7] = 0L;
        journalTestData[division][i][8] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2,
                                                                                                       RoundingMode.HALF_UP);
        journalTestData[division][i][9] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2,
                                                                                                       RoundingMode.HALF_UP);
        journalTestData[division][i][10] = TestBase.getRandomText(50);
        journalTestData[division][i][11] = TestBase.getRandomInt();
        journalTestData[division][i][12] = (new BigDecimal(TestBase.getRandomDouble(1000))).setScale(2,
                                                                                                     RoundingMode.HALF_UP);
        journalTestData[division][i][13] = TestBase.getRandomLong();
        journalTestData[division][i][14] = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.ContextIdTypeEnum.values()[TestBase.getRandomInt(
            ctxTypeLen)];
        journalTestData[division][i][15] = TestBase.getRandomText(50);
      }

      // Sort test data in increasing order by refID (journalTestData[i][1])
      Arrays.sort(journalTestData[division], 0, journalTestData[division].length, journalDataCompare);

      // Divide data into pages to test paging sync feature
      int pageCount = 3 + TestBase.getRandomInt(3);
      journalPages[division] = new int[pageCount];
      for (int i = pageCount - 1; i >= 0; i--) {
        journalPages[division][i] = size - (pageCount - i - 1) * (size / pageCount);
      }

    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_JOURNAL, 1234L,
                                                        null);

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
                                                        .createQuery("DELETE FROM WalletJournal ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {

    mockEndpoint = EasyMock.createMock(WalletApi.class);

    @SuppressWarnings("unchecked")
    List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>[][] pages = new List[7][];
    for (int division = 0; division < 7; division++) {
      //noinspection unchecked
      pages[division] = new List[journalPages[division].length];
      for (int i = 0; i < journalPages[division].length; i++) {
        pages[division][i] = new ArrayList<>();
        int limit = i + 1 == journalPages[division].length ? journalTestData[division].length : journalPages[division][i + 1];
        for (int j = journalPages[division][i]; j < limit; j++) {
          GetCorporationsCorporationIdWalletsDivisionJournal200Ok nextWallet = new GetCorporationsCorporationIdWalletsDivisionJournal200Ok();
          Object[] dt = journalTestData[division][j];
          nextWallet.setId((Long) dt[1]);
          nextWallet.setDate(new DateTime(new Date((Long) dt[2])));
          nextWallet.setRefType((GetCorporationsCorporationIdWalletsDivisionJournal200Ok.RefTypeEnum) dt[3]);
          nextWallet.setFirstPartyId((Integer) dt[4]);
          nextWallet.setSecondPartyId((Integer) dt[5]);
          nextWallet.setAmount(((BigDecimal) dt[8]).doubleValue());
          nextWallet.setBalance(((BigDecimal) dt[9]).doubleValue());
          nextWallet.setReason((String) dt[10]);
          nextWallet.setTaxReceiverId((Integer) dt[11]);
          nextWallet.setTax(((BigDecimal) dt[12]).doubleValue());
          nextWallet.setContextId((Long) dt[13]);
          nextWallet.setContextIdType(
              (GetCorporationsCorporationIdWalletsDivisionJournal200Ok.ContextIdTypeEnum) dt[14]);
          nextWallet.setDescription((String) dt[15]);
          pages[division][i].add(nextWallet);
        }
      }
    }

    mockEndpoint = EasyMock.createMock(WalletApi.class);

    for (int division = 0; division < 7; division++) {

      List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok> journalList =
          Arrays.stream(journalTestData[division])
                .map(dt -> {
                  GetCorporationsCorporationIdWalletsDivisionJournal200Ok nextWallet = new GetCorporationsCorporationIdWalletsDivisionJournal200Ok();
                  nextWallet.setId((Long) dt[1]);
                  nextWallet.setDate(new DateTime(new Date((Long) dt[2])));
                  nextWallet.setRefType((GetCorporationsCorporationIdWalletsDivisionJournal200Ok.RefTypeEnum) dt[3]);
                  nextWallet.setFirstPartyId((Integer) dt[4]);
                  nextWallet.setSecondPartyId((Integer) dt[5]);
                  nextWallet.setAmount(((BigDecimal) dt[8]).doubleValue());
                  nextWallet.setBalance(((BigDecimal) dt[9]).doubleValue());
                  nextWallet.setReason((String) dt[10]);
                  nextWallet.setTaxReceiverId((Integer) dt[11]);
                  nextWallet.setTax(((BigDecimal) dt[12]).doubleValue());
                  nextWallet.setContextId((Long) dt[13]);
                  nextWallet.setContextIdType(
                      (GetCorporationsCorporationIdWalletsDivisionJournal200Ok.ContextIdTypeEnum) dt[14]);
                  nextWallet.setDescription((String) dt[15]);
                  return nextWallet;
                })
                .collect(Collectors.toList());

      int last = 0;
      for (int i = 0; i < journalPages[division].length; i++) {
        Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                          String.valueOf(journalPages[division].length));
        ApiResponse<List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>> apir = new ApiResponse<>(200,
                                                                                                            headers,
                                                                                                            journalList.subList(
                                                                                                                last,
                                                                                                                journalPages[division][i]));
        EasyMock.expect(mockEndpoint.getCorporationsCorporationIdWalletsDivisionJournalWithHttpInfo(
            EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
            EasyMock.eq(division + 1),
            EasyMock.isNull(),
            EasyMock.isNull(),
            EasyMock.eq(i + 1),
            EasyMock.anyString()))
                .andReturn(apir);
        last = journalPages[division][i];
      }
    }

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getWalletApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(Object[][] testData, int division) throws Exception {
    // Retrieve all stored data
    List<WalletJournal> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        WalletJournal.accessQuery(corpSyncAccount, contid, 1000, false, at,
                                  new AttributeSelector("{ values: [" + division + "]}"),
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      WalletJournal nextEl = storedData.get(i);
      Object[] dt = testData[i];
      Assert.assertEquals((int) (Integer) dt[0], nextEl.getDivision());
      Assert.assertEquals((long) (Long) dt[1], nextEl.getRefID());
      Assert.assertEquals((long) (Long) dt[2], nextEl.getDate());
      Assert.assertEquals(dt[3].toString(), nextEl.getRefType());
      Assert.assertEquals((int) (Integer) dt[4], nextEl.getFirstPartyID());
      Assert.assertEquals((int) (Integer) dt[5], nextEl.getSecondPartyID());
      Assert.assertEquals(dt[6], nextEl.getArgName1());
      Assert.assertEquals((long) (Long) dt[7], nextEl.getArgID1());
      Assert.assertEquals(dt[8], nextEl.getAmount());
      Assert.assertEquals(dt[9], nextEl.getBalance());
      Assert.assertEquals(dt[10], nextEl.getReason());
      Assert.assertEquals((int) (Integer) dt[11], nextEl.getTaxReceiverID());
      Assert.assertEquals(dt[12], nextEl.getTaxAmount());
      Assert.assertEquals((long) (Long) dt[13], nextEl.getContextID());
      Assert.assertEquals(dt[14].toString(), nextEl.getContextType());
      Assert.assertEquals(dt[15], nextEl.getDescription());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationWalletJournalSync sync = new ESICorporationWalletJournalSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    for (int division = 0; division < 7; division++)
      verifyDataUpdate(journalTestData[division], division + 1);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_WALLET_JOURNAL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_JOURNAL);
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
      allData[division] = new Object[journalTestData[division].length * 2][16];
      System.arraycopy(journalTestData[division], 0, allData[division], journalTestData[division].length,
                       journalTestData[division].length);
      long firstDate = (Long) journalTestData[division][0][1] - journalTestData[division].length - 1;
      for (int i = 0; i < allData[division].length / 2; i++) {
        System.arraycopy(allData[division][i + journalTestData[division].length], 0, allData[division][i], 0, 16);
        allData[division][i][1] = firstDate + i;
        Object[] dt = allData[division][i];
        WalletJournal newEl = new WalletJournal(division + 1,
                                                (Long) dt[1],
                                                (Long) dt[2],
                                                dt[3].toString(),
                                                (Integer) dt[4],
                                                (Integer) dt[5],
                                                (String) dt[6],
                                                (Long) dt[7],
                                                (BigDecimal) dt[8],
                                                (BigDecimal) dt[9],
                                                (String) dt[10],
                                                (Integer) dt[11],
                                                (BigDecimal) dt[12],
                                                (Long) dt[13],
                                                dt[14].toString(),
                                                (String) dt[15]);
        newEl.setup(corpSyncAccount, testTime - 1);
        CachedData.update(newEl);
      }
    }

    // Perform the sync
    ESICorporationWalletJournalSync sync = new ESICorporationWalletJournalSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    for (int division = 0; division < 7; division++)
      verifyDataUpdate(allData[division], division + 1);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_WALLET_JOURNAL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_JOURNAL);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
