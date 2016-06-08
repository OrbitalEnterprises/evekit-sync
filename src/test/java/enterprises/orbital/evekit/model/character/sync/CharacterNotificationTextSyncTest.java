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

import org.easymock.EasyMock;
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
import enterprises.orbital.evekit.model.character.CharacterNotification;
import enterprises.orbital.evekit.model.character.CharacterNotificationBody;
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

public class CharacterNotificationTextSyncTest extends SyncTestBase {

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
    // 0 notificationID
    // 1 typeID
    // 2 senderID
    // 3 sentDate
    // 4 msgRead
    // 5 text
    // 6 textRetrieved
    // 7 missing
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][8];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomInt();
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomBoolean();
      testData[i][5] = TestBase.getRandomText(500);
      testData[i][6] = true;
      testData[i][7] = TestBase.getRandomBoolean();
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

  public CharacterNotification makeNotificationObject(
                                                      long time,
                                                      final Object[] instanceData,
                                                      final String tweak,
                                                      boolean retrieved)
    throws Exception {
    long notificationID = (Long) instanceData[0];
    CharacterNotification note = new CharacterNotification(
        notificationID, (Integer) instanceData[1], (Long) instanceData[2], (Long) instanceData[3], (Boolean) instanceData[4]);
    note.setup(syncAccount, time);
    return note;
  }

  public CharacterNotificationBody makeNotificationBodyObject(
                                                              long time,
                                                              final Object[] instanceData,
                                                              final String tweak,
                                                              boolean retrieved)
    throws Exception {
    long notificationID = (Long) instanceData[0];
    CharacterNotificationBody note = new CharacterNotificationBody(notificationID, retrieved, (String) instanceData[5] + tweak, (Boolean) instanceData[7]);
    note.setup(syncAccount, time);
    return note;
  }

  public INotificationText makeNotificationText(
                                                final Object[] instanceData,
                                                final String tweak) {
    return new INotificationText() {

      @Override
      public boolean isMissing() {
        return (Boolean) instanceData[7];
      }

      @Override
      public String getText() {
        return (String) instanceData[5] + tweak;
      }

      @Override
      public long getNotificationID() {
        return (Long) instanceData[0];
      }
    };
  }

  public void compareNoteWithTestData(
                                      CharacterNotification note,
                                      Object[] instanceData,
                                      String tweak) {
    Assert.assertEquals(note.getNotificationID(), (long) ((Long) instanceData[0]));
    Assert.assertEquals(note.getTypeID(), (int) ((Integer) instanceData[1]));
    Assert.assertEquals(note.getSenderID(), (long) ((Long) instanceData[2]));
    Assert.assertEquals(note.getSentDate(), (long) ((Long) instanceData[3]));
    Assert.assertEquals(note.isMsgRead(), (boolean) ((Boolean) instanceData[4]));
  }

  public void compareBodyWithTestData(
                                      CharacterNotificationBody note,
                                      Object[] instanceData,
                                      String tweak) {
    Assert.assertEquals(note.getNotificationID(), (long) ((Long) instanceData[0]));
    Assert.assertEquals(note.getText(), (String) instanceData[5] + tweak);
    Assert.assertEquals(note.isRetrieved(), (boolean) ((Boolean) instanceData[6]));
    Assert.assertEquals(note.isMissing(), (boolean) ((Boolean) instanceData[7]));
  }

  public void setupOkMock(
                          String tweak)
    throws Exception {

    mockServer = EasyMock.createMock(ICharacterAPI.class);
    final Map<Long, INotificationText> bodies = new HashMap<Long, INotificationText>();
    for (int i = 0; i < testData.length; i++) {
      INotificationText next = makeNotificationText(testData[i], tweak);
      bodies.put(next.getNotificationID(), next);
    }

    // Make a real mock class in this case since EasyMock can't handle var-args functions.
    mockServer = new ICharacterAPI() {

      // Only these methods are called.
      @Override
      public boolean isError() {
        return false;
      }

      @Override
      public Collection<INotificationText> requestNotificationTexts(
                                                                    long... notificationID)
        throws IOException {
        List<INotificationText> result = new ArrayList<INotificationText>();
        for (int i = 0; i < notificationID.length; i++) {
          Assert.assertTrue(bodies.containsKey(notificationID[i]));
          result.add(bodies.get(notificationID[i]));
        }
        return result;
      }

      // The rest of the methods are ignored.
      @Override
      public Collection<IMailBody> requestMailBodies(
                                                     long... messageID)
        throws IOException {
        return null;
      }

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
                                                                              int... eventID)
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

    };

  }

  // Test update with new notifications
  @Test
  public void testCharacterNotificationTextSyncUpdate() throws Exception {
    setupOkMock("");
    long testTime = 1234L;

    // Populate unretrieved notifications
    for (int i = 0; i < testData.length; i++) {
      CharacterNotificationBody note = makeNotificationBodyObject(testTime, testData[i], "", false);
      note = CachedData.updateData(note);
    }

    // This sync requires character notifications to already be processed.
    tracker.setNotificationsStatus(SyncState.UPDATED);
    tracker.setNotificationsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setNotificationsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterNotificationTextSync.syncNotificationTexts(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify notification bodies were updated correctly.
    for (int i = 0; i < testData.length; i++) {
      long notificationID = (Long) testData[i][0];
      CharacterNotificationBody note = CharacterNotificationBody.get(syncAccount, testTime, notificationID);
      compareBodyWithTestData(note, testData[i], "");
    }

    // Verify tracker and container were updated properly.
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getNotificationTextsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationTextsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationTextsDetail());
  }

  // Test update with notification bodies already retrieved
  @Test
  public void testCharacterNotificationTextSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock("");
    long testTime = 1234L;

    // Populate notifications that have already been retrieved
    for (int i = 0; i < testData.length; i++) {
      CharacterNotificationBody note = makeNotificationBodyObject(testTime, testData[i], "foo", true);
      note = CachedData.updateData(note);
    }

    // This sync requires character notifications to already be processed.
    tracker.setNotificationsStatus(SyncState.UPDATED);
    tracker.setNotificationsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setNotificationsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterNotificationTextSync.syncNotificationTexts(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify notifications are unchanged.
    for (int i = 0; i < testData.length; i++) {
      long notificationID = (Long) testData[i][0];
      CharacterNotificationBody note = CharacterNotificationBody.get(syncAccount, testTime, notificationID);
      compareBodyWithTestData(note, testData[i], "foo");
    }

    // Verify tracker and container were updated properly. Note that this sync uses the expiry time from UpcomingCalendarEvents.
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getNotificationTextsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationTextsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationTextsDetail());
  }

  // Test fails when prereqs not met
  @Test
  public void testCharacterNotificationTextSyncUpdateNoPreqs() throws Exception {
    setupOkMock("");
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterNotificationTextSync.syncNotificationTexts(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.ERROR, syncOutcome);
  }

  // Test skips update when already updated
  @Test
  public void testCharacterNotificationTextSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock("");
    long testTime = 1234L;

    // Populate unretrieved notifications
    for (int i = 0; i < testData.length; i++) {
      CharacterNotificationBody note = makeNotificationBodyObject(testTime, testData[i], "foo", false);
      note = CachedData.updateData(note);
    }

    // This sync requires character notifications to already be processed.
    tracker.setNotificationsStatus(SyncState.UPDATED);
    tracker.setNotificationsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setNotificationsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Set the tracker as already updated and populate the container
    tracker.setNotificationTextsStatus(SyncState.UPDATED);
    tracker.setNotificationTextsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setNotificationTextsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterNotificationTextSync.syncNotificationTexts(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify no texts were retrieved
    Set<Long> unretrieved = new HashSet<Long>();
    for (long l : CharacterNotificationBody.getUnretrievedNotificationIDs(syncAccount, testTime)) {
      unretrieved.add(l);
    }
    Assert.assertEquals(unretrieved.size(), testData.length);
    for (int i = 0; i < testData.length; i++) {
      long notificationID = (Long) testData[i][0];
      Assert.assertTrue(unretrieved.contains(notificationID));
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getNotificationTextsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationTextsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getNotificationTextsDetail());
  }

}
