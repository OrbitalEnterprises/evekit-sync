package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.api.ContactsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetAlliancesAllianceIdContacts200Ok;
import enterprises.orbital.eve.esi.client.model.GetAlliancesAllianceIdContactsLabels200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOk;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Contact;
import enterprises.orbital.evekit.model.common.ContactLabel;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ESICharacterAllianceContactsSync extends AbstractESIAccountSync<ESICharacterAllianceContactsSync.ContactData> {
  protected static final Logger log = Logger.getLogger(ESICharacterAllianceContactsSync.class.getName());

  class ContactData {
    List<GetAlliancesAllianceIdContacts200Ok> contacts;
    List<GetAlliancesAllianceIdContactsLabels200Ok> labels;
  }

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  public ESICharacterAllianceContactsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_ALLIANCE_CONTACTS;
  }

  @SuppressWarnings("Duplicates")
  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof Contact) ||
        (item instanceof ContactLabel);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof Contact)
        existing = Contact.get(account, time, ((Contact) item).getList(), ((Contact) item).getContactID());
      else
        existing = ContactLabel.get(account, time, ((ContactLabel) item).getList(), ((ContactLabel) item).getLabelID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<ContactData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    // Character must be in an alliance otherwise we skip.  So first get the characters public info
    CharacterApi capiInstance = cp.getCharacterApi();
    ApiResponse<GetCharactersCharacterIdOk> cResult = capiInstance.getCharactersCharacterIdWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        null);
    checkCommonProblems(cResult);
    Integer allianceID = cResult.getData()
                                .getAllianceId();
    if (allianceID == null) {
      // Not in an alliance.  Return an empty result and we'll check again when this endpoint expires.
      ContactData empty = new ContactData();
      empty.contacts = Collections.emptyList();
      empty.labels = Collections.emptyList();
      return new ESIAccountServerResult<>(
          OrbitalProperties.getCurrentTime() + TimeUnit.MILLISECONDS.convert(300, TimeUnit.SECONDS),
          empty);
    }

    // Since character is in an alliance, check for contacts
    ContactData data = new ContactData();
    ContactsApi apiInstance = cp.getContactsApi();

    Pair<Long, List<GetAlliancesAllianceIdContacts200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getAlliancesAllianceIdContactsWithHttpInfo(
          allianceID,
          null,
          null,
          page,
          accessToken());
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.contacts = result.getRight();

    ApiResponse<List<GetAlliancesAllianceIdContactsLabels200Ok>> clResult = apiInstance.getAlliancesAllianceIdContactsLabelsWithHttpInfo(
        allianceID,
        null,
        null,
        accessToken());
    checkCommonProblems(clResult);
    expiry = Math.max(expiry, extractExpiry(clResult, OrbitalProperties.getCurrentTime() + maxDelay()));
    data.labels = clResult.getData();

    return new ESIAccountServerResult<>(expiry, data);
  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<ContactData> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(2);

    // Assemble retrieved data and prepare hashes for comparison.
    List<Contact> retrievedContacts = new ArrayList<>();
    for (GetAlliancesAllianceIdContacts200Ok next : data.getData().contacts) {
      retrievedContacts.add(new Contact("alliance",
                                        next.getContactId(),
                                        next.getStanding(),
                                        next.getContactType()
                                            .toString(),
                                        false,
                                        false,
                                        new HashSet<>(next.getLabelIds())));
    }
    retrievedContacts.sort(Comparator.comparingInt(Contact::getContactID));
    String contactHashResult = CachedData.dataHashHelper(retrievedContacts.stream()
                                                                          .map(Contact::dataHash)
                                                                          .toArray());

    List<ContactLabel> retrievedLabels = new ArrayList<>();
    for (GetAlliancesAllianceIdContactsLabels200Ok next : data.getData().labels) {
      retrievedLabels.add(new ContactLabel("alliance",
                                           next.getLabelId(),
                                           next.getLabelName()));
    }
    retrievedLabels.sort(Comparator.comparingLong(ContactLabel::getLabelID));
    String labelHashResult = CachedData.dataHashHelper(retrievedLabels.stream()
                                                                      .map(ContactLabel::dataHash)
                                                                      .toArray());

    // Check hash for contacts
    if (cachedHash[0] == null || !cachedHash[0].equals(contactHashResult)) {
      // New contact list, process
      cacheMiss();
      cachedHash[0] = contactHashResult;
      updates.addAll(retrievedContacts);

      // Check for contacts that no longer exist and schedule for EOL
      Set<Integer> seenContacts = retrievedContacts.stream()
                                                   .map(Contact::getContactID)
                                                   .collect(Collectors.toSet());
      for (Contact existing : retrieveAll(time,
                                          (long contid, AttributeSelector at) -> Contact.accessQuery(account, contid,
                                                                                                     1000,
                                                                                                     false, at,
                                                                                                     AttributeSelector.values(
                                                                                                         "alliance"),
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR,
                                                                                                     ANY_SELECTOR))) {
        if (!seenContacts.contains(existing.getContactID())) {
          existing.evolve(null, time);
          updates.add(existing);
        }
      }
    } else {
      cacheHit();
    }

    // Check hash for labels
    if (cachedHash[1] == null || !cachedHash[1].equals(labelHashResult)) {
      // New labels list, process
      cacheMiss();
      cachedHash[1] = labelHashResult;
      updates.addAll(retrievedLabels);

      // Check for labels that no longer exist and schedule for EOL
      Set<Long> seenLabels = retrievedLabels.stream()
                                            .map(ContactLabel::getLabelID)
                                            .collect(Collectors.toSet());
      for (ContactLabel existing : CachedData.retrieveAll(time,
                                                          (contid, at) -> ContactLabel.accessQuery(account, contid,
                                                                                                   1000,
                                                                                                   false, at,
                                                                                                   AttributeSelector.values(
                                                                                                       "alliance"),
                                                                                                   ANY_SELECTOR,
                                                                                                   ANY_SELECTOR))) {
        if (!seenLabels.contains(existing.getLabelID())) {
          existing.evolve(null, time);
          updates.add(existing);
        }
      }
    } else {
      cacheHit();
    }

    // Save hashes for next execution
    currentETag = String.join("|", cachedHash);
  }

}
