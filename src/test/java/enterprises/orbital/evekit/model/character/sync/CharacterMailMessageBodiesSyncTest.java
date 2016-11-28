package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import enterprises.orbital.evekit.model.character.CharacterMailMessageBody;
import enterprises.orbital.evexmlapi.chr.ICalendarEventAttendee;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ICharacterMedal;
import enterprises.orbital.evexmlapi.chr.ICharacterSheet;
import enterprises.orbital.evexmlapi.chr.IChatChannel;
import enterprises.orbital.evexmlapi.chr.IContactNotification;
import enterprises.orbital.evexmlapi.chr.IMailBody;
import enterprises.orbital.evexmlapi.chr.IMailList;
import enterprises.orbital.evexmlapi.chr.IMailMessage;
import enterprises.orbital.evexmlapi.chr.INotification;
import enterprises.orbital.evexmlapi.chr.INotificationText;
import enterprises.orbital.evexmlapi.chr.IPartialCharacterSheet;
import enterprises.orbital.evexmlapi.chr.IPlanetaryColony;
import enterprises.orbital.evexmlapi.chr.IPlanetaryLink;
import enterprises.orbital.evexmlapi.chr.IPlanetaryPin;
import enterprises.orbital.evexmlapi.chr.IPlanetaryRoute;
import enterprises.orbital.evexmlapi.chr.IResearchAgent;
import enterprises.orbital.evexmlapi.chr.ISkillInQueue;
import enterprises.orbital.evexmlapi.chr.ISkillInTraining;
import enterprises.orbital.evexmlapi.chr.ISkillInfo;
import enterprises.orbital.evexmlapi.chr.IUpcomingCalendarEvent;
import enterprises.orbital.evexmlapi.shared.IAccountBalance;
import enterprises.orbital.evexmlapi.shared.IAsset;
import enterprises.orbital.evexmlapi.shared.IBlueprint;
import enterprises.orbital.evexmlapi.shared.IBookmarkFolder;
import enterprises.orbital.evexmlapi.shared.IContactSet;
import enterprises.orbital.evexmlapi.shared.IContract;
import enterprises.orbital.evexmlapi.shared.IContractBid;
import enterprises.orbital.evexmlapi.shared.IContractItem;
import enterprises.orbital.evexmlapi.shared.IFacWarStats;
import enterprises.orbital.evexmlapi.shared.IIndustryJob;
import enterprises.orbital.evexmlapi.shared.IKill;
import enterprises.orbital.evexmlapi.shared.ILocation;
import enterprises.orbital.evexmlapi.shared.IMarketOrder;
import enterprises.orbital.evexmlapi.shared.IStandingSet;
import enterprises.orbital.evexmlapi.shared.IWalletJournalEntry;
import enterprises.orbital.evexmlapi.shared.IWalletTransaction;

public class CharacterMailMessageBodiesSyncTest extends SyncTestBase {

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
    // 5 corpOrAllianceID
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

  public CharacterMailMessage makeMailMessage(
                                              final long time,
                                              final Object[] instanceData,
                                              final boolean retrieved)
    throws Exception {
    long msgID = (Long) instanceData[0];
    CharacterMailMessage msg = new CharacterMailMessage(
        msgID, (Long) instanceData[1], (String) instanceData[2], (Long) instanceData[3], (String) instanceData[4], (Long) instanceData[5],
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

  public CharacterMailMessageBody makeMailMessageBody(
                                                      final long time,
                                                      final Object[] instanceData,
                                                      final boolean retrieved,
                                                      String tweak)
    throws Exception {
    long msgID = (Long) instanceData[0];
    CharacterMailMessageBody msg = new CharacterMailMessageBody(msgID, retrieved, ((String) instanceData[9]) + tweak);
    msg.setup(syncAccount, time);
    return msg;
  }

  public IMailBody makeMessageBody(
                                   final Object[] instanceData,
                                   final String tweak) {
    return new IMailBody() {

      @Override
      public String getBody() {
        return (String) instanceData[9] + tweak;
      }

      @Override
      public long getMessageID() {
        return (Long) instanceData[0];
      }

    };
  }

  public void compareMsgWithTestData(
                                     CharacterMailMessage msg,
                                     Object[] instanceData,
                                     String tweak) {
    Assert.assertEquals(msg.getMessageID(), (long) ((Long) instanceData[0]));
    Assert.assertEquals(msg.getSenderID(), (long) ((Long) instanceData[1]));
    Assert.assertEquals(msg.getSentDate(), (long) ((Long) instanceData[3]));
    Assert.assertEquals(msg.getTitle(), instanceData[4]);
    Assert.assertEquals(msg.getToCorpOrAllianceID(), (long) ((Long) instanceData[5]));
    Assert.assertEquals(msg.isMsgRead(), (boolean) ((Boolean) instanceData[6]));
    Assert.assertEquals(msg.getSenderTypeID(), (int) ((Integer) instanceData[7]));
    Object[] data = (Object[]) instanceData[10];
    for (int i = 0; i < data.length; i++) {
      Assert.assertTrue(msg.getToCharacterID().contains(data[i]));
    }
    data = (Object[]) instanceData[11];
    for (int i = 0; i < data.length; i++) {
      Assert.assertTrue(msg.getToListID().contains(data[i]));
    }
  }

  public void compareBodyWithTestData(
                                      CharacterMailMessageBody msg,
                                      Object[] instanceData,
                                      String tweak) {
    Assert.assertEquals(msg.getMessageID(), (long) ((Long) instanceData[0]));
    Assert.assertTrue(msg.isRetrieved());
    Assert.assertEquals(msg.getBody(), (String) instanceData[9] + tweak);
  }

  public void setupOkMock(
                          String tweak)
    throws Exception {

    final Map<Long, IMailBody> bodies = new HashMap<Long, IMailBody>();
    for (int i = 0; i < testData.length; i++) {
      IMailBody next = makeMessageBody(testData[i], tweak);
      bodies.put(next.getMessageID(), next);
    }

    // Make a real mock class in this case since EasyMock can't handle var-args functions.
    mockServer = new ICharacterAPI() {

      // Only these methods are called.
      @Override
      public boolean isError() {
        return false;
      }

      @Override
      public Collection<IMailBody> requestMailBodies(
                                                     long... messageID)
        throws IOException {
        List<IMailBody> result = new ArrayList<IMailBody>();
        for (int i = 0; i < messageID.length; i++) {
          Assert.assertTrue(bodies.containsKey(messageID[i]));
          result.add(bodies.get(messageID[i]));
        }
        return result;
      }

      // The rest of the methods are ignored.
      @Override
      public int getEveAPIVersion() {
        return 0;
      }

      @Override
      public Date getCurrentTime() {
        return new Date(0);
      }

      @Override
      public Date getCachedUntil() {
        return new Date(0);
      }

      @Override
      public int getErrorCode() {
        return 0;
      }

      @Override
      public String getErrorString() {
        return null;
      }

      @Override
      public Date getErrorRetryAfterDate() {
        return new Date(0);
      }

      @Override
      public IAccountBalance requestAccountBalance() throws IOException {
        return null;
      }

      @Override
      public Collection<IAsset> requestAssets() throws IOException {
        return null;
      }

      @Override
      public Collection<ICalendarEventAttendee> requestCalendarEventAttendees(
                                                                              long... eventID)
        throws IOException {
        return null;
      }

      @Override
      public ICharacterSheet requestCharacterSheet() throws IOException {
        return null;
      }

      @Override
      public IPartialCharacterSheet requestClones() throws IOException {
        return null;
      }

      @Override
      public IContactSet requestContacts() throws IOException {
        return null;
      }

      @Override
      public Collection<IContactNotification> requestContactNotifications() throws IOException {
        return null;
      }

      @Override
      public IFacWarStats requestFacWarStats() throws IOException {
        return null;
      }

      @Override
      public Collection<IIndustryJob> requestIndustryJobs() throws IOException {
        return null;
      }

      @Override
      public Collection<IKill> requestKillMails() throws IOException {
        return null;
      }

      @Override
      public Collection<IKill> requestKillMails(
                                                long beforeKillID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IMailList> requestMailingLists() throws IOException {
        return null;
      }

      @Override
      public Collection<IMailMessage> requestMailMessages() throws IOException {
        return null;
      }

      @Override
      public Collection<IMarketOrder> requestMarketOrders() throws IOException {
        return null;
      }

      @Override
      public Collection<ICharacterMedal> requestMedals() throws IOException {
        return null;
      }

      @Override
      public Collection<INotification> requestNotifications() throws IOException {
        return null;
      }

      @Override
      public Collection<INotificationText> requestNotificationTexts(
                                                                    long... notificationID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IResearchAgent> requestResearchAgents() throws IOException {
        return null;
      }

      @Override
      public ISkillInTraining requestSkillInTraining() throws IOException {
        return null;
      }

      @Override
      public Collection<ISkillInQueue> requestSkillQueue() throws IOException {
        return null;
      }

      @Override
      public ISkillInfo requestSkills() throws IOException {
        return null;
      }

      @Override
      public IStandingSet requestStandings() throws IOException {
        return null;
      }

      @Override
      public Collection<IUpcomingCalendarEvent> requestUpcomingCalendarEvents() throws IOException {
        return null;
      }

      @Override
      public Collection<IWalletJournalEntry> requestWalletJournalEntries() throws IOException {
        return null;
      }

      @Override
      public Collection<IWalletJournalEntry> requestWalletJournalEntries(
                                                                         long beforeRefID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IWalletTransaction> requestWalletTransactions() throws IOException {
        return null;
      }

      @Override
      public Collection<IWalletTransaction> requestWalletTransactions(
                                                                      long beforeTransID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IContract> requestContracts() throws IOException {
        return null;
      }

      @Override
      public Collection<IContractBid> requestContractBids() throws IOException {
        return null;
      }

      @Override
      public Collection<IContractItem> requestContractItems(
                                                            long contractID)
        throws IOException {
        return null;
      }

      @Override
      public void reset() {}

      @Override
      public Collection<IIndustryJob> requestIndustryJobsHistory() throws IOException {
        return null;
      }

      @Override
      public Collection<IBlueprint> requestBlueprints() throws IOException {
        return null;
      }

      @Override
      public Collection<IPlanetaryColony> requestPlanetaryColonies() throws IOException {
        return null;
      }

      @Override
      public Collection<IPlanetaryLink> requestPlanetaryLinks(
                                                              long planetID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IPlanetaryPin> requestPlanetaryPins(
                                                            long planetID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IPlanetaryRoute> requestPlanetaryRoutes(
                                                                long planetID)
        throws IOException {
        return null;
      }

      @Override
      public IMarketOrder requestMarketOrder(
                                             long orderID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IBookmarkFolder> requestBookmarks() throws IOException {
        return null;
      }

      @Override
      public Collection<IChatChannel> requestChatChannels() throws IOException {
        return null;
      }

      @Override
      public Collection<ILocation> requestLocations(
                                                    long... itemID)
        throws IOException {
        return null;
      }

      @Override
      public Collection<IAsset> requestAssets(
                                              boolean flat)
        throws IOException {
        return null;
      }

    };

  }

  // Test update with new messages
  @Test
  public void testCharacterMailBodiesSyncUpdate() throws Exception {
    setupOkMock("");
    long testTime = 1234L;

    // Populate unretrieved mail messages
    for (int i = 0; i < testData.length; i++) {
      CharacterMailMessageBody msg = makeMailMessageBody(testTime, testData[i], false, "");
      msg = CachedData.updateData(msg);
    }

    // This sync requires character mail messages to already be processed.
    tracker.setMailMessagesStatus(SyncState.UPDATED);
    tracker.setMailMessagesDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setMailMessagesExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailMessageBodiesSync.syncMailMessageBodies(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify message bodies were updated correctly.
    for (int i = 0; i < testData.length; i++) {
      long msgID = (Long) testData[i][0];
      CharacterMailMessageBody msg = CharacterMailMessageBody.get(syncAccount, testTime, msgID);
      compareBodyWithTestData(msg, testData[i], "");
    }

    // Verify tracker and container were updated properly.
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getMailBodiesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailBodiesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailBodiesDetail());
  }

  // Test update with message bodies already retrieved
  @Test
  public void testCharacterMailBodiesSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock("");
    long testTime = 1234L;

    // Populate mail messages that have already been retrieved
    for (int i = 0; i < testData.length; i++) {
      CharacterMailMessageBody msg = makeMailMessageBody(testTime, testData[i], true, "foo");
      msg = CachedData.updateData(msg);
    }

    // This sync requires character mail messages to already be processed.
    tracker.setMailMessagesStatus(SyncState.UPDATED);
    tracker.setMailMessagesDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setMailMessagesExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailMessageBodiesSync.syncMailMessageBodies(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify message bodies are unchanged.
    for (int i = 0; i < testData.length; i++) {
      long msgID = (Long) testData[i][0];
      CharacterMailMessageBody msg = CharacterMailMessageBody.get(syncAccount, testTime, msgID);
      compareBodyWithTestData(msg, testData[i], "foo");
    }

    // Verify tracker and container were updated properly. Note that this sync uses the expiry time from UpcomingCalendarEvents.
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getMailBodiesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailBodiesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailBodiesDetail());
  }

  // Test fails when prereqs not met
  @Test
  public void testCharacterMailBodiesSyncUpdateNoPreqs() throws Exception {
    setupOkMock("");
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailMessageBodiesSync.syncMailMessageBodies(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.ERROR, syncOutcome);
  }

  // Test skips update when already updated
  @Test
  public void testCharacterMailBodiesSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock("");
    long testTime = 1234L;

    // Populate unretrieved mail messages
    for (int i = 0; i < testData.length; i++) {
      CharacterMailMessageBody msg = makeMailMessageBody(testTime, testData[i], false, "foo");
      msg = CachedData.updateData(msg);
    }

    // This sync requires character mail messages to already be processed.
    tracker.setMailMessagesStatus(SyncState.UPDATED);
    tracker.setMailMessagesDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setMailMessagesExpiry(prevDate);
    container = CachedData.updateData(container);

    // Set the tracker as already updated and populate the container
    tracker.setMailBodiesStatus(SyncState.UPDATED);
    tracker.setMailBodiesDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setMailBodiesExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterMailMessageBodiesSync.syncMailMessageBodies(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify no messages were retrieved
    Set<Long> unretrieved = new HashSet<Long>();
    for (long l : CharacterMailMessageBody.getUnretrievedMessageIDs(syncAccount, testTime)) {
      unretrieved.add(l);
    }
    Assert.assertEquals(unretrieved.size(), testData.length);
    for (int i = 0; i < testData.length; i++) {
      long msgID = (Long) testData[i][0];
      Assert.assertTrue(unretrieved.contains(msgID));
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getMailBodiesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailBodiesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getMailBodiesDetail());
  }

}
