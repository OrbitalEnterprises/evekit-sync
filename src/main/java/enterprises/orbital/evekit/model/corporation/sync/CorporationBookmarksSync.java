package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.Bookmark;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IBookmark;
import enterprises.orbital.evexmlapi.shared.IBookmarkFolder;

public class CorporationBookmarksSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationBookmarksSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CorporationSyncTracker tracker) {
    return tracker.getBookmarksStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CorporationSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setBookmarksStatus(status);
    tracker.setBookmarksDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Corporation container,
                           long expiry) {
    container.setBookmarksExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Corporation container) {
    return container.getBookmarksExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CorporationSyncTracker tracker,
                        Corporation container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) {
    assert item instanceof Bookmark;

    Bookmark api = (Bookmark) item;

    if (api.getLifeStart() != 0) {
      // Special case. This is an existing Bookmark to be EOL
      super.commit(time, tracker, container, accountKey, api);
    } else {
      Bookmark existing = Bookmark.get(accountKey, time, api.getFolderID(), api.getBookmarkID());
      if (existing != null) {
        if (!existing.equivalent(api)) {
          // Evolve
          existing.evolve(api, time);
          super.commit(time, tracker, container, accountKey, existing);
          super.commit(time, tracker, container, accountKey, api);
        }
      } else {
        // New entity
        api.setup(accountKey, time);
        super.commit(time, tracker, container, accountKey, api);
      }
    }

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestBookmarks();
  }

  protected void updateBookmarkSet(
                                   Map<Integer, Set<Integer>> map,
                                   int folderID,
                                   int bookmarkID) {
    Set<Integer> bookmarks = map.get(folderID);
    if (bookmarks == null) {
      bookmarks = new HashSet<Integer>();
      map.put(folderID, bookmarks);
    }
    bookmarks.add(bookmarkID);
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICorporationAPI corpRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IBookmarkFolder> bookmarks = (Collection<IBookmarkFolder>) data;
    Map<Integer, Set<Integer>> bookmarkSet = new HashMap<Integer, Set<Integer>>();
    for (IBookmarkFolder nextFolder : bookmarks) {
      for (IBookmark nextBookmark : nextFolder.getBookmarks()) {
        Bookmark bo = new Bookmark(
            nextFolder.getFolderID(), nextFolder.getFolderName(), nextFolder.getCreatorID(), nextBookmark.getBookmarkID(), nextBookmark.getCreatorID(),
            ModelUtil.safeConvertDate(nextBookmark.getCreated()), nextBookmark.getItemID(), nextBookmark.getTypeID(), nextBookmark.getLocationID(),
            nextBookmark.getX(), nextBookmark.getY(), nextBookmark.getZ(), nextBookmark.getMemo(), nextBookmark.getNote());
        updates.add(bo);
        updateBookmarkSet(bookmarkSet, nextFolder.getFolderID(), nextBookmark.getBookmarkID());
      }
    }
    // Find an EOL and bookmarks no longer in the list.
    for (Bookmark next : Bookmark.getAllBookmarks(syncAccount, time)) {
      int folderID = next.getFolderID();
      int bookmarkID = next.getBookmarkID();
      if (!bookmarkSet.containsKey(folderID) || !bookmarkSet.get(folderID).contains(bookmarkID)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }
    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationBookmarksSync syncher = new CorporationBookmarksSync();

  public static SyncStatus syncBookmarks(
                                         long time,
                                         SynchronizedEveAccount syncAccount,
                                         SynchronizerUtil syncUtil,
                                         ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationBookmark");

  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationBookmark", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationBookmark", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
