package enterprises.orbital.evekit.model.corporation.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.Bookmark;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IBookmark;
import enterprises.orbital.evexmlapi.shared.IBookmarkFolder;

public class CorporationBookmarksSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  static Object[][]      testData;

  static {
    // Generate test data
    // 0 int folderID;
    // 1 String folderName;
    // 2 long folderCreatorID;
    // 3 array of bookmarks as below:
    // 3.0 int bookmarkID;
    // 3.1 long bookmarkCreatorID;
    // 3.2 long created;
    // 3.3 long itemID;
    // 3.4 int typeID;
    // 3.5 long locationID;
    // 3.6 double x;
    // 3.7 double y;
    // 3.8 double z;
    // 3.9 String memo;
    // 3.10 String note;
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][14];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomInteger();
      testData[i][1] = TestBase.getRandomText(50);
      testData[i][2] = TestBase.getRandomLong();
      int numBookmarks = 5 + TestBase.getRandomInt(10);
      Object[][] bookmarkData = new Object[numBookmarks][11];
      testData[i][3] = bookmarkData;
      for (int j = 0; j < numBookmarks; j++) {
        bookmarkData[j][0] = TestBase.getUniqueRandomInteger();
        bookmarkData[j][1] = TestBase.getRandomLong();
        bookmarkData[j][2] = TestBase.getRandomLong();
        bookmarkData[j][3] = TestBase.getRandomLong();
        bookmarkData[j][4] = TestBase.getRandomInt();
        bookmarkData[j][5] = TestBase.getRandomLong();
        bookmarkData[j][6] = TestBase.getRandomDouble(100000000);
        bookmarkData[j][7] = TestBase.getRandomDouble(100000000);
        bookmarkData[j][8] = TestBase.getRandomDouble(100000000);
        bookmarkData[j][9] = TestBase.getRandomText(50);
        bookmarkData[j][10] = TestBase.getRandomText(50);
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
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IBookmark makeIBookmark(final Object[] data) {
    return new IBookmark() {

      @Override
      public int getBookmarkID() {
        return (Integer) data[0];
      }

      @Override
      public long getCreatorID() {
        return (Long) data[1];
      }

      @Override
      public Date getCreated() {
        return new Date((Long) data[2]);
      }

      @Override
      public long getItemID() {
        return (Long) data[3];
      }

      @Override
      public int getTypeID() {
        return (Integer) data[4];
      }

      @Override
      public long getLocationID() {
        return (Long) data[5];
      }

      @Override
      public double getX() {
        return (Double) data[6];
      }

      @Override
      public double getY() {
        return (Double) data[7];
      }

      @Override
      public double getZ() {
        return (Double) data[8];
      }

      @Override
      public String getMemo() {
        return (String) data[9];
      }

      @Override
      public String getNote() {
        return (String) data[10];
      }

    };
  }

  public IBookmarkFolder makeIBookmarkFolder(final Object[] data) {
    return new IBookmarkFolder() {

      @Override
      public int getFolderID() {
        return (Integer) data[0];
      }

      @Override
      public String getFolderName() {
        return (String) data[1];
      }

      @Override
      public long getCreatorID() {
        return (Long) data[2];
      }

      @Override
      public List<IBookmark> getBookmarks() {
        List<IBookmark> arrayList = new ArrayList<IBookmark>();
        Object[][] source = (Object[][]) data[3];
        for (int i = 0; i < source.length; i++) {
          arrayList.add(makeIBookmark(source[i]));
        }
        return arrayList;
      }

    };
  }

  public List<IBookmarkFolder> collectIBookmarkFolder(Object[][] sourceData) {
    List<IBookmarkFolder> result = new ArrayList<IBookmarkFolder>();
    for (int i = 0; i < sourceData.length; i++) {
      result.add(makeIBookmarkFolder(sourceData[i]));
    }
    return result;
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    List<IBookmarkFolder> result = collectIBookmarkFolder(testData);
    EasyMock.expect(mockServer.requestBookmarks()).andReturn(result);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new bookmarks
  @Test
  public void testBookmarkSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationBookmarksSync.syncBookmarks(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Check bookmarks
    for (int i = 0; i < testData.length; i++) {
      Object[][] bmData = (Object[][]) testData[i][3];
      for (int j = 0; j < bmData.length; j++) {
        int folderID = (Integer) testData[i][0];
        int bookmarkID = (Integer) bmData[j][0];
        Bookmark next = Bookmark.get(syncAccount, testTime, folderID, bookmarkID);
        Assert.assertEquals((int) ((Integer) testData[i][0]), next.getFolderID());
        Assert.assertEquals(testData[i][1], next.getFolderName());
        Assert.assertEquals((long) ((Long) testData[i][2]), next.getFolderCreatorID());
        Assert.assertEquals((int) ((Integer) bmData[j][0]), next.getBookmarkID());
        Assert.assertEquals((long) ((Long) bmData[j][1]), next.getBookmarkCreatorID());
        Assert.assertEquals((long) ((Long) bmData[j][2]), next.getCreated());
        Assert.assertEquals((long) ((Long) bmData[j][3]), next.getItemID());
        Assert.assertEquals((int) ((Integer) bmData[j][4]), next.getTypeID());
        Assert.assertEquals((long) ((Long) bmData[j][5]), next.getLocationID());
        Assert.assertEquals(((Double) bmData[j][6]), next.getX(), 0.01);
        Assert.assertEquals(((Double) bmData[j][7]), next.getY(), 0.01);
        Assert.assertEquals(((Double) bmData[j][8]), next.getZ(), 0.01);
        Assert.assertEquals(bmData[j][9], next.getMemo());
        Assert.assertEquals(bmData[j][10], next.getNote());
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getBookmarksExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksDetail());
  }

  // Test update with bookmarks already populated
  @Test
  public void testBookmarkSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing bookmarks
    for (int i = 0; i < testData.length; i++) {
      Object[][] bmData = (Object[][]) testData[i][3];
      for (int j = 0; j < bmData.length; j++) {
        Bookmark next = new Bookmark(
            (Integer) testData[i][0], (String) testData[i][1] + "foo", (Long) testData[i][2], (Integer) bmData[j][0], (Long) bmData[j][1], (Long) bmData[j][2],
            (Long) bmData[j][3], (Integer) bmData[j][4], (Long) bmData[j][5], (Double) bmData[j][6], (Double) bmData[j][7], (Double) bmData[j][8],
            (String) bmData[j][9], (String) bmData[j][10]);
        next.setup(syncAccount, testTime);
        next = CachedData.update(next);
      }
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationBookmarksSync.syncBookmarks(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify bookmarks are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      Object[][] bmData = (Object[][]) testData[i][3];
      for (int j = 0; j < bmData.length; j++) {
        int folderID = (Integer) testData[i][0];
        int bookmarkID = (Integer) bmData[j][0];
        Bookmark next = Bookmark.get(syncAccount, testTime, folderID, bookmarkID);
        Assert.assertEquals((int) ((Integer) testData[i][0]), next.getFolderID());
        Assert.assertEquals(testData[i][1], next.getFolderName());
        Assert.assertEquals((long) ((Long) testData[i][2]), next.getFolderCreatorID());
        Assert.assertEquals((int) ((Integer) bmData[j][0]), next.getBookmarkID());
        Assert.assertEquals((long) ((Long) bmData[j][1]), next.getBookmarkCreatorID());
        Assert.assertEquals((long) ((Long) bmData[j][2]), next.getCreated());
        Assert.assertEquals((long) ((Long) bmData[j][3]), next.getItemID());
        Assert.assertEquals((int) ((Integer) bmData[j][4]), next.getTypeID());
        Assert.assertEquals((long) ((Long) bmData[j][5]), next.getLocationID());
        Assert.assertEquals(((Double) bmData[j][6]), next.getX(), 0.01);
        Assert.assertEquals(((Double) bmData[j][7]), next.getY(), 0.01);
        Assert.assertEquals(((Double) bmData[j][8]), next.getZ(), 0.01);
        Assert.assertEquals(bmData[j][9], next.getMemo());
        Assert.assertEquals(bmData[j][10], next.getNote());
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getBookmarksExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksDetail());
  }

  // Test skips update when already updated
  @Test
  public void testBookmarkSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing bookmarks
    for (int i = 0; i < testData.length; i++) {
      Object[][] bmData = (Object[][]) testData[i][3];
      for (int j = 0; j < bmData.length; j++) {
        Bookmark next = new Bookmark(
            (Integer) testData[i][0], (String) testData[i][1] + "foo", (Long) testData[i][2], (Integer) bmData[j][0], (Long) bmData[j][1], (Long) bmData[j][2],
            (Long) bmData[j][3], (Integer) bmData[j][4], (Long) bmData[j][5], (Double) bmData[j][6], (Double) bmData[j][7], (Double) bmData[j][8],
            (String) bmData[j][9], (String) bmData[j][10]);
        next.setup(syncAccount, testTime);
        next = CachedData.update(next);
      }
    }

    // Set the tracker as already updated and populate the container
    tracker.setBookmarksStatus(SyncState.UPDATED);
    tracker.setBookmarksDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setBookmarksExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationBookmarksSync.syncBookmarks(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify bookmarks unchanged
    for (int i = 0; i < testData.length; i++) {
      Object[][] bmData = (Object[][]) testData[i][3];
      for (int j = 0; j < bmData.length; j++) {
        int folderID = (Integer) testData[i][0];
        int bookmarkID = (Integer) bmData[j][0];
        Bookmark next = Bookmark.get(syncAccount, testTime, folderID, bookmarkID);
        Assert.assertEquals((int) ((Integer) testData[i][0]), next.getFolderID());
        Assert.assertEquals((String) testData[i][1] + "foo", next.getFolderName());
        Assert.assertEquals((long) ((Long) testData[i][2]), next.getFolderCreatorID());
        Assert.assertEquals((int) ((Integer) bmData[j][0]), next.getBookmarkID());
        Assert.assertEquals((long) ((Long) bmData[j][1]), next.getBookmarkCreatorID());
        Assert.assertEquals((long) ((Long) bmData[j][2]), next.getCreated());
        Assert.assertEquals((long) ((Long) bmData[j][3]), next.getItemID());
        Assert.assertEquals((int) ((Integer) bmData[j][4]), next.getTypeID());
        Assert.assertEquals((long) ((Long) bmData[j][5]), next.getLocationID());
        Assert.assertEquals(((Double) bmData[j][6]), next.getX(), 0.01);
        Assert.assertEquals(((Double) bmData[j][7]), next.getY(), 0.01);
        Assert.assertEquals(((Double) bmData[j][8]), next.getZ(), 0.01);
        Assert.assertEquals(bmData[j][9], next.getMemo());
        Assert.assertEquals(bmData[j][10], next.getNote());
      }
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getBookmarksExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksDetail());
  }

  // Test update with bookmarks which should be deleted
  @Test
  public void testBookmarkSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing bookmmarks
    List<Bookmark> toDelete = new ArrayList<Bookmark>();
    for (int i = 0; i < 5; i++) {
      Bookmark next = new Bookmark(
          TestBase.getUniqueRandomInteger(), TestBase.getRandomText(50), TestBase.getRandomLong(), TestBase.getUniqueRandomInteger(), TestBase.getRandomLong(),
          TestBase.getRandomLong(), TestBase.getRandomLong(), TestBase.getRandomInt(), TestBase.getRandomLong(), TestBase.getRandomDouble(100000000),
          TestBase.getRandomDouble(100000000), TestBase.getRandomDouble(100000000), TestBase.getRandomText(50), TestBase.getRandomText(50));
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      toDelete.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationBookmarksSync.syncBookmarks(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted bookmarks no longer exist
    int len = 0;
    for (int i = 0; i < testData.length; i++) {
      len += ((Object[][]) testData[i][3]).length;
    }
    Assert.assertEquals(len, Bookmark.getAllBookmarks(syncAccount, testTime).size());
    for (int i = 0; i < testData.length; i++) {
      Object[][] bmData = (Object[][]) testData[i][3];
      for (int j = 0; j < bmData.length; j++) {
        int folderID = (Integer) testData[i][0];
        int bookmarkID = (Integer) bmData[j][0];
        Bookmark next = Bookmark.get(syncAccount, testTime, folderID, bookmarkID);
        Assert.assertEquals((int) ((Integer) testData[i][0]), next.getFolderID());
        Assert.assertEquals(testData[i][1], next.getFolderName());
        Assert.assertEquals((long) ((Long) testData[i][2]), next.getFolderCreatorID());
        Assert.assertEquals((int) ((Integer) bmData[j][0]), next.getBookmarkID());
        Assert.assertEquals((long) ((Long) bmData[j][1]), next.getBookmarkCreatorID());
        Assert.assertEquals((long) ((Long) bmData[j][2]), next.getCreated());
        Assert.assertEquals((long) ((Long) bmData[j][3]), next.getItemID());
        Assert.assertEquals((int) ((Integer) bmData[j][4]), next.getTypeID());
        Assert.assertEquals((long) ((Long) bmData[j][5]), next.getLocationID());
        Assert.assertEquals(((Double) bmData[j][6]), next.getX(), 0.01);
        Assert.assertEquals(((Double) bmData[j][7]), next.getY(), 0.01);
        Assert.assertEquals(((Double) bmData[j][8]), next.getZ(), 0.01);
        Assert.assertEquals(bmData[j][9], next.getMemo());
        Assert.assertEquals(bmData[j][10], next.getNote());
      }
    }
    for (Bookmark i : toDelete) {
      Assert.assertNull(Bookmark.get(syncAccount, testTime, i.getFolderID(), i.getBookmarkID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getBookmarksExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getBookmarksDetail());
  }
}
