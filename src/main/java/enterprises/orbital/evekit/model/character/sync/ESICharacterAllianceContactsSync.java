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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class ESICharacterAllianceContactsSync extends AbstractESIAccountSync<ESICharacterAllianceContactsSync.ContactData> {
  protected static final Logger log = Logger.getLogger(ESICharacterAllianceContactsSync.class.getName());

  class ContactData {
    List<GetAlliancesAllianceIdContacts200Ok> contacts;
    List<GetAlliancesAllianceIdContactsLabels200Ok> labels;
  }

  public ESICharacterAllianceContactsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_ALLIANCE_CONTACTS;
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

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<ContactData> data,
                                   List<CachedData> updates) throws IOException {
    // Map contacts, then look for non-existent contacts
    Set<Integer> seenContacts = new HashSet<>();
    for (GetAlliancesAllianceIdContacts200Ok next : data.getData().contacts) {
      seenContacts.add(next.getContactId());
      updates.add(new Contact("alliance",
                              next.getContactId(),
                              next.getStanding(),
                              next.getContactType()
                                  .toString(),
                              false,
                              false,
                              new HashSet<>(next.getLabelIds())));
    }

    // Check for contacts that no longer exist and schedule for EOL
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

    // Map contact labels, then look for non-existent contact labels
    Set<Long> seenLabels = new HashSet<>();
    for (GetAlliancesAllianceIdContactsLabels200Ok next : data.getData().labels) {
      seenLabels.add(next.getLabelId());
      updates.add(new ContactLabel("alliance",
                                   next.getLabelId(),
                                   next.getLabelName()));
    }

    for (ContactLabel existing : CachedData.retrieveAll(time,
                                                        (contid, at) -> ContactLabel.accessQuery(account, contid, 1000,
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
  }

}
