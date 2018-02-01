package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdWalletJournal200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdWalletJournalExtraInfo;
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

public class ESICharacterWalletJournalSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private WalletApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] journalTestData;
  private static int[] journalPages;

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

    int size = 1000 + TestBase.getRandomInt(1000);
    journalTestData = new Object[size][27];
    int refTypeLen = GetCharactersCharacterIdWalletJournal200Ok.RefTypeEnum.values().length;
    int pt1TypeLen = GetCharactersCharacterIdWalletJournal200Ok.FirstPartyTypeEnum.values().length;
    int pt2TypeLen = GetCharactersCharacterIdWalletJournal200Ok.SecondPartyTypeEnum.values().length;
    for (int i = 0; i < size; i++) {
      journalTestData[i][0] = 1; // fixed for character accounts
      journalTestData[i][1] = TestBase.getUniqueRandomLong();
      journalTestData[i][2] = journalTestData[i][1];
      journalTestData[i][3] = GetCharactersCharacterIdWalletJournal200Ok.RefTypeEnum.values()[TestBase.getRandomInt(
          refTypeLen)];
      journalTestData[i][4] = TestBase.getRandomInt();
      journalTestData[i][5] = GetCharactersCharacterIdWalletJournal200Ok.FirstPartyTypeEnum.values()[TestBase.getRandomInt(
          pt1TypeLen)];
      journalTestData[i][6] = TestBase.getRandomInt();
      journalTestData[i][7] = GetCharactersCharacterIdWalletJournal200Ok.SecondPartyTypeEnum.values()[TestBase.getRandomInt(
          pt2TypeLen)];
      journalTestData[i][8] = null;
      journalTestData[i][9] = 0L;
      journalTestData[i][10] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
      journalTestData[i][11] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
      journalTestData[i][12] = TestBase.getRandomText(50);
      journalTestData[i][13] = TestBase.getRandomInt();
      journalTestData[i][14] = (new BigDecimal(TestBase.getRandomDouble(1000))).setScale(2, RoundingMode.HALF_UP);
      journalTestData[i][15] = TestBase.getRandomLong();
      journalTestData[i][16] = TestBase.getRandomLong();
      journalTestData[i][17] = TestBase.getRandomText(50);
      journalTestData[i][18] = TestBase.getRandomInt();
      journalTestData[i][19] = TestBase.getRandomInt();
      journalTestData[i][20] = TestBase.getRandomInt();
      journalTestData[i][21] = TestBase.getRandomInt();
      journalTestData[i][22] = TestBase.getRandomInt();
      journalTestData[i][23] = TestBase.getRandomInt();
      journalTestData[i][24] = TestBase.getRandomInt();
      journalTestData[i][25] = TestBase.getRandomInt();
      journalTestData[i][26] = TestBase.getRandomInt();
    }

    // Sort test data in decreasing order by refID (journalTestData[i][1])
    Arrays.sort(journalTestData, 0, journalTestData.length, journalDataCompare);

    // Divide data into pages to test paging sync feature
    int pageCount = 3 + TestBase.getRandomInt(3);
    journalPages = new int[pageCount];
    for (int i = 0; i < pageCount; i++) {
      journalPages[i] = i * size / pageCount;
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_WALLET_JOURNAL, 1234L);

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
    List<GetCharactersCharacterIdWalletJournal200Ok>[] pages = new List[journalPages.length];
    for (int i = 0; i < journalPages.length; i++) {
      pages[i] = new ArrayList<>();
      int limit = i + 1 == journalPages.length ? journalTestData.length : journalPages[i + 1];
      for (int j = journalPages[i]; j < limit; j++) {
        GetCharactersCharacterIdWalletJournal200Ok nextWallet = new GetCharactersCharacterIdWalletJournal200Ok();
        GetCharactersCharacterIdWalletJournalExtraInfo info = new GetCharactersCharacterIdWalletJournalExtraInfo();
        Object[] dt = journalTestData[j];
        nextWallet.setRefId((Long) dt[1]);
        nextWallet.setDate(new DateTime(new Date((Long) dt[2])));
        nextWallet.setRefType((GetCharactersCharacterIdWalletJournal200Ok.RefTypeEnum) dt[3]);
        nextWallet.setFirstPartyId((Integer) dt[4]);
        nextWallet.setFirstPartyType((GetCharactersCharacterIdWalletJournal200Ok.FirstPartyTypeEnum) dt[5]);
        nextWallet.setSecondPartyId((Integer) dt[6]);
        nextWallet.setSecondPartyType((GetCharactersCharacterIdWalletJournal200Ok.SecondPartyTypeEnum) dt[7]);
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
        pages[i].add(nextWallet);
      }
    }

    mockEndpoint = EasyMock.createMock(WalletApi.class);
    for (int i = journalPages.length; i >= 0; i--) {
      Long refID = i < journalPages.length ? pages[i].get(0)
                                                     .getRefId() : Long.MAX_VALUE;
      List<GetCharactersCharacterIdWalletJournal200Ok> data = i > 0 ? pages[i - 1] : Collections.emptyList();
      ApiResponse<List<GetCharactersCharacterIdWalletJournal200Ok>> apir = new ApiResponse<>(200,
                                                                                             createHeaders("Expires",
                                                                                                           "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                             data);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdWalletJournalWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.eq(refID),
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
    List<WalletJournal> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        WalletJournal.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
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
    ESICharacterWalletJournalSync sync = new ESICharacterWalletJournalSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(journalTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_WALLET_JOURNAL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_WALLET_JOURNAL);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.  These should not be modified.  This is just a copy of the existing test data with
    // lower ref IDs and slightly adjusted data values.
    Object[][] allData = new Object[journalTestData.length * 2][27];
    System.arraycopy(journalTestData, 0, allData, journalTestData.length, journalTestData.length);
    long firstDate = (Long) journalTestData[0][1] - journalTestData.length - 1;
    for (int i = 0; i < allData.length / 2; i++) {
      System.arraycopy(allData[i + journalTestData.length], 0, allData[i], 0, 27);
      allData[i][1] = firstDate + i;
      Object[] dt = allData[i];
      WalletJournal newEl = new WalletJournal(1,
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
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterWalletJournalSync sync = new ESICharacterWalletJournalSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    verifyDataUpdate(allData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_WALLET_JOURNAL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_WALLET_JOURNAL);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
