package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.BookmarksApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBookmarks200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBookmarksCoordinates;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBookmarksFolders200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBookmarksItem;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Bookmark;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterBookmarksSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private BookmarksApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] foldersTestData;
  private static Object[][] bookmarksTestData;
  private static int[] foldersPages;
  private static int[] bookmarksPages;

  static {
    // Bookmark planets test data
    // 0 int folderID
    // 1 String name
    // 2 int folderCreatorID - always set to character ID
    int size = 100 + TestBase.getRandomInt(100);
    foldersTestData = new Object[size][3];
    for (int i = 0; i < size; i++) {
      foldersTestData[i][0] = TestBase.getUniqueRandomInteger();
      foldersTestData[i][1] = TestBase.getRandomText(50);
      foldersTestData[i][2] = 0;
    }

    // Bookmarks test data
    // 0 int folderID;
    // 1 String folderName;
    // 2 int folderCreatorID;
    // 3 int bookmarkID;
    // 4 int bookmarkCreatorID;
    // 5 long created = -1;
    // 6 long itemID;
    // 7 int typeID;
    // 8 int locationID;
    // 9 double x;
    // 10 double y;
    // 11 double z;
    // 12 String memo;
    // 13 String note;
    int bkSize = 200 + TestBase.getRandomInt(200);
    bookmarksTestData = new Object[bkSize][14];
    for (int i = 0, j = 0; i < bkSize; i++, j = (j + 1) % size) {
      bookmarksTestData[i][0] = foldersTestData[j][0];
      bookmarksTestData[i][1] = foldersTestData[j][1];
      bookmarksTestData[i][2] = foldersTestData[j][2];
      bookmarksTestData[i][3] = TestBase.getUniqueRandomInteger();
      bookmarksTestData[i][4] = TestBase.getRandomInt();
      bookmarksTestData[i][5] = TestBase.getRandomLong();
      bookmarksTestData[i][6] = TestBase.getRandomLong();
      bookmarksTestData[i][7] = TestBase.getRandomInt();
      bookmarksTestData[i][8] = TestBase.getRandomInt();
      bookmarksTestData[i][9] = TestBase.getRandomDouble(10000);
      bookmarksTestData[i][10] = TestBase.getRandomDouble(10000);
      bookmarksTestData[i][11] = TestBase.getRandomDouble(10000);
      bookmarksTestData[i][12] = TestBase.getRandomText(50);
      bookmarksTestData[i][13] = TestBase.getRandomText(50);
    }

    // Configure page separations
    int pageCount = 2 + TestBase.getRandomInt(4);
    int bkPageCount = 2 + TestBase.getRandomInt(4);
    foldersPages = new int[pageCount];
    bookmarksPages = new int[bkPageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      foldersPages[i] = size - (pageCount - 1 - i) * (size / pageCount);
    for (int i = bkPageCount - 1; i >= 0; i--)
      bookmarksPages[i] = bkSize - (bkPageCount - 1 - i) * (bkSize / bkPageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_BOOKMARKS, 1234L, null);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM Bookmark ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(BookmarksApi.class);

    // Initialize folder creator IDs
    for (int i = 0; i < bookmarksTestData.length; i++)
      bookmarksTestData[i][2] = (int) charSyncAccount.getEveCharacterID();

    // Setup planets mock calls
    List<GetCharactersCharacterIdBookmarksFolders200Ok> foldersList =
        Arrays.stream(foldersTestData)
              .map(x -> {
                GetCharactersCharacterIdBookmarksFolders200Ok nextFolder = new GetCharactersCharacterIdBookmarksFolders200Ok();
                nextFolder.setFolderId((Integer) x[0]);
                nextFolder.setName((String) x[1]);
                return nextFolder;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < foldersPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(foldersPages.length));
      ApiResponse<List<GetCharactersCharacterIdBookmarksFolders200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                                foldersList.subList(
                                                                                                    last,
                                                                                                    foldersPages[i]));
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdBookmarksFoldersWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = foldersPages[i];
    }
    // Setup bookmarks mock calls
    List<GetCharactersCharacterIdBookmarks200Ok> bookmarksList =
        Arrays.stream(bookmarksTestData)
              .map(x -> {
                GetCharactersCharacterIdBookmarks200Ok nextBookmark = new GetCharactersCharacterIdBookmarks200Ok();
                nextBookmark.setFolderId((Integer) x[0]);
                nextBookmark.setBookmarkId((Integer) x[3]);
                nextBookmark.setCreatorId((Integer) x[4]);
                nextBookmark.setCreated(new DateTime(new Date((Long) x[5])));
                nextBookmark.setLocationId((Integer) x[8]);
                nextBookmark.setLabel((String) x[12]);
                nextBookmark.setNotes((String) x[13]);
                GetCharactersCharacterIdBookmarksItem item = new GetCharactersCharacterIdBookmarksItem();
                item.setItemId((Long) x[6]);
                item.setTypeId((Integer) x[7]);
                nextBookmark.setItem(item);
                GetCharactersCharacterIdBookmarksCoordinates coords = new GetCharactersCharacterIdBookmarksCoordinates();
                coords.setX((Double) x[9]);
                coords.setY((Double) x[10]);
                coords.setZ((Double) x[11]);
                nextBookmark.setCoordinates(coords);
                return nextBookmark;
              })
              .collect(Collectors.toList());
    last = 0;
    for (int i = 0; i < bookmarksPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(bookmarksPages.length));
      ApiResponse<List<GetCharactersCharacterIdBookmarks200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                         bookmarksList.subList(last,
                                                                                                               bookmarksPages[i]));
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdBookmarksWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = bookmarksPages[i];
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getBookmarksApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Bookmark> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Bookmark.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(bookmarksTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < bookmarksTestData.length; i++) {
      Bookmark nextEl = storedData.get(i);
      Assert.assertEquals((int) (Integer) bookmarksTestData[i][0], nextEl.getFolderID());
      Assert.assertEquals(bookmarksTestData[i][1], nextEl.getFolderName());
      Assert.assertEquals((int) (Integer) bookmarksTestData[i][2], nextEl.getFolderCreatorID());
      Assert.assertEquals((int) (Integer) bookmarksTestData[i][3], nextEl.getBookmarkID());
      Assert.assertEquals((int) (Integer) bookmarksTestData[i][4], nextEl.getBookmarkCreatorID());
      Assert.assertEquals((long) (Long) bookmarksTestData[i][5], nextEl.getCreated());
      Assert.assertEquals((long) (Long) bookmarksTestData[i][6], nextEl.getItemID());
      Assert.assertEquals((int) (Integer) bookmarksTestData[i][7], nextEl.getTypeID());
      Assert.assertEquals((int) (Integer) bookmarksTestData[i][8], nextEl.getLocationID());
      Assert.assertEquals((Double) bookmarksTestData[i][9], nextEl.getX(), 0.001);
      Assert.assertEquals((Double) bookmarksTestData[i][10], nextEl.getY(), 0.001);
      Assert.assertEquals((Double) bookmarksTestData[i][11], nextEl.getZ(), 0.001);
      Assert.assertEquals(bookmarksTestData[i][12], nextEl.getMemo());
      Assert.assertEquals(bookmarksTestData[i][13], nextEl.getNote());
    }

  }

  private void verifyOldDataUpdated() throws Exception {
    // Retrieve all stored data
    List<Bookmark> storedData = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        Bookmark.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(bookmarksTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < bookmarksTestData.length; i++) {
      Bookmark nextEl = storedData.get(i);
      Assert.assertEquals((Integer) bookmarksTestData[i][0] + 1, nextEl.getFolderID());
      Assert.assertEquals(bookmarksTestData[i][1] + "1", nextEl.getFolderName());
      Assert.assertEquals((Integer) bookmarksTestData[i][2] + 1, nextEl.getFolderCreatorID());
      Assert.assertEquals((Integer) bookmarksTestData[i][3] + 1, nextEl.getBookmarkID());
      Assert.assertEquals((Integer) bookmarksTestData[i][4] + 1, nextEl.getBookmarkCreatorID());
      Assert.assertEquals((Long) bookmarksTestData[i][5] + 1, nextEl.getCreated());
      Assert.assertEquals((Long) bookmarksTestData[i][6] + 1, nextEl.getItemID());
      Assert.assertEquals((Integer) bookmarksTestData[i][7] + 1, nextEl.getTypeID());
      Assert.assertEquals((Integer) bookmarksTestData[i][8] + 1, nextEl.getLocationID());
      Assert.assertEquals((Double) bookmarksTestData[i][9] + 1D, nextEl.getX(), 0.001);
      Assert.assertEquals((Double) bookmarksTestData[i][10] + 1D, nextEl.getY(), 0.001);
      Assert.assertEquals((Double) bookmarksTestData[i][11] + 1D, nextEl.getZ(), 0.001);
      Assert.assertEquals(bookmarksTestData[i][12] + "1", nextEl.getMemo());
      Assert.assertEquals(bookmarksTestData[i][13] + "1", nextEl.getNote());
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterBookmarksSync sync = new ESICharacterBookmarksSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_BOOKMARKS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_BOOKMARKS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // All of these objects are not in the server data so we can test deletion.
    for (Object[] d : bookmarksTestData) {
      Bookmark newEl = new Bookmark((Integer) d[0] + 1,
                                    d[1] + "1",
                                    (Integer) d[2] + 1,
                                    (Integer) d[3] + 1,
                                    (Integer) d[4] + 1,
                                    (Long) d[5] + 1,
                                    (Long) d[6] + 1,
                                    (Integer) d[7] + 1,
                                    (Integer) d[8] + 1,
                                    (Double) d[9] + 1D,
                                    (Double) d[10] + 1D,
                                    (Double) d[11] + 1D,
                                    d[12] + "1",
                                    d[13] + "1");
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterBookmarksSync sync = new ESICharacterBookmarksSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyOldDataUpdated();

    // Verify updates
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_BOOKMARKS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_BOOKMARKS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
