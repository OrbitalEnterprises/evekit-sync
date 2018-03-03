package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ContactsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdContacts200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Contact;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICorporationContactsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private ContactsApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] contactsTestData;
  private static int[] contactsPages;

  static {
    // Contacts test data
    // 0 String list - always set to "corporation"
    // 1 int contactID
    // 2 float standing
    // 3 String contactType
    // 4 boolean inWatchlist
    // 5 boolean isBlocked - always set to false
    // 6 long labelID
    int size = 100 + TestBase.getRandomInt(100);
    int typeLengths = GetCorporationsCorporationIdContacts200Ok.ContactTypeEnum.values().length;
    contactsTestData = new Object[size][7];
    for (int i = 0; i < size; i++) {
      contactsTestData[i][0] = "corporation";
      contactsTestData[i][1] = TestBase.getUniqueRandomInteger();
      contactsTestData[i][2] = TestBase.getRandomFloat(10);
      contactsTestData[i][3] = GetCorporationsCorporationIdContacts200Ok.ContactTypeEnum.values()[TestBase.getRandomInt(
          typeLengths)];
      contactsTestData[i][4] = TestBase.getRandomBoolean();
      contactsTestData[i][5] = false;
      contactsTestData[i][6] = TestBase.getRandomLong();
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
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTACTS, 1234L);

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
                                                        .createQuery("DELETE FROM Contact ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(ContactsApi.class);

    // Setup contacts mock calls
    List<GetCorporationsCorporationIdContacts200Ok> contactsList =
        Arrays.stream(contactsTestData)
              .map(x -> {
                GetCorporationsCorporationIdContacts200Ok nextContact = new GetCorporationsCorporationIdContacts200Ok();
                nextContact.setContactId((int) x[1]);
                nextContact.setStanding((float) x[2]);
                nextContact.setContactType((GetCorporationsCorporationIdContacts200Ok.ContactTypeEnum) x[3]);
                nextContact.setIsWatched((boolean) x[4]);
                nextContact.setLabelId((long) x[6]);
                return nextContact;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < contactsPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(contactsPages.length));
      ApiResponse<List<GetCorporationsCorporationIdContacts200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                            contactsList.subList(
                                                                                                last,
                                                                                                contactsPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdContactsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = contactsPages[i];
    }

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getContactsApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Contact> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Contact.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                            AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                            AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                            AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(contactsTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < contactsTestData.length; i++) {
      Contact nextEl = storedData.get(i);
      Assert.assertEquals(contactsTestData[i][0], nextEl.getList());
      Assert.assertEquals((int) contactsTestData[i][1], nextEl.getContactID());
      Assert.assertEquals((float) contactsTestData[i][2], nextEl.getStanding(), 0.001);
      Assert.assertEquals(String.valueOf(contactsTestData[i][3]), nextEl.getContactType());
      Assert.assertEquals(contactsTestData[i][4], nextEl.isInWatchlist());
      Assert.assertEquals(contactsTestData[i][5], nextEl.isBlocked());
      Assert.assertEquals((long) contactsTestData[i][6], nextEl.getLabelID());
    }
  }

  private void verifyOldDataUpdated() throws Exception {
    // Retrieve all stored data
    List<Contact> storedData = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                  (long contid, AttributeSelector at) ->
                                                                      Contact.accessQuery(corpSyncAccount, contid,
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
      Assert.assertEquals(contactsTestData[i][0], nextEl.getList());
      Assert.assertEquals((int) contactsTestData[i][1], nextEl.getContactID());
      Assert.assertEquals((float) contactsTestData[i][2] + 1F, nextEl.getStanding(), 0.001);
      Assert.assertEquals(String.valueOf(contactsTestData[i][3]), nextEl.getContactType());
      Assert.assertEquals(!(boolean) contactsTestData[i][4], nextEl.isInWatchlist());
      Assert.assertEquals(contactsTestData[i][5], nextEl.isBlocked());
      Assert.assertEquals((long) contactsTestData[i][6] + 1L, nextEl.getLabelID());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationContactsSync sync = new ESICorporationContactsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CONTACTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTACTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // All of these objects are not in the server data so we can test deletion.
    for (Object[] d : contactsTestData) {
      Contact newEl = new Contact((String) d[0],
                                  (int) d[1],
                                  (float) d[2] + 1F,
                                  String.valueOf(d[3]),
                                  !(boolean) d[4],
                                  (boolean) d[5],
                                  (long) d[6] + 1L);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    // Perform the sync
    ESICorporationContactsSync sync = new ESICorporationContactsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify previous data was evolved properly
    verifyOldDataUpdated();

    // Verify updates
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_CONTACTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_CONTACTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
