package enterprises.orbital.evekit.model.corporation.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import enterprises.orbital.evekit.model.corporation.CorporationSheet;
import enterprises.orbital.evekit.model.corporation.Division;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.ICorporationSheet;
import enterprises.orbital.evexmlapi.crp.IDivision;

public class CorporationSheetSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  static Object[][]      testData;

  static {
    // generate random data
    testData = new Object[1][24];

    testData[0][0] = TestBase.getRandomLong();
    testData[0][1] = TestBase.getRandomText(50);
    testData[0][2] = TestBase.getRandomLong();
    testData[0][3] = TestBase.getRandomText(50);
    testData[0][4] = TestBase.getRandomLong();
    testData[0][5] = TestBase.getRandomText(50);
    testData[0][6] = TestBase.getRandomText(300);
    testData[0][7] = TestBase.getRandomInt();
    testData[0][8] = TestBase.getRandomInt();
    testData[0][9] = TestBase.getRandomInt();
    testData[0][10] = TestBase.getRandomInt();
    testData[0][11] = TestBase.getRandomInt();
    testData[0][12] = TestBase.getRandomInt();
    testData[0][13] = TestBase.getRandomInt();
    testData[0][14] = TestBase.getRandomInt();
    testData[0][15] = TestBase.getRandomInt();
    testData[0][16] = TestBase.getRandomInt();
    testData[0][17] = TestBase.getRandomLong();
    testData[0][18] = TestBase.getRandomText(50);
    testData[0][19] = TestBase.getRandomDouble(20);
    testData[0][20] = TestBase.getRandomText(10);
    testData[0][21] = TestBase.getRandomText(200);

    // Generate random Divisions
    int numDivisions = 8 + TestBase.getRandomInt(3);
    Object[][] divisions = new Object[numDivisions][2];
    for (int i = 0; i < numDivisions; i++) {
      divisions[i][0] = TestBase.getUniqueRandomInteger();
      divisions[i][1] = TestBase.getRandomText(50);
    }
    testData[0][22] = divisions;

    // Generate random wallet Divisions
    int numWalletDivisions = 8 + TestBase.getRandomInt(3);
    Object[][] walletDivisions = new Object[numWalletDivisions][2];
    for (int i = 0; i < numWalletDivisions; i++) {
      walletDivisions[i][0] = TestBase.getUniqueRandomInteger();
      walletDivisions[i][1] = TestBase.getRandomText(50);
    }
    testData[0][23] = walletDivisions;

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

  public void findDivisionInTestData(Division div, boolean wallet) {
    Object[][] divs = (Object[][]) testData[0][wallet ? 23 : 22];
    for (Object[] next : divs) {
      if (div.getAccountKey() == ((Integer) next[0]) && div.getDescription().equals(next[1])) { return; }
    }
    Assert.assertTrue("Failed to find div with key " + div.getAccountKey(), false);
  }

  public void compareCorporationSheetWithTestData(CorporationSheet sheet) {
    Assert.assertEquals(sheet.getAllianceID(), (long) ((Long) testData[0][0]));
    Assert.assertEquals(sheet.getAllianceName(), testData[0][1]);
    Assert.assertEquals(sheet.getCeoID(), (long) ((Long) testData[0][2]));
    Assert.assertEquals(sheet.getCeoName(), testData[0][3]);
    Assert.assertEquals(sheet.getCorporationID(), (long) ((Long) testData[0][4]));
    Assert.assertEquals(sheet.getCorporationName(), testData[0][5]);
    Assert.assertEquals(sheet.getDescription(), testData[0][6]);
    Assert.assertEquals(sheet.getLogoColor1(), (int) ((Integer) testData[0][7]));
    Assert.assertEquals(sheet.getLogoColor2(), (int) ((Integer) testData[0][8]));
    Assert.assertEquals(sheet.getLogoColor3(), (int) ((Integer) testData[0][9]));
    Assert.assertEquals(sheet.getLogoGraphicID(), (int) ((Integer) testData[0][10]));
    Assert.assertEquals(sheet.getLogoShape1(), (int) ((Integer) testData[0][11]));
    Assert.assertEquals(sheet.getLogoShape2(), (int) ((Integer) testData[0][12]));
    Assert.assertEquals(sheet.getLogoShape3(), (int) ((Integer) testData[0][13]));
    Assert.assertEquals(sheet.getMemberCount(), (int) ((Integer) testData[0][14]));
    Assert.assertEquals(sheet.getMemberLimit(), (int) ((Integer) testData[0][15]));
    Assert.assertEquals(sheet.getShares(), (int) ((Integer) testData[0][16]));
    Assert.assertEquals(sheet.getStationID(), (long) ((Long) testData[0][17]));
    Assert.assertEquals(sheet.getStationName(), testData[0][18]);
    Assert.assertEquals(sheet.getTaxRate(), ((Double) testData[0][19]), 0.001);
    Assert.assertEquals(sheet.getTicker(), testData[0][20]);
    Assert.assertEquals(sheet.getUrl(), testData[0][21]);
  }

  public Collection<Division> makeDivisionObject(long time, Object[] instanceData, boolean wallet, Integer accountKey) throws Exception {
    List<Division> result = new ArrayList<Division>();
    Object[][] divs = (Object[][]) instanceData[wallet ? 23 : 22];
    for (Object[] next : divs) {
      Division nextDiv = new Division(wallet, (Integer) next[0] + (accountKey != null ? accountKey.intValue() : 0), (String) next[1]);
      nextDiv.setup(syncAccount, time);
      result.add(nextDiv);
    }

    return result;
  }

  public CorporationSheet makeCorporationSheetObject(long time, Object[] instanceData) throws Exception {
    CorporationSheet sheet = new CorporationSheet(
        (Long) instanceData[0], (String) instanceData[1], ((Long) instanceData[2]), (String) instanceData[3], (Long) instanceData[4], (String) instanceData[5],
        (String) instanceData[6], (Integer) instanceData[7], (Integer) instanceData[8], (Integer) instanceData[9], (Integer) instanceData[10],
        (Integer) instanceData[11], (Integer) instanceData[12], (Integer) instanceData[13], (Integer) instanceData[14], (Integer) instanceData[15],
        (Integer) instanceData[16], (Long) instanceData[17], (String) instanceData[18], (Double) instanceData[19], (String) instanceData[20],
        (String) instanceData[21]);
    sheet.setup(syncAccount, time);
    return sheet;
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    final Object[] instanceData = testData[0];
    ICorporationSheet sheet = new ICorporationSheet() {

      @Override
      public long getAllianceID() {
        return ((Long) instanceData[0]);
      }

      @Override
      public String getAllianceName() {
        return (String) instanceData[1];
      }

      @Override
      public long getCeoID() {
        return ((Long) instanceData[2]);
      }

      @Override
      public String getCeoName() {
        return (String) instanceData[3];
      }

      @Override
      public long getCorporationID() {
        return ((Long) instanceData[4]);
      }

      @Override
      public String getCorporationName() {
        return (String) instanceData[5];
      }

      @Override
      public String getDescription() {
        return (String) instanceData[6];
      }

      @Override
      public Collection<IDivision> getDivisions() {
        Object[][] divs = (Object[][]) instanceData[22];
        final Set<IDivision> vals = new HashSet<IDivision>();
        for (int i = 0; i < divs.length; i++) {
          final Object[] data = divs[i];
          IDivision next = new IDivision() {

            @Override
            public int getAccountKey() {
              return ((Integer) data[0]);
            }

            @Override
            public String getDescription() {
              return (String) data[1];
            }

          };
          vals.add(next);
        }

        return vals;
      }

      @Override
      public int getLogoColor1() {
        return ((Integer) instanceData[7]);
      }

      @Override
      public int getLogoColor2() {
        return ((Integer) instanceData[8]);
      }

      @Override
      public int getLogoColor3() {
        return ((Integer) instanceData[9]);
      }

      @Override
      public int getLogoGraphicID() {
        return ((Integer) instanceData[10]);
      }

      @Override
      public int getLogoShape1() {
        return ((Integer) instanceData[11]);
      }

      @Override
      public int getLogoShape2() {
        return ((Integer) instanceData[12]);
      }

      @Override
      public int getLogoShape3() {
        return ((Integer) instanceData[13]);
      }

      @Override
      public int getMemberCount() {
        return ((Integer) instanceData[14]);
      }

      @Override
      public int getMemberLimit() {
        return ((Integer) instanceData[15]);
      }

      @Override
      public int getShares() {
        return ((Integer) instanceData[16]);
      }

      @Override
      public long getStationID() {
        return ((Long) instanceData[17]);
      }

      @Override
      public String getStationName() {
        return (String) instanceData[18];
      }

      @Override
      public double getTaxRate() {
        return ((Double) instanceData[19]);
      }

      @Override
      public String getTicker() {
        return (String) instanceData[20];
      }

      @Override
      public String getUrl() {
        return (String) instanceData[21];
      }

      @Override
      public Collection<IDivision> getWalletDivisions() {
        Object[][] divs = (Object[][]) instanceData[23];
        final Set<IDivision> vals = new HashSet<IDivision>();
        for (int i = 0; i < divs.length; i++) {
          final Object[] data = divs[i];
          IDivision next = new IDivision() {

            @Override
            public int getAccountKey() {
              return ((Integer) data[0]);
            }

            @Override
            public String getDescription() {
              return (String) data[1];
            }

          };
          vals.add(next);
        }

        return vals;
      }

    };

    EasyMock.expect(mockServer.requestCorporationSheet()).andReturn(sheet);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with new corporation sheet
  @Test
  public void testCorporationSheetSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationSheetSync.syncCorporationSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify the following elements were added correctly:
    // CorporationSheet
    CorporationSheet sheet = CorporationSheet.get(syncAccount, testTime);
    Assert.assertNotNull(sheet);
    compareCorporationSheetWithTestData(sheet);
    for (Division i : Division.getAllByType(syncAccount, testTime, false)) {
      findDivisionInTestData(i, false);
    }
    for (Division i : Division.getAllByType(syncAccount, testTime, true)) {
      findDivisionInTestData(i, true);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getCorporationSheetExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetDetail());
  }

  // Test update with corporation sheet already populated
  @Test
  public void testCorporationSheetSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate corporation sheet
    CachedData.update(makeCorporationSheetObject(testTime, testData[0]));
    for (Division i : makeDivisionObject(testTime, testData[0], false, null)) {
      CachedData.update(i);
    }
    for (Division i : makeDivisionObject(testTime, testData[0], true, null)) {
      CachedData.update(i);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationSheetSync.syncCorporationSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify the following elements are unchanged:
    // CorporationSheet
    CorporationSheet sheet = CorporationSheet.get(syncAccount, testTime);
    Assert.assertNotNull(sheet);
    compareCorporationSheetWithTestData(sheet);
    for (Division i : Division.getAllByType(syncAccount, testTime, false)) {
      findDivisionInTestData(i, false);
    }
    for (Division i : Division.getAllByType(syncAccount, testTime, true)) {
      findDivisionInTestData(i, true);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getCorporationSheetExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCorporationSheetSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing corporation sheet
    CachedData.update(makeCorporationSheetObject(testTime, testData[0]));
    for (Division i : makeDivisionObject(testTime, testData[0], false, null)) {
      CachedData.update(i);
    }
    for (Division i : makeDivisionObject(testTime, testData[0], true, null)) {
      CachedData.update(i);
    }

    // Set the tracker as already updated and populate the container
    tracker.setCorporationSheetStatus(SyncState.UPDATED);
    tracker.setCorporationSheetDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setCorporationSheetExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationSheetSync.syncCorporationSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify the following elements are unchanged:
    // CorporationSheet
    CorporationSheet sheet = CorporationSheet.get(syncAccount, testTime);
    Assert.assertNotNull(sheet);
    compareCorporationSheetWithTestData(sheet);
    for (Division i : Division.getAllByType(syncAccount, testTime, false)) {
      findDivisionInTestData(i, false);
    }
    for (Division i : Division.getAllByType(syncAccount, testTime, true)) {
      findDivisionInTestData(i, true);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getCorporationSheetExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetDetail());
  }

  // Test changed divisions are removed.
  @Test
  public void testCorporationSheetChangedDivsRemoved() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing corporation sheet
    CorporationSheet existing = makeCorporationSheetObject(testTime, testData[0]);
    // Tweak division keys to be different from test data
    Collection<Division> divs = makeDivisionObject(testTime, testData[0], false, new Integer(1));
    divs.addAll(makeDivisionObject(testTime, testData[0], true, new Integer(1)));
    for (Division next : divs) {
      next = CachedData.update(next);
    }
    existing = CachedData.update(existing);

    // Perform the sync
    SyncStatus syncOutcome = CorporationSheetSync.syncCorporationSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify the update matches the test data
    CorporationSheet sheet = CorporationSheet.get(syncAccount, testTime);
    Assert.assertNotNull(sheet);
    compareCorporationSheetWithTestData(sheet);
    for (Division i : Division.getAllByType(syncAccount, testTime, false)) {
      findDivisionInTestData(i, false);
    }
    for (Division i : Division.getAllByType(syncAccount, testTime, true)) {
      findDivisionInTestData(i, true);
    }

    // Verify the number of divisions and wallet divisions corresponds to the size of the test data.
    Assert.assertEquals(Division.getAllByType(syncAccount, testTime, false).size(), ((Object[][]) testData[0][22]).length);
    Assert.assertEquals(Division.getAllByType(syncAccount, testTime, true).size(), ((Object[][]) testData[0][23]).length);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getCorporationSheetExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getCorporationSheetDetail());
  }

}
