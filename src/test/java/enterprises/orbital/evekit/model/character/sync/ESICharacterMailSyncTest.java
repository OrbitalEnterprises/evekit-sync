package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.MailApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterMailMessage;
import enterprises.orbital.evekit.model.character.MailLabel;
import enterprises.orbital.evekit.model.character.MailMessageRecipient;
import enterprises.orbital.evekit.model.character.MailingList;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

public class ESICharacterMailSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private MailApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] mailTestData;
  private static int[] mailPages;
  private static Object[][] mailLabelsTestData;
  private static Object[][] mailingListsTestData;

  static {
    // Comparator for sorting test data in decreasing order by mailID (testData[i][0])
    Comparator<Object[]> mailDataCompare = Comparator.comparingLong(x -> (Long) x[0]);

    int size = 100 + TestBase.getRandomInt(100);
    mailTestData = new Object[size][8];
    for (int i = 0; i < size; i++) {
      // Mail header + body data
      // 0 long mailID
      // 1 String subject
      // 2 int from
      // 3 long timestamp
      // 4 int[] labels
      // 5 Object[] recipients
      // 6 boolean isRead
      // 7 String body
      //
      // We only process batch 0, so make sure all IDs end in 0
      mailTestData[i][0] = (TestBase.getUniqueRandomLong() / 10) * 10;
      mailTestData[i][1] = TestBase.getRandomText(50);
      mailTestData[i][2] = TestBase.getRandomInt();
      mailTestData[i][3] = TestBase.getRandomLong();
      int numLabels = 5 + TestBase.getRandomInt(5);
      int[] labels = new int[numLabels];
      for (int j = 0; j < numLabels; j++)
        labels[j] = TestBase.getRandomInt();
      mailTestData[i][4] = labels;
      int numRecipients = 5 + TestBase.getRandomInt(5);
      int recipientTypeLength = GetCharactersCharacterIdMailRecipient.RecipientTypeEnum.values().length;
      Object[][] recipients = new Object[numRecipients][2];
      for (int j = 0; j < numRecipients; j++) {
        recipients[j][0] = GetCharactersCharacterIdMailRecipient.RecipientTypeEnum.values()[TestBase.getRandomInt(
            recipientTypeLength)];
        recipients[j][1] = TestBase.getRandomInt();
      }
      mailTestData[i][5] = recipients;
      mailTestData[i][6] = TestBase.getRandomBoolean();
      mailTestData[i][7] = TestBase.getRandomText(1000);
    }

    // Sort test data in decreasing order by mailID
    Arrays.sort(mailTestData, 0, mailTestData.length, mailDataCompare);

    // Divide data into pages to test paging sync feature
    int pageCount = 3 + TestBase.getRandomInt(3);
    mailPages = new int[pageCount];
    for (int i = 0; i < pageCount; i++) {
      mailPages[i] = i * size / pageCount;
    }

    // Mail labels data
    // 0 int unreadCount
    // 1 int labelID
    // 2 String name
    // 3 Enum color
    int labelSize = 10 + TestBase.getRandomInt(10);
    int colorsLength = GetCharactersCharacterIdMailLabelsLabel.ColorEnum.values().length;
    mailLabelsTestData = new Object[labelSize][4];
    for (int i = 0; i < labelSize; i++) {
      mailLabelsTestData[i][0] = TestBase.getRandomInt();
      mailLabelsTestData[i][1] = TestBase.getUniqueRandomInteger();
      mailLabelsTestData[i][2] = TestBase.getRandomText(50);
      mailLabelsTestData[i][3] = GetCharactersCharacterIdMailLabelsLabel.ColorEnum.values()[TestBase.getRandomInt(
          colorsLength)];
    }

    // Mail lists data
    // 0 int listID
    // 1 String name
    int listsSize = 10 + TestBase.getRandomInt(10);
    mailingListsTestData = new Object[listsSize][2];
    for (int i = 0; i < listsSize; i++) {
      mailingListsTestData[i][0] = TestBase.getUniqueRandomInteger();
      mailingListsTestData[i][1] = TestBase.getRandomText(50);
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MAIL, 1234L, "0");

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
                                                        .createNativeQuery("DELETE FROM mail_message_label ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createNativeQuery("DELETE FROM mail_message_recipient ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CharacterMailMessage ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MailLabel ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MailingList ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(MailApi.class);

    // Setup mail headers and bodies
    List<GetCharactersCharacterIdMail200Ok> headers = new ArrayList<>();
    Map<Long, GetCharactersCharacterIdMailMailIdOk> bodies = new HashMap<>();
    for (Object[] nm : mailTestData) {
      // Add header
      GetCharactersCharacterIdMail200Ok nextHeader = new GetCharactersCharacterIdMail200Ok();
      nextHeader.setMailId((long) nm[0]);
      nextHeader.setSubject((String) nm[1]);
      nextHeader.setFrom((int) nm[2]);
      nextHeader.setTimestamp(new DateTime(new Date((long) nm[3])));
      nextHeader.setIsRead((boolean) nm[6]);
      for (int i : (int[]) nm[4])
        nextHeader.getLabels()
                  .add((long) i);
      for (Object[] nr : (Object[][]) nm[5]) {
        GetCharactersCharacterIdMailRecipient.RecipientTypeEnum rt = (GetCharactersCharacterIdMailRecipient.RecipientTypeEnum) nr[0];
        int ri = (int) nr[1];
        GetCharactersCharacterIdMailRecipient nrr = new GetCharactersCharacterIdMailRecipient();
        nrr.setRecipientId(ri);
        nrr.setRecipientType(rt);
        nextHeader.getRecipients()
                  .add(nrr);
      }
      headers.add(nextHeader);

      // Add body
      GetCharactersCharacterIdMailMailIdOk nextBody = new GetCharactersCharacterIdMailMailIdOk();
      nextBody.setFrom((int) nm[2]);
      nextBody.setBody((String) nm[7]);
      nextBody.setRead((boolean) nm[6]);
      nextBody.setSubject((String) nm[1]);
      nextBody.setTimestamp(new DateTime(new Date((long) nm[3])));
      nextBody.getLabels()
              .addAll(nextHeader.getLabels());
      for (Object[] nr : (Object[][]) nm[5]) {
        GetCharactersCharacterIdMailMailIdRecipient.RecipientTypeEnum rt = null;
        switch ((GetCharactersCharacterIdMailRecipient.RecipientTypeEnum) nr[0]) {
          case ALLIANCE:
            rt = GetCharactersCharacterIdMailMailIdRecipient.RecipientTypeEnum.ALLIANCE;
            break;
          case CHARACTER:
            rt = GetCharactersCharacterIdMailMailIdRecipient.RecipientTypeEnum.CHARACTER;
            break;
          case CORPORATION:
            rt = GetCharactersCharacterIdMailMailIdRecipient.RecipientTypeEnum.CORPORATION;
            break;
          case MAILING_LIST:
            rt = GetCharactersCharacterIdMailMailIdRecipient.RecipientTypeEnum.MAILING_LIST;
            break;
        }
        int ri = (int) nr[1];
        GetCharactersCharacterIdMailMailIdRecipient nrr = new GetCharactersCharacterIdMailMailIdRecipient();
        nrr.setRecipientId(ri);
        nrr.setRecipientType(rt);
        nextBody.getRecipients()
                .add(nrr);
      }
      bodies.put(nextHeader.getMailId(), nextBody);
    }

    // Setup mail header list mock
    @SuppressWarnings("unchecked")
    List<GetCharactersCharacterIdMail200Ok>[] pages = new List[mailPages.length];
    for (int i = 0; i < mailPages.length; i++) {
      int limit = i + 1 == mailPages.length ? mailTestData.length : mailPages[i + 1];
      pages[i] = headers.subList(mailPages[i], limit);
    }
    for (int i = mailPages.length; i >= 0; i--) {
      Integer mailID = i < mailPages.length ? pages[i].get(0)
                                                      .getMailId()
                                                      .intValue() : Integer.MAX_VALUE;
      List<GetCharactersCharacterIdMail200Ok> data = i > 0 ? pages[i - 1] : Collections.emptyList();
      ApiResponse<List<GetCharactersCharacterIdMail200Ok>> apir = new ApiResponse<>(200,
                                                                                    createHeaders("Expires",
                                                                                                  "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                    data);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdMailWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(mailID),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Now setup mail body calls
    for (Map.Entry<Long, GetCharactersCharacterIdMailMailIdOk> nb : bodies.entrySet()) {
      ApiResponse<GetCharactersCharacterIdMailMailIdOk> apir = new ApiResponse<>(200,
                                                                                 createHeaders("Expires",
                                                                                               "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                 nb.getValue());
      // Must be "anyTimes" because mail sync will batch to only those mail IDs that end in 0
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdMailMailIdWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.eq(nb.getKey()
                        .intValue()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir)
              .anyTimes();
    }

    // Setup mail labels mock
    {
      GetCharactersCharacterIdMailLabelsOk mlData = new GetCharactersCharacterIdMailLabelsOk();
      mlData.setTotalUnreadCount(TestBase.getRandomInt());
      for (Object[] ld : mailLabelsTestData) {
        GetCharactersCharacterIdMailLabelsLabel lbl = new GetCharactersCharacterIdMailLabelsLabel();
        lbl.setUnreadCount((int) ld[0]);
        lbl.setLabelId((int) ld[1]);
        lbl.setName((String) ld[2]);
        lbl.setColor((GetCharactersCharacterIdMailLabelsLabel.ColorEnum) ld[3]);
        mlData.getLabels()
              .add(lbl);
      }
      ApiResponse<GetCharactersCharacterIdMailLabelsOk> apir = new ApiResponse<>(200,
                                                                                 createHeaders("Expires",
                                                                                               "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                 mlData);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdMailLabelsWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Setup mailing lists mock
    {
      List<GetCharactersCharacterIdMailLists200Ok> listData = new ArrayList<>();
      for (Object[] ld : mailingListsTestData) {
        GetCharactersCharacterIdMailLists200Ok ll = new GetCharactersCharacterIdMailLists200Ok();
        ll.setMailingListId((int) ld[0]);
        ll.setName((String) ld[1]);
        listData.add(ll);
      }
      ApiResponse<List<GetCharactersCharacterIdMailLists200Ok>> apir = new ApiResponse<>(200,
                                                                                         createHeaders("Expires",
                                                                                                       "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                         listData);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdMailListsWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
    }

    // Complete mock with provider
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getMailApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long atTime, Object[][] testMailData, Object[][] testLabelData,
                                Object[][] testListData) throws Exception {
    // Retrieve stored messages
    List<CharacterMailMessage> storedMail = AbstractESIAccountSync.retrieveAll(atTime,
                                                                               (long contid, AttributeSelector at) ->
                                                                                   CharacterMailMessage.accessQuery(
                                                                                       charSyncAccount, contid, 1000,
                                                                                       false, at,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR,
                                                                                       AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testMailData.length, storedMail.size());

    // Check stored data
    for (int i = 0; i < testMailData.length; i++) {
      CharacterMailMessage nextEl = storedMail.get(i);
      Object[] dt = testMailData[i];
      int[] labels = (int[]) dt[4];
      Object[][] recipients = (Object[][]) dt[5];
      Assert.assertEquals((long) dt[0], nextEl.getMessageID());
      Assert.assertEquals(dt[1], nextEl.getTitle());
      Assert.assertEquals((int) dt[2], nextEl.getSenderID());
      Assert.assertEquals((long) dt[3], nextEl.getSentDate());
      Assert.assertEquals(dt[6], nextEl.isMsgRead());
      Assert.assertEquals(dt[7], nextEl.getBody());
      Assert.assertEquals(labels.length, nextEl.getLabels()
                                               .size());
      for (int j : labels) {
        Assert.assertTrue(nextEl.getLabels()
                                .contains(j));
      }
      Assert.assertEquals(recipients.length, nextEl.getRecipients()
                                                   .size());
      for (int j = 0; j < recipients.length; j++) {
        MailMessageRecipient rcp = new MailMessageRecipient(String.valueOf(recipients[j][0]), (int) recipients[j][1]);
        Assert.assertTrue(nextEl.getRecipients()
                                .contains(rcp));
      }
    }

    // Retrieve stored labels
    List<MailLabel> storedLabels = AbstractESIAccountSync.retrieveAll(atTime, (long contid, AttributeSelector at) ->
        MailLabel.accessQuery(charSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                              AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testLabelData.length, storedLabels.size());

    // Check stored data
    for (int i = 0; i < testLabelData.length; i++) {
      MailLabel nextEl = storedLabels.get(i);
      Object[] dt = testLabelData[i];
      Assert.assertEquals((int) dt[0], nextEl.getUnreadCount());
      Assert.assertEquals((int) dt[1], nextEl.getLabelID());
      Assert.assertEquals(dt[2], nextEl.getName());
      Assert.assertEquals(String.valueOf(dt[3]), nextEl.getColor());
    }

    // Retrieve stored lists
    List<MailingList> storedLists = AbstractESIAccountSync.retrieveAll(atTime, (long contid, AttributeSelector at) ->
        MailingList.accessQuery(charSyncAccount, contid, 1000, false, at,
                                AbstractESIAccountSync.ANY_SELECTOR,
                                AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testListData.length, storedLists.size());

    // Check stored data
    for (int i = 0; i < testListData.length; i++) {
      MailingList nextEl = storedLists.get(i);
      Object[] dt = testListData[i];
      Assert.assertEquals((int) dt[0], nextEl.getListID());
      Assert.assertEquals(dt[1], nextEl.getDisplayName());
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterMailSync sync = new ESICharacterMailSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, mailTestData, mailLabelsTestData, mailingListsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MAIL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MAIL);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.  This is a copy of existing messages with modified data.
    Object[][] newMailTestData = new Object[mailTestData.length][8];
    for (int i = 0; i < mailTestData.length; i++) {
      newMailTestData[i][0] = mailTestData[i][0];
      newMailTestData[i][1] = mailTestData[i][1] + "1";
      newMailTestData[i][2] = (int) mailTestData[i][2] + 1;
      newMailTestData[i][3] = (long) mailTestData[i][3] + 1L;
      int[] lbl = (int[]) mailTestData[i][4];
      Set<Integer> labelSet = new HashSet<>();
      newMailTestData[i][4] = new int[lbl.length];
      for (int j = 0; j < lbl.length; j++) {
        ((int[]) newMailTestData[i][4])[j] = lbl[j] + 1;
        labelSet.add(lbl[j] + 1);
      }
      Set<MailMessageRecipient> rcpSet = new HashSet<>();
      Object[][] rcp = (Object[][]) mailTestData[i][5];
      Object[][] rcpCopy = new Object[rcp.length][2];
      for (int j = 0; j < rcp.length; j++) {
        rcpCopy[j][0] = rcp[j][0];
        rcpCopy[j][1] = (int) rcp[j][1] + 1;
        rcpSet.add(new MailMessageRecipient(String.valueOf(rcpCopy[j][0]), (int) rcpCopy[j][1]));
      }
      newMailTestData[i][5] = rcpCopy;
      newMailTestData[i][6] = !(boolean) mailTestData[i][6];
      newMailTestData[i][7] = mailTestData[i][7] + "1";

      CharacterMailMessage existing = new CharacterMailMessage((long) newMailTestData[i][0],
                                                               (int) newMailTestData[i][2],
                                                               (long) newMailTestData[i][3],
                                                               (String) newMailTestData[i][1],
                                                               (boolean) newMailTestData[i][6],
                                                               labelSet,
                                                               rcpSet,
                                                               (String) newMailTestData[i][7]);

      existing.setup(charSyncAccount, testTime - 1);
      CachedData.update(existing);
    }

    Object[][] newMailLabelData = new Object[mailLabelsTestData.length][4];
    for (int i = 0; i < mailLabelsTestData.length; i++) {
      newMailLabelData[i][0] = (int) mailLabelsTestData[i][0] + 1;
      newMailLabelData[i][1] = mailLabelsTestData[i][1];
      newMailLabelData[i][2] = mailLabelsTestData[i][2] + "1";
      newMailLabelData[i][3] = mailLabelsTestData[i][3];

      MailLabel existing = new MailLabel((int) newMailLabelData[i][1],
                                         (int) newMailLabelData[i][0],
                                         (String) newMailLabelData[i][2],
                                         String.valueOf(newMailLabelData[i][3]));

      existing.setup(charSyncAccount, testTime - 1);
      CachedData.update(existing);
    }

    Object[][] newMailListData = new Object[mailingListsTestData.length][2];
    for (int i = 0; i < mailingListsTestData.length; i++) {
      newMailListData[i][0] = mailingListsTestData[i][0];
      newMailListData[i][1] = mailingListsTestData[i][1] + "1";

      MailingList existing = new MailingList((String) newMailListData[i][1],
                                             (int) newMailListData[i][0]);

      existing.setup(charSyncAccount, testTime - 1);
      CachedData.update(existing);
    }

    // Perform the sync
    ESICharacterMailSync sync = new ESICharacterMailSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old data updates
    verifyDataUpdate(testTime - 1, newMailTestData, newMailLabelData, newMailListData);

    // Verify new data updates
    verifyDataUpdate(testTime, mailTestData, mailLabelsTestData, mailingListsTestData);


    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_MAIL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_MAIL);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
