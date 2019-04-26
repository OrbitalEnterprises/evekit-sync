package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.api.ContactsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetAlliancesAllianceIdContacts200Ok;
import enterprises.orbital.eve.esi.client.model.GetAlliancesAllianceIdContactsLabels200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdOk;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Contact;
import enterprises.orbital.evekit.model.common.ContactLabel;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterAllianceContactsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi capiMock;
  private ContactsApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] contactsTestData;
  private static Object[][] labelsTestData;
  private static int[] contactsPages;

  static {
    // Contacts test data
    // 0 String list - always set to "alliance"
    // 1 int contactID
    // 2 float standing
    // 3 String contactType
    // 4 long[] labels
    int size = 100 + TestBase.getRandomInt(100);
    int typeLengths = GetAlliancesAllianceIdContacts200Ok.ContactTypeEnum.values().length;
    contactsTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      contactsTestData[i][0] = "alliance";
      contactsTestData[i][1] = TestBase.getUniqueRandomInteger();
      contactsTestData[i][2] = TestBase.getRandomFloat(10);
      contactsTestData[i][3] = GetAlliancesAllianceIdContacts200Ok.ContactTypeEnum.values()[TestBase.getRandomInt(
          typeLengths)];
      int labelLength = 5 + TestBase.getRandomInt(5);
      long[] labels = new long[labelLength];
      for (int j = 0; j < labelLength; j++)
        labels[j] = TestBase.getUniqueRandomLong();
      contactsTestData[i][4] = labels;
    }

    // Labels test data
    // 0 String list - always set to "alliance"
    // 1 long labelID
    // 2 String name
    int bkSize = 50 + TestBase.getRandomInt(50);
    labelsTestData = new Object[bkSize][3];
    for (int i = 0; i < bkSize; i++) {
      labelsTestData[i][0] = "alliance";
      labelsTestData[i][1] = TestBase.getUniqueRandomLong();
      labelsTestData[i][2] = TestBase.getRandomText(50);
    }

    // Configure page separations
    int pageCount = 2 + TestBase.getRandomInt(4);
    contactsPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      contactsPages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_ALLIANCE_CONTACTS, 1234L,
                                                        null);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createNativeQuery("DELETE FROM contact_label ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM Contact ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM ContactLabel ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    // Mock call to retrieve character sheet
    capiMock = EasyMock.createMock(CharacterApi.class);

    int allianceID = 1578;
    {
      GetCharactersCharacterIdOk charSheet = new GetCharactersCharacterIdOk();
      charSheet.setAllianceId(allianceID);
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
      ApiResponse<GetCharactersCharacterIdOk> apir = new ApiResponse<>(200, headers, charSheet);
      EasyMock.expect(capiMock.getCharactersCharacterIdWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    mockEndpoint = EasyMock.createMock(ContactsApi.class);

    // Setup contacts mock calls
    List<GetAlliancesAllianceIdContacts200Ok> contactsList =
        Arrays.stream(contactsTestData)
              .map(x -> {
                GetAlliancesAllianceIdContacts200Ok nextContact = new GetAlliancesAllianceIdContacts200Ok();
                nextContact.setContactId((int) x[1]);
                nextContact.setStanding((float) x[2]);
                nextContact.setContactType((GetAlliancesAllianceIdContacts200Ok.ContactTypeEnum) x[3]);
                for (long next : (long[]) x[4])
                  nextContact.getLabelIds()
                             .add(next);
                return nextContact;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < contactsPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(contactsPages.length));
      ApiResponse<List<GetAlliancesAllianceIdContacts200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                      contactsList.subList(
                                                                                          last,
                                                                                          contactsPages[i]));
      EasyMock.expect(mockEndpoint.getAlliancesAllianceIdContactsWithHttpInfo(
          EasyMock.eq(allianceID),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(apir);
      last = contactsPages[i];
    }

    // Setup labels mock calls
    List<GetAlliancesAllianceIdContactsLabels200Ok> bookmarksList =
        Arrays.stream(labelsTestData)
              .map(x -> {
                GetAlliancesAllianceIdContactsLabels200Ok nextLabel = new GetAlliancesAllianceIdContactsLabels200Ok();
                nextLabel.setLabelId((long) x[1]);
                nextLabel.setLabelName((String) x[2]);
                return nextLabel;
              })
              .collect(Collectors.toList());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetAlliancesAllianceIdContactsLabels200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                          bookmarksList);
    EasyMock.expect(mockEndpoint.getAlliancesAllianceIdContactsLabelsWithHttpInfo(
        EasyMock.eq(allianceID),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getContactsApi())
            .andReturn(mockEndpoint);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(capiMock);
  }

  private void verifyDataUpdate() throws Exception {
    {
      // Retrieve all stored data
      List<Contact> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
          Contact.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

      // Check data matches test data
      Assert.assertEquals(contactsTestData.length, storedData.size());

      // Check stored data
      for (Object[] contactsTestDatum : contactsTestData) {
        String listName = (String) contactsTestDatum[0];
        int contactID = (int) contactsTestDatum[1];
        Contact nextEl = null;
        for (Contact j : storedData) {
          if (j.getList()
               .equals(listName) && j.getContactID() == contactID) {
            nextEl = j;
            break;
          }
        }
        Assert.assertNotNull(nextEl);
        long[] labels = (long[]) contactsTestDatum[4];
        Assert.assertEquals(contactsTestDatum[0], nextEl.getList());
        Assert.assertEquals((int) contactsTestDatum[1], nextEl.getContactID());
        Assert.assertEquals((float) contactsTestDatum[2], nextEl.getStanding(), 0.001);
        Assert.assertEquals(String.valueOf(contactsTestDatum[3]), nextEl.getContactType());
        Assert.assertFalse(nextEl.isInWatchlist());
        Assert.assertFalse(nextEl.isBlocked());
        Assert.assertEquals(labels.length, nextEl.getLabels()
                                                 .size());
        for (long j : labels) {
          Assert.assertTrue(nextEl.getLabels()
                                  .contains(j));
        }
      }
    }

    {
      // Retrieve all stored data
      List<ContactLabel> storedData = AbstractESIAccountSync.retrieveAll(testTime,
                                                                         (long contid, AttributeSelector at) ->
                                                                             ContactLabel.accessQuery(charSyncAccount,
                                                                                                      contid, 1000,
                                                                                                      false,
                                                                                                      at,
                                                                                                      AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                      AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                      AbstractESIAccountSync.ANY_SELECTOR));

      // Check data matches test data
      Assert.assertEquals(labelsTestData.length, storedData.size());

      // Check stored data
      for (int i = 0; i < labelsTestData.length; i++) {
        ContactLabel nextEl = null;
        long labelID = (long) labelsTestData[i][1];
        for (ContactLabel j : storedData) {
          if (j.getLabelID() == labelID) {
            nextEl = j;
            break;
          }
        }
        Assert.assertNotNull(nextEl);
        Assert.assertEquals(labelsTestData[i][0], nextEl.getList());
        Assert.assertEquals((long) labelsTestData[i][1], nextEl.getLabelID());
        Assert.assertEquals(labelsTestData[i][2], nextEl.getName());
      }
    }

  }

  private void verifyOldDataUpdated() throws Exception {
    {
      // Retrieve all stored data
      List<Contact> storedData = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                    (long contid, AttributeSelector at) ->
                                                                        Contact.accessQuery(charSyncAccount, contid,
                                                                                            1000, false, at,
                                                                                            AbstractESIAccountSync.ANY_SELECTOR,
                                                                                            AbstractESIAccountSync.ANY_SELECTOR,
                                                                                            AbstractESIAccountSync.ANY_SELECTOR,
                                                                                            AbstractESIAccountSync.ANY_SELECTOR,
                                                                                            AbstractESIAccountSync.ANY_SELECTOR,
                                                                                            AbstractESIAccountSync.ANY_SELECTOR,
                                                                                            AbstractESIAccountSync.ANY_SELECTOR));

      // Check data matches test data
      Assert.assertEquals(contactsTestData.length, storedData.size());

      // Check stored data
      for (int i = 0; i < contactsTestData.length; i++) {
        Contact nextEl = storedData.get(i);
        long[] labels = (long[]) contactsTestData[i][4];
        Assert.assertEquals(contactsTestData[i][0], nextEl.getList());
        Assert.assertEquals((int) contactsTestData[i][1], nextEl.getContactID());
        Assert.assertEquals((float) contactsTestData[i][2] + 1F, nextEl.getStanding(), 0.001);
        Assert.assertEquals(String.valueOf(contactsTestData[i][3]), nextEl.getContactType());
        Assert.assertFalse(nextEl.isInWatchlist());
        Assert.assertFalse(nextEl.isBlocked());
        Assert.assertEquals(labels.length, nextEl.getLabels()
                                                 .size());
        for (long j : labels) {
          Assert.assertTrue(nextEl.getLabels()
                                  .contains(j));
        }
      }
    }

    {
      // Retrieve all stored data
      List<ContactLabel> storedData = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                         (long contid, AttributeSelector at) ->
                                                                             ContactLabel.accessQuery(charSyncAccount,
                                                                                                      contid, 1000,
                                                                                                      false,
                                                                                                      at,
                                                                                                      AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                      AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                      AbstractESIAccountSync.ANY_SELECTOR));

      // Check data matches test data
      Assert.assertEquals(labelsTestData.length, storedData.size());

      // Check stored data
      for (int i = 0; i < labelsTestData.length; i++) {
        ContactLabel nextEl = storedData.get(i);
        Assert.assertEquals(labelsTestData[i][0], nextEl.getList());
        Assert.assertEquals((long) labelsTestData[i][1], nextEl.getLabelID());
        Assert.assertEquals(labelsTestData[i][2] + "1", nextEl.getName());
      }
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint, capiMock);

    // Perform the sync
    ESICharacterAllianceContactsSync sync = new ESICharacterAllianceContactsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint, capiMock);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_ALLIANCE_CONTACTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_ALLIANCE_CONTACTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint, capiMock);

    // Populate existing
    //
    // All of these objects are not in the server data so we can test deletion.
    for (Object[] d : contactsTestData) {
      Set<Long> labels = new HashSet<>();
      for (long next : (long[]) d[4])
        labels.add(next);
      Contact newEl = new Contact((String) d[0],
                                  (int) d[1],
                                  (float) d[2] + 1F,
                                  String.valueOf(d[3]),
                                  false,
                                  false,
                                  labels);
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    for (Object[] d : labelsTestData) {
      ContactLabel newEl = new ContactLabel((String) d[0],
                                            (long) d[1],
                                            d[2] + "1");
      newEl.setup(charSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICharacterAllianceContactsSync sync = new ESICharacterAllianceContactsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint, capiMock);

    // Verify previous data was evolved properly
    verifyOldDataUpdated();

    // Verify updates
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_ALLIANCE_CONTACTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_ALLIANCE_CONTACTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
