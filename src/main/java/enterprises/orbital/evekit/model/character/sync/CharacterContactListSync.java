package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.Contact;
import enterprises.orbital.evekit.model.common.ContactLabel;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IContact;
import enterprises.orbital.evexmlapi.shared.IContactLabel;
import enterprises.orbital.evexmlapi.shared.IContactSet;

public class CharacterContactListSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterContactListSync.class.getName());

  @Override
  public boolean isRefreshed(CapsuleerSyncTracker tracker) {
    return tracker.getContactListStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {
    tracker.setContactListStatus(status);
    tracker.setContactListDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Capsuleer container, long expiry) throws IOException {
    container.setContactListExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(Capsuleer container) {
    return container.getContactListExpiry();
  }

  @Override
  public boolean commit(long time, CapsuleerSyncTracker tracker, Capsuleer container, SynchronizedEveAccount accountKey, CachedData item) throws IOException {

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
  protected Object getServerData(ICharacterAPI charRequest) throws IOException {
    return charRequest.requestContacts();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
    throws IOException {
    IContactSet contacts = (IContactSet) data;

    Map<String, Set<Integer>> contactSet = new HashMap<String, Set<Integer>>();
    contactSet.put("character", new HashSet<Integer>());
    contactSet.put("corporate", new HashSet<Integer>());
    contactSet.put("alliance", new HashSet<Integer>());
    Map<String, Set<Long>> labelSet = new HashMap<String, Set<Long>>();
    labelSet.put("character", new HashSet<Long>());
    labelSet.put("corporate", new HashSet<Long>());
    labelSet.put("alliance", new HashSet<Long>());

    Map<String, List<IContact>> processContacts = new HashMap<String, List<IContact>>();
    processContacts.put("character", contacts.getContacts());
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
    processLabels.put("character", contacts.getContactLabels());
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
        assert list.equals("character") || list.equals("corporate") || list.equals("alliance");
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
      assert list.equals("character") || list.equals("corporate") || list.equals("alliance");
      if (!labelSet.get(list).contains(labelID)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterContactListSync syncher = new CharacterContactListSync();

  public static SyncStatus syncContactList(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "CharacterContactList");

  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContactList", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CharacterContactList", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
