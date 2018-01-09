package enterprises.orbital.evekit.model.corporation.sync;

import java.text.DateFormat;
import java.text.ParseException;
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
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.CorporationMemberMedal;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IMemberMedal;

public class CorporationMemberMedalsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  // medalID
  // characterID
  // issued date
  // issuer ID
  // reason
  // status
  Object[][]             testData = new Object[][] {
                                      {
                                          1234, 4321L, "Jan 15, 2000", 8721L, "medal one reason", "medal one status"
                                        },
                                      {
                                          4321, 1234L, "Mar 13, 2007", 1288L, "medal two reason", "medal two status"
                                        },
                                      {
                                          2773, 3772L, "Sep 3, 2003", 8821L, "medal three reason", "medal three status"
                                        }
                                    };

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

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IMemberMedal> notes = new ArrayList<IMemberMedal>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      notes.add(new IMemberMedal() {

        @Override
        public int getMedalID() {
          return (Integer) instanceData[0];
        }

        @Override
        public long getCharacterID() {
          return (Long) instanceData[1];
        }

        @Override
        public Date getIssued() {
          try {
            return DateFormat.getDateInstance().parse((String) instanceData[2]);
          } catch (ParseException e) {
            // Shouldn't happen!
            throw new RuntimeException(e);
          }
        }

        @Override
        public long getIssuerID() {
          return (Long) instanceData[3];
        }

        @Override
        public String getReason() {
          return (String) instanceData[4];
        }

        @Override
        public String getStatus() {
          return (String) instanceData[5];
        }
      });

    }

    EasyMock.expect(mockServer.requestMemberMedals()).andReturn(notes);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new medals
  @Test
  public void testCorporationMemberMedalsSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberMedalsSync.syncCorporationMemberMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify notifications were added correctly.
    for (int i = 0; i < testData.length; i++) {
      CorporationMemberMedal next = CorporationMemberMedal.get(syncAccount, testTime, (Integer) testData[i][0], (Long) testData[i][1],
                                                               DateFormat.getDateInstance().parse((String) testData[i][2]).getTime());
      Assert.assertEquals(testData[i][0], next.getMedalID());
      Assert.assertEquals(testData[i][1], next.getCharacterID());
      Assert.assertEquals(DateFormat.getDateInstance().parse((String) testData[i][2]).getTime(), next.getIssued());
      Assert.assertEquals(testData[i][3], next.getIssuerID());
      Assert.assertEquals(testData[i][4], next.getReason());
      Assert.assertEquals(testData[i][5], next.getStatus());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberMedalsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberMedalsDetail());
  }

  // Test update with medals already populated
  @Test
  public void testCorporationMemberMedalsSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing medals
    for (int i = 0; i < testData.length; i++) {
      CorporationMemberMedal next = new CorporationMemberMedal(
          (Integer) testData[i][0], (Long) testData[i][1], DateFormat.getDateInstance().parse((String) testData[i][2]).getTime(), ((Long) testData[i][3]) + 5L,
          (String) testData[i][4], (String) testData[i][5]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberMedalsSync.syncCorporationMemberMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify medals have been changed to downloaded version
    for (int i = 0; i < testData.length; i++) {
      CorporationMemberMedal next = CorporationMemberMedal.get(syncAccount, testTime, (Integer) testData[i][0], (Long) testData[i][1],
                                                               DateFormat.getDateInstance().parse((String) testData[i][2]).getTime());
      Assert.assertEquals(((Integer) testData[i][0]).intValue(), next.getMedalID());
      Assert.assertEquals(((Long) testData[i][1]).longValue(), next.getCharacterID());
      Assert.assertEquals(DateFormat.getDateInstance().parse((String) testData[i][2]).getTime(), next.getIssued());
      Assert.assertEquals(((Long) testData[i][3]).longValue(), next.getIssuerID());
      Assert.assertEquals(testData[i][4], next.getReason());
      Assert.assertEquals(testData[i][5], next.getStatus());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getMemberMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberMedalsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberMedalsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationMemberMedalsSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing medals
    for (int i = 0; i < testData.length; i++) {
      CorporationMemberMedal next = new CorporationMemberMedal(
          (Integer) testData[i][0], (Long) testData[i][1], DateFormat.getDateInstance().parse((String) testData[i][2]).getTime(), ((Long) testData[i][3]) + 5L,
          (String) testData[i][4], (String) testData[i][5]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setMemberMedalsStatus(SyncState.UPDATED);
    tracker.setMemberMedalsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setMemberMedalsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationMemberMedalsSync.syncCorporationMemberMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify medals unchanged
    for (int i = 0; i < testData.length; i++) {
      CorporationMemberMedal next = CorporationMemberMedal.get(syncAccount, testTime, (Integer) testData[i][0], (Long) testData[i][1],
                                                               DateFormat.getDateInstance().parse((String) testData[i][2]).getTime());
      Assert.assertEquals(((Integer) testData[i][0]).intValue(), next.getMedalID());
      Assert.assertEquals(((Long) testData[i][1]).longValue(), next.getCharacterID());
      Assert.assertEquals(DateFormat.getDateInstance().parse((String) testData[i][2]).getTime(), next.getIssued());
      Assert.assertEquals(((Long) testData[i][3]).longValue() + 5L, next.getIssuerID());
      Assert.assertEquals(testData[i][4], next.getReason());
      Assert.assertEquals(testData[i][5], next.getStatus());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getMemberMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberMedalsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getMemberMedalsDetail());
  }

}
