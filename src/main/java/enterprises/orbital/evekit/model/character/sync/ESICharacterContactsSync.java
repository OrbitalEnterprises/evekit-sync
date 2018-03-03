package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ContactsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdContacts200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdContactsLabels200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Contact;
import enterprises.orbital.evekit.model.common.ContactLabel;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterContactsSync extends AbstractESIAccountSync<ESICharacterContactsSync.ContactData> {
  protected static final Logger log = Logger.getLogger(ESICharacterContactsSync.class.getName());

  class ContactData {
    List<GetCharactersCharacterIdContacts200Ok> contacts;
    List<GetCharactersCharacterIdContactsLabels200Ok> labels;
  }

  public ESICharacterContactsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_CONTACTS;
  }

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
    ContactData data = new ContactData();
    ContactsApi apiInstance = cp.getContactsApi();

    Pair<Long, List<GetCharactersCharacterIdContacts200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCharactersCharacterIdContactsWithHttpInfo(
          (int) account.getEveCharacterID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.contacts = result.getRight();

    ApiResponse<List<GetCharactersCharacterIdContactsLabels200Ok>> clResult = apiInstance.getCharactersCharacterIdContactsLabelsWithHttpInfo(
        (int) account.getEveCharacterID(),
        null,
        accessToken(),
        null,
        null);
    checkCommonProblems(clResult);
    expiry = Math.max(expiry, extractExpiry(clResult, OrbitalProperties.getCurrentTime() + maxDelay()));
    data.labels = clResult.getData();

    return new ESIAccountServerResult<>(expiry, data);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<ContactData> data,
                                   List<CachedData> updates) throws IOException {
    // Map contacts, then look for non-existent contacts
    Set<Integer> seenContacts = new HashSet<>();
    for (GetCharactersCharacterIdContacts200Ok next : data.getData().contacts) {
      seenContacts.add(next.getContactId());
      updates.add(new Contact("character",
                              next.getContactId(),
                              next.getStanding(),
                              next.getContactType()
                                  .toString(),
                              nullSafeBoolean(next.getIsWatched(), false),
                              nullSafeBoolean(next.getIsBlocked(), false),
                              nullSafeLong(next.getLabelId(), 0L)));
    }

    // Check for contacts that no longer exist and schedule for EOL
    for (Contact existing : retrieveAll(time,
                                        (long contid, AttributeSelector at) -> Contact.accessQuery(account, contid,
                                                                                                   1000,
                                                                                                   false, at,
                                                                                                   AttributeSelector.values(
                                                                                                       "character"),
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

    // Map contact labels, then look for non-existent contact labels
    Set<Long> seenLabels = new HashSet<>();
    for (GetCharactersCharacterIdContactsLabels200Ok next : data.getData().labels) {
      seenLabels.add(next.getLabelId());
      updates.add(new ContactLabel("character",
                                   next.getLabelId(),
                                   next.getLabelName()));
    }

    for (ContactLabel existing : CachedData.retrieveAll(time,
                                                        (contid, at) -> ContactLabel.accessQuery(account, contid, 1000,
                                                                                                 false, at,
                                                                                                 AttributeSelector.values(
                                                                                                     "character"),
                                                                                                 ANY_SELECTOR,
                                                                                                 ANY_SELECTOR))) {
      if (!seenLabels.contains(existing.getLabelID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }
  }

}
