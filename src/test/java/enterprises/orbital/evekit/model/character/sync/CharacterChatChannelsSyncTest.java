package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.easymock.EasyMock;
import org.hsqldb.rights.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.ChatChannel;
import enterprises.orbital.evekit.model.character.ChatChannelMember;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IChatChannel;
import enterprises.orbital.evexmlapi.chr.IChatChannelMember;

public class CharacterChatChannelsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  User                   testUser;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  static Object[][]      testData;

  static {
    // Generate test data
    // 0 long channelID
    // 1 long ownerID
    // 2 String ownerName
    // 3 String displayName
    // 4 String comparisonKey
    // 5 boolean hasPassword
    // 6 String motd
    // 7 array of "allowed" members as below:
    // 7.0 long accessorID
    // 7.1 String accessorName
    // 8 array of "blocked" members as below:
    // 8.0 long accessorID
    // 8.1 String accessorName
    // 8.2 long untilWhen
    // 8.3 String reason
    // 9 array of "muted" members as below:
    // 9.0 long accessorID
    // 9.1 String accessorName
    // 9.2 long untilWhen
    // 9.3 String reason
    // 10 array of "operators" members as below:
    // 10.0 long accessorID
    // 10.1 String accessorName
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][11];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomText(50);
      testData[i][3] = TestBase.getRandomText(50);
      testData[i][4] = TestBase.getRandomText(50);
      testData[i][5] = TestBase.getRandomBoolean();
      testData[i][6] = TestBase.getRandomText(50);
      int numAllowed = 5 + TestBase.getRandomInt(10);
      int numBlocked = 5 + TestBase.getRandomInt(10);
      int numMuted = 5 + TestBase.getRandomInt(10);
      int numOperators = 5 + TestBase.getRandomInt(10);
      Object[][] allowedData = new Object[numAllowed][2];
      testData[i][7] = allowedData;
      for (int j = 0; j < numAllowed; j++) {
        allowedData[j][0] = TestBase.getUniqueRandomLong();
        allowedData[j][1] = TestBase.getRandomText(50);
      }
      Object[][] blockedData = new Object[numBlocked][4];
      testData[i][8] = blockedData;
      for (int j = 0; j < numBlocked; j++) {
        blockedData[j][0] = TestBase.getUniqueRandomLong();
        blockedData[j][1] = TestBase.getRandomText(50);
        blockedData[j][2] = TestBase.getRandomLong();
        blockedData[j][3] = TestBase.getRandomText(50);
      }
      Object[][] mutedData = new Object[numMuted][4];
      testData[i][9] = mutedData;
      for (int j = 0; j < numMuted; j++) {
        mutedData[j][0] = TestBase.getUniqueRandomLong();
        mutedData[j][1] = TestBase.getRandomText(50);
        mutedData[j][2] = TestBase.getRandomLong();
        mutedData[j][3] = TestBase.getRandomText(50);
      }
      Object[][] operatorsData = new Object[numOperators][2];
      testData[i][10] = operatorsData;
      for (int j = 0; j < numOperators; j++) {
        operatorsData[j][0] = TestBase.getUniqueRandomLong();
        operatorsData[j][1] = TestBase.getRandomText(50);
      }
    }
  }

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IChatChannelMember makeIChatChannelMember(
                                                   final Object[] data) {
    return new IChatChannelMember() {

      @Override
      public long getAccessorID() {
        return (Long) data[0];
      }

      @Override
      public String getAccessorName() {
        return (String) data[1];
      }

      @Override
      public Date getUntilWhen() {
        if (data.length == 4) {
          return new Date((Long) data[2]);
        } else {
          return new Date(0L);
        }
      }

      @Override
      public String getReason() {
        if (data.length == 4) {
          return (String) data[3];
        } else {
          return null;
        }
      }

    };
  }

  public IChatChannel makeIChatChannel(
                                       final Object[] data) {
    return new IChatChannel() {

      @Override
      public long getChannelID() {
        return (Long) data[0];
      }

      @Override
      public long getOwnerID() {
        return (Long) data[1];
      }

      @Override
      public String getOwnerName() {
        return (String) data[2];
      }

      @Override
      public String getDisplayName() {
        return (String) data[3];
      }

      @Override
      public String getComparisonKey() {
        return (String) data[4];
      }

      @Override
      public boolean hasPassword() {
        return (Boolean) data[5];
      }

      @Override
      public String getMOTD() {
        return (String) data[6];
      }

      @Override
      public Collection<IChatChannelMember> getAllowed() {
        List<IChatChannelMember> members = new ArrayList<IChatChannelMember>();
        Object[][] memberData = (Object[][]) data[7];
        for (int i = 0; i < memberData.length; i++) {
          members.add(makeIChatChannelMember(memberData[i]));
        }
        return members;
      }

      @Override
      public Collection<IChatChannelMember> getBlocked() {
        List<IChatChannelMember> members = new ArrayList<IChatChannelMember>();
        Object[][] memberData = (Object[][]) data[8];
        for (int i = 0; i < memberData.length; i++) {
          members.add(makeIChatChannelMember(memberData[i]));
        }
        return members;
      }

      @Override
      public Collection<IChatChannelMember> getMuted() {
        List<IChatChannelMember> members = new ArrayList<IChatChannelMember>();
        Object[][] memberData = (Object[][]) data[9];
        for (int i = 0; i < memberData.length; i++) {
          members.add(makeIChatChannelMember(memberData[i]));
        }
        return members;
      }

      @Override
      public Collection<IChatChannelMember> getOperators() {
        List<IChatChannelMember> members = new ArrayList<IChatChannelMember>();
        Object[][] memberData = (Object[][]) data[10];
        for (int i = 0; i < memberData.length; i++) {
          members.add(makeIChatChannelMember(memberData[i]));
        }
        return members;
      }

    };

  }

  public List<IChatChannel> collectIChatChannel(
                                                Object[][] sourceData) {
    List<IChatChannel> result = new ArrayList<IChatChannel>();
    for (int i = 0; i < sourceData.length; i++) {
      result.add(makeIChatChannel(sourceData[i]));
    }
    return result;
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    List<IChatChannel> result = collectIChatChannel(testData);
    EasyMock.expect(mockServer.requestChatChannels()).andReturn(result);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new channels
  @Test
  public void testChannelSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterChatChannelsSync.syncChatChannels(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Check channels
    for (int i = 0; i < testData.length; i++) {
      Object[][] allowedData = (Object[][]) testData[i][7];
      Object[][] blockedData = (Object[][]) testData[i][8];
      Object[][] mutedData = (Object[][]) testData[i][9];
      Object[][] operatorsData = (Object[][]) testData[i][10];
      long channelID = (Long) testData[i][0];
      ChatChannel nextChannel = ChatChannel.get(syncAccount, testTime, channelID);
      Assert.assertEquals((long) ((Long) testData[i][0]), nextChannel.getChannelID());
      Assert.assertEquals((long) ((Long) testData[i][1]), nextChannel.getOwnerID());
      Assert.assertEquals(testData[i][2], nextChannel.getOwnerName());
      Assert.assertEquals(testData[i][3], nextChannel.getDisplayName());
      Assert.assertEquals(testData[i][4], nextChannel.getComparisonKey());
      Assert.assertEquals((boolean) ((Boolean) testData[i][5]), nextChannel.isHasPassword());
      Assert.assertEquals(testData[i][6], nextChannel.getMotd());
      for (int j = 0; j < allowedData.length; j++) {
        long accessorID = (Long) allowedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "allowed", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("allowed", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) allowedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(allowedData[j][1], nextMember.getAccessorName());
      }
      for (int j = 0; j < blockedData.length; j++) {
        long accessorID = (Long) blockedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "blocked", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("blocked", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) blockedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(blockedData[j][1], nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) blockedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(blockedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < mutedData.length; j++) {
        long accessorID = (Long) mutedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "muted", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("muted", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) mutedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(mutedData[j][1], nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) mutedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(mutedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < operatorsData.length; j++) {
        long accessorID = (Long) operatorsData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "operators", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("operators", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) operatorsData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(operatorsData[j][1], nextMember.getAccessorName());
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getChatChannelsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsDetail());
  }

  // Test update with channels already populated
  @Test
  public void testChannelSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing channels
    for (int i = 0; i < testData.length; i++) {
      Object[][] allowedData = (Object[][]) testData[i][7];
      Object[][] blockedData = (Object[][]) testData[i][8];
      Object[][] mutedData = (Object[][]) testData[i][9];
      Object[][] operatorsData = (Object[][]) testData[i][10];
      ChatChannel nextChannel = new ChatChannel(
          (Long) testData[i][0], (Long) testData[i][1], (String) testData[i][2], (String) testData[i][3], (String) testData[i][4], (Boolean) testData[i][5],
          (String) testData[i][6]);
      nextChannel.setup(syncAccount, testTime);
      nextChannel = CachedData.update(nextChannel);
      for (int j = 0; j < allowedData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember((Long) testData[i][0], "allowed", (Long) allowedData[j][0], (String) allowedData[j][1], 0, null);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
      for (int j = 0; j < blockedData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember(
            (Long) testData[i][0], "blocked", (Long) blockedData[j][0], (String) blockedData[j][1], (Long) blockedData[j][2], (String) blockedData[j][3]);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
      for (int j = 0; j < mutedData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember(
            (Long) testData[i][0], "muted", (Long) mutedData[j][0], (String) mutedData[j][1], (Long) mutedData[j][2], (String) mutedData[j][3]);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
      for (int j = 0; j < operatorsData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember(
            (Long) testData[i][0], "operators", (Long) operatorsData[j][0], (String) operatorsData[j][1], 0, null);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterChatChannelsSync.syncChatChannels(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify channels are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      Object[][] allowedData = (Object[][]) testData[i][7];
      Object[][] blockedData = (Object[][]) testData[i][8];
      Object[][] mutedData = (Object[][]) testData[i][9];
      Object[][] operatorsData = (Object[][]) testData[i][10];
      long channelID = (Long) testData[i][0];
      ChatChannel nextChannel = ChatChannel.get(syncAccount, testTime, channelID);
      Assert.assertEquals((long) ((Long) testData[i][0]), nextChannel.getChannelID());
      Assert.assertEquals((long) ((Long) testData[i][1]), nextChannel.getOwnerID());
      Assert.assertEquals(testData[i][2], nextChannel.getOwnerName());
      Assert.assertEquals(testData[i][3], nextChannel.getDisplayName());
      Assert.assertEquals(testData[i][4], nextChannel.getComparisonKey());
      Assert.assertEquals((boolean) ((Boolean) testData[i][5]), nextChannel.isHasPassword());
      Assert.assertEquals(testData[i][6], nextChannel.getMotd());
      for (int j = 0; j < allowedData.length; j++) {
        long accessorID = (Long) allowedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "allowed", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("allowed", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) allowedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(allowedData[j][1], nextMember.getAccessorName());
      }
      for (int j = 0; j < blockedData.length; j++) {
        long accessorID = (Long) blockedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "blocked", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("blocked", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) blockedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(blockedData[j][1], nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) blockedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(blockedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < mutedData.length; j++) {
        long accessorID = (Long) mutedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "muted", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("muted", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) mutedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(mutedData[j][1], nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) mutedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(mutedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < operatorsData.length; j++) {
        long accessorID = (Long) operatorsData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "operators", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("operators", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) operatorsData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(operatorsData[j][1], nextMember.getAccessorName());
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getChatChannelsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testChannelsSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing channels
    for (int i = 0; i < testData.length; i++) {
      Object[][] allowedData = (Object[][]) testData[i][7];
      Object[][] blockedData = (Object[][]) testData[i][8];
      Object[][] mutedData = (Object[][]) testData[i][9];
      Object[][] operatorsData = (Object[][]) testData[i][10];
      ChatChannel nextChannel = new ChatChannel(
          (Long) testData[i][0], (Long) testData[i][1], ((String) testData[i][2]) + "foo", (String) testData[i][3], (String) testData[i][4],
          (Boolean) testData[i][5], (String) testData[i][6]);
      nextChannel.setup(syncAccount, testTime);
      nextChannel = CachedData.update(nextChannel);
      for (int j = 0; j < allowedData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember(
            (Long) testData[i][0], "allowed", (Long) allowedData[j][0], ((String) allowedData[j][1]) + "foo", 0, null);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
      for (int j = 0; j < blockedData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember(
            (Long) testData[i][0], "blocked", (Long) blockedData[j][0], ((String) blockedData[j][1]) + "foo", (Long) blockedData[j][2],
            (String) blockedData[j][3]);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
      for (int j = 0; j < mutedData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember(
            (Long) testData[i][0], "muted", (Long) mutedData[j][0], ((String) mutedData[j][1]) + "foo", (Long) mutedData[j][2], (String) mutedData[j][3]);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
      for (int j = 0; j < operatorsData.length; j++) {
        ChatChannelMember nextMember = new ChatChannelMember(
            (Long) testData[i][0], "operators", (Long) operatorsData[j][0], ((String) operatorsData[j][1]) + "foo", 0, null);
        nextMember.setup(syncAccount, testTime);
        nextMember = CachedData.update(nextMember);
      }
    }

    // Set the tracker as already updated and populate the container
    tracker.setChatChannelsStatus(SyncState.UPDATED);
    tracker.setChatChannelsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setChatChannelsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterChatChannelsSync.syncChatChannels(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify channels unchanged
    for (int i = 0; i < testData.length; i++) {
      Object[][] allowedData = (Object[][]) testData[i][7];
      Object[][] blockedData = (Object[][]) testData[i][8];
      Object[][] mutedData = (Object[][]) testData[i][9];
      Object[][] operatorsData = (Object[][]) testData[i][10];
      long channelID = (Long) testData[i][0];
      ChatChannel nextChannel = ChatChannel.get(syncAccount, testTime, channelID);
      Assert.assertEquals((long) ((Long) testData[i][0]), nextChannel.getChannelID());
      Assert.assertEquals((long) ((Long) testData[i][1]), nextChannel.getOwnerID());
      Assert.assertEquals(((String) testData[i][2]) + "foo", nextChannel.getOwnerName());
      Assert.assertEquals(testData[i][3], nextChannel.getDisplayName());
      Assert.assertEquals(testData[i][4], nextChannel.getComparisonKey());
      Assert.assertEquals((boolean) ((Boolean) testData[i][5]), nextChannel.isHasPassword());
      Assert.assertEquals(testData[i][6], nextChannel.getMotd());
      for (int j = 0; j < allowedData.length; j++) {
        long accessorID = (Long) allowedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "allowed", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("allowed", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) allowedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(((String) allowedData[j][1]) + "foo", nextMember.getAccessorName());
      }
      for (int j = 0; j < blockedData.length; j++) {
        long accessorID = (Long) blockedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "blocked", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("blocked", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) blockedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(((String) blockedData[j][1]) + "foo", nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) blockedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(blockedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < mutedData.length; j++) {
        long accessorID = (Long) mutedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "muted", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("muted", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) mutedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(((String) mutedData[j][1]) + "foo", nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) mutedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(mutedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < operatorsData.length; j++) {
        long accessorID = (Long) operatorsData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "operators", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("operators", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) operatorsData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(((String) operatorsData[j][1]) + "foo", nextMember.getAccessorName());
      }
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getChatChannelsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsDetail());
  }

  // Test update with channels and members which should be deleted
  @Test
  public void testBookmarkSyncUpdateDelete() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing channels
    List<ChatChannel> toDeleteChannels = new ArrayList<ChatChannel>();
    for (int i = 0; i < 5; i++) {
      ChatChannel nextChannel = new ChatChannel(
          TestBase.getUniqueRandomLong(), TestBase.getRandomLong(), TestBase.getRandomText(50), TestBase.getRandomText(50), TestBase.getRandomText(50),
          TestBase.getRandomBoolean(), TestBase.getRandomText(50));
      nextChannel.setup(syncAccount, testTime);
      nextChannel = CachedData.update(nextChannel);
      toDeleteChannels.add(nextChannel);
    }
    String[] cats = new String[] {
        "allowed", "blocked", "muted", "operators"
    };
    List<ChatChannelMember> toDeleteMembers = new ArrayList<ChatChannelMember>();
    for (int i = 0; i < 5; i++) {
      int category = TestBase.getRandomInt(4);
      ChatChannelMember nextMember = new ChatChannelMember(
          TestBase.getUniqueRandomLong(), cats[category], TestBase.getUniqueRandomLong(), TestBase.getRandomText(50), TestBase.getRandomLong(),
          TestBase.getRandomText(50));
      nextMember.setup(syncAccount, testTime);
      nextMember = CachedData.update(nextMember);
      toDeleteMembers.add(nextMember);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterChatChannelsSync.syncChatChannels(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify deleted channels and members no longer exist
    int len = 0;
    for (int i = 0; i < testData.length; i++) {
      len += ((Object[][]) testData[i][7]).length;
      len += ((Object[][]) testData[i][8]).length;
      len += ((Object[][]) testData[i][9]).length;
      len += ((Object[][]) testData[i][10]).length;
    }
    Assert.assertEquals(testData.length, ChatChannel.getAllChatChannels(syncAccount, testTime).size());
    Assert.assertEquals(len, ChatChannelMember.getAllChatChannelMembers(syncAccount, testTime).size());
    for (int i = 0; i < testData.length; i++) {
      Object[][] allowedData = (Object[][]) testData[i][7];
      Object[][] blockedData = (Object[][]) testData[i][8];
      Object[][] mutedData = (Object[][]) testData[i][9];
      Object[][] operatorsData = (Object[][]) testData[i][10];
      long channelID = (Long) testData[i][0];
      ChatChannel nextChannel = ChatChannel.get(syncAccount, testTime, channelID);
      Assert.assertEquals((long) ((Long) testData[i][0]), nextChannel.getChannelID());
      Assert.assertEquals((long) ((Long) testData[i][1]), nextChannel.getOwnerID());
      Assert.assertEquals(testData[i][2], nextChannel.getOwnerName());
      Assert.assertEquals(testData[i][3], nextChannel.getDisplayName());
      Assert.assertEquals(testData[i][4], nextChannel.getComparisonKey());
      Assert.assertEquals((boolean) ((Boolean) testData[i][5]), nextChannel.isHasPassword());
      Assert.assertEquals(testData[i][6], nextChannel.getMotd());
      for (int j = 0; j < allowedData.length; j++) {
        long accessorID = (Long) allowedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "allowed", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("allowed", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) allowedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(allowedData[j][1], nextMember.getAccessorName());
      }
      for (int j = 0; j < blockedData.length; j++) {
        long accessorID = (Long) blockedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "blocked", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("blocked", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) blockedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(blockedData[j][1], nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) blockedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(blockedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < mutedData.length; j++) {
        long accessorID = (Long) mutedData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "muted", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("muted", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) mutedData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(mutedData[j][1], nextMember.getAccessorName());
        Assert.assertEquals((long) ((Long) mutedData[j][2]), nextMember.getUntilWhen());
        Assert.assertEquals(mutedData[j][3], nextMember.getReason());
      }
      for (int j = 0; j < operatorsData.length; j++) {
        long accessorID = (Long) operatorsData[j][0];
        ChatChannelMember nextMember = ChatChannelMember.get(syncAccount, testTime, channelID, "operators", accessorID);
        Assert.assertEquals(channelID, nextMember.getChannelID());
        Assert.assertEquals("operators", nextMember.getCategory());
        Assert.assertEquals((long) ((Long) operatorsData[j][0]), nextMember.getAccessorID());
        Assert.assertEquals(operatorsData[j][1], nextMember.getAccessorName());
      }
    }
    for (ChatChannel i : toDeleteChannels) {
      Assert.assertNull(ChatChannel.get(syncAccount, testTime, i.getChannelID()));
    }
    for (ChatChannelMember i : toDeleteMembers) {
      Assert.assertNull(ChatChannelMember.get(syncAccount, testTime, i.getChannelID(), i.getCategory(), i.getAccessorID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getChatChannelsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getChatChannelsDetail());
  }
}
