package enterprises.orbital.evekit.model.corporation.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
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

public class CorporationContactListSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  static String[]        listTypes = {
                                       "corporate", "alliance"
                                     };

  static Object[][]      testData;

  static {
    // Generate test data
    // 0 list
    // 1 contactID
    // 2 contactName
    // 3 standing
    // 4 inWatchList
    // 5 contactTypeID
    // 6 labelMask
    // 7 labelList
    // 8 labelID
    // 9 labelName
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][10];
    for (int i = 0; i < size; i++) {
      testData[i][0] = listTypes[TestBase.getRandomInt(2)];
      testData[i][1] = TestBase.getUniqueRandomInteger();
      testData[i][2] = TestBase.getRandomText(50);
      testData[i][3] = TestBase.getRandomDouble(10.0);
      testData[i][4] = TestBase.getRandomBoolean();
      testData[i][5] = TestBase.getRandomInt();
      testData[i][6] = TestBase.getRandomLong();
      testData[i][7] = listTypes[TestBase.getRandomInt(2)];
      testData[i][8] = TestBase.getUniqueRandomLong();
      testData[i][9] = TestBase.getRandomText(50);
    }
  }

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IContact makeIContact(final Object[] data) {
    return new IContact() {

      @Override
      public int getContactID() {
        return (Integer) data[1];
      }

      @Override
      public String getContactName() {
        return (String) data[2];
      }

      @Override
      public double getStanding() {
        return (Double) data[3];
      }

      @Override
      public int getContactTypeID() {
        return (Integer) data[5];
      }

      @Override
      public boolean isInWatchlist() {
        return (Boolean) data[4];
      }

      @Override
      public long getLabelMask() {
        return (Long) data[6];
      }

    };
  }

  public IContactLabel makeIContactLabel(final Object[] data) {
    return new IContactLabel() {

      @Override
      public long getLabelID() {
        return (Long) data[8];
      }

      @Override
      public String getName() {
        return (String) data[9];
      }

    };
  }

  public List<IContact> collectIContact(String type, Object[][] sourceData) {
    List<IContact> result = new ArrayList<IContact>();
    for (int i = 0; i < sourceData.length; i++) {
      String next = (String) sourceData[i][0];
      if (next.equals(type)) {
        result.add(makeIContact(sourceData[i]));
      }
    }
    return result;
  }

  public List<IContactLabel> collectIContactLabel(String type, Object[][] sourceData) {
    List<IContactLabel> result = new ArrayList<IContactLabel>();
    for (int i = 0; i < sourceData.length; i++) {
      String next = (String) sourceData[i][7];
      if (next.equals(type)) {
        result.add(makeIContactLabel(sourceData[i]));
      }
    }
    return result;
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    IContactSet contactSet = new IContactSet() {

      @Override
      public List<IContact> getContacts() {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<IContact> getCorporateContacts() {
        return collectIContact("corporate", testData);
      }

      @Override
      public List<IContact> getAllianceContacts() {
        return collectIContact("alliance", testData);
      }

      @Override
      public List<IContactLabel> getContactLabels() {
        throw new UnsupportedOperationException();
      }

      @Override
      public List<IContactLabel> getCorporateContactLabels() {
        return collectIContactLabel("corporate", testData);
      }

      @Override
      public List<IContactLabel> getAllianceContactLabels() {
        return collectIContactLabel("alliance", testData);
      }

    };

    EasyMock.expect(mockServer.requestContacts()).andReturn(contactSet);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new medals
  @Test
  public void testContactListSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationContactListSync.syncContactList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Check contacts
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][0];
      int contactID = (Integer) testData[i][1];
      Contact next = Contact.get(syncAccount, testTime, list, contactID);
      Assert.assertEquals(testData[i][2], next.getContactName());
      Assert.assertEquals(((Double) testData[i][3]), next.getStanding(), 0.01);
      Assert.assertEquals((boolean) ((Boolean) testData[i][4]), next.isInWatchlist());
      Assert.assertEquals((int) ((Integer) testData[i][5]), next.getContactTypeID());
      Assert.assertEquals((long) ((Long) testData[i][6]), next.getLabelMask());
    }

    // Check labels
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][7];
      long labelID = (Long) testData[i][8];
      ContactLabel next = ContactLabel.get(syncAccount, testTime, list, labelID);
      Assert.assertEquals(testData[i][9], next.getName());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContactListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListDetail());
  }

  // Test update with contacts already populated
  @Test
  public void testContactListSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contacts
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][0];
      int contactID = (Integer) testData[i][1];
      Contact next = new Contact(
          list, contactID, (String) testData[i][2] + "foo", (Double) testData[i][3], (Integer) testData[i][5], (Boolean) testData[i][4], (Long) testData[i][6]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Populate existing labels
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][7];
      long labelID = (Long) testData[i][8];
      ContactLabel next = new ContactLabel(list, labelID, (String) testData[i][9] + "foo");
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationContactListSync.syncContactList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify contact and labels are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][0];
      int contactID = (Integer) testData[i][1];
      Contact next = Contact.get(syncAccount, testTime, list, contactID);
      Assert.assertEquals(testData[i][2], next.getContactName());
      Assert.assertEquals(((Double) testData[i][3]), next.getStanding(), 0.01);
      Assert.assertEquals((boolean) ((Boolean) testData[i][4]), next.isInWatchlist());
      Assert.assertEquals((int) ((Integer) testData[i][5]), next.getContactTypeID());
      Assert.assertEquals((long) ((Long) testData[i][6]), next.getLabelMask());
    }

    // and labels as well...
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][7];
      long labelID = (Long) testData[i][8];
      ContactLabel next = ContactLabel.get(syncAccount, testTime, list, labelID);
      Assert.assertEquals(testData[i][9], next.getName());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContactListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListDetail());
  }

  // Test skips update when already updated
  @Test
  public void testContactListSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contacts
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][0];
      int contactID = (Integer) testData[i][1];
      Contact next = new Contact(
          list, contactID, (String) testData[i][2] + "foo", (Double) testData[i][3], (Integer) testData[i][5], (Boolean) testData[i][4], (Long) testData[i][6]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Populate existing labels
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][7];
      long labelID = (Long) testData[i][8];
      ContactLabel next = new ContactLabel(list, labelID, (String) testData[i][9] + "foo");
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setContactListStatus(SyncState.UPDATED);
    tracker.setContactListDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setContactListExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationContactListSync.syncContactList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify contacts unchanged
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][0];
      int contactID = (Integer) testData[i][1];
      Contact next = Contact.get(syncAccount, testTime, list, contactID);
      Assert.assertEquals((String) testData[i][2] + "foo", next.getContactName());
      Assert.assertEquals(((Double) testData[i][3]), next.getStanding(), 0.01);
      Assert.assertEquals((boolean) ((Boolean) testData[i][4]), next.isInWatchlist());
    }

    // and labels as well...
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][7];
      long labelID = (Long) testData[i][8];
      ContactLabel next = ContactLabel.get(syncAccount, testTime, list, labelID);
      Assert.assertEquals((String) testData[i][9] + "foo", next.getName());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getContactListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListDetail());
  }

  // Test update with contacts which should be deleted
  @Test
  public void testContactListSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contacts which should be deleted
    List<Contact> toDelete = new ArrayList<Contact>();
    for (int i = 0; i < 5; i++) {
      String list = listTypes[TestBase.getRandomInt(2)];
      int contactID = TestBase.getUniqueRandomInteger();
      Contact next = new Contact(
          list, contactID, TestBase.getRandomText(50), TestBase.getRandomDouble(10), TestBase.getRandomInt(), TestBase.getRandomBoolean(),
          TestBase.getRandomLong());
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      toDelete.add(next);
    }

    // and labels as well...
    List<ContactLabel> toDeleteLabel = new ArrayList<ContactLabel>();
    for (int i = 0; i < 5; i++) {
      String list = listTypes[TestBase.getRandomInt(2)];
      long labelID = TestBase.getUniqueRandomLong();
      ContactLabel next = new ContactLabel(list, labelID, TestBase.getRandomText(50));
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
      toDeleteLabel.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationContactListSync.syncContactList(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted contacts no longer exist
    Assert.assertEquals(testData.length, Contact.getAllContacts(syncAccount, testTime, -1, -1).size());
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][0];
      int contactID = (Integer) testData[i][1];
      Contact next = Contact.get(syncAccount, testTime, list, contactID);
      Assert.assertEquals(testData[i][2], next.getContactName());
      Assert.assertEquals(((Double) testData[i][3]), next.getStanding(), 0.01);
      Assert.assertEquals((boolean) ((Boolean) testData[i][4]), next.isInWatchlist());
    }
    for (Contact i : toDelete) {
      Assert.assertNull(Contact.get(syncAccount, testTime, i.getList(), i.getContactID()));
    }

    // and labels as well...
    Assert.assertEquals(testData.length, ContactLabel.getAllContactLabels(syncAccount, testTime).size());
    for (int i = 0; i < testData.length; i++) {
      String list = (String) testData[i][7];
      long labelID = (Long) testData[i][8];
      ContactLabel next = ContactLabel.get(syncAccount, testTime, list, labelID);
      Assert.assertEquals(testData[i][9], next.getName());
    }
    for (ContactLabel i : toDeleteLabel) {
      Assert.assertNull(ContactLabel.get(syncAccount, testTime, i.getList(), i.getLabelID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContactListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContactListDetail());
  }
}
