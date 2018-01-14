package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;

public class AbstractCorporationSyncTest extends SyncTestBase {

  // Concrete extension of abstract class so we can properly test.
  public class ClassUnderTest extends AbstractCorporationSync {

    @Override
    public boolean isRefreshed(CorporationSyncTracker tracker) {
      return false;
    }

    @Override
    public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {}

    @Override
    public void updateExpiry(Corporation container, long expiry) {}

    @Override
    public long getExpiryTime(Corporation container) {
      return -1;
    }

    @Override
    protected Object getServerData(ICorporationAPI charRequest) throws IOException {
      return null;
    }

    @Override
    protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI charRequest, Object data, List<CachedData> updates)
      throws IOException {
      return -1;
    }

  }

  // Local mocks and other objects
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Corporation            container;
  CorporationSyncTracker tracker;
  SynchronizerUtil       syncUtil;
  ICorporationAPI        mockServer;

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true);

    // Prepare a test sync tracker
    tracker = CorporationSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Corporation.getOrCreateCorporation(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  @Test
  public void testSynchronizerUtilReturnsNonZero() {
    ClassUnderTest cut = new ClassUnderTest();
    ICorporationAPI mockRequest = EasyMock.createMock(ICorporationAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CorporationSyncTracker.class, Corporation.class, syncAccount, "SyncUnitTest", cut)).andReturn(SyncStatus.ERROR);
    EasyMock.replay(mockUtil);
    SyncStatus result = cut.syncData(1234L, syncAccount, mockUtil, mockRequest, "SyncUnitTest");
    Assert.assertEquals(SyncStatus.ERROR, result);
    EasyMock.verify(mockUtil);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testCorpRequestIsError() throws Exception {
    int errorCode = 100;
    long testTime = 1234L;
    String errorText = "Error Text";
    String errorString = "Error " + errorCode + ": " + errorText;
    ClassUnderTest cut = new ClassUnderTest();
    ICorporationAPI mockRequest = EasyMock.createMock(ICorporationAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CorporationSyncTracker.class, Corporation.class, syncAccount, "SyncUnitTest", cut)).andReturn(SyncStatus.CONTINUE);
    EasyMock.expect(mockRequest.isError()).andReturn(true);
    EasyMock.expect(mockRequest.getErrorCode()).andReturn(errorCode).times(2);
    EasyMock.expect(mockRequest.getErrorString()).andReturn(errorText);
    mockUtil.storeSynchResults(EasyMock.eq(testTime), EasyMock.eq(CorporationSyncTracker.class), EasyMock.eq(Corporation.class), EasyMock.eq(syncAccount),
                               EasyMock.eq(SyncTracker.SyncState.SYNC_ERROR), EasyMock.eq(errorString), EasyMock.eq(-1L), EasyMock.eq("SyncUnitTest"),
                               (List<CachedData>) EasyMock.notNull(), EasyMock.eq(cut));
    EasyMock.replay(mockUtil);
    EasyMock.replay(mockRequest);
    SyncStatus result = cut.syncData(testTime, syncAccount, mockUtil, mockRequest, "SyncUnitTest");
    Assert.assertEquals(SyncStatus.DONE, result);
    EasyMock.verify(mockUtil);
    EasyMock.verify(mockRequest);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testCorpRequestThrowsIOException() throws IOException {
    long testTime = 1234L;
    ClassUnderTest extended = new ClassUnderTest() {

      @Override
      protected Object getServerData(ICorporationAPI charRequest) throws IOException {
        throw new IOException("Test Exception");
      }

    };

    String errorString = "request failed with IO error";
    ICorporationAPI mockRequest = EasyMock.createMock(ICorporationAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CorporationSyncTracker.class, Corporation.class, syncAccount, "SyncUnitTest", extended))
        .andReturn(SyncStatus.CONTINUE);
    mockUtil.storeSynchResults(EasyMock.eq(testTime), EasyMock.eq(CorporationSyncTracker.class), EasyMock.eq(Corporation.class), EasyMock.eq(syncAccount),
                               EasyMock.eq(SyncTracker.SyncState.SYNC_ERROR), EasyMock.eq(errorString), EasyMock.eq(-1L), EasyMock.eq("SyncUnitTest"),
                               (List<CachedData>) EasyMock.notNull(), EasyMock.eq(extended));
    EasyMock.replay(mockUtil);
    EasyMock.replay(mockRequest);
    SyncStatus result = extended.syncData(testTime, syncAccount, mockUtil, mockRequest, "SyncUnitTest");
    Assert.assertEquals(SyncStatus.DONE, result);
    EasyMock.verify(mockUtil);
    EasyMock.verify(mockRequest);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testStoreSyncThrowsIOException() throws IOException {
    long testTime = 1234L;
    ClassUnderTest cut = new ClassUnderTest();
    ICorporationAPI mockRequest = EasyMock.createMock(ICorporationAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CorporationSyncTracker.class, Corporation.class, syncAccount, "SyncUnitTest", cut)).andReturn(SyncStatus.CONTINUE);
    EasyMock.expect(mockRequest.isError()).andReturn(false);
    mockUtil.storeSynchResults(EasyMock.eq(testTime), EasyMock.eq(CorporationSyncTracker.class), EasyMock.eq(Corporation.class), EasyMock.eq(syncAccount),
                               EasyMock.eq(SyncTracker.SyncState.UPDATED), (String) EasyMock.isNull(), EasyMock.eq(-1L), EasyMock.eq("SyncUnitTest"),
                               (List<CachedData>) EasyMock.notNull(), EasyMock.eq(cut));
    EasyMock.expectLastCall().andThrow(new IOException("Test Exception"));
    EasyMock.replay(mockUtil);
    EasyMock.replay(mockRequest);
    SyncStatus result = cut.syncData(testTime, syncAccount, mockUtil, mockRequest, "SyncUnitTest");
    Assert.assertEquals(SyncStatus.ERROR, result);
    EasyMock.verify(mockUtil);
    EasyMock.verify(mockRequest);
  }

}
