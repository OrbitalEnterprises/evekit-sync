package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
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
  private static final String PROP_MAIL_SHARD_COUNT = "enterprises.orbital.evekit.sync.mail_shard_count";
  public static final int DEF_MAIL_SHARD_COUNT = 10;
  private String context;

  // Capture data for mail headers, mail bodies, mail labels and mailing lists
  static class MailData {
    List<GetCharactersCharacterIdMail200Ok> headers = new ArrayList<>();
    List<GetCharactersCharacterIdMailLabelsLabel> labels = new ArrayList<>();
    List<GetCharactersCharacterIdMailLists200Ok> lists = new ArrayList<>();
    Map<Integer, GetCharactersCharacterIdMailMailIdOk> bodies = new HashMap<>();
  }

  public ESICharacterMailSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_MAIL;
  }

  @Override
  protected String getNextSyncContext() {
    return context;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CharacterMailMessage) ||
        (item instanceof MailingList) ||
        (item instanceof MailLabel);

    CachedData existing;
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
    final int mailShardCount = PersistentProperty.getIntegerPropertyWithFallback(PROP_MAIL_SHARD_COUNT, DEF_MAIL_SHARD_COUNT);

    // Retrieve mail headers
    List<GetCharactersCharacterIdMail200Ok> prelimResults = new ArrayList<>();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdMail200Ok>> result = apiInstance.getCharactersCharacterIdMailWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        null,
        (int) mailIdLimit,
        accessToken());
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
                                                                    null,
                                                                    (int) mailIdLimit,
                                                                    accessToken());
      checkCommonProblems(result);
    }

    // Sort results in increasing order by mailID so we insert in order
    prelimResults.sort(Comparator.comparingLong(GetCharactersCharacterIdMail200Ok::getMailId));

    // If a context is present, use it to filter out which mail headers we'll process.
    // We do this since mail is sync'd frequently but usually changes slowly.
    int mailFilter;
    try {
      mailFilter = Integer.valueOf(getCurrentTracker().getContext());
      mailFilter = Math.max(mailFilter, 0);
    } catch (Exception e) {
      // No filter exists, assign a random filter
      mailFilter = (int) ((OrbitalProperties.getCurrentTime() / 1000) % mailShardCount);
    }

    // Filter headers by mail ID to create a smaller processing batch.
    // Eventually, all headers will be processed as the filter cycles.
    final int mailBatch = mailFilter;
    prelimResults = prelimResults.stream()
                                 .filter(x -> (x.getMailId() % mailShardCount) == mailBatch)
                                 .collect(Collectors.toList());

    // Prepare filter and context for next tracker
    mailFilter = (mailFilter + 1) % mailShardCount;
    context = String.valueOf(mailFilter);

    // Now retrieve message bodies
    for (GetCharactersCharacterIdMail200Ok next : prelimResults) {
      try {
        ESIThrottle.throttle(endpoint().name(), account);
        ApiResponse<GetCharactersCharacterIdMailMailIdOk> bodyResponse = apiInstance.getCharactersCharacterIdMailMailIdWithHttpInfo(
            (int) account.getEveCharacterID(),
            next.getMailId(),
            null,
            null,
            accessToken());
        checkCommonProblems(bodyResponse);

        // If we succeed then record this header and body for possible storage
        resultData.headers.add(next);
        resultData.bodies.put(next.getMailId(), bodyResponse.getData());
      } catch (ApiException | IOException e) {
        // Skip this header, try to make progress with what is left
        log.log(Level.FINE, "Skipping failed header " + next, e);
        if (e instanceof ApiException)
          ESIThrottle.throttle((ApiException) e);
      }
    }

    // Retrieve mailing lists
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdMailLists200Ok>> listResponse = apiInstance.getCharactersCharacterIdMailListsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        accessToken());
    checkCommonProblems(listResponse);
    resultData.lists = listResponse.getData();

    // Retrieve mail labels
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdMailLabelsOk> labelResponse = apiInstance.getCharactersCharacterIdMailLabelsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null,
        accessToken());
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
      Set<Integer> labels = new HashSet<>(nm.getLabels());
      Set<MailMessageRecipient> recipients = nm.getRecipients()
                                               .stream()
                                               .map(x -> new MailMessageRecipient(x.getRecipientType()
                                                                                   .toString(), x.getRecipientId()))
                                               .collect(Collectors.toSet());
      CharacterMailMessage newm = new CharacterMailMessage(nullSafeInteger(nm.getMailId(), 0),
                                                           nullSafeInteger(nm.getFrom(), 0),
                                                           nullSafeDateTime(nm.getTimestamp(),
                                                                            new DateTime(new Date(0L))).getMillis(),
                                                           nm.getSubject(),
                                                           nullSafeBoolean(nm.getIsRead(), false),
                                                           labels,
                                                           recipients,
                                                           body.getBody());
      // Pre-filter to avoid holding the lock for a long time in the transaction
      CharacterMailMessage existing = CharacterMailMessage.get(account, time, newm.getMessageID());
      if (existing == null || !existing.equivalent(newm))
        updates.add(newm);
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
