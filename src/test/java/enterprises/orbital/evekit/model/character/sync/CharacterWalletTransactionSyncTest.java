package enterprises.orbital.evekit.model.character.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.WalletTransaction;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IWalletTransaction;

public class CharacterWalletTransactionSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  static Object[][]      testData;
  static Object[][]      largeTestData;

  static {
    // Comparator for sorting test data in decreasing order by transactionID (testData[i][1])
    Comparator<Object[]> testDataCompare = new Comparator<Object[]>() {

      @Override
      public int compare(
                         Object[] arg0,
                         Object[] arg1) {
        long v0 = (Long) arg0[1];
        long v1 = (Long) arg1[1];

        if (v0 > v1) { return -1; }
        if (v0 == v1) { return 0; }
        return 1;
      }
    };

    // Generate a "normal" amount of test data, and also generate a large amount of test data which should force the sync to finish early due to retrieving the
    // max number of entries.
    {
      // "Normal" data
      int size = 20 + TestBase.getRandomInt(20);
      testData = new Object[size][15];
      for (int i = 0; i < size; i++) {
        testData[i][0] = 1000; // fixed for character accounts
        testData[i][1] = TestBase.getUniqueRandomLong();
        testData[i][2] = testData[i][1];
        testData[i][3] = TestBase.getRandomInt();
        testData[i][4] = TestBase.getRandomText(50);
        testData[i][5] = TestBase.getRandomInt();
        testData[i][6] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
        testData[i][7] = TestBase.getRandomLong();
        testData[i][8] = TestBase.getRandomText(50);
        testData[i][9] = TestBase.getRandomInt();
        testData[i][10] = TestBase.getRandomText(50);
        testData[i][11] = TestBase.getRandomText(50);
        testData[i][12] = TestBase.getRandomText(50);
        testData[i][13] = TestBase.getRandomLong();
        testData[i][14] = TestBase.getRandomInt();
      }

      // Sort test data in decreasing order by transactionID (testData[i][1])
      Arrays.sort(testData, 0, testData.length, testDataCompare);
    }

    {
      // "Large" data
      int size = CharacterWalletJournalSync.MAX_RECORD_DOWNLOAD + TestBase.getRandomInt(200);
      largeTestData = new Object[size][15];
      for (int i = 0; i < size; i++) {
        largeTestData[i][0] = 1000; // fixed for character accounts
        largeTestData[i][1] = TestBase.getUniqueRandomLong();
        largeTestData[i][2] = largeTestData[i][1];
        largeTestData[i][3] = TestBase.getRandomInt();
        largeTestData[i][4] = TestBase.getRandomText(50);
        largeTestData[i][5] = TestBase.getRandomInt();
        largeTestData[i][6] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
        largeTestData[i][7] = TestBase.getRandomLong();
        largeTestData[i][8] = TestBase.getRandomText(50);
        largeTestData[i][9] = TestBase.getRandomInt();
        largeTestData[i][10] = TestBase.getRandomText(50);
        largeTestData[i][11] = TestBase.getRandomText(50);
        largeTestData[i][12] = TestBase.getRandomText(50);
        largeTestData[i][13] = TestBase.getRandomLong();
        largeTestData[i][14] = TestBase.getRandomInt();
      }

      // Sort test data in decreasing order by transactionID (largeTestData[i][1])
      Arrays.sort(largeTestData, 0, largeTestData.length, testDataCompare);
    }

  }

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IWalletTransaction makeTransaction(
                                            final Object[] instanceData,
                                            final String tweak) {
    return new IWalletTransaction() {

      @Override
      public String getTypeName() {
        return (String) instanceData[4] + tweak;
      }

      @Override
      public int getTypeID() {
        return (Integer) instanceData[5];
      }

      @Override
      public String getTransactionType() {
        return (String) instanceData[11] + tweak;
      }

      @Override
      public long getTransactionID() {
        return (Long) instanceData[1];
      }

      @Override
      public String getTransactionFor() {
        return (String) instanceData[12] + tweak;
      }

      @Override
      public Date getTransactionDateTime() {
        return new Date((Long) instanceData[2]);
      }

      @Override
      public String getStationName() {
        return (String) instanceData[10] + tweak;
      }

      @Override
      public long getStationID() {
        return (Integer) instanceData[9];
      }

      @Override
      public long getQuantity() {
        return (Integer) instanceData[3];
      }

      @Override
      public BigDecimal getPrice() {
        return (BigDecimal) instanceData[6];
      }

      @Override
      public long getJournalTransactionID() {
        return (Long) instanceData[13];
      }

      @Override
      public String getClientName() {
        return (String) instanceData[8] + tweak;
      }

      @Override
      public long getClientID() {
        return (Long) instanceData[7];
      }

      @Override
      public String getCharacterName() {
        // unused for character wallet transactions
        return null;
      }

      @Override
      public long getCharacterID() {
        // unused for character wallet transactions
        return 0;
      }

      @Override
      public int getClientTypeID() {
        return (Integer) instanceData[14];
      }
    };
  }

  public Collection<IWalletTransaction> assembleEntries(
                                                        Object[][] source,
                                                        int count,
                                                        Long startingTransactionID,
                                                        boolean forward,
                                                        String tweak) {
    List<IWalletTransaction> entries = new ArrayList<IWalletTransaction>();
    if (!forward) {
      for (int i = 0; i < source.length && count > 0; i++) {
        long transactionID = (Long) source[i][1];
        if (transactionID < startingTransactionID) {
          entries.add(makeTransaction(source[i], tweak));
          count--;
        }
      }
    } else {
      for (int i = source.length - 1; i >= 0 && count > 0; i--) {
        long transactionID = (Long) source[i][1];
        if (transactionID > startingTransactionID) {
          entries.add(makeTransaction(source[i], tweak));
          count--;
        }
      }
    }

    return entries;
  }

  public WalletTransaction makeTransactionObject(
                                                 long time,
                                                 Object[] instanceData,
                                                 String tweak)
    throws Exception {
    int accountKey = (Integer) instanceData[0];
    long transactionID = (Long) instanceData[1];
    long date = (Long) instanceData[2];
    WalletTransaction entry = new WalletTransaction(
        accountKey, transactionID, date, (Integer) instanceData[3], (String) instanceData[4] + tweak, (Integer) instanceData[5], (BigDecimal) instanceData[6],
        (Long) instanceData[7], (String) instanceData[8] + tweak, (Integer) instanceData[9], (String) instanceData[10] + tweak,
        (String) instanceData[11] + tweak, (String) instanceData[12] + tweak, (Long) instanceData[13], (Integer) instanceData[14], 0, null);
    entry.setup(syncAccount, time);
    return entry;
  }

  public void setupOkMock(
                          final String tweak,
                          final boolean useLarge)
    throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);

    IAnswer<Collection<IWalletTransaction>> forwardMockAnswerer = new IAnswer<Collection<IWalletTransaction>>() {

      long lastTransactionID = (Long) (useLarge ? largeTestData[largeTestData.length / 2][1] : testData[testData.length / 2][1]);

      @Override
      public Collection<IWalletTransaction> answer() throws Throwable {
        Collection<IWalletTransaction> entries = assembleEntries(useLarge ? largeTestData : testData, 20, lastTransactionID, true, tweak);
        for (IWalletTransaction next : entries) {
          if (next.getTransactionID() > lastTransactionID) {
            lastTransactionID = next.getTransactionID();
          }
        }
        return entries;
      }
    };

    IAnswer<Collection<IWalletTransaction>> backwardMockAnswerer = new IAnswer<Collection<IWalletTransaction>>() {

      @Override
      public Collection<IWalletTransaction> answer() throws Throwable {
        long startingRefID = (Long) (EasyMock.getCurrentArguments())[0];
        return assembleEntries(useLarge ? largeTestData : testData, 20, startingRefID, false, tweak);
      }
    };

    // Sequence should be a plain request, followed by specific requests until we run out of elements to serve.
    EasyMock.expect(mockServer.requestWalletTransactions()).andAnswer(forwardMockAnswerer);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.requestWalletTransactions(EasyMock.anyLong())).andAnswer(backwardMockAnswerer);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
    EasyMock.expectLastCall().anyTimes();
  }

  public void compareAgainstTestData(
                                     WalletTransaction entry,
                                     Object[] instanceData,
                                     String tweak) {
    Assert.assertEquals(entry.getAccountKey(), (int) ((Integer) instanceData[0]));
    Assert.assertEquals(entry.getTransactionID(), (long) ((Long) instanceData[1]));
    Assert.assertEquals(entry.getDate(), (long) ((Long) instanceData[2]));
    Assert.assertEquals(entry.getQuantity(), (int) ((Integer) instanceData[3]));
    Assert.assertEquals(entry.getTypeName(), (String) instanceData[4] + tweak);
    Assert.assertEquals(entry.getTypeID(), (int) ((Integer) instanceData[5]));
    Assert.assertEquals(entry.getPrice(), instanceData[6]);
    Assert.assertEquals(entry.getClientID(), (long) ((Long) instanceData[7]));
    Assert.assertEquals(entry.getClientName(), (String) instanceData[8] + tweak);
    Assert.assertEquals(entry.getStationID(), (int) ((Integer) instanceData[9]));
    Assert.assertEquals(entry.getStationName(), (String) instanceData[10] + tweak);
    Assert.assertEquals(entry.getTransactionType(), (String) instanceData[11] + tweak);
    Assert.assertEquals(entry.getTransactionFor(), (String) instanceData[12] + tweak);
    Assert.assertEquals(entry.getJournalTransactionID(), (long) ((Long) instanceData[13]));
    Assert.assertEquals(entry.getClientTypeID(), (int) ((Integer) instanceData[14]));
    Assert.assertEquals(entry.getCharacterID(), 0L);
    Assert.assertNull(entry.getCharacterName());
  }

  // Test update with all new wallet transactions, normal sized data
  @Test
  public void testCharacterWalletTransactionSyncNormalUpdate() throws Exception {
    setupOkMock("", false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterWalletTransactionSync.syncCharacterWalletTransaction(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify entries were added correctly.
    for (int i = 0; i < testData.length; i++) {
      long transactionID = (Long) testData[i][1];
      WalletTransaction entry = WalletTransaction.get(syncAccount, testTime, transactionID);
      compareAgainstTestData(entry, testData[i], "");
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getWalletTransactionsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsDetail());
  }

  // Test update with all new wallet transactions, large amount of data
  @Test
  public void testCharacterWalletTransactionSyncLargeUpdate() throws Exception {
    if (Boolean.valueOf(System.getProperty("org.evekit.model.unittest.skipbig", "false"))) { return; }

    setupOkMock("", true);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterWalletTransactionSync.syncCharacterWalletTransaction(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify entries were added correctly. There are more entries than we will populate, but all forward items should be populated so we check going backwards
    // until we hit the max population count. Note that more records than this may be populated because we populate as many records as the server is willing to
    // provide.
    int i;
    for (i = 0; i < CharacterWalletTransactionSync.MAX_RECORD_DOWNLOAD; i++) {
      long transactionID = (Long) largeTestData[i][1];
      WalletTransaction entry = WalletTransaction.get(syncAccount, testTime, transactionID);
      compareAgainstTestData(entry, largeTestData[i], "");
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getWalletTransactionsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsDetail());
  }

  // Test update with entries already populated
  @Test
  public void testCharacterWalletTransactionSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock("", false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing wallet entries which should not be modified.
    for (int i = 0; i < testData.length; i++) {
      CachedData.update(makeTransactionObject(testTime, testData[i], "foo"));
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterWalletTransactionSync.syncCharacterWalletTransaction(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify previously added entries are unmodified.
    for (int i = 0; i < testData.length; i++) {
      long transactionID = (Long) testData[i][1];
      WalletTransaction entry = WalletTransaction.get(syncAccount, testTime, transactionID);
      compareAgainstTestData(entry, testData[i], "foo");
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getWalletTransactionsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterWalletTransactionSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock("foo", false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing wallet entries which should not be modified.
    for (int i = 0; i < testData.length; i++) {
      CachedData.update(makeTransactionObject(testTime, testData[i], "foo"));
    }

    // Set the tracker as already updated and populate the container
    tracker.setWalletTransactionsStatus(SyncState.UPDATED);
    tracker.setWalletTransactionsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setWalletTransactionsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterWalletTransactionSync.syncCharacterWalletTransaction(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify previously added entries are unmodified.
    for (int i = 0; i < testData.length; i++) {
      long transactionID = (Long) testData[i][1];
      WalletTransaction entry = WalletTransaction.get(syncAccount, testTime, transactionID);
      compareAgainstTestData(entry, testData[i], "foo");
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getWalletTransactionsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getWalletTransactionsDetail());
  }

}
