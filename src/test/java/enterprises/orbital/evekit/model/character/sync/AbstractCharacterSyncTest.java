package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;

public class AbstractCharacterSyncTest extends SyncTestBase {

  // Concrete extension of abstract class so we can properly test.
  public class ClassUnderTest extends AbstractCharacterSync {

    @Override
    public boolean isRefreshed(CapsuleerSyncTracker tracker) {
      return false;
    }

    @Override
    public void updateStatus(CapsuleerSyncTracker tracker, SyncState status, String detail) {}

    @Override
    public void updateExpiry(Capsuleer container, long expiry) {}

    @Override
    public long getExpiryTime(Capsuleer container) {
      return -1;
    }

    @Override
    protected Object getServerData(ICharacterAPI charRequest) throws IOException {
      return null;
    }

    @Override
    protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICharacterAPI charRequest, Object data, List<CachedData> updates)
      throws IOException {
      return -1;
    }

  }

  // Local mocks and other objects
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true);

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  @Test
  public void testSynchronizerUtilReturnsNonZero() {
    ClassUnderTest cut = new ClassUnderTest();
    ICharacterAPI mockRequest = EasyMock.createMock(ICharacterAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, "SyncUnitTest", cut)).andReturn(SyncStatus.ERROR);
    EasyMock.replay(mockUtil);
    SyncStatus result = cut.syncData(1234L, syncAccount, mockUtil, mockRequest, "SyncUnitTest");
    Assert.assertEquals(SyncStatus.ERROR, result);
    EasyMock.verify(mockUtil);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testCharRequestIsError() throws Exception {
    int errorCode = 100;
    long testTime = 1234L;
    String errorText = "Error Text";
    String errorString = "Error " + errorCode + ": " + errorText;
    ClassUnderTest cut = new ClassUnderTest();
    ICharacterAPI mockRequest = EasyMock.createMock(ICharacterAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, "SyncUnitTest", cut)).andReturn(SyncStatus.CONTINUE);
    EasyMock.expect(mockRequest.isError()).andReturn(true);
    EasyMock.expect(mockRequest.getErrorCode()).andReturn(errorCode).times(2);
    EasyMock.expect(mockRequest.getErrorString()).andReturn(errorText);
    mockUtil.storeSynchResults(EasyMock.eq(testTime), EasyMock.eq(CapsuleerSyncTracker.class), EasyMock.eq(Capsuleer.class), EasyMock.eq(syncAccount),
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
  public void testCharRequestThrowsIOException() throws IOException {
    long testTime = 1234L;
    ClassUnderTest extended = new ClassUnderTest() {

      @Override
      protected Object getServerData(ICharacterAPI charRequest) throws IOException {
        throw new IOException("Test Exception");
      }

    };

    String errorString = "request failed with IO error";
    ICharacterAPI mockRequest = EasyMock.createMock(ICharacterAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, "SyncUnitTest", extended)).andReturn(SyncStatus.CONTINUE);
    mockUtil.storeSynchResults(EasyMock.eq(testTime), EasyMock.eq(CapsuleerSyncTracker.class), EasyMock.eq(Capsuleer.class), EasyMock.eq(syncAccount),
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
    ICharacterAPI mockRequest = EasyMock.createMock(ICharacterAPI.class);
    SynchronizerUtil mockUtil = EasyMock.createMock(SynchronizerUtil.class);
    EasyMock.expect(mockUtil.preSyncCheck(CapsuleerSyncTracker.class, Capsuleer.class, syncAccount, "SyncUnitTest", cut)).andReturn(SyncStatus.CONTINUE);
    EasyMock.expect(mockRequest.isError()).andReturn(false);
    mockUtil.storeSynchResults(EasyMock.eq(testTime), EasyMock.eq(CapsuleerSyncTracker.class), EasyMock.eq(Capsuleer.class), EasyMock.eq(syncAccount),
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
