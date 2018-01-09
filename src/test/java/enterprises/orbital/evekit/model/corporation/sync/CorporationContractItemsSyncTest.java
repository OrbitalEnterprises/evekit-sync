package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.base.OrbitalProperties;
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
import enterprises.orbital.evekit.model.common.ContractItem;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.shared.IContractItem;

public class CorporationContractItemsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                         testDate;
  long                         prevDate;
  EveKitUserAccount            userAccount;
  SynchronizedEveAccount       syncAccount;
  Corporation                  container;
  CorporationSyncTracker       tracker;
  SynchronizerUtil             syncUtil;
  ICorporationAPI              mockServer;

  static Object[]              testContracts;
  static Map<Long, Object[][]> testContractItems = new HashMap<Long, Object[][]>();

  static {
    // Generate test contracts, each with one or more test contract items
    int size = 20 + TestBase.getRandomInt(20);
    testContracts = new Object[size];
    for (int i = 0; i < size; i++) {
      testContracts[i] = TestBase.getUniqueRandomLong();
      int numItems = 2 + TestBase.getRandomInt(10);
      Object[][] testItems = new Object[numItems][7];
      for (int j = 0; j < numItems; j++) {
        testItems[j][0] = testContracts[i];
        testItems[j][1] = TestBase.getUniqueRandomLong();
        testItems[j][2] = TestBase.getRandomInt(100000);
        testItems[j][3] = Math.abs(TestBase.getRandomLong());
        testItems[j][4] = (long) TestBase.getRandomInt(5);
        testItems[j][5] = TestBase.getRandomBoolean();
        testItems[j][6] = TestBase.getRandomBoolean();
      }
      testContractItems.put((Long) testContracts[i], testItems);
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

  public void setupOkMock(
                          boolean skipOdd)
    throws Exception {
    mockServer = EasyMock.createMock(ICorporationAPI.class);
    for (int i = 0; i < testContracts.length; i++) {
      final long contractID = (Long) testContracts[i];
      final Collection<IContractItem> items = new ArrayList<IContractItem>();
      Object[][] itemSet = testContractItems.get(contractID);
      for (int j = 0; j < itemSet.length; j++) {
        final Object[] instanceData = itemSet[j];
        items.add(new IContractItem() {

          @Override
          public long getRecordID() {
            return (Long) instanceData[1];
          }

          @Override
          public int getTypeID() {
            return (Integer) instanceData[2];
          }

          @Override
          public long getQuantity() {
            return (Long) instanceData[3];
          }

          @Override
          public long getRawQuantity() {
            return (Long) instanceData[4];
          }

          @Override
          public boolean isSingleton() {
            return (Boolean) instanceData[5];
          }

          @Override
          public boolean isIncluded() {
            return (Boolean) instanceData[6];
          }

        });
      }

      if (!skipOdd || (i % 2) == 0) {
        EasyMock.expect(mockServer.requestContractItems(contractID)).andReturn(items);
      }
    }

    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
    EasyMock.expectLastCall().anyTimes();
    mockServer.reset();
    EasyMock.expectLastCall().anyTimes();
  }

  public void compareWithTestData(
                                  long contractID,
                                  Object[] testData,
                                  ContractItem result,
                                  long fudge) {
    Assert.assertEquals(contractID, result.getContractID());
    Assert.assertEquals(testData[1], result.getRecordID());
    Assert.assertEquals(testData[2], result.getTypeID());
    Assert.assertEquals(((Long) testData[3]) + fudge, result.getQuantity());
    Assert.assertEquals(testData[4], result.getRawQuantity());
    Assert.assertEquals(testData[5], result.isSingleton());
    Assert.assertEquals(testData[6], result.isIncluded());
  }

  public ContractItem makeContractItem(
                                       long time,
                                       Object[] testData,
                                       long fudge)
    throws IOException {
    long contractID = (Long) testData[0];
    long recordID = (Long) testData[1];
    ContractItem next = new ContractItem(
        contractID, recordID, (Integer) testData[2], ((Long) testData[3]) + fudge, (Long) testData[4], (Boolean) testData[5], (Boolean) testData[6]);
    next.setup(syncAccount, time);
    return next;
  }

  @Test
  public void testContractItemsSyncUpdate() throws Exception {
    setupOkMock(false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate contracts for which to retrieve contract items
    for (int i = 0; i < testContracts.length; i++) {
      Contract nextContract = new Contract(
          (Long) testContracts[i], 0, 0, 0, 0, 0, 0, "", "", "", true, "", OrbitalProperties.getCurrentTime(), 0, 0, 0, 0, BigDecimal.ONE, BigDecimal.ONE,
          BigDecimal.ONE, BigDecimal.ONE, 0);
      nextContract.setup(syncAccount, testTime);
      nextContract = CachedData.update(nextContract);
    }

    // This sync requires contracts to already be processed
    tracker.setContractsStatus(SyncState.UPDATED);
    tracker.setContractsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setContractsExpiry(testDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationContractItemsSync.syncCorporationContractItems(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify contract items were created
    for (int i = 0; i < testContracts.length; i++) {
      long contractID = (Long) testContracts[i];
      Object[][] itemData = testContractItems.get(contractID);
      for (int j = 0; j < itemData.length; j++) {
        long recordID = (Long) itemData[j][1];
        ContractItem next = ContractItem.get(syncAccount, testTime, contractID, recordID);
        Assert.assertNotNull(next);
        compareWithTestData(contractID, itemData[j], next, 0);
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContractItemsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsDetail());
  }

  @Test
  public void testContractItemsSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock(false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate contracts for which to retrieve contract items
    for (int i = 0; i < testContracts.length; i++) {
      Contract nextContract = new Contract(
          (Long) testContracts[i], 0, 0, 0, 0, 0, 0, "", "", "", true, "", OrbitalProperties.getCurrentTime(), 0, 0, 0, 0, BigDecimal.ONE, BigDecimal.ONE,
          BigDecimal.ONE, BigDecimal.ONE, 0);
      nextContract.setup(syncAccount, testTime);
      nextContract = CachedData.update(nextContract);
    }

    // Populate existing items with tweak.
    for (long contractID : testContractItems.keySet()) {
      Object[][] testItems = testContractItems.get(contractID);
      for (int i = 0; i < testItems.length; i++) {
        CachedData.update(makeContractItem(testTime, testItems[i], 44L));
      }
    }

    // This sync requires contracts to already be processed
    tracker.setContractsStatus(SyncState.UPDATED);
    tracker.setContractsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setContractsExpiry(testDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationContractItemsSync.syncCorporationContractItems(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify contracts are changed, the sync always updates existing data.
    for (int i = 0; i < testContracts.length; i++) {
      long contractID = (Long) testContracts[i];
      Object[][] itemData = testContractItems.get(contractID);
      for (int j = 0; j < itemData.length; j++) {
        long recordID = (Long) itemData[j][1];
        ContractItem next = ContractItem.get(syncAccount, testTime, contractID, recordID);
        Assert.assertNotNull(next);
        compareWithTestData(contractID, itemData[j], next, 0);
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContractItemsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsDetail());
  }

  @Test
  public void testContractSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock(false);
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate contracts for which to retrieve contract items
    for (int i = 0; i < testContracts.length; i++) {
      Contract nextContract = new Contract(
          (Long) testContracts[i], 0, 0, 0, 0, 0, 0, "", "", "", true, "", OrbitalProperties.getCurrentTime(), 0, 0, 0, 0, BigDecimal.ONE, BigDecimal.ONE,
          BigDecimal.ONE, BigDecimal.ONE, 0);
      nextContract.setup(syncAccount, testTime);
    }

    // Populate existing items with tweak.
    for (long contractID : testContractItems.keySet()) {
      Object[][] testItems = testContractItems.get(contractID);
      for (int i = 0; i < testItems.length; i++) {
        CachedData.update(makeContractItem(testTime, testItems[i], 44L));
      }
    }

    // Set update as already performed
    tracker.setContractItemsStatus(SyncState.UPDATED);
    tracker.setContractItemsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setContractItemsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationContractItemsSync.syncCorporationContractItems(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify contracts are unchanged
    for (int i = 0; i < testContracts.length; i++) {
      long contractID = (Long) testContracts[i];
      Object[][] itemData = testContractItems.get(contractID);
      for (int j = 0; j < itemData.length; j++) {
        long recordID = (Long) itemData[j][1];
        ContractItem next = ContractItem.get(syncAccount, testTime, contractID, recordID);
        Assert.assertNotNull(next);
        compareWithTestData(contractID, itemData[j], next, 44L);
      }
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Corporation.getCorporation(syncAccount).getContractItemsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsDetail());
  }

  /**
   * Test that we skip updating contract items for very old contracts.
   * 
   * @throws Exception
   */
  @Test
  public void testContractSyncUpdateSkipOld() throws Exception {
    // Prepare mock
    setupOkMock(true);
    EasyMock.replay(mockServer);
    long testTime = OrbitalProperties.getCurrentTime();

    // Populate contracts for which to retrieve contract items. Set up odd numbered contracts
    // so that we don't retrieve their items because they are too old.
    for (int i = 0; i < testContracts.length; i++) {
      long dateIssued = OrbitalProperties.getCurrentTime();
      if (i % 2 == 1) {
        dateIssued -= 8 * 3600 * 24;
      }
      Contract nextContract = new Contract(
          (Long) testContracts[i], 0, 0, 0, 0, 0, 0, "", "", "", true, "", dateIssued, 0, 0, 0, 0, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
          BigDecimal.ONE, 0);
      nextContract.setup(syncAccount, testTime);
      nextContract = CachedData.update(nextContract);
    }

    // Populate existing items with tweak.
    for (long contractID : testContractItems.keySet()) {
      Object[][] testItems = testContractItems.get(contractID);
      for (int i = 0; i < testItems.length; i++) {
        CachedData.update(makeContractItem(testTime, testItems[i], 44L));
      }
    }

    // This sync requires contracts to already be processed
    tracker.setContractsStatus(SyncState.UPDATED);
    tracker.setContractsDetail(null);
    CorporationSyncTracker.updateTracker(tracker);
    container.setContractsExpiry(testDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CorporationContractItemsSync.syncCorporationContractItems(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify contracts are changed for even contract, and unchanged for odd contracts.
    for (int i = 0; i < testContracts.length; i++) {
      long contractID = (Long) testContracts[i];
      Object[][] itemData = testContractItems.get(contractID);
      if (i % 2 == 0) {
        for (int j = 0; j < itemData.length; j++) {
          long recordID = (Long) itemData[j][1];
          ContractItem next = ContractItem.get(syncAccount, testTime, contractID, recordID);
          Assert.assertNotNull(next);
          compareWithTestData(contractID, itemData[j], next, 0);
        }
      } else {
        for (int j = 0; j < itemData.length; j++) {
          long recordID = (Long) itemData[j][1];
          ContractItem next = ContractItem.get(syncAccount, testTime, contractID, recordID);
          Assert.assertNotNull(next);
          compareWithTestData(contractID, itemData[j], next, 44L);
        }
      }
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Corporation.getCorporation(syncAccount).getContractItemsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsStatus());
    Assert.assertNull(CorporationSyncTracker.getUnfinishedTracker(syncAccount).getContractItemsDetail());
  }

}
