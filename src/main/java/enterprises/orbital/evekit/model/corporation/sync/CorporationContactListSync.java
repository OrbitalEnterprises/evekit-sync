package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.common.Contact;
import enterprises.orbital.evekit.model.common.ContactLabel;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IContact;
import enterprises.orbital.evexmlapi.shared.IContactLabel;
import enterprises.orbital.evexmlapi.shared.IContactSet;

public class CorporationContactListSync extends AbstractCorporationSync {

  protected static final Logger log = Logger.getLogger(CorporationContactListSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getContactListStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setContactListStatus(status);
    tracker.setContactListDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) {
    container.setContactListExpiry(expiry);
    CachedData.updateData(container);
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getContactListExpiry();
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) {
    if (item instanceof Contact) {
      Contact api = (Contact) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing Contact to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        Contact existing = Contact.get(accountKey, time, api.getList(), api.getContactID());
        if (existing != null) {
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
    } else if (item instanceof ContactLabel) {
      ContactLabel api = (ContactLabel) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing ContactLabel to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        ContactLabel existing = ContactLabel.get(accountKey, time, api.getList(), api.getLabelID());
        if (existing != null) {
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

    } else {
      // Should never happen!
      log.severe("Unexpected object: " + item.getClass().getCanonicalName());
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestContacts();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    IContactSet contacts = (IContactSet) data;

    Map<String, Set<Integer>> contactSet = new HashMap<String, Set<Integer>>();
    contactSet.put("corporate", new HashSet<Integer>());
    contactSet.put("alliance", new HashSet<Integer>());
    Map<String, Set<Long>> labelSet = new HashMap<String, Set<Long>>();
    labelSet.put("corporate", new HashSet<Long>());
    labelSet.put("alliance", new HashSet<Long>());

    Map<String, List<IContact>> processContacts = new HashMap<String, List<IContact>>();
    processContacts.put("corporate", contacts.getCorporateContacts());
    processContacts.put("alliance", contacts.getAllianceContacts());
    for (String nextType : processContacts.keySet()) {
      for (IContact nextContact : processContacts.get(nextType)) {
        Contact contextObj = new Contact(
            nextType, nextContact.getContactID(), nextContact.getContactName(), nextContact.getStanding(), nextContact.getContactTypeID(),
            nextContact.isInWatchlist(), nextContact.getLabelMask());
        updates.add(contextObj);
        contactSet.get(nextType).add(contextObj.getContactID());
      }
    }

    Map<String, List<IContactLabel>> processLabels = new HashMap<String, List<IContactLabel>>();
    processLabels.put("corporate", contacts.getCorporateContactLabels());
    processLabels.put("alliance", contacts.getAllianceContactLabels());
    for (String nextType : processLabels.keySet()) {
      for (IContactLabel nextLabel : processLabels.get(nextType)) {
        ContactLabel contextObj = new ContactLabel(nextType, nextLabel.getLabelID(), nextLabel.getName());
        updates.add(contextObj);
        labelSet.get(nextType).add(contextObj.getLabelID());
      }
    }

    // From the set of all contacts, find contacts no longer in the list.
    // These should all be EOL
    long contid = -1;
    List<Contact> nextBatch = Contact.getAllContacts(syncAccount, time, 1000, contid);
    while (!nextBatch.isEmpty()) {
      for (Contact next : nextBatch) {
        String list = next.getList();
        int contactID = next.getContactID();
        assert list.equals("corporate") || list.equals("alliance");
        if (!contactSet.get(list).contains(contactID)) {
          next.evolve(null, time);
          updates.add(next);
        }
        contid = Math.max(contid, next.getCid());
      }
      nextBatch = Contact.getAllContacts(syncAccount, time, 1000, contid);
    }
    // Now do the same for ContactLabels
    for (ContactLabel next : ContactLabel.getAllContactLabels(syncAccount, time)) {
      String list = next.getList();
      long labelID = next.getLabelID();
      assert list.equals("corporate") || list.equals("alliance");
      if (!labelSet.get(list).contains(labelID)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationContactListSync syncher = new CorporationContactListSync();

  public static SyncStatus syncContactList(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationContactList");

  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContactList", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationContactList", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
