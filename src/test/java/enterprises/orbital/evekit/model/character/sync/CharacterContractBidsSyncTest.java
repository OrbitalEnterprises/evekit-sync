package enterprises.orbital.evekit.model.character.sync;

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
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.ContractBid;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IContractBid;

public class CharacterContractBidsSyncTest extends SyncTestBase {

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

  static {
    // Generate test data
    int size = 20 + TestBase.getRandomInt(20);
    testData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getUniqueRandomLong();
      testData[i][2] = TestBase.getRandomLong();
      testData[i][3] = Math.abs(TestBase.getRandomLong());
      testData[i][4] = TestBase.getRandomDouble(500000);
    }
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

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    Collection<IContractBid> bids_one = new ArrayList<IContractBid>();
    Collection<IContractBid> bids_two = new ArrayList<IContractBid>();
    for (int i = 0; i < testData.length; i++) {
      final Object[] instanceData = testData[i];
      Collection<IContractBid> tgt = (i % 2 == 0) ? bids_one : bids_two;
      tgt.add(new IContractBid() {

        @Override
        public long getBidID() {
          return (Long) instanceData[0];
        }

        @Override
        public long getContractID() {
          return (Long) instanceData[1];
        }

        @Override
        public long getBidderID() {
          return (Long) instanceData[2];
        }

        @Override
        public Date getDateBid() {
          return new Date((Long) instanceData[3]);
        }

        @Override
        public BigDecimal getAmount() {
          return new BigDecimal((Double) instanceData[4]).setScale(2, RoundingMode.HALF_UP);
        }
      });
    }

    EasyMock.expect(mockServer.requestContractBids()).andReturn(bids_one);
    EasyMock.expect(mockServer.requestContractBids()).andReturn(bids_two);
    EasyMock.expect(mockServer.requestContractBids()).andReturn(new ArrayList<IContractBid>());
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
    EasyMock.expectLastCall().anyTimes();
  }

  public void compareWithTestData(Object[] testData, ContractBid result, long fudge) {
    Assert.assertEquals(testData[0], result.getBidID());
    Assert.assertEquals(testData[1], result.getContractID());
    Assert.assertEquals(((Long) testData[2]) + fudge, result.getBidderID());
    Assert.assertEquals(testData[3], result.getDateBid());
    Assert.assertEquals((new BigDecimal((Double) testData[4])).setScale(2, RoundingMode.HALF_UP), result.getAmount());
  }

  public ContractBid makeContractBid(long time, Object[] testData, long fudge) throws IOException {
    long bidID = (Long) testData[0];
    long contractID = (Long) testData[1];
    ContractBid next = new ContractBid(
        bidID, contractID, ((Long) testData[2]) + fudge, (Long) testData[3], (new BigDecimal((Double) testData[4])).setScale(2, RoundingMode.HALF_UP));
    next.setup(syncAccount, time);
    return next;
  }

  @Test
  public void testContractBidSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterContractBidsSync.syncCharacterContractBids(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    for (int i = 0; i < testData.length; i++) {
      long bidID = (Long) testData[i][0];
      long contractID = (Long) testData[i][1];
      ContractBid next = ContractBid.get(syncAccount, testTime, contractID, bidID);
      Assert.assertNotNull(next);
      compareWithTestData(testData[i], next, 0);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getContractBidsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContractBidsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContractBidsDetail());
  }

  @Test
  public void testContractBidSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contract bids
    for (int i = 0; i < testData.length; i++) {
      ContractBid next = makeContractBid(testTime, testData[i], 44L);
      next = CachedData.update(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterContractBidsSync.syncCharacterContractBids(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify contract bids are changed, the sync always updates existing data.
    for (int i = 0; i < testData.length; i++) {
      long bidID = (Long) testData[i][0];
      long contractID = (Long) testData[i][1];
      ContractBid next = ContractBid.get(syncAccount, testTime, contractID, bidID);
      Assert.assertNotNull(next);
      compareWithTestData(testData[i], next, 0L);
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getContractBidsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContractBidsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContractBidsDetail());
  }

  @Test
  public void testContractBidSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing contract bids
    for (int i = 0; i < testData.length; i++) {
      ContractBid next = makeContractBid(testTime, testData[i], 44L);
      next = CachedData.update(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setContractBidsStatus(SyncState.UPDATED);
    tracker.setContractBidsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setContractBidsExpiry(prevDate);
    container = CachedData.update(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterContractBidsSync.syncCharacterContractBids(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify contracts unchanged
    for (int i = 0; i < testData.length; i++) {
      long bidID = (Long) testData[i][0];
      long contractID = (Long) testData[i][1];
      ContractBid next = ContractBid.get(syncAccount, testTime, contractID, bidID);
      Assert.assertNotNull(next);
      compareWithTestData(testData[i], next, 44L);
    }

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getContractBidsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContractBidsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getContractBidsDetail());
  }

}
