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
import enterprises.orbital.evekit.model.common.Standing;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IStanding;
import enterprises.orbital.evexmlapi.shared.IStandingSet;

public class CorporationStandingSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  static Object[][]      agentTestData, npcTestData, factionTestData;

  static {
    // Generate random test data
    int size = 20 + TestBase.getRandomInt(20);
    agentTestData = new Object[size][3];
    size = 20 + TestBase.getRandomInt(20);
    npcTestData = new Object[size][3];
    size = 20 + TestBase.getRandomInt(20);
    factionTestData = new Object[size][3];
    for (int i = 0; i < agentTestData.length; i++) {
      agentTestData[i][0] = TestBase.getUniqueRandomInteger();
      agentTestData[i][1] = TestBase.getRandomText(50);
      agentTestData[i][2] = TestBase.getRandomDouble(10);
    }
    for (int i = 0; i < npcTestData.length; i++) {
      npcTestData[i][0] = TestBase.getUniqueRandomInteger();
      npcTestData[i][1] = TestBase.getRandomText(50);
      npcTestData[i][2] = TestBase.getRandomDouble(10);
    }
    for (int i = 0; i < factionTestData.length; i++) {
      factionTestData[i][0] = TestBase.getUniqueRandomInteger();
      factionTestData[i][1] = TestBase.getRandomText(50);
      factionTestData[i][2] = TestBase.getRandomDouble(10);
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
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IStanding makeStanding(final double standing, final String name, final int fromID) {
    return new IStanding() {

      @Override
      public double getStanding() {
        return standing;
      }

      @Override
      public String getFromName() {
        return name;
      }

      @Override
      public int getFromID() {
        return fromID;
      }
    };
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    final List<IStanding> agentStandings = new ArrayList<IStanding>();
    final List<IStanding> npcStandings = new ArrayList<IStanding>();
    final List<IStanding> factionStandings = new ArrayList<IStanding>();
    for (int i = 0; i < agentTestData.length; i++) {
      final Object[] instanceData = agentTestData[i];
      agentStandings.add(makeStanding((Double) instanceData[2], (String) instanceData[1], (Integer) instanceData[0]));
    }
    for (int i = 0; i < npcTestData.length; i++) {
      final Object[] instanceData = npcTestData[i];
      npcStandings.add(makeStanding((Double) instanceData[2], (String) instanceData[1], (Integer) instanceData[0]));
    }
    for (int i = 0; i < factionTestData.length; i++) {
      final Object[] instanceData = factionTestData[i];
      factionStandings.add(makeStanding((Double) instanceData[2], (String) instanceData[1], (Integer) instanceData[0]));
    }
    IStandingSet standings = new IStandingSet() {

      @Override
      public List<IStanding> getAgentStandings() {
        return agentStandings;
      }

      @Override
      public List<IStanding> getNPCCorporationStandings() {
        return npcStandings;
      }

      @Override
      public List<IStanding> getFactionStandings() {
        return factionStandings;
      }

    };
    EasyMock.expect(mockServer.requestStandings()).andReturn(standings);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with all new standings
  @Test
  public void testCorporationStandingSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationStandingSync.syncStanding(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify standings were added correctly.
    for (int i = 0; i < agentTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "AGENT", (Integer) agentTestData[i][0]);
      Assert.assertEquals(agentTestData[i][1], next.getFromName());
      Assert.assertEquals(agentTestData[i][2], next.getStanding());
    }
    for (int i = 0; i < npcTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "NPC_CORPORATION", (Integer) npcTestData[i][0]);
      Assert.assertEquals(npcTestData[i][1], next.getFromName());
      Assert.assertEquals(npcTestData[i][2], next.getStanding());
    }
    for (int i = 0; i < factionTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "FACTION", (Integer) factionTestData[i][0]);
      Assert.assertEquals(factionTestData[i][1], next.getFromName());
      Assert.assertEquals(factionTestData[i][2], next.getStanding());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getStandingsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStandingsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStandingsDetail());
  }

  // Test update with standings already populated
  @Test
  public void testCorporationStandingSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing standings
    for (int i = 0; i < agentTestData.length; i++) {
      Standing next = new Standing("AGENT", (Integer) agentTestData[i][0], (String) agentTestData[i][1], (Double) agentTestData[i][2]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }
    for (int i = 0; i < npcTestData.length; i++) {
      Standing next = new Standing("NPC_CORPORATION", (Integer) npcTestData[i][0], (String) npcTestData[i][1], (Double) npcTestData[i][2]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }
    for (int i = 0; i < factionTestData.length; i++) {
      Standing next = new Standing("FACTION", (Integer) factionTestData[i][0], (String) factionTestData[i][1], (Double) factionTestData[i][2]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationStandingSync.syncStanding(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify standings are unchanged
    for (int i = 0; i < agentTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "AGENT", (Integer) agentTestData[i][0]);
      Assert.assertEquals(agentTestData[i][1], next.getFromName());
      Assert.assertEquals(agentTestData[i][2], next.getStanding());
    }
    for (int i = 0; i < npcTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "NPC_CORPORATION", (Integer) npcTestData[i][0]);
      Assert.assertEquals(npcTestData[i][1], next.getFromName());
      Assert.assertEquals(npcTestData[i][2], next.getStanding());
    }
    for (int i = 0; i < factionTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "FACTION", (Integer) factionTestData[i][0]);
      Assert.assertEquals(factionTestData[i][1], next.getFromName());
      Assert.assertEquals(factionTestData[i][2], next.getStanding());
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getStandingsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStandingsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStandingsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationStandingSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing standings
    for (int i = 0; i < agentTestData.length; i++) {
      Standing next = new Standing("AGENT", (Integer) agentTestData[i][0], (String) agentTestData[i][1], (Double) agentTestData[i][2]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }
    for (int i = 0; i < npcTestData.length; i++) {
      Standing next = new Standing("NPC_CORPORATION", (Integer) npcTestData[i][0], (String) npcTestData[i][1], (Double) npcTestData[i][2]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }
    for (int i = 0; i < factionTestData.length; i++) {
      Standing next = new Standing("FACTION", (Integer) factionTestData[i][0], (String) factionTestData[i][1], (Double) factionTestData[i][2]);
      next.setup(syncAccount, testTime);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setStandingsStatus(SyncState.UPDATED);
    tracker.setStandingsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setStandingsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationStandingSync.syncStanding(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify standings are unchanged
    for (int i = 0; i < agentTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "AGENT", (Integer) agentTestData[i][0]);
      Assert.assertEquals(agentTestData[i][1], next.getFromName());
      Assert.assertEquals(agentTestData[i][2], next.getStanding());
    }
    for (int i = 0; i < npcTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "NPC_CORPORATION", (Integer) npcTestData[i][0]);
      Assert.assertEquals(npcTestData[i][1], next.getFromName());
      Assert.assertEquals(npcTestData[i][2], next.getStanding());
    }
    for (int i = 0; i < factionTestData.length; i++) {
      Standing next = Standing.get(syncAccount, testTime, "FACTION", (Integer) factionTestData[i][0]);
      Assert.assertEquals(factionTestData[i][1], next.getFromName());
      Assert.assertEquals(factionTestData[i][2], next.getStanding());
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getStandingsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStandingsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStandingsDetail());
  }

}
