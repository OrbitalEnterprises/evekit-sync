package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdNotifications200Ok;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdNotificationsContacts200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterContactNotification;
import enterprises.orbital.evekit.model.character.CharacterNotification;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ESICharacterNotificationSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] contactTestData;
  private static Object[][] noteTestData;

  static {
    int size = 100 + TestBase.getRandomInt(100);
    contactTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      // Character contact notification
      // 0 int notificationID
      // 1 int senderID
      // 2 long sentDate
      // 3 float standingLevel
      // 4 String messageData
      contactTestData[i][0] = TestBase.getUniqueRandomInteger();
      contactTestData[i][1] = TestBase.getRandomInt();
      contactTestData[i][2] = TestBase.getRandomLong();
      contactTestData[i][3] = TestBase.getRandomFloat(10);
      contactTestData[i][4] = TestBase.getRandomText(50);
    }

    size = 100 + TestBase.getRandomInt(100);
    noteTestData = new Object[size][7];
    int typeLen = GetCharactersCharacterIdNotifications200Ok.TypeEnum.values().length;
    int senderTypeLen = GetCharactersCharacterIdNotifications200Ok.SenderTypeEnum.values().length;
    for (int i = 0; i < size; i++) {
      // Character notification
      // 0 long notificationID
      // 1 String type
      // 2 int senderID
      // 3 String senderType
      // 4 long sentDate
      // 5 boolean msgRead
      // 6 String text
      noteTestData[i][0] = TestBase.getUniqueRandomLong();
      noteTestData[i][1] = GetCharactersCharacterIdNotifications200Ok.TypeEnum.values()[TestBase.getRandomInt(typeLen)];
      noteTestData[i][2] = TestBase.getRandomInt();
      noteTestData[i][3] = GetCharactersCharacterIdNotifications200Ok.SenderTypeEnum.values()[TestBase.getRandomInt(
          senderTypeLen)];
      noteTestData[i][4] = TestBase.getRandomLong();
      noteTestData[i][5] = TestBase.getRandomBoolean();
      noteTestData[i][6] = TestBase.getRandomText(50);
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_NOTIFICATIONS, 1234L, null);

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
                                                        .createQuery("DELETE FROM CharacterContactNotification ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CharacterNotification ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CharacterApi.class);

    {
      // Setup contact notifications
      List<GetCharactersCharacterIdNotificationsContacts200Ok> contacts =
          Arrays.stream(contactTestData)
                .map(x -> {
                  GetCharactersCharacterIdNotificationsContacts200Ok newContact = new GetCharactersCharacterIdNotificationsContacts200Ok();
                  newContact.setNotificationId((int) x[0]);
                  newContact.setSenderCharacterId((int) x[1]);
                  newContact.setSendDate(new DateTime(new Date((long) x[2])));
                  newContact.setStandingLevel((float) x[3]);
                  newContact.setMessage((String) x[4]);
                  return newContact;
                })
                .collect(Collectors.toList());

      ApiResponse<List<GetCharactersCharacterIdNotificationsContacts200Ok>> apir = new ApiResponse<>(200,
                                                                                                     createHeaders(
                                                                                                         "Expires",
                                                                                                         "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                                     contacts);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdNotificationsContactsWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    {
      // Setup notifications
      List<GetCharactersCharacterIdNotifications200Ok> notes =
          Arrays.stream(noteTestData)
                .map(x -> {
                  GetCharactersCharacterIdNotifications200Ok newNote = new GetCharactersCharacterIdNotifications200Ok();
                  newNote.setNotificationId((long) x[0]);
                  newNote.setType((GetCharactersCharacterIdNotifications200Ok.TypeEnum) x[1]);
                  newNote.setSenderId((int) x[2]);
                  newNote.setSenderType((GetCharactersCharacterIdNotifications200Ok.SenderTypeEnum) x[3]);
                  newNote.setTimestamp(new DateTime(new Date((long) x[4])));
                  newNote.setIsRead((boolean) x[5]);
                  newNote.setText((String) x[6]);
                  return newNote;
                })
                .collect(Collectors.toList());

      ApiResponse<List<GetCharactersCharacterIdNotifications200Ok>> apir = new ApiResponse<>(200,
                                                                                             createHeaders(
                                                                                                 "Expires",
                                                                                                 "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                             notes);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdNotificationsWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Complete mock with provider
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long atTime, Object[][] testContactData, Object[][] testNoteData) throws Exception {

    // Retrieve stored contact notifications
    List<CharacterContactNotification> storedCN = AbstractESIAccountSync.retrieveAll(atTime,
                                                                                     (long contid, AttributeSelector at) ->
                                                                                         CharacterContactNotification.accessQuery(
                                                                                             charSyncAccount, contid,
                                                                                             1000,
                                                                                             false, at,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testContactData.length, storedCN.size());

    // Check stored data
    for (int i = 0; i < testContactData.length; i++) {
      CharacterContactNotification nextEl = storedCN.get(i);
      Object[] dt = testContactData[i];
      Assert.assertEquals((int) dt[0], nextEl.getNotificationID());
      Assert.assertEquals((int) dt[1], nextEl.getSenderID());
      Assert.assertEquals((long) dt[2], nextEl.getSentDate());
      Assert.assertEquals((float) dt[3], nextEl.getStandingLevel(), 0.001);
      Assert.assertEquals(dt[4], nextEl.getMessageData());
    }

    // Retrieve stored notifications
    List<CharacterNotification> storedN = AbstractESIAccountSync.retrieveAll(atTime,
                                                                             (long contid, AttributeSelector at) ->
                                                                                 CharacterNotification.accessQuery(
                                                                                     charSyncAccount, contid, 1000,
                                                                                     false, at,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testNoteData.length, storedN.size());

    // Check stored data
    for (int i = 0; i < testNoteData.length; i++) {
      CharacterNotification nextEl = storedN.get(i);
      Object[] dt = testNoteData[i];
      Assert.assertEquals((long) dt[0], nextEl.getNotificationID());
      Assert.assertEquals(String.valueOf(dt[1]), nextEl.getType());
      Assert.assertEquals((int) dt[2], nextEl.getSenderID());
      Assert.assertEquals(String.valueOf(dt[3]), nextEl.getSenderType());
      Assert.assertEquals((long) dt[4], nextEl.getSentDate());
      Assert.assertEquals(dt[5], nextEl.isMsgRead());
      Assert.assertEquals(dt[6], nextEl.getText());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterNotificationSync sync = new ESICharacterNotificationSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, contactTestData, noteTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_NOTIFICATIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_NOTIFICATIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing contact notifications.  Since contact notifications are immutable,
    // we make these with unique notification IDs.
    Object[][] newContactTestData = new Object[contactTestData.length][5];
    for (int i = 0; i < contactTestData.length; i++) {
      newContactTestData[i][0] = TestBase.getUniqueRandomInteger();
      newContactTestData[i][1] = (int) contactTestData[i][1] + 1;
      newContactTestData[i][2] = (long) contactTestData[i][2] + 1;
      newContactTestData[i][3] = (float) contactTestData[i][3] + 1.0F;
      newContactTestData[i][4] = contactTestData[4] + "1";

      CharacterContactNotification existing = new CharacterContactNotification(
          (int) newContactTestData[i][0],
          (int) newContactTestData[i][1],
          (long) newContactTestData[i][2],
          (float) newContactTestData[i][3],
          (String) newContactTestData[i][4]);
      existing.setup(charSyncAccount, testTime - 1);
      CachedData.update(existing);
    }

    // Populate existing notifications.  These should be updated.
    Object[][] newNoteTestData = new Object[noteTestData.length][7];
    for (int i = 0; i < noteTestData.length; i++) {
      newNoteTestData[i][0] = noteTestData[i][0];
      newNoteTestData[i][1] = noteTestData[i][1];
      newNoteTestData[i][2] = (int) noteTestData[i][2] + 1;
      newNoteTestData[i][3] = noteTestData[i][3];
      newNoteTestData[i][4] = (long) noteTestData[i][4] + 1;
      newNoteTestData[i][5] = !(boolean) noteTestData[i][5];
      newNoteTestData[i][6] = noteTestData[i][6] + "1";

      CharacterNotification existing = new CharacterNotification(
          (long) newNoteTestData[i][0],
          String.valueOf(newNoteTestData[i][1]),
          (int) newNoteTestData[i][2],
          String.valueOf(newNoteTestData[i][3]),
          (long) newNoteTestData[i][4],
          (boolean) newNoteTestData[i][5],
          (String) newNoteTestData[i][6]);

      existing.setup(charSyncAccount, testTime - 1);
      CachedData.update(existing);
    }


    // Perform the sync
    ESICharacterNotificationSync sync = new ESICharacterNotificationSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old data updates
    verifyDataUpdate(testTime - 1, newContactTestData, newNoteTestData);

    // Verify new data updates
    Object[][] appContactData = new Object[newContactTestData.length + contactTestData.length][5];
    System.arraycopy(newContactTestData, 0, appContactData, 0, newContactTestData.length);
    System.arraycopy(contactTestData, 0, appContactData, newContactTestData.length, contactTestData.length);
    verifyDataUpdate(testTime, appContactData, noteTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_NOTIFICATIONS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_NOTIFICATIONS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
