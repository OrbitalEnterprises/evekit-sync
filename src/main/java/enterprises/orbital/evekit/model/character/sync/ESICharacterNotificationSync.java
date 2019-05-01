package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdNotifications200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdNotificationsContacts200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterContactNotification;
import enterprises.orbital.evekit.model.character.CharacterNotification;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class ESICharacterNotificationSync extends AbstractESIAccountSync<ESICharacterNotificationSync.NotificationData> {
  protected static final Logger log = Logger.getLogger(ESICharacterNotificationSync.class.getName());

  // Capture data for notifications and contact notifications
  static class NotificationData {
    List<GetCharactersCharacterIdNotificationsContacts200Ok> contacts = new ArrayList<>();
    List<GetCharactersCharacterIdNotifications200Ok> notes = new ArrayList<>();
  }

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  public ESICharacterNotificationSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_NOTIFICATIONS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CharacterContactNotification) ||
        (item instanceof CharacterNotification);

    CachedData existing = null;
    if (item instanceof CharacterContactNotification) {
      if (CharacterContactNotification.get(account, time,
                                           ((CharacterContactNotification) item).getNotificationID()) != null) {
        // Item already exists. We don't need to check if it's changed because contact notifications are immutable.
        return;
      }
    } else {
      existing = CharacterNotification.get(account, time, ((CharacterNotification) item).getNotificationID());
    }

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<NotificationData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {

    CharacterApi apiInstance = cp.getCharacterApi();
    NotificationData resultData = new NotificationData();

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(2);

    long expiry;
    try {
      // Retrieve contact notifications
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCharactersCharacterIdNotificationsContacts200Ok>> result = apiInstance.getCharactersCharacterIdNotificationsContactsWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          cachedHash[0],
          accessToken());
      checkCommonProblems(result);
      cacheMiss();
      cachedHash[0] = extractETag(result, null);
      resultData.contacts = result.getData();
      expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());
    } catch (ApiException e) {
      // Trap 304 which indicates there are no changes from the last call
      // Anything else is rethrown.
      if (e.getCode() != 304) throw e;
      cacheHit();
      cachedHash[0] = extractETag(e, null);
      resultData.contacts = null;
      expiry = extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    try {
      // Retrieve notifications
      ESIThrottle.throttle(endpoint().name(), account);
      ApiResponse<List<GetCharactersCharacterIdNotifications200Ok>> noteResult = apiInstance.getCharactersCharacterIdNotificationsWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          cachedHash[1],
          accessToken());
      checkCommonProblems(noteResult);
      cacheMiss();
      cachedHash[1] = extractETag(noteResult, null);
      resultData.notes = noteResult.getData();
      expiry = Math.max(expiry, extractExpiry(noteResult, OrbitalProperties.getCurrentTime() + maxDelay()));
    } catch (ApiException e) {
      // Trap 304 which indicates there are no changes from the last call
      // Anything else is rethrown.
      if (e.getCode() != 304) throw e;
      cacheHit();
      cachedHash[1] = extractETag(e, null);
      resultData.notes = null;
      expiry = extractExpiry(e, OrbitalProperties.getCurrentTime() + maxDelay());
    }

    // Save hashes for next execution
    currentETag = String.join("|", cachedHash);

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<NotificationData> data,
                                   List<CachedData> updates) throws IOException {

    if (data.getData().contacts != null) {
      // Get list of current contact notifications.  Since these are immutable, we can
      // skip ones we already know about.
      Set<Integer> seenContactNotifications = new HashSet<>();
      for (CharacterContactNotification existing : retrieveAll(time,
                                                               (long contid, AttributeSelector at) -> CharacterContactNotification.accessQuery(
                                                                   account, contid,
                                                                   1000,
                                                                   false, at,
                                                                   ANY_SELECTOR,
                                                                   ANY_SELECTOR,
                                                                   ANY_SELECTOR,
                                                                   ANY_SELECTOR,
                                                                   ANY_SELECTOR))) {
        seenContactNotifications.add(existing.getNotificationID());
      }

      // Assemble contact notifications
      for (GetCharactersCharacterIdNotificationsContacts200Ok nm : data.getData().contacts) {
        if (!seenContactNotifications.contains(nm.getNotificationId())) {
          // Haven't seen this one yet, add it.
          updates.add(new CharacterContactNotification(nm.getNotificationId(),
                                                       nm.getSenderCharacterId(),
                                                       nm.getSendDate()
                                                         .getMillis(),
                                                       nm.getStandingLevel(),
                                                       nm.getMessage()));
        }
      }
    }

    if (data.getData().notes != null) {
      // Assemble notifications
      Map<Long, CharacterNotification> seenNotifications = new HashMap<>();
      for (CharacterNotification existing : retrieveAll(time,
                                                        (long contid, AttributeSelector at) -> CharacterNotification.accessQuery(
                                                            account, contid,
                                                            1000,
                                                            false, at,
                                                            ANY_SELECTOR,
                                                            ANY_SELECTOR,
                                                            ANY_SELECTOR,
                                                            ANY_SELECTOR,
                                                            ANY_SELECTOR,
                                                            ANY_SELECTOR,
                                                            ANY_SELECTOR))) {
        seenNotifications.put(existing.getNotificationID(), existing);
      }

      for (GetCharactersCharacterIdNotifications200Ok nm : data.getData().notes) {
        CharacterNotification newNote = new CharacterNotification(nm.getNotificationId(),
                                                                  nm.getType()
                                                                    .toString(),
                                                                  nm.getSenderId(),
                                                                  nm.getSenderType()
                                                                    .toString(),
                                                                  nm.getTimestamp()
                                                                    .getMillis(),
                                                                  nullSafeBoolean(nm.getIsRead(), false),
                                                                  nm.getText());
        // Only add if we've never seen this notification before, or if something has changed.
        if (!seenNotifications.containsKey(newNote.getNotificationID()) ||
            !seenNotifications.get(newNote.getNotificationID())
                              .equivalent(newNote)) {
          updates.add(newNote);
        }
      }
    }
  }

}
