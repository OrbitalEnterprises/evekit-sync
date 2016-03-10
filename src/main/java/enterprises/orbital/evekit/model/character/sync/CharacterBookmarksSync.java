package enterprises.orbital.evekit.model.character.sync;

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
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.Bookmark;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IBookmark;
import enterprises.orbital.evexmlapi.shared.IBookmarkFolder;

public class CharacterBookmarksSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterBookmarksSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getBookmarksStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setBookmarksStatus(status);
    tracker.setBookmarksDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) {
    container.setBookmarksExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getBookmarksExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
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
        // Existing, evolve if changed
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
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestBookmarks();
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
                                   ICharacterAPI charRequest,
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
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterBookmarksSync syncher = new CharacterBookmarksSync();

  public static SyncStatus syncBookmarks(
                                         long time,
                                         SynchronizedEveAccount syncAccount,
                                         SynchronizerUtil syncUtil,
                                         ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterBookmark");

  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterBookmark", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterBookmark", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
