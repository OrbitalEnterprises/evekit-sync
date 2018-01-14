package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hsqldb.rights.User;
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
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evekit.model.common.Location;
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

public class CharacterLocationsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  User                   testUser;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  static Object[][]      testDataAssets;
  static Object[][]      testDataLocations;

  static {
    // Generate two bits of test data:
    // 1) A set of assets for which locations will be retrieved
    // 2) Corresponding locations for each generated asset
    // Generate more than 1000 assets to test paging through the locations API
    int size = 1000 + TestBase.getRandomInt(1000);
    testDataAssets = new Object[size][8];
    testDataLocations = new Object[size][5];
    for (int i = 0; i < size; i++) {
      // 0 long itemID;
      // 1 long locationID;
      // 2 int typeID;
      // 3 long quantity;
      // 4 int flag;
      // 5 boolean singleton;
      // 6 long rawQuantity;
      // 7 long container;
      testDataAssets[i][0] = TestBase.getUniqueRandomLong();
      testDataAssets[i][1] = TestBase.getRandomLong();
      testDataAssets[i][2] = TestBase.getRandomInt();
      testDataAssets[i][3] = TestBase.getRandomLong();
      testDataAssets[i][4] = TestBase.getRandomInt();
      testDataAssets[i][5] = TestBase.getRandomBoolean();
      testDataAssets[i][6] = TestBase.getRandomLong();
      testDataAssets[i][7] = TestBase.getRandomLong();
      // 0 long itemID;
      // 1 String itemName;
      // 2 double x;
      // 3 double y;
      // 4 double z;
      testDataLocations[i][0] = testDataAssets[i][0];
      testDataLocations[i][1] = TestBase.getRandomText(50);
      testDataLocations[i][2] = TestBase.getRandomDouble(50000);
      testDataLocations[i][3] = TestBase.getRandomDouble(50000);
      testDataLocations[i][4] = TestBase.getRandomDouble(50000);
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

  public void setupOkMock(
                          final Date cachedUntil)
    throws Exception {
    final Map<Long, ILocation> locations = new HashMap<>();
    for (int i = 0; i < testDataLocations.length; i++) {
      final Object[] instanceData = testDataLocations[i];
      long itemID = (Long) testDataLocations[i][0];
      locations.put(itemID, new ILocation() {

        @Override
        public long getItemID() {
          return (Long) instanceData[0];
        }

        @Override
        public String getItemName() {
          return (String) instanceData[1];
        }

        @Override
        public double getX() {
          return (Double) instanceData[2];
        }

        @Override
        public double getY() {
          return (Double) instanceData[3];
        }

        @Override
        public double getZ() {
          return (Double) instanceData[4];
        }
      });
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
        return null;
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
        return cachedUntil;
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
        List<ILocation> result = new ArrayList<ILocation>();
        for (int i = 0; i < itemID.length; i++) {
          Assert.assertTrue(locations.containsKey(itemID[i]));
          result.add(locations.get(itemID[i]));
        }
        return result;
      }

      @Override
      public Collection<IAsset> requestAssets(
                                              boolean flat)
        throws IOException {
        return null;
      }

    };

  }

  public Asset makeAsset(
                         final long time,
                         final Object[] instanceData)
    throws Exception {
    long itemID = (Long) instanceData[0];
    Asset newAsset = new Asset(
        itemID, (Long) instanceData[1], (Integer) instanceData[2], (Long) instanceData[3], (Integer) instanceData[4], (Boolean) instanceData[5],
        (Long) instanceData[6], (Long) instanceData[7]);
    newAsset.setup(syncAccount, time);
    return newAsset;
  }

  @Test
  public void testLocationsSyncUpdate() throws Exception {
    setupOkMock(new Date(testDate));
    long testTime = 1234L;

    // Populate assets
    for (int i = 0; i < testDataAssets.length; i++) {
      Asset nextAsset = makeAsset(testTime, testDataAssets[i]);
      CachedData.update(nextAsset);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterLocationsSync.syncCharacterLocations(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify new data populated.
    for (int i = 0; i < testDataLocations.length; i++) {
      long itemID = (Long) testDataLocations[i][0];
      Location next = Location.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testDataLocations[i][1], next.getItemName());
      Assert.assertEquals(testDataLocations[i][2], next.getX());
      Assert.assertEquals(testDataLocations[i][3], next.getY());
      Assert.assertEquals(testDataLocations[i][4], next.getZ());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getLocationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsDetail());
  }

  @Test
  public void testLocationsSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock(new Date(testDate));
    long testTime = 1234L;

    // Populate assets
    for (int i = 0; i < testDataAssets.length; i++) {
      Asset nextAsset = makeAsset(testTime, testDataAssets[i]);
      CachedData.update(nextAsset);
    }

    // Populate existing locations
    for (int i = 0; i < testDataLocations.length; i++) {
      long itemID = (Long) testDataLocations[i][0];
      Location next = new Location(
          itemID, (String) testDataLocations[i][1] + "foo", (Double) testDataLocations[i][2] + 7, (Double) testDataLocations[i][3] + 7,
          (Double) testDataLocations[i][4] + 7);
      next.setup(syncAccount, testTime);
      CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterLocationsSync.syncCharacterLocations(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify changed, the sync always updates existing data.
    for (int i = 0; i < testDataLocations.length; i++) {
      long itemID = (Long) testDataLocations[i][0];
      Location next = Location.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testDataLocations[i][1], next.getItemName());
      Assert.assertEquals(testDataLocations[i][2], next.getX());
      Assert.assertEquals(testDataLocations[i][3], next.getY());
      Assert.assertEquals(testDataLocations[i][4], next.getZ());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getLocationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testLocationsSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock(new Date(testDate));
    long testTime = 1234L;

    // Populate assets
    for (int i = 0; i < testDataAssets.length; i++) {
      Asset nextAsset = makeAsset(testTime, testDataAssets[i]);
      CachedData.update(nextAsset);
    }

    // Populate existing locations
    for (int i = 0; i < testDataLocations.length; i++) {
      long itemID = (Long) testDataLocations[i][0];
      Location next = new Location(
          itemID, (String) testDataLocations[i][1] + "foo", (Double) testDataLocations[i][2] + 7, (Double) testDataLocations[i][3] + 7,
          (Double) testDataLocations[i][4] + 7);
      next.setup(syncAccount, testTime);
      CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setLocationsStatus(SyncState.UPDATED);
    tracker.setLocationsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setLocationsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterLocationsSync.syncCharacterLocations(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify data unchanged
    for (int i = 0; i < testDataLocations.length; i++) {
      long itemID = (Long) testDataLocations[i][0];
      Location next = Location.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals((String) testDataLocations[i][1] + "foo", next.getItemName());
      Assert.assertEquals((Double) testDataLocations[i][2] + 7, next.getX(), 0.0001);
      Assert.assertEquals((Double) testDataLocations[i][3] + 7, next.getY(), 0.0001);
      Assert.assertEquals((Double) testDataLocations[i][4] + 7, next.getZ(), 0.0001);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getLocationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsDetail());
  }

  // Test update with locations which should be deleted
  @Test
  public void testLocationsSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock(new Date(testDate));
    long testTime = 1234L;

    // Populate assets
    for (int i = 0; i < testDataAssets.length; i++) {
      Asset nextAsset = makeAsset(testTime, testDataAssets[i]);
      CachedData.update(nextAsset);
    }

    // Populate existing locations which should be deleted
    List<Location> toDelete = new ArrayList<Location>();
    for (int i = 0; i < 5; i++) {
      long itemID = TestBase.getUniqueRandomLong();
      Location next = new Location(
          itemID, TestBase.getRandomText(50), TestBase.getRandomDouble(50000), TestBase.getRandomDouble(50000), TestBase.getRandomDouble(50000));
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      toDelete.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterLocationsSync.syncCharacterLocations(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);

    // Verify deleted locations no longer exist
    int locationCount = 0;
    long contid = -1;
    List<Location> retrieve = Location.getAllLocations(syncAccount, testTime, -1, contid);
    while (retrieve.size() > 0) {
      locationCount += retrieve.size();
      contid = retrieve.get(retrieve.size() - 1).getItemID();
      retrieve = Location.getAllLocations(syncAccount, testTime, -1, contid);
    }
    Assert.assertEquals(testDataLocations.length, locationCount);
    for (int i = 0; i < testDataLocations.length; i++) {
      long itemID = (Long) testDataLocations[i][0];
      Location next = Location.get(syncAccount, testTime, itemID);
      Assert.assertNotNull(next);
      Assert.assertEquals(testDataLocations[i][1], next.getItemName());
      Assert.assertEquals(testDataLocations[i][2], next.getX());
      Assert.assertEquals(testDataLocations[i][3], next.getY());
      Assert.assertEquals(testDataLocations[i][4], next.getZ());
    }
    for (Location i : toDelete) {
      Assert.assertNull(Location.get(syncAccount, testTime, i.getItemID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getLocationsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getLocationsDetail());
  }
}
