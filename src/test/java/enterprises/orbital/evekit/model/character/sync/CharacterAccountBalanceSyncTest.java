package enterprises.orbital.evekit.model.character.sync;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.util.Date;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.AccountBalance;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;

public class CharacterAccountBalanceSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testlong;
  long                   prevlong;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();
    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true, 1234, "abcd", 5678, "charname", 8765, "corpname");
    testlong = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevlong = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    IAccountBalance mockBalance = new IAccountBalance() {

      @Override
      public int getAccountID() {
        return 12345;
      }

      @Override
      public int getAccountKey() {
        return 1000;
      }

      @Override
      public BigDecimal getBalance() {
        return new BigDecimal(12994.75).setScale(2, RoundingMode.HALF_UP);
      }
    };
    EasyMock.expect(mockServer.requestAccountBalance()).andReturn(mockBalance);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testlong));
  }

  // Test update with no previous account balance
  @Test
  public void testCharacterAccountBalanceSyncUpdate() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterAccountBalanceSync.syncAccountBalance(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify balance was updated properly
    AccountBalance result = AccountBalance.get(syncAccount, testTime, 12345);
    Assert.assertEquals(12345, result.getAccountID());
    Assert.assertEquals(1000, result.getAccountKey());
    Assert.assertEquals(new BigDecimal(12994.75), result.getBalance());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testlong, Capsuleer.getCapsuleer(syncAccount).getAccountBalanceExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceDetail());
  }

  // Test update with previous account balance
  @Test
  public void testCharacterAccountBalanceSyncUpdateExisting() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate an existing account balance
    AccountBalance existing = new AccountBalance(12345, 1000, new BigDecimal(822.57));
    existing.setup(syncAccount, testTime);
    existing = CachedData.updateData(existing);
    BigDecimal testBalance = (new BigDecimal(12994.75)).setScale(2, RoundingMode.HALF_UP);

    // Perform the sync
    SyncStatus syncOutcome = CharacterAccountBalanceSync.syncAccountBalance(4567L, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify balance was updated properly
    AccountBalance result = AccountBalance.getByKey(syncAccount, 4567L, 1000);
    Assert.assertEquals(12345, result.getAccountID());
    Assert.assertEquals(1000, result.getAccountKey());
    Assert.assertEquals(testBalance, result.getBalance());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testlong, Capsuleer.getCapsuleer(syncAccount).getAccountBalanceExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterAccountBalanceSyncUpdateSkip() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);

    // Populate an existing account balance
    BigDecimal testBalance = (new BigDecimal(822.58)).setScale(2, RoundingMode.HALF_UP);
    AccountBalance existing = new AccountBalance(12345, 1000, testBalance);
    existing.setup(syncAccount, 1234L);
    existing = CachedData.updateData(existing);

    // Set the tracker as already updated and populate the container
    tracker.setAccountBalanceStatus(SyncState.UPDATED);
    tracker.setAccountBalanceDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setAccountBalanceExpiry(prevlong);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterAccountBalanceSync.syncAccountBalance(1234L, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify balance unchanged
    AccountBalance result = AccountBalance.getByKey(syncAccount, 1234L, 1000);
    Assert.assertEquals(12345, result.getAccountID());
    Assert.assertEquals(1000, result.getAccountKey());
    Assert.assertEquals(testBalance, result.getBalance());

    // Verify tracker and container unchanged
    Assert.assertEquals(prevlong, Capsuleer.getCapsuleer(syncAccount).getAccountBalanceExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountBalanceDetail());
  }

}
