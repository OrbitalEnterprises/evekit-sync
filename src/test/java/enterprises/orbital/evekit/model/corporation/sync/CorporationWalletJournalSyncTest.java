package enterprises.orbital.evekit.model.corporation.sync;

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

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.WalletJournal;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;
import enterprises.orbital.evexmlapi.shared.IWalletJournalEntry;

public class CorporationWalletJournalSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  static int[]           accounts;
  static Object[][][]    testData;
  static Object[][][]    largeTestData;

  static {
    // Comparator for sorting test data in decreasing order by refID (testData[i][1])
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

    // Generate sample account keys.
    int numAccounts = 5 + TestBase.getRandomInt(5);
    accounts = new int[numAccounts];
    testData = new Object[numAccounts][][];
    largeTestData = new Object[numAccounts][][];

    for (int acct = 0; acct < numAccounts; acct++) {
      accounts[acct] = TestBase.getRandomInt(2000);

      // Generate a "normal" amount of test data, and also generate a large amount of test data which should force the sync to finish early due to retrieving
      // the max number of entries.
      {
        // "Normal" data
        int size = 20 + TestBase.getRandomInt(20);
        testData[acct] = new Object[size][17];
        for (int i = 0; i < size; i++) {
          testData[acct][i][0] = accounts[acct];
          testData[acct][i][1] = TestBase.getUniqueRandomLong();
          testData[acct][i][2] = testData[acct][i][1];
          testData[acct][i][3] = TestBase.getRandomInt();
          testData[acct][i][4] = TestBase.getRandomText(50);
          testData[acct][i][5] = TestBase.getRandomLong();
          testData[acct][i][6] = TestBase.getRandomText(50);
          testData[acct][i][7] = TestBase.getRandomLong();
          testData[acct][i][8] = TestBase.getRandomText(50);
          testData[acct][i][9] = TestBase.getRandomLong();
          testData[acct][i][10] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
          testData[acct][i][11] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
          testData[acct][i][12] = TestBase.getRandomText(50);
          testData[acct][i][13] = TestBase.getRandomLong();
          testData[acct][i][14] = (new BigDecimal(TestBase.getRandomDouble(1000))).setScale(2, RoundingMode.HALF_UP);
          testData[acct][i][15] = TestBase.getRandomInt();
          testData[acct][i][16] = TestBase.getRandomInt();
        }

        // Sort test data in decreasing order by refID (testData[i][1])
        Arrays.sort(testData[acct], 0, testData[acct].length, testDataCompare);
      }

      {
        // "Large" data
        int size = CorporationWalletJournalSync.MAX_RECORD_DOWNLOAD + TestBase.getRandomInt(200);
        largeTestData[acct] = new Object[size][17];
        for (int i = 0; i < size; i++) {
          largeTestData[acct][i][0] = accounts[acct];
          largeTestData[acct][i][1] = TestBase.getUniqueRandomLong();
          largeTestData[acct][i][2] = largeTestData[acct][i][1];
          largeTestData[acct][i][3] = TestBase.getRandomInt();
          largeTestData[acct][i][4] = TestBase.getRandomText(50);
          largeTestData[acct][i][5] = TestBase.getRandomLong();
          largeTestData[acct][i][6] = TestBase.getRandomText(50);
          largeTestData[acct][i][7] = TestBase.getRandomLong();
          largeTestData[acct][i][8] = TestBase.getRandomText(50);
          largeTestData[acct][i][9] = TestBase.getRandomLong();
          largeTestData[acct][i][10] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
          largeTestData[acct][i][11] = (new BigDecimal(TestBase.getRandomDouble(1000000))).setScale(2, RoundingMode.HALF_UP);
          largeTestData[acct][i][12] = TestBase.getRandomText(50);
          largeTestData[acct][i][13] = TestBase.getRandomLong();
          largeTestData[acct][i][14] = (new BigDecimal(TestBase.getRandomDouble(1000))).setScale(2, RoundingMode.HALF_UP);
          largeTestData[acct][i][15] = TestBase.getRandomInt();
          largeTestData[acct][i][16] = TestBase.getRandomInt();
        }

        // Sort test data in decreasing order by refID (largeTestData[i][1])
        Arrays.sort(largeTestData[acct], 0, largeTestData[acct].length, testDataCompare);
      }
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
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IWalletJournalEntry makeEntry(
                                       final Object[] instanceData,
                                       final String tweak) {
    return new IWalletJournalEntry() {

      @Override
      public long getTaxReceiverID() {
        return (Long) instanceData[13];
      }

      @Override
      public BigDecimal getTaxAmount() {
        return (BigDecimal) instanceData[14];
      }

      @Override
      public int getRefTypeID() {
        return (Integer) instanceData[3];
      }

      @Override
      public long getRefID() {
        return (Long) instanceData[1];
      }

      @Override
      public String getReason() {
        return (String) instanceData[12] + tweak;
      }

      @Override
      public String getOwnerName2() {
        return (String) instanceData[6] + tweak;
      }

      @Override
      public String getOwnerName1() {
        return (String) instanceData[4] + tweak;
      }

      @Override
      public long getOwnerID2() {
        return (Long) instanceData[7];
      }

      @Override
      public long getOwnerID1() {
        return (Long) instanceData[5];
      }

      @Override
      public Date getDate() {
        return new Date((Long) instanceData[2]);
      }

      @Override
      public BigDecimal getBalance() {
        return (BigDecimal) instanceData[11];
      }

      @Override
      public String getArgName1() {
        return (String) instanceData[8] + tweak;
      }

      @Override
      public long getArgID1() {
        return (Long) instanceData[9];
      }

      @Override
      public BigDecimal getAmount() {
        return (BigDecimal) instanceData[10];
      }

      @Override
      public int getOwner1TypeID() {
        return (Integer) instanceData[15];
      }

      @Override
      public int getOwner2TypeID() {
        return (Integer) instanceData[16];
      }
    };
  }

  public Collection<IWalletJournalEntry> assembleEntries(
                                                         Object[][] source,
                                                         int count,
                                                         Long startingRefID,
                                                         boolean forward,
                                                         String tweak) {
    List<IWalletJournalEntry> entries = new ArrayList<IWalletJournalEntry>();
    if (!forward) {
      for (int i = 0; i < source.length && count > 0; i++) {
        long refID = (Long) source[i][1];
        if (refID < startingRefID) {
          entries.add(makeEntry(source[i], tweak));
          count--;
        }
      }
    } else {
      for (int i = source.length - 1; i >= 0 && count > 0; i--) {
        long refID = (Long) source[i][1];
        if (refID > startingRefID) {
          entries.add(makeEntry(source[i], tweak));
          count--;
        }
      }
    }

    return entries;
  }

  public WalletJournal makeJournalObject(
                                         long time,
                                         Object[] instanceData,
                                         String tweak)
    throws Exception {
    int accountKey = (Integer) instanceData[0];
    long refID = (Long) instanceData[1];
    long date = (Long) instanceData[2];
    WalletJournal entry = new WalletJournal(
        accountKey, refID, date, (Integer) instanceData[3], (String) instanceData[4] + tweak, (Long) instanceData[5], (String) instanceData[6] + tweak,
        (Long) instanceData[7], (String) instanceData[8] + tweak, (Long) instanceData[9], (BigDecimal) instanceData[10], (BigDecimal) instanceData[11],
        (String) instanceData[12] + tweak, (Long) instanceData[13], (BigDecimal) instanceData[14], (Integer) instanceData[15], (Integer) instanceData[16]);
    entry.setup(syncAccount, time);
    return entry;
  }

  public void setupOkMock(
                          final String tweak,
                          final boolean useLarge)
    throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);

    Collection<IAccountBalance> balances = new ArrayList<IAccountBalance>();
    for (int i = 0; i < accounts.length; i++) {
      final int acctKey = accounts[i];
      balances.add(new IAccountBalance() {

        @Override
        public int getAccountID() {
          return acctKey * 10;
        }

        @Override
        public int getAccountKey() {
          return acctKey;
        }

        @Override
        public BigDecimal getBalance() {
          return new BigDecimal(1234.56).setScale(2, RoundingMode.HALF_UP);
        }
      });
    }

    IAnswer<Collection<IWalletJournalEntry>> forwardMockAnswerer = new IAnswer<Collection<IWalletJournalEntry>>() {

      @Override
      public Collection<IWalletJournalEntry> answer() throws Throwable {
        int key = (Integer) (EasyMock.getCurrentArguments())[0];
        int keyindex = 0;
        for (int i = 0; i < accounts.length; i++) {
          if (accounts[i] == key) {
            keyindex = i;
            break;
          }
        }
        long lastRefID = (Long) (useLarge ? largeTestData[keyindex][largeTestData.length / 2][1] : testData[keyindex][testData.length / 2][1]);

        Collection<IWalletJournalEntry> entries = assembleEntries(useLarge ? largeTestData[keyindex] : testData[keyindex], 20, lastRefID, true, tweak);
        for (IWalletJournalEntry next : entries) {
          if (next.getRefID() > lastRefID) {
            lastRefID = next.getRefID();
          }
        }
        return entries;
      }
    };

    IAnswer<Collection<IWalletJournalEntry>> backwardMockAnswerer = new IAnswer<Collection<IWalletJournalEntry>>() {

      @Override
      public Collection<IWalletJournalEntry> answer() throws Throwable {
        int key = (Integer) (EasyMock.getCurrentArguments())[0];
        int keyindex = 0;
        for (int i = 0; i < accounts.length; i++) {
          if (accounts[i] == key) {
            keyindex = i;
            break;
          }
        }
        long startingRefID = (Long) (EasyMock.getCurrentArguments())[1];
        return assembleEntries(useLarge ? largeTestData[keyindex] : testData[keyindex], 20, startingRefID, false, tweak);
      }
    };

    // Prep for answering list of accounts.
    EasyMock.expect(mockServer.requestAccountBalances()).andReturn(balances);
    EasyMock.expectLastCall().anyTimes();

    // Sequence should be a plain request, followed by specific requests until we run out of elements to serve.
    EasyMock.expect(mockServer.requestWalletJournalEntries(EasyMock.anyInt())).andAnswer(forwardMockAnswerer);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.requestWalletJournalEntries(EasyMock.anyInt(), EasyMock.anyLong())).andAnswer(backwardMockAnswerer);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
    EasyMock.expectLastCall().anyTimes();
  }

  public void compareAgainstTestData(
                                     WalletJournal entry,
                                     Object[] instanceData,
                                     String tweak) {
    Assert.assertEquals(entry.getAccountKey(), (int) ((Integer) instanceData[0]));
    Assert.assertEquals(entry.getRefID(), (long) ((Long) instanceData[1]));
    Assert.assertEquals(entry.getDate(), (long) ((Long) instanceData[2]));
    Assert.assertEquals(entry.getRefTypeID(), (int) ((Integer) instanceData[3]));
    Assert.assertEquals(entry.getOwnerName1(), (String) instanceData[4] + tweak);
    Assert.assertEquals(entry.getOwnerID1(), (long) ((Long) instanceData[5]));
    Assert.assertEquals(entry.getOwnerName2(), (String) instanceData[6] + tweak);
    Assert.assertEquals(entry.getOwnerID2(), (long) ((Long) instanceData[7]));
    Assert.assertEquals(entry.getArgName1(), (String) instanceData[8] + tweak);
    Assert.assertEquals(entry.getArgID1(), (long) ((Long) instanceData[9]));
    Assert.assertEquals(entry.getAmount(), instanceData[10]);
    Assert.assertEquals(entry.getBalance(), instanceData[11]);
    Assert.assertEquals(entry.getReason(), (String) instanceData[12] + tweak);
    Assert.assertEquals(entry.getTaxReceiverID(), (long) ((Long) instanceData[13]));
    Assert.assertEquals(entry.getTaxAmount(), instanceData[14]);
    Assert.assertEquals(entry.getOwner1TypeID(), (int) ((Integer) instanceData[15]));
    Assert.assertEquals(entry.getOwner2TypeID(), (int) ((Integer) instanceData[16]));
  }

  // Test update with all new wallet transactions, normal sized data
  @Test
  public void testCorporationWalletJournalSyncNormalUpdate() throws Exception {
    setupOkMock("", false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    int updateSum = 0;
    for (int acct = 0; acct < accounts.length; acct++) {
      updateSum += testData[acct].length;
    }
    long start = OrbitalProperties.getCurrentTime();
    SyncStatus syncOutcome = CorporationWalletJournalSync.syncCorporationWalletJournal(testTime, syncAccount, syncUtil, mockServer);
    long end = OrbitalProperties.getCurrentTime();
    long diff = end - start;
    double rate = (double) diff / updateSum;
    System.out.println("CorpWalletJournal rate = " + rate + " millis/entry");
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify entries were added correctly.
    for (int acct = 0; acct < accounts.length; acct++) {
      for (int i = 0; i < testData[acct].length; i++) {
        int accountKey = (Integer) testData[acct][i][0];
        long refID = (Long) testData[acct][i][1];
        WalletJournal entry = WalletJournal.get(syncAccount, testTime, accountKey, refID);
        compareAgainstTestData(entry, testData[acct][i], "");
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getWalletJournalExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalDetail());
  }

  // Test update with all new wallet transactions, large amount of data
  @Test
  public void testCorporationWalletJournalSyncLargeUpdate() throws Exception {
    if (Boolean.valueOf(System.getProperty("org.evekit.model.unittest.skipbig", "false"))) { return; }

    setupOkMock("", true);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationWalletJournalSync.syncCorporationWalletJournal(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify entries were added correctly. There are more entries than we will populate, but all forward items should be populated so we check going backwards
    // until we hit the max population count. Note that more records than this may be populated because we populate as many records as the server is willing to
    // provide.
    for (int acct = 0; acct < accounts.length; acct++) {
      for (int i = 0; i < CorporationWalletJournalSync.MAX_RECORD_DOWNLOAD; i++) {
        int accountKey = (Integer) largeTestData[acct][i][0];
        long refID = (Long) largeTestData[acct][i][1];
        WalletJournal entry = WalletJournal.get(syncAccount, testTime, accountKey, refID);
        compareAgainstTestData(entry, largeTestData[acct][i], "");
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getWalletJournalExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalDetail());
  }

  // Test update with entries already populated
  @Test
  public void testCorporationWalletJournalSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock("", false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing wallet entries which should not be modified.
    for (int acct = 0; acct < accounts.length; acct++) {
      for (int i = 0; i < testData[acct].length; i++) {
        CachedData.update(makeJournalObject(testTime, testData[acct][i], "foo"));
      }
    }

    // Perform the sync
    int updateSum = 0;
    for (int acct = 0; acct < accounts.length; acct++) {
      updateSum += testData[acct].length;
    }
    long start = OrbitalProperties.getCurrentTime();
    SyncStatus syncOutcome = CorporationWalletJournalSync.syncCorporationWalletJournal(testTime, syncAccount, syncUtil, mockServer);
    long end = OrbitalProperties.getCurrentTime();
    long diff = end - start;
    double rate = (double) diff / updateSum;
    System.out.println("CorpWalletJournal rate = " + rate + " millis/entry");
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify previously added entries are unmodified.
    for (int acct = 0; acct < accounts.length; acct++) {
      for (int i = 0; i < testData[acct].length; i++) {
        int accountKey = (Integer) testData[acct][i][0];
        long refID = (Long) testData[acct][i][1];
        WalletJournal entry = WalletJournal.get(syncAccount, testTime, accountKey, refID);
        compareAgainstTestData(entry, testData[acct][i], "foo");
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getWalletJournalExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationWalletJournalSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock("foo", false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing wallet entries which should not be modified.
    for (int acct = 0; acct < accounts.length; acct++) {
      for (int i = 0; i < testData[acct].length; i++) {
        CachedData.update(makeJournalObject(testTime, testData[acct][i], "foo"));
      }
    }

    // Set the tracker as already updated and populate the container
    tracker.setWalletJournalStatus(SyncState.UPDATED);
    tracker.setWalletJournalDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setWalletJournalExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationWalletJournalSync.syncCorporationWalletJournal(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify previously added entries are unmodified.
    for (int acct = 0; acct < accounts.length; acct++) {
      for (int i = 0; i < testData[acct].length; i++) {
        int accountKey = (Integer) testData[acct][i][0];
        long refID = (Long) testData[acct][i][1];
        WalletJournal entry = WalletJournal.get(syncAccount, testTime, accountKey, refID);
        compareAgainstTestData(entry, testData[acct][i], "foo");
      }
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getWalletJournalExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getWalletJournalDetail());
  }

}
