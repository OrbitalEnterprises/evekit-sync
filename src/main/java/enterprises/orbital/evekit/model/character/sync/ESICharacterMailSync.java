package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MailApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterMailMessage;
import enterprises.orbital.evekit.model.character.MailLabel;
import enterprises.orbital.evekit.model.character.MailMessageRecipient;
import enterprises.orbital.evekit.model.character.MailingList;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterMailSync extends AbstractESIAccountSync<ESICharacterMailSync.MailData> {
  protected static final Logger log = Logger.getLogger(ESICharacterMailSync.class.getName());

  // Capture data for mail headers, mail bodies, mail labels and mailing lists
  static class MailData {
    List<GetCharactersCharacterIdMail200Ok> headers = new ArrayList<>();
    List<GetCharactersCharacterIdMailLabelsLabel> labels = new ArrayList<>();
    List<GetCharactersCharacterIdMailLists200Ok> lists = new ArrayList<>();
    Map<Long, GetCharactersCharacterIdMailMailIdOk> bodies = new HashMap<>();
  }

  public ESICharacterMailSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_MAIL;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CharacterMailMessage) ||
        (item instanceof MailingList) ||
        (item instanceof MailLabel);

    CachedData existing = null;
    if (item instanceof CharacterMailMessage) {
      existing = CharacterMailMessage.get(account, time, ((CharacterMailMessage) item).getMessageID());
    } else if (item instanceof MailingList) {
      existing = MailingList.get(account, time, ((MailingList) item).getListID());
    } else {
      existing = MailLabel.get(account, time, ((MailLabel) item).getLabelID());
    }

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<MailData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {

    MailApi apiInstance = cp.getMailApi();
    MailData resultData = new MailData();
    long mailIdLimit = Integer.MAX_VALUE;

    // Retrieve mail headers
    List<GetCharactersCharacterIdMail200Ok> prelimResults = new ArrayList<>();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdMail200Ok>> result = apiInstance.getCharactersCharacterIdMailWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        (int) mailIdLimit,
        accessToken(),
        null,
        null);
    checkCommonProblems(result);

    // Crawl mail backwards until no more entries are retrieved
    while (!result.getData()
                  .isEmpty()) {
      prelimResults.addAll(result.getData());
      //noinspection ConstantConditions
      mailIdLimit = result.getData()
                          .stream()
                          .min(Comparator.comparingLong(GetCharactersCharacterIdMail200Ok::getMailId))
                          .get()
                          .getMailId();
      ESIThrottle.throttle(endpoint().name(), account);
      result = apiInstance.getCharactersCharacterIdMailWithHttpInfo((int) account.getEveCharacterID(),
                                                                    null,
                                                                    null,
                                                                    (int) mailIdLimit,
                                                                    accessToken(),
                                                                    null,
                                                                    null);
      checkCommonProblems(result);
    }

    // Sort results in increasing order by mailID so we insert in order
    prelimResults.sort(Comparator.comparingLong(GetCharactersCharacterIdMail200Ok::getMailId));

    // Now retrieve message bodies
    for (GetCharactersCharacterIdMail200Ok next : prelimResults) {
      try {
        ESIThrottle.throttle(endpoint().name(), account);
        ApiResponse<GetCharactersCharacterIdMailMailIdOk> bodyResponse = apiInstance.getCharactersCharacterIdMailMailIdWithHttpInfo(
            (int) account.getEveCharacterID(),
            next.getMailId()
                .intValue(),
            null,
            accessToken(),
            null,
            null);
        checkCommonProblems(bodyResponse);

        // If we succeed then record this header and body for possible storage
        resultData.headers.add(next);
        resultData.bodies.put(next.getMailId(), bodyResponse.getData());
      } catch (ApiException | IOException e) {
        // Skip this header, try to make progress with what is left
        log.log(Level.FINE, "Skipping failed header " + next, e);
      }
    }

    // Retrieve mailing lists
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdMailLists200Ok>> listResponse = apiInstance.getCharactersCharacterIdMailListsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        accessToken(),
        null,
        null);
    checkCommonProblems(listResponse);
    resultData.lists = listResponse.getData();

    // Retrieve mail labels
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdMailLabelsOk> labelResponse = apiInstance.getCharactersCharacterIdMailLabelsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        accessToken(),
        null,
        null);
    checkCommonProblems(listResponse);
    long expiry = extractExpiry(labelResponse, OrbitalProperties.getCurrentTime() + maxDelay());
    resultData.labels = labelResponse.getData()
                                     .getLabels();

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<MailData> data,
                                   List<CachedData> updates) throws IOException {

    // Assemble mail messages
    for (GetCharactersCharacterIdMail200Ok nm : data.getData().headers) {
      GetCharactersCharacterIdMailMailIdOk body = data.getData().bodies.get(nm.getMailId());
      Set<Integer> labels = nm.getLabels()
                              .stream()
                              .map(Long::intValue)
                              .collect(Collectors.toSet());
      Set<MailMessageRecipient> recipients = nm.getRecipients()
                                               .stream()
                                               .map(x -> new MailMessageRecipient(x.getRecipientType()
                                                                                   .toString(), x.getRecipientId()))
                                               .collect(Collectors.toSet());
      updates.add(new CharacterMailMessage(nullSafeLong(nm.getMailId(), 0L),
                                           nullSafeInteger(nm.getFrom(), 0),
                                           nullSafeDateTime(nm.getTimestamp(), new DateTime(new Date(0L))).getMillis(),
                                           nm.getSubject(),
                                           nullSafeBoolean(nm.getIsRead(), false),
                                           labels,
                                           recipients,
                                           body.getBody()));
    }

    // Assemble mailing lists
    for (GetCharactersCharacterIdMailLists200Ok nl : data.getData().lists) {
      updates.add(new MailingList(nl.getName(), nl.getMailingListId()));
    }

    // Assembling mailing labels
    for (GetCharactersCharacterIdMailLabelsLabel nl : data.getData().labels) {
      updates.add(new MailLabel(nullSafeInteger(nl.getLabelId(), 0),
                                nullSafeInteger(nl.getUnreadCount(), 0),
                                nl.getName(),
                                nl.getColor()
                                  .toString()));
    }
  }

}
