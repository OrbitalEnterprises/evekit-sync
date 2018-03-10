package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.BookmarksApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBookmarks200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdBookmarksFolders200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Bookmark;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterBookmarksSync extends AbstractESIAccountSync<ESICharacterBookmarksSync.BookmarkData> {
  protected static final Logger log = Logger.getLogger(ESICharacterBookmarksSync.class.getName());

  class BookmarkData {
    List<GetCharactersCharacterIdBookmarksFolders200Ok> folders;
    List<GetCharactersCharacterIdBookmarks200Ok> bookmarks;
  }

  public ESICharacterBookmarksSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_BOOKMARKS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof Bookmark;
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      existing = Bookmark.get(account, time, ((Bookmark) item).getFolderID(), ((Bookmark) item).getBookmarkID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<BookmarkData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    BookmarkData data = new BookmarkData();
    BookmarksApi apiInstance = cp.getBookmarksApi();

    Pair<Long, List<GetCharactersCharacterIdBookmarksFolders200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdBookmarksFoldersWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.folders = result.getRight();

    Pair<Long, List<GetCharactersCharacterIdBookmarks200Ok>> bkResult = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdBookmarksWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });
    long bkExpiry = bkResult.getLeft() > 0 ? bkResult.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.bookmarks = bkResult.getRight();

    return new ESIAccountServerResult<>(Math.max(expiry, bkExpiry), data);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<BookmarkData> data,
                                   List<CachedData> updates) throws IOException {
    // Map bookmark planets, then build bookmark objects
    Map<Integer, GetCharactersCharacterIdBookmarksFolders200Ok> folderMap = data.getData().folders.stream()
                                                                                                  .collect(
                                                                                                      Collectors.toMap(
                                                                                                          GetCharactersCharacterIdBookmarksFolders200Ok::getFolderId,
                                                                                                          x -> x));
    Set<Pair<Integer, Integer>> seenBookmarks = new HashSet<>();

    // Putting bookmarks in a folder is optional, register a default folder for this case
    GetCharactersCharacterIdBookmarksFolders200Ok defaultFolder = new GetCharactersCharacterIdBookmarksFolders200Ok();
    defaultFolder.setFolderId(0);
    defaultFolder.setName(null);
    folderMap.put(0, defaultFolder);

    for (GetCharactersCharacterIdBookmarks200Ok next : data.getData().bookmarks) {
      GetCharactersCharacterIdBookmarksFolders200Ok folder = folderMap.get(nullSafeInteger(next.getFolderId(), 0));
      if (folder == null) {
        log.warning("Can not map folder for bookmark, skipping: " + next);
      } else {
        double x = 0D, y = 0D, z = 0D;
        long itemID = 0L;
        int typeID = 0;
        if (next.getCoordinates() != null) {
          x = next.getCoordinates()
                  .getX();
          y = next.getCoordinates()
                  .getY();
          z = next.getCoordinates()
                  .getZ();
        }
        if (next.getItem() != null) {
          itemID = next.getItem()
                       .getItemId();
          typeID = next.getItem()
                       .getTypeId();
        }
        Bookmark nextBookmark = new Bookmark(folder.getFolderId(),
                                             folder.getName(),
                                             (int) account.getEveCharacterID(),
                                             next.getBookmarkId(),
                                             next.getCreatorId(),
                                             next.getCreated()
                                                 .getMillis(),
                                             itemID,
                                             typeID,
                                             next.getLocationId(),
                                             x,
                                             y,
                                             z,
                                             next.getLabel(),
                                             next.getNotes());
        seenBookmarks.add(Pair.of(folder.getFolderId(), next.getBookmarkId()));
        updates.add(nextBookmark);
      }
    }

    // Check for bookmarks that no longer exist and schedule for EOL
    for (Bookmark existing : retrieveAll(time,
                                         (long contid, AttributeSelector at) -> Bookmark.accessQuery(account, contid,
                                                                                                     1000,
                                                                                                     false, at,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR))) {
      if (!seenBookmarks.contains(Pair.of(existing.getFolderID(), existing.getBookmarkID()))) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
