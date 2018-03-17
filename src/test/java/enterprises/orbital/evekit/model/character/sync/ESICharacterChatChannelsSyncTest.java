package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.ChatChannel;
import enterprises.orbital.evekit.model.character.ChatChannelMember;
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
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterChatChannelsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private CharacterApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] channelsTestData;

  static {
    // Channel test data
    // 0 int channelID
    // 1 int ownerID
    // 2 String displayName
    // 3 String comparisonKey
    // 4 boolean hasPassword
    // 5 String motd
    // 6 Object[][] allowed
    // 7 Object[][] operator
    // 8 Object[][] blocked
    // 9 Object[][] muted
    int size = 50 + TestBase.getRandomInt(50);
    channelsTestData = new Object[size][10];
    for (int i = 0; i < size; i++) {
      channelsTestData[i][0] = TestBase.getUniqueRandomInteger();
      channelsTestData[i][1] = TestBase.getRandomInt();
      channelsTestData[i][2] = TestBase.getRandomText(50);
      channelsTestData[i][3] = TestBase.getRandomText(50);
      channelsTestData[i][4] = TestBase.getRandomBoolean();
      channelsTestData[i][5] = TestBase.getRandomText(1000);

      {
        // Allowed
        int memCount = 10 + TestBase.getRandomInt(10);
        int typeLen = GetCharactersCharacterIdChatChannelsAllowed.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][2];
        channelsTestData[i][6] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = TestBase.getUniqueRandomInteger();
          memData[j][1] = GetCharactersCharacterIdChatChannelsAllowed.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
        }
      }

      {
        // Operator
        int memCount = 10 + TestBase.getRandomInt(10);
        int typeLen = GetCharactersCharacterIdChatChannelsOperator.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][2];
        channelsTestData[i][7] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = TestBase.getUniqueRandomInteger();
          memData[j][1] = GetCharactersCharacterIdChatChannelsOperator.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
        }
      }

      {
        // Blocked
        int memCount = 10 + TestBase.getRandomInt(10);
        int typeLen = GetCharactersCharacterIdChatChannelsBlocked.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][4];
        channelsTestData[i][8] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = TestBase.getUniqueRandomInteger();
          memData[j][1] = GetCharactersCharacterIdChatChannelsBlocked.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
          memData[j][2] = TestBase.getRandomText(50);
          memData[j][3] = TestBase.getRandomLong();
        }
      }

      {
        // Muted
        int memCount = 10 + TestBase.getRandomInt(10);
        int typeLen = GetCharactersCharacterIdChatChannelsMuted.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][4];
        channelsTestData[i][9] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = TestBase.getUniqueRandomInteger();
          memData[j][1] = GetCharactersCharacterIdChatChannelsMuted.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
          memData[j][2] = TestBase.getRandomText(50);
          memData[j][3] = TestBase.getRandomLong();
        }
      }

    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CHANNELS, 1234L);

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
                                                        .createQuery("DELETE FROM ChatChannel ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM ChatChannelMember ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(CharacterApi.class);

    // Set up channels
    List<GetCharactersCharacterIdChatChannels200Ok> titles = Arrays.stream(channelsTestData)
                                                                   .map(x -> {
                                                                     GetCharactersCharacterIdChatChannels200Ok newChannel = new GetCharactersCharacterIdChatChannels200Ok();
                                                                     newChannel.setChannelId((int) x[0]);
                                                                     newChannel.setOwnerId((int) x[1]);
                                                                     newChannel.setName((String) x[2]);
                                                                     newChannel.setComparisonKey((String) x[3]);
                                                                     newChannel.setHasPassword((boolean) x[4]);
                                                                     newChannel.setMotd((String) x[5]);

                                                                     newChannel.getAllowed()
                                                                               .addAll(
                                                                                   Arrays.stream((Object[][]) x[6])
                                                                                         .map(y -> {
                                                                                           GetCharactersCharacterIdChatChannelsAllowed newMem = new GetCharactersCharacterIdChatChannelsAllowed();
                                                                                           newMem.setAccessorId(
                                                                                               (int) y[0]);
                                                                                           newMem.setAccessorType(
                                                                                               (GetCharactersCharacterIdChatChannelsAllowed.AccessorTypeEnum) y[1]);
                                                                                           return newMem;
                                                                                         })
                                                                                         .collect(Collectors.toList()));

                                                                     newChannel.getOperators()
                                                                               .addAll(
                                                                                   Arrays.stream((Object[][]) x[7])
                                                                                         .map(y -> {
                                                                                           GetCharactersCharacterIdChatChannelsOperator newMem = new GetCharactersCharacterIdChatChannelsOperator();
                                                                                           newMem.setAccessorId(
                                                                                               (int) y[0]);
                                                                                           newMem.setAccessorType(
                                                                                               (GetCharactersCharacterIdChatChannelsOperator.AccessorTypeEnum) y[1]);
                                                                                           return newMem;
                                                                                         })
                                                                                         .collect(Collectors.toList()));

                                                                     newChannel.getBlocked()
                                                                               .addAll(
                                                                                   Arrays.stream((Object[][]) x[8])
                                                                                         .map(y -> {
                                                                                           GetCharactersCharacterIdChatChannelsBlocked newMem = new GetCharactersCharacterIdChatChannelsBlocked();
                                                                                           newMem.setAccessorId(
                                                                                               (int) y[0]);
                                                                                           newMem.setAccessorType(
                                                                                               (GetCharactersCharacterIdChatChannelsBlocked.AccessorTypeEnum) y[1]);
                                                                                           newMem.setReason(
                                                                                               (String) y[2]);
                                                                                           newMem.setEndAt(new DateTime(
                                                                                               new Date((long) y[3])));
                                                                                           return newMem;
                                                                                         })
                                                                                         .collect(Collectors.toList()));

                                                                     newChannel.getMuted()
                                                                               .addAll(
                                                                                   Arrays.stream((Object[][]) x[9])
                                                                                         .map(y -> {
                                                                                           GetCharactersCharacterIdChatChannelsMuted newMem = new GetCharactersCharacterIdChatChannelsMuted();
                                                                                           newMem.setAccessorId(
                                                                                               (int) y[0]);
                                                                                           newMem.setAccessorType(
                                                                                               (GetCharactersCharacterIdChatChannelsMuted.AccessorTypeEnum) y[1]);
                                                                                           newMem.setReason(
                                                                                               (String) y[2]);
                                                                                           newMem.setEndAt(new DateTime(
                                                                                               new Date((long) y[3])));
                                                                                           return newMem;
                                                                                         })
                                                                                         .collect(Collectors.toList()));

                                                                     return newChannel;
                                                                   })
                                                                   .collect(Collectors.toList());

    // Setup retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCharactersCharacterIdChatChannels200Ok>> apir = new ApiResponse<>(200, headers, titles);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdChatChannelsWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getCharacterApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate(long time, Object[][] testData) throws Exception {

    // Retrieve stored channels
    List<ChatChannel> storedChannels = CachedData.retrieveAll(time,
                                                              (contid, at) -> ChatChannel.accessQuery(
                                                                  charSyncAccount, contid, 1000, false, at,
                                                                  AttributeSelector.any(), AttributeSelector.any(),
                                                                  AttributeSelector.any(), AttributeSelector.any(),
                                                                  AttributeSelector.any(), AttributeSelector.any()));

    // Compare against test data
    Assert.assertEquals(testData.length, storedChannels.size());
    for (int i = 0; i < testData.length; i++) {
      ChatChannel nextChannel = storedChannels.get(i);
      Assert.assertEquals((int) testData[i][0], nextChannel.getChannelID());
      Assert.assertEquals((int) testData[i][1], nextChannel.getOwnerID());
      Assert.assertEquals(testData[i][2], nextChannel.getDisplayName());
      Assert.assertEquals(testData[i][3], nextChannel.getComparisonKey());
      Assert.assertEquals(testData[i][4], nextChannel.isHasPassword());
      Assert.assertEquals(testData[i][5], nextChannel.getMotd());
    }

    // Retrieved stored members
    List<ChatChannelMember> storedMembers = CachedData.retrieveAll(time,
                                                                   (contid, at) -> ChatChannelMember.accessQuery(
                                                                       charSyncAccount, contid, 1000, false, at,
                                                                       AttributeSelector.any(), AttributeSelector.any(),
                                                                       AttributeSelector.any(), AttributeSelector.any(),
                                                                       AttributeSelector.any(),
                                                                       AttributeSelector.any()));

    // Compare against test data
    int memberCount = Arrays.stream(testData)
                            .map(x -> ((Object[][]) x[6]).length +
                                ((Object[][]) x[7]).length +
                                ((Object[][]) x[8]).length +
                                ((Object[][]) x[9]).length)
                            .reduce(0, Integer::sum);
    Assert.assertEquals(memberCount, storedMembers.size());
    for (int i = 0, j = 0; i < testData.length; i++) {
      for (Object[] next : (Object[][]) testData[i][6]) {
        ChatChannelMember nextMem = storedMembers.get(j++);
        Assert.assertEquals((int) testData[i][0], nextMem.getChannelID());
        Assert.assertEquals(ChatChannelMember.CAT_ALLOWED, nextMem.getCategory());
        Assert.assertEquals((int) next[0], nextMem.getAccessorID());
        Assert.assertEquals(String.valueOf(next[1]), nextMem.getAccessorType());
        Assert.assertNull(nextMem.getReason());
        Assert.assertEquals(0L, nextMem.getUntilWhen());
      }
      for (Object[] next : (Object[][]) testData[i][7]) {
        ChatChannelMember nextMem = storedMembers.get(j++);
        Assert.assertEquals((int) testData[i][0], nextMem.getChannelID());
        Assert.assertEquals(ChatChannelMember.CAT_OPERATOR, nextMem.getCategory());
        Assert.assertEquals((int) next[0], nextMem.getAccessorID());
        Assert.assertEquals(String.valueOf(next[1]), nextMem.getAccessorType());
        Assert.assertNull(nextMem.getReason());
        Assert.assertEquals(0L, nextMem.getUntilWhen());
      }
      for (Object[] next : (Object[][]) testData[i][8]) {
        ChatChannelMember nextMem = storedMembers.get(j++);
        Assert.assertEquals((int) testData[i][0], nextMem.getChannelID());
        Assert.assertEquals(ChatChannelMember.CAT_BLOCKED, nextMem.getCategory());
        Assert.assertEquals((int) next[0], nextMem.getAccessorID());
        Assert.assertEquals(String.valueOf(next[1]), nextMem.getAccessorType());
        Assert.assertEquals(next[2], nextMem.getReason());
        Assert.assertEquals((long) next[3], nextMem.getUntilWhen());
      }
      for (Object[] next : (Object[][]) testData[i][9]) {
        ChatChannelMember nextMem = storedMembers.get(j++);
        Assert.assertEquals((int) testData[i][0], nextMem.getChannelID());
        Assert.assertEquals(ChatChannelMember.CAT_MUTED, nextMem.getCategory());
        Assert.assertEquals((int) next[0], nextMem.getAccessorID());
        Assert.assertEquals(String.valueOf(next[1]), nextMem.getAccessorType());
        Assert.assertEquals(next[2], nextMem.getReason());
        Assert.assertEquals((long) next[3], nextMem.getUntilWhen());
      }
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterChatChannelsSync sync = new ESICharacterChatChannelsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, channelsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CHANNELS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CHANNELS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Create channels which will be modified by update.
    Object[][] newTestData = new Object[channelsTestData.length][10];
    for (int i = 0; i < newTestData.length; i++) {
      newTestData[i][0] = channelsTestData[i][0];
      newTestData[i][1] = (int) channelsTestData[i][1] + 1;
      newTestData[i][2] = channelsTestData[i][2] + "1";
      newTestData[i][3] = channelsTestData[i][3] + "1";
      newTestData[i][4] = TestBase.getRandomBoolean();
      newTestData[i][5] = TestBase.getRandomText(1000);

      ChatChannel oldChannel = new ChatChannel((int) newTestData[i][0],
                                               (int) newTestData[i][1],
                                               (String) newTestData[i][2],
                                               (String) newTestData[i][3],
                                               (boolean) newTestData[i][4],
                                               (String) newTestData[i][5]);
      oldChannel.setup(charSyncAccount, testTime - 1);
      CachedData.update(oldChannel);

      {
        // Allowed
        Object[][] oldData = (Object[][]) channelsTestData[i][6];
        int memCount = oldData.length;
        int typeLen = GetCharactersCharacterIdChatChannelsAllowed.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][2];
        newTestData[i][6] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = oldData[j][0];
          memData[j][1] = GetCharactersCharacterIdChatChannelsAllowed.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
          while (memData[j][1].equals(oldData[j][1])) {
            memData[j][1] = GetCharactersCharacterIdChatChannelsAllowed.AccessorTypeEnum.values()[TestBase.getRandomInt(
                typeLen)];
          }

          ChatChannelMember oldMember = new ChatChannelMember((int) newTestData[i][0],
                                                              ChatChannelMember.CAT_ALLOWED,
                                                              (int) memData[j][0],
                                                              String.valueOf(memData[j][1]),
                                                              0,
                                                              null);
          oldMember.setup(charSyncAccount, testTime - 1);
          CachedData.update(oldMember);
        }
      }

      {
        // Operator
        Object[][] oldData = (Object[][]) channelsTestData[i][7];
        int memCount = oldData.length;
        int typeLen = GetCharactersCharacterIdChatChannelsOperator.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][2];
        newTestData[i][7] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = oldData[j][0];
          memData[j][1] = GetCharactersCharacterIdChatChannelsOperator.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
          while (memData[j][1].equals(oldData[j][1])) {
            memData[j][1] = GetCharactersCharacterIdChatChannelsOperator.AccessorTypeEnum.values()[TestBase.getRandomInt(
                typeLen)];
          }

          ChatChannelMember oldMember = new ChatChannelMember((int) newTestData[i][0],
                                                              ChatChannelMember.CAT_OPERATOR,
                                                              (int) memData[j][0],
                                                              String.valueOf(memData[j][1]),
                                                              0,
                                                              null);
          oldMember.setup(charSyncAccount, testTime - 1);
          CachedData.update(oldMember);
        }
      }

      {
        // Blocked
        Object[][] oldData = (Object[][]) channelsTestData[i][8];
        int memCount = oldData.length;
        int typeLen = GetCharactersCharacterIdChatChannelsBlocked.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][4];
        newTestData[i][8] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = oldData[j][0];
          memData[j][1] = GetCharactersCharacterIdChatChannelsBlocked.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
          while (memData[j][1].equals(oldData[j][1])) {
            memData[j][1] = GetCharactersCharacterIdChatChannelsBlocked.AccessorTypeEnum.values()[TestBase.getRandomInt(
                typeLen)];
          }
          memData[j][2] = oldData[j][2] + "1";
          memData[j][3] = (long) oldData[j][3] + 1;

          ChatChannelMember oldMember = new ChatChannelMember((int) newTestData[i][0],
                                                              ChatChannelMember.CAT_BLOCKED,
                                                              (int) memData[j][0],
                                                              String.valueOf(memData[j][1]),
                                                              (long) memData[j][3],
                                                              (String) memData[j][2]);
          oldMember.setup(charSyncAccount, testTime - 1);
          CachedData.update(oldMember);
        }
      }

      {
        // Muted
        Object[][] oldData = (Object[][]) channelsTestData[i][9];
        int memCount = oldData.length;
        int typeLen = GetCharactersCharacterIdChatChannelsMuted.AccessorTypeEnum.values().length;
        Object[][] memData = new Object[memCount][4];
        newTestData[i][9] = memData;
        for (int j = 0; j < memCount; j++) {
          memData[j][0] = oldData[j][0];
          memData[j][1] = GetCharactersCharacterIdChatChannelsMuted.AccessorTypeEnum.values()[TestBase.getRandomInt(
              typeLen)];
          while (memData[j][1].equals(oldData[j][1])) {
            memData[j][1] = GetCharactersCharacterIdChatChannelsMuted.AccessorTypeEnum.values()[TestBase.getRandomInt(
                typeLen)];
          }
          memData[j][2] = oldData[j][2] + "1";
          memData[j][3] = (long) oldData[j][3] + 1;

          ChatChannelMember oldMember = new ChatChannelMember((int) newTestData[i][0],
                                                              ChatChannelMember.CAT_MUTED,
                                                              (int) memData[j][0],
                                                              String.valueOf(memData[j][1]),
                                                              (long) memData[j][3],
                                                              (String) memData[j][2]);
          oldMember.setup(charSyncAccount, testTime - 1);
          CachedData.update(oldMember);
        }
      }

    }

    // Perform the sync
    ESICharacterChatChannelsSync sync = new ESICharacterChatChannelsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates which will also verify that all old data were properly end of life
    verifyDataUpdate(testTime - 1, newTestData);
    verifyDataUpdate(testTime, channelsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_CHANNELS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_CHANNELS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
