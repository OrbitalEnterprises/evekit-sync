package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ContactsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContacts200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Contact;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICorporationContactsSync extends AbstractESIAccountSync<List<GetCorporationsCorporationIdContacts200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICorporationContactsSync.class.getName());

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
    assert item instanceof Contact;
    CachedData existing = null;
    if (item.getLifeStart() == 0)
      // Only need to check for existing item if current item is an update
      existing = Contact.get(account, time, ((Contact) item).getList(), ((Contact) item).getContactID());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCorporationsCorporationIdContacts200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    ContactsApi apiInstance = cp.getContactsApi();

    Pair<Long, List<GetCorporationsCorporationIdContacts200Ok>> result = pagedResultRetriever((page) -> {
      ESIThrottle.throttle(endpoint().name(), account);
      return apiInstance.getCorporationsCorporationIdContactsWithHttpInfo(
          (int) account.getEveCorporationID(),
          null,
          page,
          accessToken(),
          null,
          null);
    });

    return new ESIAccountServerResult<>(Math.max(result.getLeft(), OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getRight());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCorporationsCorporationIdContacts200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    // Map contacts, then look for non-existent contacts
    Set<Integer> seenContacts = new HashSet<>();
    for (GetCorporationsCorporationIdContacts200Ok next : data.getData()) {
      seenContacts.add(next.getContactId());
      updates.add(new Contact("corporation",
                              next.getContactId(),
                              next.getStanding(),
                              next.getContactType()
                                  .toString(),
                              nullSafeBoolean(next.getIsWatched(), false),
                              false,
                              nullSafeLong(next.getLabelId(), 0L)));
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
  }

}
