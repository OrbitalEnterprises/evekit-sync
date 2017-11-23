package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import enterprises.orbital.evekit.model.common.Contract;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IContract;

public class CorporationContractsSyncTest extends SyncTestBase {

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
    // Generate test data
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][22];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomLong();
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = TestBase.getRandomLong();
      testData[i][4] = TestBase.getRandomLong();
      testData[i][5] = TestBase.getRandomLong();
      testData[i][6] = TestBase.getRandomLong();
      testData[i][7] = TestBase.getRandomText(50);
      testData[i][8] = TestBase.getRandomText(50);
      testData[i][9] = TestBase.getRandomText(50);
      testData[i][10] = TestBase.getRandomBoolean();
      testData[i][11] = TestBase.getRandomText(30);
      testData[i][12] = Math.abs(TestBase.getRandomLong());
      testData[i][13] = Math.abs(TestBase.getRandomLong());
      testData[i][14] = Math.abs(TestBase.getRandomLong());
      testData[i][15] = TestBase.getRandomInt(30);
      testData[i][16] = Math.abs(TestBase.getRandomLong());
      testData[i][17] = TestBase.getRandomDouble(20000);
      testData[i][18] = TestBase.getRandomDouble(20000);
      testData[i][19] = TestBase.getRandomDouble(300000);
      testData[i][20] = TestBase.getRandomDouble(500000);
      testData[i][21] = TestBase.getRandomDouble(500000);
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

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    Collection<IContract> contracts = new ArrayList<IContract>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      contracts.add(new IContract() {

        @Override
        public long getContractID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getIssuerID() {
          return (Long) instanceData[1];
        }

        @Override
        public long getIssuerCorpID() {
          return (Long) instanceData[2];
        }

        @Override
        public long getAssigneeID() {
          return (Long) instanceData[3];
        }

        @Override
        public long getAcceptorID() {
          return (Long) instanceData[4];
        }

        @Override
        public long getStartStationID() {
          return (Long) instanceData[5];
        }

        @Override
        public long getEndStationID() {
          return (Long) instanceData[6];
        }

        @Override
        public String getType() {
          return (String) instanceData[7];
        }

        @Override
        public String getStatus() {
          return (String) instanceData[8];
        }

        @Override
        public String getTitle() {
          return (String) instanceData[9];
        }

        @Override
        public boolean isForCorp() {
          return (Boolean) instanceData[10];
        }

        @Override
        public String getAvailability() {
          return (String) instanceData[11];
        }

        @Override
        public Date getDateIssued() {
          return new Date((Long) instanceData[12]);
        }

        @Override
        public Date getDateExpired() {
          return new Date((Long) instanceData[13]);
        }

        @Override
        public Date getDateAccepted() {
          return new Date((Long) instanceData[14]);
        }

        @Override
        public int getNumDays() {
          return (Integer) instanceData[15];
        }

        @Override
        public Date getDateCompleted() {
          return new Date((Long) instanceData[16]);
        }

        @Override
        public BigDecimal getPrice() {
          return new BigDecimal((Double) instanceData[17]).setScale(2, RoundingMode.HALF_DOWN);
        }

        @Override
        public BigDecimal getReward() {
          return new BigDecimal((Double) instanceData[18]).setScale(2, RoundingMode.HALF_DOWN);
        }

        @Override
        public BigDecimal getCollateral() {
          return new BigDecimal((Double) instanceData[19]).setScale(2, RoundingMode.HALF_DOWN);
        }

        @Override
        public BigDecimal getBuyout() {
          return new BigDecimal((Double) instanceData[20]).setScale(2, RoundingMode.HALF_DOWN);
        }

        @Override
        public double getVolume() {
          return (Double) instanceData[21];
        }
      });
    }

    EasyMock.expect(mockServer.requestContracts()).andReturn(contracts);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  public void compareWithTestData(
                                  Object[] testData,
                                  Contract result,
                                  String fudge) {
    Assert.assertEquals(testData[0], result.getContractID());
    Assert.assertEquals(testData[1], result.getIssuerID());
    Assert.assertEquals(testData[2], result.getIssuerCorpID());
    Assert.assertEquals(testData[3], result.getAssigneeID());
    Assert.assertEquals(testData[4], result.getAcceptorID());
    Assert.assertEquals(testData[5], result.getStartStationID());
    Assert.assertEquals(testData[6], result.getEndStationID());
    Assert.assertEquals(testData[7], result.getType());
    Assert.assertEquals(testData[8], result.getStatus());
    Assert.assertEquals(((String) testData[9]) + (fudge != null ? fudge : ""), result.getTitle());
    Assert.assertEquals(testData[10], result.isForCorp());
    Assert.assertEquals(testData[11], result.getAvailability());
    Assert.assertEquals(testData[12], result.getDateIssued());
    Assert.assertEquals(testData[13], result.getDateExpired());
    Assert.assertEquals(testData[14], result.getDateAccepted());
    Assert.assertEquals(testData[15], result.getNumDays());
    Assert.assertEquals(testData[16], result.getDateCompleted());
    Assert.assertEquals((new BigDecimal((Double) testData[17])).setScale(2, RoundingMode.HALF_UP), result.getPrice());
    Assert.assertEquals((new BigDecimal((Double) testData[18])).setScale(2, RoundingMode.HALF_UP), result.getReward());
    Assert.assertEquals((new BigDecimal((Double) testData[19])).setScale(2, RoundingMode.HALF_UP), result.getCollateral());
    Assert.assertEquals((new BigDecimal((Double) testData[20])).setScale(2, RoundingMode.HALF_UP), result.getBuyout());
    Assert.assertEquals(testData[21], result.getVolume());
  }

  public Contract makeContract(
                               long time,
                               Object[] testData,
                               String fudge)
    throws IOException {
    long contractID = (Long) testData[0];
    Contract next = new Contract(
        contractID, (Long) testData[1], (Long) testData[2], (Long) testData[3], (Long) testData[4], (Long) testData[5], (Long) testData[6],
        (String) testData[7], (String) testData[8], ((String) testData[9]) + (fudge != null ? fudge : ""), (Boolean) testData[10], (String) testData[11],
        (Long) testData[12], (Long) testData[13], (Long) testData[14], (Integer) testData[15], (Long) testData[16],
        (new BigDecimal((Double) testData[17])).setScale(2, RoundingMode.HALF_UP), (new BigDecimal((Double) testData[18])).setScale(2, RoundingMode.HALF_UP),
        (new BigDecimal((Double) testData[19])).setScale(2, RoundingMode.HALF_UP), (new BigDecimal((Double) testData[20])).setScale(2, RoundingMode.HALF_UP),
        (Double) testData[21]);
    next.setup(syncAccount, time);
    return next;
  }

  @Test
  public void testContractSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CorporationContractsSync.syncCorporationContracts(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    for (int i = 0; i < testData.length; i++) {
      long contractID = (Long) testData[i][0];
      Contract next = Contract.get(syncAccount, testTime, contractID);
      Assert.assertNotNull(next);
      compareWithTestData(testData[i], next, null);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContractsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractsDetail());
  }

  @Test
  public void testContractSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contracts
    for (int i = 0; i < testData.length; i++) {
      Contract next = makeContract(testTime, testData[i], "foo");
      next = CachedData.updateData(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CorporationContractsSync.syncCorporationContracts(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify contracts are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      long contractID = (Long) testData[i][0];
      Contract next = Contract.get(syncAccount, testTime, contractID);
      Assert.assertNotNull(next);
      compareWithTestData(testData[i], next, null);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContractsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractsDetail());
  }

  @Test
  public void testContractSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contacts
    for (int i = 0; i < testData.length; i++) {
      Contract next = makeContract(testTime, testData[i], "foo");
      next = CachedData.updateData(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setContractsStatus(SyncState.UPDATED);
    tracker.setContractsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setContractsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationContractsSync.syncCorporationContracts(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify contracts unchanged
    for (int i = 0; i < testData.length; i++) {
      long contractID = (Long) testData[i][0];
      Contract next = Contract.get(syncAccount, testTime, contractID);
      Assert.assertNotNull(next);
      compareWithTestData(testData[i], next, "foo");
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getContractsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractsDetail());
  }

}
