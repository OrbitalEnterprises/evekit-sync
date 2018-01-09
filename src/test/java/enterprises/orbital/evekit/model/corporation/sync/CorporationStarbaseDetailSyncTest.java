package enterprises.orbital.evekit.model.corporation.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

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
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.Fuel;
import enterprises.orbital.evekit.model.corporation.StarbaseDetail;
import enterprises.orbital.evexmlapi.crp.ICombatSetting;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.IFuel;
import enterprises.orbital.evexmlapi.crp.IStarbase;
import enterprises.orbital.evexmlapi.crp.IStarbaseDetail;

public class CorporationStarbaseDetailSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                        testDate;
  long                        prevDate;
  EveKitUserAccount           userAccount;
  SynchronizedEveAccount      syncAccount;
  Corporation                 container;
  CorporationSyncTracker      tracker;
  SynchronizerUtil            syncUtil;
  ICorporationAPI             mockServer;

  // 0 long itemID;
  // ...rest of Starbase data is irrelevant for this test.
  protected static Object[]   starbaseData;

  // 0 long itemID;
  // 1 int state;
  // 2 long stateDate;
  // 3 long onlineDate;
  // 4 int usageFlags;
  // 5 int deployFlags;
  // 6 boolean allowAllianceMembers;
  // 7 boolean allowCorporationMembers;
  // 8 long useStandingsFrom;
  // 9 boolean onAggressionEnabled;
  // 10 int onAgressionStanding;
  // 11 boolean onCorporationWarEnabled;
  // 12 int onCorporationWarStanding;
  // 13 boolean onStandingDropEnabled;
  // 14 int onStandingDropStanding;
  // 15 boolean onStatusDropEnabled;
  // 16 int onStatusDropStanding;
  // 17 random array of fuel map entries, fuel is int typeID, and int quantity
  protected static Object[][] testData;

  static {
    // Generate test data
    int size = 10 + TestBase.getRandomInt(10);
    starbaseData = new Object[size];
    for (int i = 0; i < size; i++) {
      starbaseData[i] = TestBase.getUniqueRandomLong();
    }
    // Pre-generate type IDs for fuel so that all starbases have the same fuel types.
    int fuelCount = 1 + TestBase.getRandomInt(5);
    int[] fuelTypes = new int[fuelCount];
    for (int i = 0; i < fuelCount; i++) {
      fuelTypes[i] = TestBase.getUniqueRandomInteger();
    }
    // Now finish generating test data.
    testData = new Object[size][18];
    for (int i = 0; i < size; i++) {
      testData[i][0] = starbaseData[i];
      testData[i][1] = TestBase.getRandomInt();
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomInt();
      testData[i][5] = TestBase.getRandomInt();
      testData[i][6] = TestBase.getRandomBoolean();
      testData[i][7] = TestBase.getRandomBoolean();
      testData[i][8] = TestBase.getRandomLong();
      testData[i][9] = TestBase.getRandomBoolean();
      testData[i][10] = TestBase.getRandomInt();
      testData[i][11] = TestBase.getRandomBoolean();
      testData[i][12] = TestBase.getRandomInt();
      testData[i][13] = TestBase.getRandomBoolean();
      testData[i][14] = TestBase.getRandomInt();
      testData[i][15] = TestBase.getRandomBoolean();
      testData[i][16] = TestBase.getRandomInt();
      Object[][] fuelData = new Object[fuelCount][2];
      for (int j = 0; j < fuelCount; j++) {
        fuelData[j][0] = fuelTypes[j];
        fuelData[j][1] = TestBase.getRandomInt();
      }
      testData[i][17] = fuelData;
    }
  }

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

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IStarbase> starbasess = new ArrayList<IStarbase>();
    for (int i = 0; i < starbaseData.length; i++) {
      final long itemID = (Long) starbaseData[i];
      starbasess.add(new IStarbase() {

        @Override
        public long getItemID() {
          return itemID;
        }

        @Override
        public long getLocationID() {
          return 0L;
        }

        @Override
        public int getMoonID() {
          return 0;
        }

        @Override
        public Date getOnlineTimestamp() {
          return new Date(0L);
        }

        @Override
        public int getState() {
          return 0;
        }

        @Override
        public Date getStateTimestamp() {
          return new Date(0L);
        }

        @Override
        public int getTypeID() {
          return 0;
        }

        @Override
        public long getStandingOwnerID() {
          return 0L;
        }
      });
    }

    EasyMock.expect(mockServer.requestStarbaseList()).andReturn(starbasess).anyTimes();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      IStarbaseDetail detail = new IStarbaseDetail() {

        @Override
        public int getState() {
          return (Integer) instanceData[1];
        }

        @Override
        public Date getStateTimestamp() {
          return new Date((Long) instanceData[2]);
        }

        @Override
        public Date getOnlineTimestamp() {
          return new Date((Long) instanceData[3]);
        }

        @Override
        public int getUsageFlags() {
          return (Integer) instanceData[4];
        }

        @Override
        public int getDeployFlags() {
          return (Integer) instanceData[5];
        }

        @Override
        public boolean isAllowAllianceMembers() {
          return (Boolean) instanceData[6];
        }

        @Override
        public boolean isAllowCorporationMembers() {
          return (Boolean) instanceData[7];
        }

        @Override
        public long getUseStandingsFrom() {
          return (Long) instanceData[8];
        }

        @Override
        public ICombatSetting getOnAggression() {
          return new ICombatSetting() {

            @Override
            public int getStanding() {
              return (Integer) instanceData[10];
            }

            @Override
            public boolean isEnabled() {
              return (Boolean) instanceData[9];
            }
          };
        }

        @Override
        public ICombatSetting getOnCorporationWar() {
          return new ICombatSetting() {

            @Override
            public int getStanding() {
              return (Integer) instanceData[12];
            }

            @Override
            public boolean isEnabled() {
              return (Boolean) instanceData[11];
            }
          };
        }

        @Override
        public ICombatSetting getOnStandingDrop() {
          return new ICombatSetting() {

            @Override
            public int getStanding() {
              return (Integer) instanceData[14];
            }

            @Override
            public boolean isEnabled() {
              return (Boolean) instanceData[13];
            }
          };
        }

        @Override
        public ICombatSetting getOnStatusDrop() {
          return new ICombatSetting() {

            @Override
            public int getStanding() {
              return (Integer) instanceData[16];
            }

            @Override
            public boolean isEnabled() {
              return (Boolean) instanceData[15];
            }
          };
        }

        @Override
        public Collection<IFuel> getFuelMap() {
          final Object[][] fuelData = (Object[][]) instanceData[17];
          final Collection<IFuel> fuelResult = new ArrayList<IFuel>();
          for (int k = 0; k < fuelData.length; k++) {
            final Object[] next = fuelData[k];
            fuelResult.add(new IFuel() {
              @Override
              public int getTypeID() {
                return (Integer) next[0];
              }

              @Override
              public int getQuantity() {
                return (Integer) next[1];
              }
            });
          }
          return fuelResult;
        }

      };
      EasyMock.expect(mockServer.requestStarbaseDetail((Long) starbaseData[i])).andReturn(detail).anyTimes();
    }
    EasyMock.expect(mockServer.isError()).andReturn(false).anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate)).anyTimes();
  }

  // Test update with all new starbase details.
  @Test
  public void testCorporationStarbaseDetailSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationStarbaseDetailSync.syncStarbaseDetail(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify starbase details were added correctly.
    int fuelCount = 0;
    for (int i = 0; i < testData.length; i++) {
      StarbaseDetail next = StarbaseDetail.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getItemID());
      Assert.assertEquals(testData[i][1], next.getState());
      Assert.assertEquals(testData[i][2], next.getStateTimestamp());
      Assert.assertEquals(testData[i][3], next.getOnlineTimestamp());
      Assert.assertEquals(testData[i][4], next.getUsageFlags());
      Assert.assertEquals(testData[i][5], next.getDeployFlags());
      Assert.assertEquals(testData[i][6], next.isAllowAllianceMembers());
      Assert.assertEquals(testData[i][7], next.isAllowCorporationMembers());
      Assert.assertEquals(testData[i][8], next.getUseStandingsFrom());
      Assert.assertEquals(testData[i][9], next.isOnAggressionEnabled());
      Assert.assertEquals(testData[i][10], next.getOnAggressionStanding());
      Assert.assertEquals(testData[i][11], next.isOnCorporationWarEnabled());
      Assert.assertEquals(testData[i][12], next.getOnCorporationWarStanding());
      Assert.assertEquals(testData[i][13], next.isOnStandingDropEnabled());
      Assert.assertEquals(testData[i][14], next.getOnStandingDropStanding());
      Assert.assertEquals(testData[i][15], next.isOnStatusDropEnabled());
      Assert.assertEquals(testData[i][16], next.getOnStatusDropStanding());
      Object[][] fuel = (Object[][]) testData[i][17];
      fuelCount += fuel.length;
      for (int j = 0; j < fuel.length; j++) {
        Fuel nextFuel = Fuel.get(syncAccount, testTime, (Long) testData[i][0], (Integer) fuel[j][0]);
        Assert.assertEquals(testData[i][0], nextFuel.getItemID());
        Assert.assertEquals(fuel[j][0], nextFuel.getTypeID());
        Assert.assertEquals(fuel[j][1], nextFuel.getQuantity());
      }
    }
    Assert.assertEquals(fuelCount, Fuel.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getStarbaseDetailExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseDetailStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseDetailDetail());
  }

  // Test update with starbase details already populated.
  @Test
  public void testCorporatioStarbaseDetailSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing starbase and fuel details.
    for (int i = 0; i < testData.length; i++) {
      StarbaseDetail next = new StarbaseDetail(
          (Long) testData[i][0] + 2, (Integer) testData[i][1] + 2, (Long) testData[i][2] + 2L, (Long) testData[i][3] + 2L, (Integer) testData[i][4] + 2,
          (Integer) testData[i][5] + 2, !(Boolean) testData[i][6], !(Boolean) testData[i][7], (Long) testData[i][8] + 2L, !(Boolean) testData[i][9],
          (Integer) testData[i][10] + 2, !(Boolean) testData[i][11], (Integer) testData[i][12] + 2, !(Boolean) testData[i][13], (Integer) testData[i][14] + 2,
          !(Boolean) testData[i][15], (Integer) testData[i][16] + 2);
      next.setup(syncAccount, testTime);
      Object[][] fuelData = (Object[][]) testData[i][17];
      for (int j = 0; j < fuelData.length; j++) {
        Fuel nextFuel = new Fuel((Long) testData[i][0] + 2, (Integer) fuelData[j][0] + 2, (Integer) fuelData[j][1] + 2);
        nextFuel.setup(syncAccount, testTime);
        nextFuel = CachedData.update(nextFuel);
      }
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationStarbaseDetailSync.syncStarbaseDetail(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify starbase details have been changed to downloaded version.
    int fuelCount = 0;
    for (int i = 0; i < testData.length; i++) {
      StarbaseDetail next = StarbaseDetail.get(syncAccount, testTime, (Long) testData[i][0]);
      Assert.assertEquals(testData[i][0], next.getItemID());
      Assert.assertEquals(testData[i][1], next.getState());
      Assert.assertEquals(testData[i][2], next.getStateTimestamp());
      Assert.assertEquals(testData[i][3], next.getOnlineTimestamp());
      Assert.assertEquals(testData[i][4], next.getUsageFlags());
      Assert.assertEquals(testData[i][5], next.getDeployFlags());
      Assert.assertEquals(testData[i][6], next.isAllowAllianceMembers());
      Assert.assertEquals(testData[i][7], next.isAllowCorporationMembers());
      Assert.assertEquals(testData[i][8], next.getUseStandingsFrom());
      Assert.assertEquals(testData[i][9], next.isOnAggressionEnabled());
      Assert.assertEquals(testData[i][10], next.getOnAggressionStanding());
      Assert.assertEquals(testData[i][11], next.isOnCorporationWarEnabled());
      Assert.assertEquals(testData[i][12], next.getOnCorporationWarStanding());
      Assert.assertEquals(testData[i][13], next.isOnStandingDropEnabled());
      Assert.assertEquals(testData[i][14], next.getOnStandingDropStanding());
      Assert.assertEquals(testData[i][15], next.isOnStatusDropEnabled());
      Assert.assertEquals(testData[i][16], next.getOnStatusDropStanding());
      Object[][] fuel = (Object[][]) testData[i][17];
      fuelCount += fuel.length;
      for (int j = 0; j < fuel.length; j++) {
        Fuel nextFuel = Fuel.get(syncAccount, testTime, (Long) testData[i][0], (Integer) fuel[j][0]);
        Assert.assertEquals(testData[i][0], nextFuel.getItemID());
        Assert.assertEquals(fuel[j][0], nextFuel.getTypeID());
        Assert.assertEquals(fuel[j][1], nextFuel.getQuantity());
      }
    }
    Assert.assertEquals(fuelCount, Fuel.getAll(syncAccount, testTime).size());

    // Verify tracker and container were updated properly.
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getStarbaseDetailExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseDetailStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseDetailDetail());
  }

  // Test skips update when already updated.
  @Test
  public void testCorporationStarbaseDetailSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing details.
    for (int i = 0; i < testData.length; i++) {
      StarbaseDetail next = new StarbaseDetail(
          (Long) testData[i][0] + 2, (Integer) testData[i][1] + 2, (Long) testData[i][2] + 2L, (Long) testData[i][3] + 2L, (Integer) testData[i][4] + 2,
          (Integer) testData[i][5] + 2, !(Boolean) testData[i][6], !(Boolean) testData[i][7], (Long) testData[i][8] + 2L, !(Boolean) testData[i][9],
          (Integer) testData[i][10] + 2, !(Boolean) testData[i][11], (Integer) testData[i][12] + 2, !(Boolean) testData[i][13], (Integer) testData[i][14] + 2,
          !(Boolean) testData[i][15], (Integer) testData[i][16] + 2);
      next.setup(syncAccount, testTime);
      Object[][] fuelData = (Object[][]) testData[i][17];
      for (int j = 0; j < fuelData.length; j++) {
        Fuel nextFuel = new Fuel((Long) testData[i][0] + 2, (Integer) fuelData[j][0] + 2, (Integer) fuelData[j][1] + 2);
        nextFuel.setup(syncAccount, testTime);
        nextFuel = CachedData.update(nextFuel);
      }
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setStarbaseDetailStatus(SyncState.UPDATED);
    tracker.setStarbaseDetailDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setStarbaseDetailExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationStarbaseDetailSync.syncStarbaseDetail(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify details unchanged.
    int fuelCount = 0;
    for (int i = 0; i < testData.length; i++) {
      StarbaseDetail next = StarbaseDetail.get(syncAccount, testTime, (Long) testData[i][0] + 2);
      Assert.assertEquals((Long) testData[i][0] + 2L, next.getItemID());
      Assert.assertEquals((Integer) testData[i][1] + 2, next.getState());
      Assert.assertEquals((Long) testData[i][2] + 2L, next.getStateTimestamp());
      Assert.assertEquals((Long) testData[i][3] + 2L, next.getOnlineTimestamp());
      Assert.assertEquals((Integer) testData[i][4] + 2, next.getUsageFlags());
      Assert.assertEquals((Integer) testData[i][5] + 2, next.getDeployFlags());
      Assert.assertEquals(!(Boolean) testData[i][6], next.isAllowAllianceMembers());
      Assert.assertEquals(!(Boolean) testData[i][7], next.isAllowCorporationMembers());
      Assert.assertEquals((Long) testData[i][8] + 2L, next.getUseStandingsFrom());
      Assert.assertEquals(!(Boolean) testData[i][9], next.isOnAggressionEnabled());
      Assert.assertEquals((Integer) testData[i][10] + 2, next.getOnAggressionStanding());
      Assert.assertEquals(!(Boolean) testData[i][11], next.isOnCorporationWarEnabled());
      Assert.assertEquals((Integer) testData[i][12] + 2, next.getOnCorporationWarStanding());
      Assert.assertEquals(!(Boolean) testData[i][13], next.isOnStandingDropEnabled());
      Assert.assertEquals((Integer) testData[i][14] + 2, next.getOnStandingDropStanding());
      Assert.assertEquals(!(Boolean) testData[i][15], next.isOnStatusDropEnabled());
      Assert.assertEquals((Integer) testData[i][16] + 2, next.getOnStatusDropStanding());
      Object[][] fuel = (Object[][]) testData[i][17];
      fuelCount += fuel.length;
      for (int j = 0; j < fuel.length; j++) {
        Fuel nextFuel = Fuel.get(syncAccount, testTime, (Long) testData[i][0] + 2L, (Integer) fuel[j][0] + 2);
        Assert.assertEquals((Long) testData[i][0] + 2L, nextFuel.getItemID());
        Assert.assertEquals((Integer) fuel[j][0] + 2, nextFuel.getTypeID());
        Assert.assertEquals((Integer) fuel[j][1] + 2, nextFuel.getQuantity());
      }
    }
    Assert.assertEquals(fuelCount, Fuel.getAll(syncAccount, testTime).size());

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getStarbaseDetailExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseDetailStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getStarbaseDetailDetail());
  }

}
