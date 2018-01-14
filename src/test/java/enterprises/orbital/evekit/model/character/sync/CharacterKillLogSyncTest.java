package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.easymock.EasyMock;
import org.easymock.IAnswer;
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
import enterprises.orbital.evekit.model.common.Kill;
import enterprises.orbital.evekit.model.common.KillAttacker;
import enterprises.orbital.evekit.model.common.KillItem;
import enterprises.orbital.evekit.model.common.KillVictim;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IKill;
import enterprises.orbital.evexmlapi.shared.IKillAttacker;
import enterprises.orbital.evexmlapi.shared.IKillItem;
import enterprises.orbital.evexmlapi.shared.IKillVictim;

public class CharacterKillLogSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  static Object[][]      testData;

  // Generate random test data
  static {
    int size = 10 + TestBase.getRandomInt(10);
    testData = new Object[size][7];
    for (int i = 0; i < size; i++) {
      int numAttackers = 3 + TestBase.getRandomInt(5);
      Object[][] attackers = new Object[numAttackers][13];
      testData[i][0] = attackers;
      for (int j = 0; j < numAttackers; j++) {
        attackers[j][0] = TestBase.getRandomLong();
        attackers[j][1] = TestBase.getRandomText(50);
        attackers[j][2] = TestBase.getUniqueRandomLong();
        attackers[j][3] = TestBase.getRandomText(50);
        attackers[j][4] = TestBase.getRandomLong();
        attackers[j][5] = TestBase.getRandomText(50);
        attackers[j][6] = TestBase.getRandomInt();
        attackers[j][7] = TestBase.getRandomInt();
        attackers[j][8] = TestBase.getRandomText(50);
        attackers[j][9] = TestBase.getRandomDouble(10.0);
        attackers[j][10] = TestBase.getRandomInt();
        attackers[j][11] = TestBase.getRandomInt();
        attackers[j][12] = TestBase.getRandomBoolean();
      }

      int numItems = 5 + TestBase.getRandomInt(5);
      Object[][] items = new Object[numItems][6];
      testData[i][1] = items;
      int rotator = 0;
      for (int j = 0; j < numItems; j++) {
        items[j][0] = TestBase.getRandomInt();
        items[j][1] = TestBase.getRandomInt();
        items[j][2] = TestBase.getRandomInt();
        items[j][3] = TestBase.getUniqueRandomInteger();
        items[j][4] = TestBase.getRandomBoolean();

        if (rotator == 0) {
          items[j][5] = new Object[0][6];
        }

        // Every 1st element is one level deep
        if (rotator == 1) {
          // Generate random contained items
          int numSubItems = 1 + TestBase.getRandomInt(5);
          Object[][] subItems = new Object[numSubItems][6];
          items[j][5] = subItems;
          for (int k = 0; k < numSubItems; k++) {
            subItems[k][0] = TestBase.getRandomInt();
            subItems[k][1] = TestBase.getRandomInt();
            subItems[k][2] = TestBase.getRandomInt();
            subItems[k][3] = TestBase.getUniqueRandomInteger();
            subItems[k][4] = TestBase.getRandomBoolean();
            subItems[k][5] = new Object[0][6];
          }
        }

        // Every 2nd element is two levels deep
        if (rotator == 2) {
          // Generate random contained items, each of which also has contained items
          int numSubItems = 1 + TestBase.getRandomInt(5);
          Object[][] subItems = new Object[numSubItems][6];
          items[j][5] = subItems;
          for (int k = 0; k < numSubItems; k++) {
            subItems[k][0] = TestBase.getRandomInt();
            subItems[k][1] = TestBase.getRandomInt();
            subItems[k][2] = TestBase.getRandomInt();
            subItems[k][3] = TestBase.getUniqueRandomInteger();
            subItems[k][4] = TestBase.getRandomBoolean();

            int numSubSubItems = 1 + TestBase.getRandomInt(5);
            Object[][] subSubItems = new Object[numSubSubItems][6];
            subItems[k][5] = subSubItems;
            for (int l = 0; l < numSubSubItems; l++) {
              subSubItems[l][0] = TestBase.getRandomInt();
              subSubItems[l][1] = TestBase.getRandomInt();
              subSubItems[l][2] = TestBase.getRandomInt();
              subSubItems[l][3] = TestBase.getUniqueRandomInteger();
              subSubItems[l][4] = TestBase.getRandomBoolean();
              subSubItems[l][5] = new Object[0][6];
            }
          }
        }

        rotator = (rotator + 1) % 3;
      }

      Object[] victim = new Object[10];
      testData[i][6] = victim;
      victim[0] = TestBase.getRandomLong();
      victim[1] = TestBase.getRandomText(50);
      victim[2] = TestBase.getRandomLong();
      victim[3] = TestBase.getRandomText(50);
      victim[4] = TestBase.getRandomLong();
      victim[5] = TestBase.getRandomText(50);
      victim[6] = TestBase.getRandomLong();
      victim[7] = TestBase.getRandomLong();
      victim[8] = TestBase.getRandomText(50);
      victim[9] = TestBase.getRandomInt();

      testData[i][2] = TestBase.getUniqueRandomLong();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomInt();
      testData[i][5] = TestBase.getRandomLong();
    }

    // Sort test data in decreasing order by killID (testData[i][2])
    Comparator<Object> testDataCompare = new Comparator<Object>() {

      @Override
      public int compare(Object arg0, Object arg1) {
        long v0 = (Long) (((Object[]) arg0)[2]);
        long v1 = (Long) (((Object[]) arg1)[2]);

        if (v0 > v1) { return -1; }
        if (v0 == v1) { return 0; }
        return 1;
      }
    };
    Arrays.sort(testData, 0, testData.length, testDataCompare);

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
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public Collection<IKill> assembleKills(int count, Long startingID, long killIDAdjustment, String tweak) {
    List<IKill> kills = new ArrayList<IKill>();
    for (int i = 0; i < testData.length && count > 0; i++) {
      if (startingID == null || ((Long) testData[i][2] + killIDAdjustment) < startingID) {
        kills.add(makeKill(testData[i], killIDAdjustment, tweak));
        count--;
      }
    }

    return kills;
  }

  public IKill makeKill(final Object[] instanceData, final long killIDAdjustment, final String tweak) {
    final List<IKillAttacker> attackers = new ArrayList<IKillAttacker>();
    final Object[][] attackerData = (Object[][]) instanceData[0];
    for (int i = 0; i < attackerData.length; i++) {
      final int j = i;
      attackers.add(new IKillAttacker() {

        @Override
        public long getAllianceID() {
          return (Long) attackerData[j][0];
        }

        @Override
        public String getAllianceName() {
          return (String) attackerData[j][1] + tweak;
        }

        @Override
        public long getCharacterID() {
          return (Long) attackerData[j][2];
        }

        @Override
        public String getCharacterName() {
          return (String) attackerData[j][3] + tweak;
        }

        @Override
        public long getCorporationID() {
          return (Long) attackerData[j][4];
        }

        @Override
        public String getCorporationName() {
          return (String) attackerData[j][5] + tweak;
        }

        @Override
        public int getDamageDone() {
          return (Integer) attackerData[j][6];
        }

        @Override
        public int getFactionID() {
          return (Integer) attackerData[j][7];
        }

        @Override
        public String getFactionName() {
          return (String) attackerData[j][8] + tweak;
        }

        @Override
        public double getSecurityStatus() {
          return (Double) attackerData[j][9];
        }

        @Override
        public int getShipTypeID() {
          return (Integer) attackerData[j][10];
        }

        @Override
        public int getWeaponTypeID() {
          return (Integer) attackerData[j][11];
        }

        @Override
        public boolean isFinalBlow() {
          return (Boolean) attackerData[j][12];
        }

      });
    }
    final List<IKillItem> items = new ArrayList<IKillItem>();
    final Object[][] itemData = (Object[][]) instanceData[1];
    Queue<SourceItemPair> nextItemQueue = new LinkedList<SourceItemPair>();
    for (int i = 0; i < itemData.length; i++) {
      nextItemQueue.add(new SourceItemPair(null, itemData[i]));
    }
    while (!nextItemQueue.isEmpty()) {
      SourceItemPair pair = nextItemQueue.remove();
      final Object[] nextData = pair.data;
      IKillItem nextItem = new IKillItem() {
        private final Collection<IKillItem> subItems = new ArrayList<IKillItem>();

        @Override
        public int getFlag() {
          return (Integer) nextData[0];
        }

        @Override
        public int getQtyDestroyed() {
          return (Integer) nextData[1];
        }

        @Override
        public int getQtyDropped() {
          return (Integer) nextData[2];
        }

        @Override
        public int getTypeID() {
          return (Integer) nextData[3];
        }

        @Override
        public boolean isSingleton() {
          return (Boolean) nextData[4];
        }

        @Override
        public Collection<IKillItem> getContainedItems() {
          return subItems;
        }
      };

      if (pair.container != null) {
        ((List<IKillItem>) pair.container.getContainedItems()).add(nextItem);
      }

      Object[][] contained = (Object[][]) nextData[5];
      for (int j = 0; j < contained.length; j++) {
        nextItemQueue.add(new SourceItemPair(nextItem, contained[j]));
      }
    }
    final Object[] killVictimData = (Object[]) instanceData[6];
    final IKillVictim victim = new IKillVictim() {

      @Override
      public long getAllianceID() {
        return (Long) killVictimData[0];
      }

      @Override
      public String getAllianceName() {
        return (String) killVictimData[1] + tweak;
      }

      @Override
      public long getCharacterID() {
        return (Long) killVictimData[2];
      }

      @Override
      public String getCharacterName() {
        return (String) killVictimData[3] + tweak;
      }

      @Override
      public long getCorporationID() {
        return (Long) killVictimData[4];
      }

      @Override
      public String getCorporationName() {
        return (String) killVictimData[5] + tweak;
      }

      @Override
      public long getDamageTaken() {
        return (Long) killVictimData[6];
      }

      @Override
      public long getFactionID() {
        return (Long) killVictimData[7];
      }

      @Override
      public String getFactionName() {
        return (String) killVictimData[8] + tweak;
      }

      @Override
      public int getShipTypeID() {
        return (Integer) killVictimData[9];
      }

    };

    IKill kill = new IKill() {

      @Override
      public Collection<IKillAttacker> getAttackers() {
        return attackers;
      }

      @Override
      public Collection<IKillItem> getItems() {
        return items;
      }

      @Override
      public long getKillID() {
        return (Long) instanceData[2] + killIDAdjustment;
      }

      @Override
      public Date getKillTime() {
        return new Date((Long) instanceData[3]);
      }

      @Override
      public int getMoonID() {
        return (Integer) instanceData[4];
      }

      @Override
      public long getSolarSystemID() {
        return (Long) instanceData[5];
      }

      @Override
      public IKillVictim getVictim() {
        return victim;
      }
    };

    return kill;
  }

  public Kill makeKillObject(
                             final long time,
                             final Object[] instanceData,
                             List<KillAttacker> attackers,
                             List<KillItem> items,
                             List<KillVictim> victims,
                             long killIDAdjustment,
                             String tweak) throws Exception {
    long killID = (Long) instanceData[2] + killIDAdjustment;
    Kill kill = new Kill(killID, (Long) instanceData[3], (Integer) instanceData[4], (Long) instanceData[5]);
    kill.setup(syncAccount, time);

    final Object[][] attackerData = (Object[][]) instanceData[0];
    for (int i = 0; i < attackerData.length; i++) {
      long attackerCharacterID = (Long) attackerData[i][2];
      KillAttacker attacker = new KillAttacker(
          killID, attackerCharacterID, (Long) attackerData[i][0], (String) attackerData[i][1] + tweak, (String) attackerData[i][3] + tweak,
          (Long) attackerData[i][4], (String) attackerData[i][5] + tweak, (Integer) attackerData[i][6], (Integer) attackerData[i][7],
          (String) attackerData[i][8] + tweak, (Double) attackerData[i][9], (Integer) attackerData[i][10], (Integer) attackerData[i][11],
          (Boolean) attackerData[i][12]);
      attacker.setup(syncAccount, time);
      attackers.add(attacker);
    }

    final Object[][] itemData = (Object[][]) instanceData[1];
    Queue<KillItemPair> nextItemQueue = new LinkedList<KillItemPair>();
    for (int i = 0; i < itemData.length; i++) {
      nextItemQueue.add(new KillItemPair(null, itemData[i]));
    }
    int sequence = 0;
    while (!nextItemQueue.isEmpty()) {
      KillItemPair nextData = nextItemQueue.remove();
      KillItem item = new KillItem(
          killID, (Integer) nextData.data[3], (Integer) nextData.data[0], (Integer) nextData.data[1], (Integer) nextData.data[2], (Boolean) nextData.data[4],
          sequence++, nextData.container == null ? KillItem.TOP_LEVEL : nextData.container.getSequence());
      item.setup(syncAccount, time);
      for (Object[] contained : (Object[][]) nextData.data[5]) {
        nextItemQueue.add(new KillItemPair(item, contained));
      }

      items.add(item);
    }

    final Object[] killVictimData = (Object[]) instanceData[6];
    KillVictim victim = new KillVictim(
        killID, (Long) killVictimData[0], (String) killVictimData[1] + tweak, (Long) killVictimData[2], (String) killVictimData[3] + tweak,
        (Long) killVictimData[4], (String) killVictimData[5] + tweak, (Long) killVictimData[6], (Long) killVictimData[7], (String) killVictimData[8] + tweak,
        (Integer) killVictimData[9]);
    victim.setup(syncAccount, time);
    victims.add(victim);

    return kill;
  }

  public void setupOkMock(final long killIDAdjustment, final String tweak) throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);

    // Divide test data into equal sized chunks
    final int testDataSize = testData.length / 3;

    IAnswer<Collection<IKill>> mockAnswerer = new IAnswer<Collection<IKill>>() {
      @Override
      public Collection<IKill> answer() throws Throwable {
        Long killIdLimit = (Long) (EasyMock.getCurrentArguments())[0];
        return assembleKills(testDataSize, killIdLimit, killIDAdjustment, tweak);
      }
    };

    // Sequence should be a plain request, followed by specific requests
    // until we run out of elements to serve.
    EasyMock.expect(mockServer.requestKillMails()).andReturn(assembleKills(testDataSize, null, killIDAdjustment, tweak));
    EasyMock.expectLastCall().once();
    EasyMock.expect(mockServer.isError()).andReturn(false);
    for (int i = testDataSize; i < testData.length; i += testDataSize) {
      EasyMock.expect(mockServer.requestKillMails(EasyMock.anyLong())).andAnswer(mockAnswerer);
      EasyMock.expect(mockServer.isError()).andReturn(false);
    }
    EasyMock.expect(mockServer.requestKillMails(EasyMock.anyLong())).andReturn(new ArrayList<IKill>());
    EasyMock.expect(mockServer.isError()).andReturn(true);
    EasyMock.expect(mockServer.getErrorCode()).andReturn(119);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  public void checkNoKillsFromTestData(long time, long killIDAdjustment, String tweak) throws Exception {
    for (int i = 0; i < testData.length; i++) {
      IKill next = makeKill(testData[i], killIDAdjustment, tweak);
      Kill check = Kill.get(syncAccount, time, next.getKillID());
      Assert.assertNull(check);

      for (IKillAttacker nextAttacker : next.getAttackers()) {
        KillAttacker checkAttacker = KillAttacker.get(syncAccount, time, next.getKillID(), nextAttacker.getCharacterID());
        Assert.assertNull(checkAttacker);
      }

      Queue<IKillItem> nextItemQueue = new LinkedList<IKillItem>();
      for (IKillItem nextItem : next.getItems()) {
        nextItemQueue.add(nextItem);
      }
      int sequence = 0;
      while (!nextItemQueue.isEmpty()) {
        IKillItem sample = nextItemQueue.remove();
        KillItem checkItem = KillItem.get(syncAccount, time, next.getKillID(), sequence++);
        Assert.assertNull(checkItem);
        for (IKillItem nextItem : sample.getContainedItems()) {
          nextItemQueue.add(nextItem);
        }
      }

      KillVictim checkVictim = KillVictim.get(syncAccount, time, next.getKillID());
      Assert.assertNull(checkVictim);
    }
  }

  public void checkKillsMatchTestData(long time, long killIDAdjustment, String tweak) throws Exception {
    for (int i = 0; i < testData.length; i++) {
      IKill next = makeKill(testData[i], killIDAdjustment, tweak);
      Kill check = Kill.get(syncAccount, time, next.getKillID());
      Assert.assertNotNull(check);
      Assert.assertEquals(next.getKillTime().getTime(), check.getKillTime());
      Assert.assertEquals(next.getMoonID(), check.getMoonID());
      Assert.assertEquals(next.getSolarSystemID(), check.getSolarSystemID());

      for (IKillAttacker nextAttacker : next.getAttackers()) {
        KillAttacker checkAttacker = KillAttacker.get(syncAccount, time, next.getKillID(), nextAttacker.getCharacterID());
        Assert.assertNotNull(checkAttacker);
        Assert.assertEquals(nextAttacker.getAllianceID(), checkAttacker.getAllianceID());
        Assert.assertEquals(nextAttacker.getAllianceName(), checkAttacker.getAllianceName());
        Assert.assertEquals(nextAttacker.getCharacterID(), checkAttacker.getAttackerCharacterID());
        Assert.assertEquals(nextAttacker.getCharacterName(), checkAttacker.getAttackerCharacterName());
        Assert.assertEquals(nextAttacker.getCorporationID(), checkAttacker.getAttackerCorporationID());
        Assert.assertEquals(nextAttacker.getCorporationName(), checkAttacker.getAttackerCorporationName());
        Assert.assertEquals(nextAttacker.getDamageDone(), checkAttacker.getDamageDone());
        Assert.assertEquals(nextAttacker.getFactionID(), checkAttacker.getFactionID());
        Assert.assertEquals(nextAttacker.getFactionName(), checkAttacker.getFactionName());
        Assert.assertEquals(nextAttacker.getSecurityStatus(), checkAttacker.getSecurityStatus(), 0.01);
        Assert.assertEquals(nextAttacker.getShipTypeID(), checkAttacker.getShipTypeID());
        Assert.assertEquals(nextAttacker.getWeaponTypeID(), checkAttacker.getWeaponTypeID());
        Assert.assertEquals(nextAttacker.isFinalBlow(), checkAttacker.isFinalBlow());
      }

      int sequence = 0;
      Queue<IKillItemPair> nextItemQueue = new LinkedList<IKillItemPair>();
      for (IKillItem nextItem : next.getItems()) {
        KillItem checkItem = KillItem.get(syncAccount, time, next.getKillID(), sequence++);
        nextItemQueue.add(new IKillItemPair(checkItem, nextItem));
      }
      while (!nextItemQueue.isEmpty()) {
        IKillItemPair pair = nextItemQueue.remove();
        Assert.assertNotNull(pair.stored);
        Assert.assertEquals(pair.source.getFlag(), pair.stored.getFlag());
        Assert.assertEquals(pair.source.getQtyDestroyed(), pair.stored.getQtyDestroyed());
        Assert.assertEquals(pair.source.getQtyDropped(), pair.stored.getQtyDropped());
        Assert.assertEquals(pair.source.getTypeID(), pair.stored.getTypeID());
        Assert.assertEquals(pair.source.isSingleton(), pair.stored.isSingleton());

        for (IKillItem nextItem : pair.source.getContainedItems()) {
          KillItem checkItem = KillItem.get(syncAccount, time, next.getKillID(), sequence++);
          nextItemQueue.add(new IKillItemPair(checkItem, nextItem));
        }
      }

      IKillVictim nextVictim = next.getVictim();
      KillVictim checkVictim = KillVictim.get(syncAccount, time, next.getKillID());
      Assert.assertNotNull(checkVictim);
      Assert.assertEquals(nextVictim.getAllianceID(), checkVictim.getAllianceID());
      Assert.assertEquals(nextVictim.getAllianceName(), checkVictim.getAllianceName());
      Assert.assertEquals(nextVictim.getCharacterID(), checkVictim.getKillCharacterID());
      Assert.assertEquals(nextVictim.getCharacterName(), checkVictim.getKillCharacterName());
      Assert.assertEquals(nextVictim.getCorporationID(), checkVictim.getKillCorporationID());
      Assert.assertEquals(nextVictim.getCorporationName(), checkVictim.getKillCorporationName());
      Assert.assertEquals(nextVictim.getDamageTaken(), checkVictim.getDamageTaken());
      Assert.assertEquals(nextVictim.getFactionID(), checkVictim.getFactionID());
      Assert.assertEquals(nextVictim.getFactionName(), checkVictim.getFactionName());
      Assert.assertEquals(nextVictim.getShipTypeID(), checkVictim.getShipTypeID());
    }
  }

  // Test update with all new kills
  @Test
  public void testCharacterKillLogSyncUpdate() throws Exception {
    setupOkMock(0, "");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterKillLogSync.syncCharacterKillLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify kills were added correctly.
    checkKillsMatchTestData(testTime, 0, "");

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getKilllogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getKilllogStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getKilllogDetail());
  }

  // Test update with kill log already populated
  @Test
  public void testCharacterKillLogSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock(0, "");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing kills which should not be modified.
    for (int i = 0; i < testData.length; i++) {
      List<KillAttacker> attackers = new ArrayList<KillAttacker>();
      List<KillItem> items = new ArrayList<KillItem>();
      List<KillVictim> victim = new ArrayList<KillVictim>();
      Kill kill = makeKillObject(testTime, testData[i], attackers, items, victim, 0, "foo");
      kill = CachedData.update(kill);
      for (KillAttacker attacker : attackers) {
        attacker = CachedData.update(attacker);
      }
      for (KillItem item : items) {
        item = CachedData.update(item);
      }
      CachedData.update(victim.get(0));
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterKillLogSync.syncCharacterKillLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify previously added kills are evolved.
    checkKillsMatchTestData(testTime, 0, "");

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getKilllogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getKilllogStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getKilllogDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterKillLogSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock(0, "");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing kills with adjusted kill IDs.
    for (int i = 0; i < testData.length; i++) {
      List<KillAttacker> attackers = new ArrayList<KillAttacker>();
      List<KillItem> items = new ArrayList<KillItem>();
      List<KillVictim> victim = new ArrayList<KillVictim>();
      Kill kill = makeKillObject(testTime, testData[i], attackers, items, victim, 25, "foo");
      kill = CachedData.update(kill);
      for (KillAttacker attacker : attackers) {
        attacker = CachedData.update(attacker);
      }
      for (KillItem item : items) {
        item = CachedData.update(item);
      }
      CachedData.update(victim.get(0));
    }

    // Set the tracker as already updated and populate the container
    tracker.setKilllogStatus(SyncState.UPDATED);
    tracker.setKilllogDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setKilllogExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterKillLogSync.syncCharacterKillLog(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify previously added kills are unmodified.
    checkKillsMatchTestData(testTime, 25, "foo");

    // Verify no kills were added from unmodified test data.
    checkNoKillsFromTestData(testTime, 0, "");

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getKilllogExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getKilllogStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getKilllogDetail());
  }

  private class SourceItemPair {
    final IKillItem container;
    final Object[]  data;

    public SourceItemPair(IKillItem container, Object[] data) {
      super();
      this.container = container;
      this.data = data;
    }
  }

  private class KillItemPair {
    final KillItem container;
    final Object[] data;

    public KillItemPair(KillItem container, Object[] data) {
      super();
      this.container = container;
      this.data = data;
    }
  }

  private class IKillItemPair {
    final KillItem  stored;
    final IKillItem source;

    public IKillItemPair(KillItem stored, IKillItem source) {
      super();
      this.stored = stored;
      this.source = source;
    }
  }

}
