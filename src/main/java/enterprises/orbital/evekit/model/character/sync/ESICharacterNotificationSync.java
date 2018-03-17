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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterNotificationSync extends AbstractESIAccountSync<ESICharacterNotificationSync.NotificationData> {
  protected static final Logger log = Logger.getLogger(ESICharacterNotificationSync.class.getName());

  // Capture data for notifications and contact notifications
  static class NotificationData {
    List<GetCharactersCharacterIdNotificationsContacts200Ok> contacts = new ArrayList<>();
    List<GetCharactersCharacterIdNotifications200Ok> notes = new ArrayList<>();
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

    // Retrieve contact notifications
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdNotificationsContacts200Ok>> result = apiInstance.getCharactersCharacterIdNotificationsContactsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        accessToken(),
        null,
        null);
    checkCommonProblems(result);
    resultData.contacts = result.getData();
    long expiry = extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay());

    // Retrieve notifications
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdNotifications200Ok>> noteResult = apiInstance.getCharactersCharacterIdNotificationsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        accessToken(),
        null,
        null);
    checkCommonProblems(noteResult);
    resultData.notes = noteResult.getData();
    expiry = Math.max(expiry, extractExpiry(noteResult, OrbitalProperties.getCurrentTime() + maxDelay()));

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<NotificationData> data,
                                   List<CachedData> updates) throws IOException {

    // Assemble contact notifications
    for (GetCharactersCharacterIdNotificationsContacts200Ok nm : data.getData().contacts) {
      updates.add(new CharacterContactNotification(nm.getNotificationId(),
                                                   nm.getSenderCharacterId(),
                                                   nm.getSendDate()
                                                     .getMillis(),
                                                   nm.getStandingLevel(),
                                                   nm.getMessage()));
    }

    // Assemble notifications
    for (GetCharactersCharacterIdNotifications200Ok nm : data.getData().notes) {
      updates.add(new CharacterNotification(nm.getNotificationId(),
                                            nm.getType()
                                              .toString(),
                                            nm.getSenderId(),
                                            nm.getSenderType()
                                              .toString(),
                                            nm.getTimestamp()
                                              .getMillis(),
                                            nullSafeBoolean(nm.getIsRead(), false),
                                            nm.getText()));
    }

  }

}
