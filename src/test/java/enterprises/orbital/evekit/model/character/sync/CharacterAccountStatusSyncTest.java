package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.hsqldb.rights.User;
import org.junit.After;
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
import enterprises.orbital.evekit.model.common.AccountStatus;
import enterprises.orbital.evexmlapi.act.IAccountAPI;
import enterprises.orbital.evexmlapi.act.IAccountStatus;
import enterprises.orbital.evexmlapi.act.IMultiCharacterTraining;

public class CharacterAccountStatusSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  User                   testUser;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  IAccountAPI            mockServer;

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true, 1234, "abcd", 5678, "charname", 8765, "corpname");
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  @Override
  @After
  public void teardown() throws Exception {}

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(IAccountAPI.class);
    IAccountStatus mockStatus = new IAccountStatus() {
      final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

      @Override
      public Date getPaidUntil() {
        try {
          return sdf.parse("2015-05-05 04:56:46");
        } catch (ParseException e) {
          e.printStackTrace();
        }
        return new Date(0);
      }

      @Override
      public Date getCreateDate() {
        try {
          return sdf.parse("2010-05-16 03:24:00");
        } catch (ParseException e) {
          e.printStackTrace();
        }
        return new Date(0);
      }

      @Override
      public long getLogonCount() {
        return 2827L;
      }

      @Override
      public long getLogonMinutes() {
        return 161197L;
      }

      @Override
      public List<IMultiCharacterTraining> getMultiCharacterTraining() {
        List<IMultiCharacterTraining> result = new ArrayList<IMultiCharacterTraining>();
        result.add(new IMultiCharacterTraining() {

          @Override
          public Date getTrainingEnd() {
            try {
              return sdf.parse("2014-12-11 14:15:16");
            } catch (ParseException e) {
              e.printStackTrace();
            }
            return new Date(0);
          }

        });
        result.add(new IMultiCharacterTraining() {

          @Override
          public Date getTrainingEnd() {
            try {
              return sdf.parse("2014-12-11 20:20:20");
            } catch (ParseException e) {
              e.printStackTrace();
            }
            return new Date(0);
          }

        });
        return result;
      }

    };
    EasyMock.expect(mockServer.requestAccountStatus()).andReturn(mockStatus);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  @Test
  public void testCharacterAccountStatusSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterAccountStatusSync.syncAccountStatus(testTime, syncAccount, syncUtil, null, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify status was updated properly
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    AccountStatus result = AccountStatus.get(syncAccount, testTime);
    Assert.assertEquals(sdf.parse("2015-05-05 04:56:46").getTime(), result.getPaidUntil());
    Assert.assertEquals(sdf.parse("2010-05-16 03:24:00").getTime(), result.getCreateDate());
    Assert.assertEquals(2827, result.getLogonCount());
    Assert.assertEquals(161197, result.getLogonMinutes());
    Assert.assertEquals(2, result.getMultiCharacterTraining().size());
    Assert.assertTrue(result.getMultiCharacterTraining().contains(sdf.parse("2014-12-11 14:15:16").getTime()));
    Assert.assertTrue(result.getMultiCharacterTraining().contains(sdf.parse("2014-12-11 20:20:20").getTime()));

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getAccountStatusExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountStatusStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountStatusDetail());
  }

  @Test
  public void testCharacterAccountStatusSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate an existing account status
    AccountStatus existing = new AccountStatus(12345678, 87654321, 1000, 2000);
    existing.getMultiCharacterTraining().add(1L);
    existing.getMultiCharacterTraining().add(2L);
    existing.getMultiCharacterTraining().add(3L);
    existing.setup(syncAccount, testTime);
    existing = CachedData.updateData(existing);

    // Perform the sync
    SyncStatus syncOutcome = CharacterAccountStatusSync.syncAccountStatus(testTime, syncAccount, syncUtil, null, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify balance was updated properly
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    AccountStatus result = AccountStatus.get(syncAccount, testTime);
    Assert.assertEquals(sdf.parse("2015-05-05 04:56:46").getTime(), result.getPaidUntil());
    Assert.assertEquals(sdf.parse("2010-05-16 03:24:00").getTime(), result.getCreateDate());
    Assert.assertEquals(2827, result.getLogonCount());
    Assert.assertEquals(161197, result.getLogonMinutes());
    Assert.assertEquals(2, result.getMultiCharacterTraining().size());
    Assert.assertTrue(result.getMultiCharacterTraining().contains(sdf.parse("2014-12-11 14:15:16").getTime()));
    Assert.assertTrue(result.getMultiCharacterTraining().contains(sdf.parse("2014-12-11 20:20:20").getTime()));

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getAccountStatusExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountStatusStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountStatusDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterAccountStatusSyncUpdateSkip() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate an existing account balance
    AccountStatus existing = new AccountStatus(12345678, 87654321, 1000, 2000);
    existing.getMultiCharacterTraining().add(1L);
    existing.getMultiCharacterTraining().add(2L);
    existing.getMultiCharacterTraining().add(3L);
    existing.setup(syncAccount, testTime);
    existing = CachedData.updateData(existing);

    // Set the tracker as already updated and populate the container
    tracker.setAccountStatusStatus(SyncState.UPDATED);
    tracker.setAccountStatusDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setAccountStatusExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterAccountStatusSync.syncAccountStatus(testTime, syncAccount, syncUtil, null, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify unchanged
    AccountStatus result = AccountStatus.get(syncAccount, testTime);
    Assert.assertEquals(12345678, result.getPaidUntil());
    Assert.assertEquals(87654321, result.getCreateDate());
    Assert.assertEquals(1000, result.getLogonCount());
    Assert.assertEquals(2000, result.getLogonMinutes());
    Assert.assertEquals(3, result.getMultiCharacterTraining().size());
    Assert.assertTrue(result.getMultiCharacterTraining().contains(1L));
    Assert.assertTrue(result.getMultiCharacterTraining().contains(2L));
    Assert.assertTrue(result.getMultiCharacterTraining().contains(3L));

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getAccountStatusExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountStatusStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAccountStatusDetail());
  }

}
