package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ContactsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContacts200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContactsLabels200Ok;
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

public class ESICorporationContactsSync extends AbstractESIAccountSync<ESICorporationContactsSync.ContactData> {
  protected static final Logger log = Logger.getLogger(ESICorporationContactsSync.class.getName());

  class ContactData {
    List<GetCorporationsCorporationIdContacts200Ok> contacts;
    List<GetCorporationsCorporationIdContactsLabels200Ok> labels;
  }

  public ESICorporationContactsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CORP_CONTACTS;
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
    ESICorporationContactsSync.ContactData data = new ESICorporationContactsSync.ContactData();
    ContactsApi apiInstance = cp.getContactsApi();

    Pair<Long, List<GetCorporationsCorporationIdContacts200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdContactsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          null,
          page,
          accessToken());
    });
    long expiry = result.getLeft() > 0 ? result.getLeft() : OrbitalProperties.getCurrentTime() + maxDelay();
    data.contacts = result.getRight();

    ApiResponse<List<GetCorporationsCorporationIdContactsLabels200Ok>> clResult = apiInstance.getCorporationsCorporationIdContactsLabelsWithHttpInfo(
        (int) account.getEveCorporationID(),
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
    for (GetCorporationsCorporationIdContacts200Ok next : data.getData().contacts) {
      seenContacts.add(next.getContactId());
      updates.add(new Contact("corporation",
                              next.getContactId(),
                              next.getStanding(),
                              next.getContactType()
                                  .toString(),
                              nullSafeBoolean(next.getIsWatched(), false),
                              false,
                              new HashSet<>(next.getLabelIds())));
    }

    // Check for contacts that no longer exist and schedule for EOL
    for (Contact existing : retrieveAll(time,
                                        (long contid, AttributeSelector at) -> Contact.accessQuery(account, contid,
                                                                                                   1000,
                                                                                                   false, at,
                                                                                                   AttributeSelector.values(
                                                                                                       "corporation"),
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
    for (GetCorporationsCorporationIdContactsLabels200Ok next : data.getData().labels) {
      seenLabels.add(next.getLabelId());
      updates.add(new ContactLabel("corporation",
                                   next.getLabelId(),
                                   next.getLabelName()));
    }

    for (ContactLabel existing : CachedData.retrieveAll(time,
                                                        (contid, at) -> ContactLabel.accessQuery(account, contid, 1000,
                                                                                                 false, at,
                                                                                                 AttributeSelector.values(
                                                                                                     "corporation"),
                                                                                                 ANY_SELECTOR,
                                                                                                 ANY_SELECTOR))) {
      if (!seenLabels.contains(existing.getLabelID())) {
        existing.evolve(null, time);
        updates.add(existing);
      }
    }

  }

}
