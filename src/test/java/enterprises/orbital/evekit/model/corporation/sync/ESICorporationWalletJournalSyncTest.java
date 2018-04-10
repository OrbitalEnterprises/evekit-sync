package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionJournal200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWalletsDivisionJournalExtraInfo;
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
    // 5 String firstPartyType;
    // 6 int secondPartyID;
    // 7 String secondPartyType;
    // 8 String argName1;
    // 9 long argID1;
    // 10 BigDecimal amount;
    // 11 BigDecimal balance;
    // 12 String reason;
    // 13 int taxReceiverID;
    // 14 BigDecimal taxAmount;
    // 15 long locationID;
    // 16 long transactionID;
    // 17 String npcName;
    // 18 int npcID;
    // 19 int destroyedShipTypeID;
    // 20 int characterID;
    // 21 int corporationID;
    // 22 int allianceID;
    // 23 int jobID;
    // 24 int contractID;
    // 25 int systemID;
    // 26 int planetID;

    // Generate separate batches and page offsets for each division
    journalTestData = new Object[7][][];
    journalPages = new int[7][];

    for (int division = 0; division < 7; division++) {
      int size = 1000 + TestBase.getRandomInt(1000);
      journalTestData[division] = new Object[size][27];
      int refTypeLen = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.RefTypeEnum.values().length;
      int pt1TypeLen = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.FirstPartyTypeEnum.values().length;
      int pt2TypeLen = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.SecondPartyTypeEnum.values().length;
      for (int i = 0; i < size; i++) {
        journalTestData[division][i][0] = division + 1;
        journalTestData[division][i][1] = TestBase.getUniqueRandomLong();
        journalTestData[division][i][2] = journalTestData[division][i][1];
        journalTestData[division][i][3] = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.RefTypeEnum.values()[TestBase.getRandomInt(
            refTypeLen)];
        journalTestData[division][i][4] = TestBase.getRandomInt();
        journalTestData[division][i][5] = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.FirstPartyTypeEnum.values()[TestBase.getRandomInt(
            pt1TypeLen)];
        journalTestData[division][i][6] = TestBase.getRandomInt();
        journalTestData[division][i][7] = GetCorporationsCorporationIdWalletsDivisionJournal200Ok.SecondPartyTypeEnum.values()[TestBase.getRandomInt(
            pt2TypeLen)];
        journalTestData[division][i][8] = null;
        journalTestData[division][i][9] = 0L;
        journalTestData[division][i][10] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2,
                                                                                                        RoundingMode.HALF_UP);
        journalTestData[division][i][11] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2,
                                                                                                        RoundingMode.HALF_UP);
        journalTestData[division][i][12] = TestBase.getRandomText(50);
        journalTestData[division][i][13] = TestBase.getRandomInt();
        journalTestData[division][i][14] = (new BigDecimal(TestBase.getRandomDouble(1000))).setScale(2,
                                                                                                     RoundingMode.HALF_UP);
        journalTestData[division][i][15] = TestBase.getRandomLong();
        journalTestData[division][i][16] = TestBase.getRandomLong();
        journalTestData[division][i][17] = TestBase.getRandomText(50);
        journalTestData[division][i][18] = TestBase.getRandomInt();
        journalTestData[division][i][19] = TestBase.getRandomInt();
        journalTestData[division][i][20] = TestBase.getRandomInt();
        journalTestData[division][i][21] = TestBase.getRandomInt();
        journalTestData[division][i][22] = TestBase.getRandomInt();
        journalTestData[division][i][23] = TestBase.getRandomInt();
        journalTestData[division][i][24] = TestBase.getRandomInt();
        journalTestData[division][i][25] = TestBase.getRandomInt();
        journalTestData[division][i][26] = TestBase.getRandomInt();
      }

      // Sort test data in increasing order by refID (journalTestData[i][1])
      Arrays.sort(journalTestData[division], 0, journalTestData[division].length, journalDataCompare);

      // Divide data into pages to test paging sync feature
      int pageCount = 3 + TestBase.getRandomInt(3);
      journalPages[division] = new int[pageCount];
      for (int i = 0; i < pageCount; i++) {
        journalPages[division][i] = i * size / pageCount;
      }
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_JOURNAL, 1234L, null);

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
          GetCorporationsCorporationIdWalletsDivisionJournalExtraInfo info = new GetCorporationsCorporationIdWalletsDivisionJournalExtraInfo();
          Object[] dt = journalTestData[division][j];
          nextWallet.setRefId((Long) dt[1]);
          nextWallet.setDate(new DateTime(new Date((Long) dt[2])));
          nextWallet.setRefType((GetCorporationsCorporationIdWalletsDivisionJournal200Ok.RefTypeEnum) dt[3]);
          nextWallet.setFirstPartyId((Integer) dt[4]);
          nextWallet.setFirstPartyType(
              (GetCorporationsCorporationIdWalletsDivisionJournal200Ok.FirstPartyTypeEnum) dt[5]);
          nextWallet.setSecondPartyId((Integer) dt[6]);
          nextWallet.setSecondPartyType(
              (GetCorporationsCorporationIdWalletsDivisionJournal200Ok.SecondPartyTypeEnum) dt[7]);
          nextWallet.setAmount(((BigDecimal) dt[10]).doubleValue());
          nextWallet.setBalance(((BigDecimal) dt[11]).doubleValue());
          nextWallet.setReason((String) dt[12]);
          nextWallet.setTaxReceiverId((Integer) dt[13]);
          nextWallet.setTax(((BigDecimal) dt[14]).doubleValue());
          nextWallet.setExtraInfo(info);
          info.setLocationId((Long) dt[15]);
          info.setTransactionId((Long) dt[16]);
          info.setNpcName((String) dt[17]);
          info.setNpcId((Integer) dt[18]);
          info.setDestroyedShipTypeId((Integer) dt[19]);
          info.setCharacterId((Integer) dt[20]);
          info.setCorporationId((Integer) dt[21]);
          info.setAllianceId((Integer) dt[22]);
          info.setJobId((Integer) dt[23]);
          info.setContractId((Integer) dt[24]);
          info.setSystemId((Integer) dt[25]);
          info.setPlanetId((Integer) dt[26]);
          pages[division][i].add(nextWallet);
        }
      }
    }

    mockEndpoint = EasyMock.createMock(WalletApi.class);
    for (int division = 0; division < 7; division++) {
      for (int i = journalPages[division].length; i >= 0; i--) {
        Long refID = i < journalPages[division].length ? pages[division][i].get(0)
                                                                           .getRefId() : Long.MAX_VALUE;
        List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok> data = i > 0 ? pages[division][i - 1] : Collections.emptyList();
        ApiResponse<List<GetCorporationsCorporationIdWalletsDivisionJournal200Ok>> apir = new ApiResponse<>(200,
                                                                                                            createHeaders(
                                                                                                                "Expires",
                                                                                                                "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                                            data);
        EasyMock.expect(mockEndpoint.getCorporationsCorporationIdWalletsDivisionJournalWithHttpInfo(
            EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
            EasyMock.eq(division + 1),
            EasyMock.isNull(),
            EasyMock.eq(refID),
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
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                                  AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

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
      Assert.assertEquals(dt[5].toString(), nextEl.getFirstPartyType());
      Assert.assertEquals((int) (Integer) dt[6], nextEl.getSecondPartyID());
      Assert.assertEquals(dt[7].toString(), nextEl.getSecondPartyType());
      Assert.assertEquals(dt[8], nextEl.getArgName1());
      Assert.assertEquals((long) (Long) dt[9], nextEl.getArgID1());
      Assert.assertEquals(dt[10], nextEl.getAmount());
      Assert.assertEquals(dt[11], nextEl.getBalance());
      Assert.assertEquals(dt[12], nextEl.getReason());
      Assert.assertEquals((int) (Integer) dt[13], nextEl.getTaxReceiverID());
      Assert.assertEquals(dt[14], nextEl.getTaxAmount());
      Assert.assertEquals((long) (Long) dt[15], nextEl.getLocationID());
      Assert.assertEquals((long) (Long) dt[16], nextEl.getTransactionID());
      Assert.assertEquals(dt[17], nextEl.getNpcName());
      Assert.assertEquals((int) (Integer) dt[18], nextEl.getNpcID());
      Assert.assertEquals((int) (Integer) dt[19], nextEl.getDestroyedShipTypeID());
      Assert.assertEquals((int) (Integer) dt[20], nextEl.getCharacterID());
      Assert.assertEquals((int) (Integer) dt[21], nextEl.getCorporationID());
      Assert.assertEquals((int) (Integer) dt[22], nextEl.getAllianceID());
      Assert.assertEquals((int) (Integer) dt[23], nextEl.getJobID());
      Assert.assertEquals((int) (Integer) dt[24], nextEl.getContractID());
      Assert.assertEquals((int) (Integer) dt[25], nextEl.getSystemID());
      Assert.assertEquals((int) (Integer) dt[26], nextEl.getPlanetID());
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
      allData[division] = new Object[journalTestData[division].length * 2][27];
      System.arraycopy(journalTestData[division], 0, allData[division], journalTestData[division].length,
                       journalTestData[division].length);
      long firstDate = (Long) journalTestData[division][0][1] - journalTestData[division].length - 1;
      for (int i = 0; i < allData[division].length / 2; i++) {
        System.arraycopy(allData[division][i + journalTestData[division].length], 0, allData[division][i], 0, 27);
        allData[division][i][1] = firstDate + i;
        Object[] dt = allData[division][i];
        WalletJournal newEl = new WalletJournal(division + 1,
                                                (Long) dt[1],
                                                (Long) dt[2],
                                                dt[3].toString(),
                                                (Integer) dt[4],
                                                dt[5].toString(),
                                                (Integer) dt[6],
                                                dt[7].toString(),
                                                (String) dt[8],
                                                (Long) dt[9],
                                                (BigDecimal) dt[10],
                                                (BigDecimal) dt[11],
                                                (String) dt[12],
                                                (Integer) dt[13],
                                                (BigDecimal) dt[14],
                                                (Long) dt[15],
                                                (Long) dt[16],
                                                (String) dt[17],
                                                (Integer) dt[18],
                                                (Integer) dt[19],
                                                (Integer) dt[20],
                                                (Integer) dt[21],
                                                (Integer) dt[22],
                                                (Integer) dt[23],
                                                (Integer) dt[24],
                                                (Integer) dt[25],
                                                (Integer) dt[26]);
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
