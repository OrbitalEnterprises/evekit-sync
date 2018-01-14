package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Set;

import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import org.easymock.EasyMock;
import org.junit.After;
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
import enterprises.orbital.evekit.model.character.CharacterMailMessage;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IMailMessage;

public class CharacterMailMessageSyncTest extends SyncTestBase {

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

  static {
    // Generate random test data
    // 0 messageID
    // 1 senderID
    // 2 senderName
    // 3 sentDate
    // 4 title
    // 5 toCorpOrAllianceID
    // 6 msgRead
    // 7 senderTypeID
    // 8 bodyRetrieved
    // 9 body
    // 10 toCharacterID list
    // 11 toListID list
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][12];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomText(50);
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomText(50);
      testData[i][5] = TestBase.getRandomLong();
      testData[i][6] = TestBase.getRandomBoolean();
      testData[i][7] = TestBase.getRandomInt();
      testData[i][8] = TestBase.getRandomBoolean();
      testData[i][9] = TestBase.getRandomText(500);
      int numReceivers = TestBase.getRandomInt(10);
      Object[] receivers = new Object[numReceivers];
      for (int j = 0; j < numReceivers; j++) {
        receivers[j] = TestBase.getUniqueRandomLong();
      }
      testData[i][10] = receivers;
      int numLists = TestBase.getRandomInt(4);
      Object[] lists = new Object[numLists];
      for (int j = 0; j < numLists; j++) {
        lists[j] = TestBase.getUniqueRandomLong();
      }
      testData[i][11] = lists;
    }
  }

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

  public IMailMessage makeMessage(
                                  final Object[] instanceData,
                                  final String tweak) {
    return new IMailMessage() {

      @Override
      public long getMessageID() {
        return (Long) instanceData[0];
      }

      @Override
      public long getSenderID() {
        return (Long) instanceData[1];
      }

      @Override
      public String getSenderName() {
        return (String) instanceData[2];
      }

      @Override
      public Date getSentDate() {
        return new Date((Long) instanceData[3]);
      }

      @Override
      public String getTitle() {
        return (String) instanceData[4] + tweak;
      }

      @Override
      public long[] getToCharacterIDs() {
        Object[] data = (Object[]) instanceData[10];
        long[] ids = new long[data.length];
        for (int i = 0; i < data.length; i++) {
          ids[i] = (Long) data[i];
        }
        return ids;
      }

      @Override
      public long getToCorpOrAllianceID() {
        return (Long) instanceData[5];
      }

      @Override
      public long[] getToListID() {
        Object[] data = (Object[]) instanceData[11];
        long[] ids = new long[data.length];
        for (int i = 0; i < data.length; i++) {
          ids[i] = (Long) data[i];
        }
        return ids;
      }

      @Override
      public boolean isRead() {
        return (Boolean) instanceData[6];
      }

      @Override
      public int getSenderTypeID() {
        return (Integer) instanceData[7];
      }
    };
  }

  @Override
  @After
  public void teardown() throws Exception {
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CHARACTERMAILMESSAGE_TOCHARACTERID")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createNativeQuery("delete from CHARACTERMAILMESSAGE_TOLISTID")
                                                                            .executeUpdate());
    super.teardown();
  }

  public CharacterMailMessage makeMessageObject(
                                                final long time,
                                                final Object[] instanceData,
                                                final String tweak)
    throws Exception {
    long msgID = (Long) instanceData[0];
    CharacterMailMessage msg = new CharacterMailMessage(
        msgID, (Long) instanceData[1], (String) instanceData[2], (Long) instanceData[3], ((String) instanceData[4]) + tweak, (Long) instanceData[5],
        (Boolean) instanceData[6], (Integer) instanceData[7]);
    Object[] data = (Object[]) instanceData[10];
    for (int i = 0; i < data.length; i++) {
      msg.getToCharacterID().add((Long) data[i]);
    }
    data = (Object[]) instanceData[11];
    for (int i = 0; i < data.length; i++) {
      msg.getToListID().add((Long) data[i]);
    }
    msg.setup(syncAccount, time);
    return msg;
  }

  public void checkMessage(
                           CharacterMailMessage msg,
                           IMailMessage check) {
    Assert.assertEquals(msg.getMessageID(), check.getMessageID());
    Assert.assertEquals(msg.getSenderID(), check.getSenderID());
    Assert.assertEquals(msg.getSenderName(), check.getSenderName());
    Set<Long> charIDs = msg.getToCharacterID();
    long[] refIDs = check.getToCharacterIDs();
    Assert.assertEquals(charIDs.size(), refIDs.length);
    for (int i = 0; i < refIDs.length; i++) {
      Assert.assertTrue(charIDs.contains(refIDs[i]));
    }
    Assert.assertEquals(msg.getSentDate(), check.getSentDate().getTime());
    Assert.assertEquals(msg.getTitle(), check.getTitle());
    Assert.assertEquals(msg.getToCorpOrAllianceID(), check.getToCorpOrAllianceID());
    Set<Long> listIDs = msg.getToListID();
    refIDs = check.getToListID();
    Assert.assertEquals(listIDs.size(), refIDs.length);
    for (int i = 0; i < refIDs.length; i++) {
      Assert.assertTrue(listIDs.contains(refIDs[i]));
    }
    Assert.assertEquals(msg.isMsgRead(), check.isRead());
    Assert.assertEquals(msg.getSenderTypeID(), check.getSenderTypeID());
  }

  public void setupOkMock(
                          String tweak)
    throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    Collection<IMailMessage> messages = new ArrayList<IMailMessage>();
    for (int i = 0; i < testData.length; i++) {
      messages.add(makeMessage(testData[i], tweak));
    }

    EasyMock.expect(mockServer.requestMailMessages()).andReturn(messages);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new messages
  @Test
  public void testCharacterMailMessageSyncUpdate() throws Exception {
    setupOkMock("");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailMessageSync.syncMailMessages(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify messages were added correctly.
    for (int i = 0; i < testData.length; i++) {
      IMailMessage msg = makeMessage(testData[i], "");
      CharacterMailMessage check = CharacterMailMessage.get(syncAccount, testTime, msg.getMessageID());
      Assert.assertNotNull(check);
      checkMessage(check, msg);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getMailMessagesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailMessagesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailMessagesDetail());
  }

  // Test update with messages already populated
  @Test
  public void testCharacterMailMessageSyncUpdateExisting() throws Exception {
    setupOkMock("");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing messages with a slight tweak
    for (int i = 0; i < testData.length; i++) {
      CachedData.update(makeMessageObject(testTime, testData[i], "foo"));
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailMessageSync.syncMailMessages(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify messages are changed. This happens because the old messages are versioned away.
    for (int i = 0; i < testData.length; i++) {
      IMailMessage msg = makeMessage(testData[i], "");
      CharacterMailMessage check = CharacterMailMessage.get(syncAccount, testTime, msg.getMessageID());
      Assert.assertNotNull(check);
      checkMessage(check, msg);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getMailMessagesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailMessagesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailMessagesDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterMailMessageSyncUpdateSkip() throws Exception {
    // Prepare a mock which returns a valid IAccountBalance
    setupOkMock("");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing notifications
    // Populate existing messages with a slight tweak
    for (int i = 0; i < testData.length; i++) {
      CachedData.update(makeMessageObject(testTime, testData[i], "foo"));
    }

    // Set the tracker as already updated and populate the container
    tracker.setMailMessagesStatus(SyncState.UPDATED);
    tracker.setMailMessagesDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setMailMessagesExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailMessageSync.syncMailMessages(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify messages are unchanged.
    for (int i = 0; i < testData.length; i++) {
      IMailMessage msg = makeMessage(testData[i], "foo");
      CharacterMailMessage check = CharacterMailMessage.get(syncAccount, testTime, msg.getMessageID());
      Assert.assertNotNull(check);
      checkMessage(check, msg);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getMailMessagesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailMessagesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailMessagesDetail());
  }

}
