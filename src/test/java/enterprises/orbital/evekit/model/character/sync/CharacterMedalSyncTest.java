package enterprises.orbital.evekit.model.character.sync;

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
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.CharacterMedal;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ICharacterMedal;

public class CharacterMedalSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  // medalID
  // description
  // title
  // corpID
  // issued date
  // issuer id
  // reason
  // status
  Object[][]             testData = new Object[][] {
                                      {
                                          1234, "medal one", "medal one title", 4321L, "Jan 15, 2000", 8721L, "medal one reason", "medal one status"
                                        },
                                      {
                                          1234, "medal one", "medal one title", 4321L, "Jan 15, 2001", 8721L, "medal one second award", "medal one status"
                                        },
                                      {
                                          4321, "medal two", "medal two title", 1234L, "Mar 13, 2007", 1288L, "medal two reason", "medal two status"
                                        },
                                      {
                                          2773, "medal three", "medal three title", 3772L, "Sep 3, 2003", 8821L, "medal three reason", "medal three status"
                                        }
                                    };

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    Collection<ICharacterMedal> notes = new ArrayList<ICharacterMedal>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      notes.add(new ICharacterMedal() {

        @Override
        public String getDescription() {
          return (String) instanceData[1];
        }

        @Override
        public int getMedalID() {
          return (Integer) instanceData[0];
        }

        @Override
        public String getTitle() {
          return (String) instanceData[2];
        }

        @Override
        public long getCorporationID() {
          return (Long) instanceData[3];
        }

        @Override
        public Date getIssued() {
          try {
            return DateFormat.getDateInstance().parse((String) instanceData[4]);
          } catch (ParseException e) {
            // Shouldn't happen!
            throw new RuntimeException(e);
          }
        }

        @Override
        public long getIssuerID() {
          return (Long) instanceData[5];
        }

        @Override
        public String getReason() {
          return (String) instanceData[6];
        }

        @Override
        public String getStatus() {
          return (String) instanceData[7];
        }
      });

    }

    EasyMock.expect(mockServer.requestMedals()).andReturn(notes);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new medals
  @Test
  public void testCharacterMedalSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterMedalSync.syncCharacterMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify notifications were added correctly.
    for (int i = 0; i < testData.length; i++) {
      long refTime = DateFormat.getDateInstance().parse((String) testData[i][4]).getTime();
      CharacterMedal next = CharacterMedal.get(syncAccount, testTime, (Integer) testData[i][0], refTime);
      Assert.assertEquals(testData[i][1], next.getDescription());
      Assert.assertEquals(testData[i][2], next.getTitle());
      Assert.assertEquals(testData[i][3], next.getCorporationID());
      Assert.assertEquals(refTime, next.getIssued());
      Assert.assertEquals(testData[i][5], next.getIssuerID());
      Assert.assertEquals(testData[i][6], next.getReason());
      Assert.assertEquals(testData[i][7], next.getStatus());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMedalsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMedalsDetail());
  }

  // Test update with medals already populated
  @Test
  public void testCharacterMedalSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing medals
    for (int i = 0; i < testData.length; i++) {
      long refTime = DateFormat.getDateInstance().parse((String) testData[i][4]).getTime();
      CharacterMedal next = new CharacterMedal(
          (String) testData[i][1], (Integer) testData[i][0], (String) testData[i][2], (Long) testData[i][3], refTime, (Long) testData[i][5],
          (String) testData[i][6], (String) testData[i][7]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterMedalSync.syncCharacterMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify medals are unchanged
    for (int i = 0; i < testData.length; i++) {
      long refTime = DateFormat.getDateInstance().parse((String) testData[i][4]).getTime();
      CharacterMedal next = CharacterMedal.get(syncAccount, testTime, (Integer) testData[i][0], refTime);
      Assert.assertEquals(testData[i][1], next.getDescription());
      Assert.assertEquals(testData[i][2], next.getTitle());
      Assert.assertEquals(testData[i][3], next.getCorporationID());
      Assert.assertEquals(refTime, next.getIssued());
      Assert.assertEquals(testData[i][5], next.getIssuerID());
      Assert.assertEquals(testData[i][6], next.getReason());
      Assert.assertEquals(testData[i][7], next.getStatus());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMedalsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMedalsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterMedalSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing medals
    for (int i = 0; i < testData.length; i++) {
      long refTime = DateFormat.getDateInstance().parse((String) testData[i][4]).getTime();
      CharacterMedal next = new CharacterMedal(
          (String) testData[i][1], (Integer) testData[i][0], (String) testData[i][2], (Long) testData[i][3], refTime, (Long) testData[i][5],
          (String) testData[i][6], (String) testData[i][7]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setMedalsStatus(SyncState.UPDATED);
    tracker.setMedalsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setMedalsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterMedalSync.syncCharacterMedals(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify medals unchanged
    for (int i = 0; i < testData.length; i++) {
      long refTime = DateFormat.getDateInstance().parse((String) testData[i][4]).getTime();
      CharacterMedal next = CharacterMedal.get(syncAccount, testTime, (Integer) testData[i][0], refTime);
      Assert.assertEquals(testData[i][1], next.getDescription());
      Assert.assertEquals(testData[i][2], next.getTitle());
      Assert.assertEquals(testData[i][3], next.getCorporationID());
      Assert.assertEquals(refTime, next.getIssued());
      Assert.assertEquals(testData[i][5], next.getIssuerID());
      Assert.assertEquals(testData[i][6], next.getReason());
      Assert.assertEquals(testData[i][7], next.getStatus());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getMedalsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMedalsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMedalsDetail());
  }

}
