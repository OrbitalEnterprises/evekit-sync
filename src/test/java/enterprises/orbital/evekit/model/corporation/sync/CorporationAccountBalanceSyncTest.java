package enterprises.orbital.evekit.model.corporation.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.AccountBalance;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;

public class CorporationAccountBalanceSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

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

  public static Object[][] mockData = {
      {
          new Integer(12345), new Integer(1000), (new BigDecimal(12994.75)).setScale(2, RoundingMode.HALF_UP)
      }, {
          new Integer(54321), new Integer(1001), (new BigDecimal(82114.78)).setScale(2, RoundingMode.HALF_UP)
      }, {
          new Integer(11111), new Integer(1002), (new BigDecimal(85.23)).setScale(2, RoundingMode.HALF_UP)
      }, {
          new Integer(22222), new Integer(1003), (new BigDecimal(0)).setScale(2, RoundingMode.HALF_UP)
      }, {
          new Integer(33333), new Integer(1004), (new BigDecimal(88313.21)).setScale(2, RoundingMode.HALF_UP)
      }, {
          new Integer(44444), new Integer(1005), (new BigDecimal(459988234.12)).setScale(2, RoundingMode.HALF_UP)
      }, {
          new Integer(55555), new Integer(1006), (new BigDecimal(984123483.20)).setScale(2, RoundingMode.HALF_UP)
      }, {
          new Integer(66666), new Integer(1007), (new BigDecimal(0.0)).setScale(2, RoundingMode.HALF_UP)
      }
  };

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IAccountBalance> balances = new ArrayList<IAccountBalance>();
    for (Object[] next : mockData) {
      final Object[] sample = next;
      balances.add(new IAccountBalance() {

        @Override
        public int getAccountID() {
          return (Integer) sample[0];
        }

        @Override
        public int getAccountKey() {
          return (Integer) sample[1];
        }

        @Override
        public BigDecimal getBalance() {
          return (BigDecimal) sample[2];
        }
      });
    }
    EasyMock.expect(mockServer.requestAccountBalances()).andReturn(balances);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with no previous account balance
  @Test
  public void testCorporationAccountBalanceSyncUpdate() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationAccountBalanceSync.syncAccountBalance(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify balance was updated properly
    for (Object[] next : mockData) {
      AccountBalance result = AccountBalance.get(syncAccount, testTime, (Integer) next[0]);
      Assert.assertEquals(((Integer) next[0]).intValue(), result.getAccountID());
      Assert.assertEquals(((Integer) next[1]).intValue(), result.getAccountKey());
      Assert.assertEquals(next[2], result.getBalance());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getAccountBalanceExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceDetail());
  }

  // Test update with previous account balance
  @Test
  public void testCorporationAccountBalanceSyncUpdateExisting() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate an existing account balance
    for (Object[] next : mockData) {
      AccountBalance existing = new AccountBalance((Integer) next[0], (Integer) next[1], ((BigDecimal) next[2]).add(BigDecimal.TEN));
      existing.setup(syncAccount, testTime);
      existing = CachedData.updateData(existing);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationAccountBalanceSync.syncAccountBalance(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify balance was updated properly
    for (Object[] next : mockData) {
      AccountBalance result = AccountBalance.get(syncAccount, testTime, (Integer) next[0]);
      Assert.assertEquals(((Integer) next[0]).intValue(), result.getAccountID());
      Assert.assertEquals(((Integer) next[1]).intValue(), result.getAccountKey());
      Assert.assertEquals(next[2], result.getBalance());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getAccountBalanceExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationAccountBalanceSyncUpdateSkip() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate an existing account balance
    for (Object[] next : mockData) {
      AccountBalance existing = new AccountBalance((Integer) next[0], (Integer) next[1], ((BigDecimal) next[2]).add(BigDecimal.TEN));
      existing.setup(syncAccount, testTime);
      existing = CachedData.updateData(existing);
    }

    // Set the tracker as already updated and populate the container
    tracker.setAccountBalanceStatus(SyncState.UPDATED);
    tracker.setAccountBalanceDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setAccountBalanceExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationAccountBalanceSync.syncAccountBalance(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify balance unchanged
    for (Object[] next : mockData) {
      AccountBalance result = AccountBalance.get(syncAccount, testTime, (Integer) next[0]);
      Assert.assertEquals(((Integer) next[0]).intValue(), result.getAccountID());
      Assert.assertEquals(((Integer) next[1]).intValue(), result.getAccountKey());
      Assert.assertEquals(((BigDecimal) next[2]).add(BigDecimal.TEN), result.getBalance());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getAccountBalanceExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceDetail());
  }

}
