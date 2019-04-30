package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.KillmailsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Kill;
import enterprises.orbital.evekit.model.common.KillAttacker;
import enterprises.orbital.evekit.model.common.KillItem;
import enterprises.orbital.evekit.model.common.KillVictim;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICorporationKillmaillSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private KillmailsApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] killmailTestData;
  private static int[] killmailPages;

  static {
    // Comparator for sorting test data in increasing order by killID (testData[i][1])
    Comparator<Object[]> killDataCompare = Comparator.comparingInt(x -> (Integer) x[0]);

    int size = 50 + TestBase.getRandomInt(50);
    killmailTestData = new Object[size][8];
    for (int i = 0; i < size; i++) {
      // Killhash data
      // 0 int killID; (also used as the hash converted to a string)
      // 1 long killTime = -1;
      // 2 int moonID;
      // 3 int solarSystemID;
      // 4 int warID;
      // 5 Object[] killvictim
      // 6 Object[][] killattackers
      // 7 Object[][] killitems
      killmailTestData[i][0] = TestBase.getUniqueRandomInteger() * 10;
      killmailTestData[i][1] = TestBase.getRandomLong();
      killmailTestData[i][2] = TestBase.getRandomInt();
      killmailTestData[i][3] = TestBase.getRandomInt();
      killmailTestData[i][4] = TestBase.getRandomInt();

      // Killvictim data
      // 0 int killID;
      // 1 int allianceID;
      // 2 int killCharacterID;
      // 3 int killCorporationID;
      // 4 int damageTaken;
      // 5 int factionID;
      // 6 int shipTypeID;
      // 7 double x;
      // 8 double y;
      // 9 double z;
      Object[] victim = new Object[10];
      killmailTestData[i][5] = victim;
      victim[0] = killmailTestData[i][0];
      victim[1] = TestBase.getRandomInt();
      victim[2] = TestBase.getRandomInt();
      victim[3] = TestBase.getRandomInt();
      victim[4] = TestBase.getRandomInt();
      victim[5] = TestBase.getRandomInt();
      victim[6] = TestBase.getRandomInt();
      victim[7] = TestBase.getRandomDouble(10000);
      victim[8] = TestBase.getRandomDouble(10000);
      victim[9] = TestBase.getRandomDouble(10000);

      // Killattacker data
      // 0 int killID;
      // 1 int attackerCharacterID;
      // 2 int allianceID;
      // 3 int attackerCorporationID;
      // 4 int damageDone;
      // 5 int factionID;
      // 6 float securityStatus;
      // 7 int shipTypeID;
      // 8 int weaponTypeID;
      // 9 boolean finalBlow;
      int attackerCount = 3 + TestBase.getRandomInt(3);
      Object[][] attackers = new Object[attackerCount][10];
      killmailTestData[i][6] = attackers;
      for (int j = 0; j < attackerCount; j++) {
        attackers[j][0] = killmailTestData[i][0];
        attackers[j][1] = TestBase.getRandomInt();
        attackers[j][2] = TestBase.getRandomInt();
        attackers[j][3] = TestBase.getRandomInt();
        attackers[j][4] = TestBase.getRandomInt();
        attackers[j][5] = TestBase.getRandomInt();
        attackers[j][6] = TestBase.getRandomFloat(10);
        attackers[j][7] = TestBase.getRandomInt();
        attackers[j][8] = TestBase.getRandomInt();
        attackers[j][9] = TestBase.getRandomBoolean();
      }

      // Killitem data
      // 0 int killID;
      // 1 int typeID;
      // 2 int flag;
      // 3 long qtyDestroyed;
      // 4 long qtyDropped;
      // 5 int singleton;
      // 6 int sequence;
      // 7 int containerSequence = TOP_LEVEL;
      int itemCount = 5 + TestBase.getRandomInt(5);
      Object[][] items = new Object[itemCount][8];
      killmailTestData[i][7] = items;
      for (int j = 0; j < itemCount; j++) {
        items[j][0] = killmailTestData[i][0];
        items[j][1] = TestBase.getRandomInt();
        items[j][2] = TestBase.getRandomInt();
        items[j][3] = TestBase.getRandomLong();
        items[j][4] = TestBase.getRandomLong();
        items[j][5] = TestBase.getRandomInt();
        items[j][6] = j;
        items[j][7] = KillItem.TOP_LEVEL;
      }
      for (int j = 1; j < itemCount; j += 2) {
        items[j][7] = items[j - 1][6];
      }
    }

    // Sort test data in increasing order by killID (killmailTestData[i][0])
    Arrays.sort(killmailTestData, 0, killmailTestData.length, killDataCompare);

    // Divide data into pages to test paging sync feature
    int pageCount = 3 + TestBase.getRandomInt(3);
    killmailPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--) {
      killmailPages[i] = size - (pageCount - i - 1) * (size / pageCount);
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_KILL_MAIL, 1234L, "0");

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
                                                        .createQuery("DELETE FROM Kill ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM KillAttacker ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM KillVictim ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM KillItem ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(KillmailsApi.class);

    // Setup kill list
    List<GetCorporationsCorporationIdKillmailsRecent200Ok> killmailList =
        Arrays.stream(killmailTestData)
              .map(x -> {
                GetCorporationsCorporationIdKillmailsRecent200Ok killHash = new GetCorporationsCorporationIdKillmailsRecent200Ok();
                killHash.setKillmailId((Integer) x[0]);
                killHash.setKillmailHash(String.valueOf(x[0]));
                return killHash;
              })
              .collect(Collectors.toList());

    // First setup kill list mock
    int last = 0;
    for (int i = 0; i < killmailPages.length; i++) {
      ApiResponse<List<GetCorporationsCorporationIdKillmailsRecent200Ok>> apir = new ApiResponse<>(200,
                                                                                                   createHeaders(
                                                                                                       "Expires",
                                                                                                       "Thu, 21 Dec 2017 12:00:00 GMT",
                                                                                                       "X-Pages",
                                                                                                       String.valueOf(killmailPages.length)),
                                                                                                   killmailList.subList(
                                                                                                       last,
                                                                                                       killmailPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdKillmailsRecentWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(apir);
      last = killmailPages[i];
    }

    // Now setup kill hash calls
    for (Object[] nk : killmailTestData) {
      GetKillmailsKillmailIdKillmailHashOk killData = new GetKillmailsKillmailIdKillmailHashOk();
      killData.setKillmailId((Integer) nk[0]);
      killData.setKillmailTime(new DateTime(new Date((Long) nk[1])));
      killData.setMoonId((Integer) nk[2]);
      killData.setSolarSystemId((Integer) nk[3]);
      killData.setWarId((Integer) nk[4]);

      GetKillmailsKillmailIdKillmailHashVictim killVictim = new GetKillmailsKillmailIdKillmailHashVictim();
      Object[] nv = (Object[]) nk[5];
      killData.setVictim(killVictim);
      killVictim.setAllianceId((Integer) nv[1]);
      killVictim.setCharacterId((Integer) nv[2]);
      killVictim.setCorporationId((Integer) nv[3]);
      killVictim.setDamageTaken((Integer) nv[4]);
      killVictim.setFactionId((Integer) nv[5]);
      killVictim.setShipTypeId((Integer) nv[6]);
      GetKillmailsKillmailIdKillmailHashPosition killVictimPos = new GetKillmailsKillmailIdKillmailHashPosition();
      killVictim.setPosition(killVictimPos);
      killVictimPos.setX((Double) nv[7]);
      killVictimPos.setY((Double) nv[8]);
      killVictimPos.setZ((Double) nv[9]);

      List<GetKillmailsKillmailIdKillmailHashItem> topItems = new ArrayList<>();
      killVictim.setItems(topItems);
      for (Object[] ni : (Object[][]) nk[7]) {
        int containerSequence = (Integer) ni[7];
        if (containerSequence == -1) {
          GetKillmailsKillmailIdKillmailHashItem newTI = new GetKillmailsKillmailIdKillmailHashItem();
          newTI.setItemTypeId((Integer) ni[1]);
          newTI.setFlag((Integer) ni[2]);
          newTI.setQuantityDestroyed((Long) ni[3]);
          newTI.setQuantityDropped((Long) ni[4]);
          newTI.setSingleton((Integer) ni[5]);
          topItems.add(newTI);
        } else {
          GetKillmailsKillmailIdKillmailHashItemsItem newTI = new GetKillmailsKillmailIdKillmailHashItemsItem();
          newTI.setItemTypeId((Integer) ni[1]);
          newTI.setFlag((Integer) ni[2]);
          newTI.setQuantityDestroyed((Long) ni[3]);
          newTI.setQuantityDropped((Long) ni[4]);
          newTI.setSingleton((Integer) ni[5]);
          topItems.get(topItems.size() - 1)
                  .getItems()
                  .add(newTI);
        }
      }

      killData.getAttackers()
              .addAll(
                  Arrays.stream((Object[][]) nk[6])
                        .map(x -> {
                          GetKillmailsKillmailIdKillmailHashAttacker newA = new GetKillmailsKillmailIdKillmailHashAttacker();
                          newA.setCharacterId((Integer) x[1]);
                          newA.setAllianceId((Integer) x[2]);
                          newA.setCorporationId((Integer) x[3]);
                          newA.setDamageDone((Integer) x[4]);
                          newA.setFactionId((Integer) x[5]);
                          newA.setSecurityStatus((Float) x[6]);
                          newA.setShipTypeId((Integer) x[7]);
                          newA.setWeaponTypeId((Integer) x[8]);
                          newA.setFinalBlow((Boolean) x[9]);
                          return newA;
                        })
                        .collect(Collectors.toList()));

      // Insert mock call and response
      ApiResponse<GetKillmailsKillmailIdKillmailHashOk> apir = new ApiResponse<>(200,
                                                                                 createHeaders("Expires",
                                                                                               "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                 killData);
      EasyMock.expect(mockEndpoint.getKillmailsKillmailIdKillmailHashWithHttpInfo(
          String.valueOf(killData.getKillmailId()),
          killData.getKillmailId(),
          null,
          null))
              .andReturn(apir);
    }


    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getKillmailsApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(Object[][] testData) throws Exception {
    // Retrieve all stored data
    List<Kill> storedKills = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Kill.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                         AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                         AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedKills.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      Kill nextEl = storedKills.get(i);
      Object[] dt = testData[i];
      Assert.assertEquals((int) (Integer) dt[0], nextEl.getKillID());
      Assert.assertEquals((long) (Long) dt[1], nextEl.getKillTime());
      Assert.assertEquals((int) (Integer) dt[2], nextEl.getMoonID());
      Assert.assertEquals((int) (Integer) dt[3], nextEl.getSolarSystemID());
      Assert.assertEquals((int) (Integer) dt[4], nextEl.getWarID());
    }

    // Retrieve all stored data
    List<KillVictim> storedVictims = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        KillVictim.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                               AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testData.length, storedVictims.size());

    // Check stored data
    for (int i = 0; i < testData.length; i++) {
      KillVictim nextEl = storedVictims.get(i);
      Object[] dt = (Object[]) testData[i][5];
      Assert.assertEquals((int) (Integer) dt[0], nextEl.getKillID());
      Assert.assertEquals((int) (Integer) dt[1], nextEl.getAllianceID());
      Assert.assertEquals((int) (Integer) dt[2], nextEl.getKillCharacterID());
      Assert.assertEquals((int) (Integer) dt[3], nextEl.getKillCorporationID());
      Assert.assertEquals((int) (Integer) dt[4], nextEl.getDamageTaken());
      Assert.assertEquals((int) (Integer) dt[5], nextEl.getFactionID());
      Assert.assertEquals((int) (Integer) dt[6], nextEl.getShipTypeID());
      Assert.assertEquals((Double) dt[7], nextEl.getX(), 0.001);
      Assert.assertEquals((Double) dt[8], nextEl.getY(), 0.001);
      Assert.assertEquals((Double) dt[9], nextEl.getZ(), 0.001);
    }

    // Retrieve all stored data
    List<KillAttacker> storedAttackers = AbstractESIAccountSync.retrieveAll(testTime,
                                                                            (long contid, AttributeSelector at) ->
                                                                                KillAttacker.accessQuery(
                                                                                    corpSyncAccount, contid, 1000,
                                                                                    false, at,
                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
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
    int attackerLength = Arrays.stream(testData)
                               .map(x -> ((Object[][]) x[6]).length)
                               .reduce(0, Integer::sum);
    Assert.assertEquals(attackerLength, storedAttackers.size());

    // Check stored data
    for (int i = 0, j = 0; i < testData.length; i++) {
      Object[][] da = (Object[][]) testData[i][6];
      for (Object[] nextA : da) {
        KillAttacker nextEl = storedAttackers.get(j++);
        Assert.assertEquals((int) (Integer) nextA[0], nextEl.getKillID());
        Assert.assertEquals((int) (Integer) nextA[1], nextEl.getAttackerCharacterID());
        Assert.assertEquals((int) (Integer) nextA[2], nextEl.getAllianceID());
        Assert.assertEquals((int) (Integer) nextA[3], nextEl.getAttackerCorporationID());
        Assert.assertEquals((int) (Integer) nextA[4], nextEl.getDamageDone());
        Assert.assertEquals((int) (Integer) nextA[5], nextEl.getFactionID());
        Assert.assertEquals((Float) nextA[6], nextEl.getSecurityStatus(), 0.001);
        Assert.assertEquals((int) (Integer) nextA[7], nextEl.getShipTypeID());
        Assert.assertEquals((int) (Integer) nextA[8], nextEl.getWeaponTypeID());
        Assert.assertEquals(nextA[9], nextEl.isFinalBlow());
      }
    }

    // Retrieve all stored data
    List<KillItem> storedItems = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        KillItem.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    int itemLength = Arrays.stream(testData)
                           .map(x -> ((Object[][]) x[7]).length)
                           .reduce(0, Integer::sum);
    Assert.assertEquals(itemLength, storedItems.size());

    // Check stored data
    for (int i = 0, j = 0; i < testData.length; i++) {
      Object[][] da = (Object[][]) testData[i][7];
      for (Object[] nextA : da) {
        KillItem nextEl = storedItems.get(j++);
        Assert.assertEquals((int) (Integer) nextA[0], nextEl.getKillID());
        Assert.assertEquals((int) (Integer) nextA[1], nextEl.getTypeID());
        Assert.assertEquals((int) (Integer) nextA[2], nextEl.getFlag());
        Assert.assertEquals((long) (Long) nextA[3], nextEl.getQtyDestroyed());
        Assert.assertEquals((long) (Long) nextA[4], nextEl.getQtyDropped());
        Assert.assertEquals((int) (Integer) nextA[5], nextEl.getSingleton());
        Assert.assertEquals((int) (Integer) nextA[6], nextEl.getSequence());
        Assert.assertEquals((int) (Integer) nextA[7], nextEl.getContainerSequence());
      }
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationKillMailSync sync = new ESICorporationKillMailSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(killmailTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_KILL_MAIL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_KILL_MAIL);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing.  These should not be modified.  This is just a copy of the existing test data with
    // lower kill IDs and slightly adjusted data values.
    int firstID = (Integer) killmailTestData[killmailTestData.length - 1][0] - killmailTestData.length - 1;
    Object[][] allData = new Object[killmailTestData.length * 2][8];
    for (int i = 0; i < allData.length / 2; i++) {
      System.arraycopy(killmailTestData[i], 0, allData[i], 0, 8);
      System.arraycopy(killmailTestData[i], 0, allData[i + killmailTestData.length], 0, 8);
    }
    for (int i = 0; i < allData.length / 2; i++) {
      // Reset kill ID
      allData[i][0] = firstID + i;

      // Copy victim
      Object[] victimCopy = new Object[10];
      allData[i][5] = victimCopy;
      Object[] victim = (Object[]) allData[i + killmailTestData.length][5];
      System.arraycopy(victim, 0, victimCopy, 0, 10);
      victimCopy[0] = firstID + i;

      // Copy attackers
      int attackerLen = ((Object[][]) allData[i + killmailTestData.length][6]).length;
      Object[][] attackersCopy = new Object[attackerLen][10];
      allData[i][6] = attackersCopy;
      for (int j = 0; j < attackerLen; j++) {
        Object[] srcAttacker = ((Object[][]) allData[i + killmailTestData.length][6])[j];
        Object[] dstAttacker = attackersCopy[j];
        System.arraycopy(srcAttacker, 0, dstAttacker, 0, 10);
        dstAttacker[0] = firstID + i;
      }

      // Copy items
      int itemLen = ((Object[][]) allData[i + killmailTestData.length][7]).length;
      Object[][] itemsCopy = new Object[itemLen][8];
      allData[i][7] = itemsCopy;
      for (int j = 0; j < itemLen; j++) {
        Object[] srcItem = ((Object[][]) allData[i + killmailTestData.length][7])[j];
        Object[] dstItem = itemsCopy[j];
        System.arraycopy(srcItem, 0, dstItem, 0, 8);
        dstItem[0] = firstID + i;
      }
    }

    for (int i = 0; i < allData.length / 2; i++) {
      Object[] dt = allData[i];
      Kill nk = new Kill((Integer) dt[0],
                         (Long) dt[1],
                         (Integer) dt[2],
                         (Integer) dt[3],
                         (Integer) dt[4]);
      nk.setup(corpSyncAccount, testTime - 1);
      CachedData.update(nk);

      Object[] victim = (Object[]) dt[5];
      KillVictim nv = new KillVictim((Integer) victim[0],
                                     (Integer) victim[1],
                                     (Integer) victim[2],
                                     (Integer) victim[3],
                                     (Integer) victim[4],
                                     (Integer) victim[5],
                                     (Integer) victim[6],
                                     (Double) victim[7],
                                     (Double) victim[8],
                                     (Double) victim[9]);
      nv.setup(corpSyncAccount, testTime - 1);
      CachedData.update(nv);

      Object[][] attackers = (Object[][]) dt[6];
      for (Object[] nextAttacker : attackers) {
        KillAttacker na = new KillAttacker((Integer) nextAttacker[0],
                                           (Integer) nextAttacker[1],
                                           (Integer) nextAttacker[2],
                                           (Integer) nextAttacker[3],
                                           (Integer) nextAttacker[4],
                                           (Integer) nextAttacker[5],
                                           (Float) nextAttacker[6],
                                           (Integer) nextAttacker[7],
                                           (Integer) nextAttacker[8],
                                           (Boolean) nextAttacker[9]);
        na.setup(corpSyncAccount, testTime - 1);
        CachedData.update(na);
      }

      Object[][] items = (Object[][]) dt[7];
      for (Object[] nextItem : items) {
        KillItem ni = new KillItem((Integer) nextItem[0],
                                   (Integer) nextItem[1],
                                   (Integer) nextItem[2],
                                   (Long) nextItem[3],
                                   (Long) nextItem[4],
                                   (Integer) nextItem[5],
                                   (Integer) nextItem[6],
                                   (Integer) nextItem[7]);
        ni.setup(corpSyncAccount, testTime - 1);
        CachedData.update(ni);
      }
    }

    // Perform the sync
    ESICorporationKillMailSync sync = new ESICorporationKillMailSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    verifyDataUpdate(allData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_KILL_MAIL);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_KILL_MAIL);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
