package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.easymock.EasyMock;
import org.hsqldb.rights.User;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.shared.IAsset;

public class CharacterAssetsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  User                   testUser;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  Random                 gen         = new Random(OrbitalProperties.getCurrentTime());
  int                    max         = 1 << 20;
  Set<Integer>           usedItemIds = new HashSet<Integer>();

  public long getUnusedItemID() {
    int next = gen.nextInt(max) + 1;
    while (usedItemIds.contains(next)) {
      next = gen.nextInt(max) + 1;
    }
    usedItemIds.add(next);

    return next;
  }

  // int flag
  // long itemID
  // long locationID
  // long quantity
  // boolean singleton
  // int typeID
  // long rawQuantity
  Object[][] testData = new Object[][] {
      {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }, {
          gen.nextInt(max) + 1, getUnusedItemID(), (long) gen.nextInt(max) + 1, (long) gen.nextInt(max) + 1, gen.nextBoolean(), gen.nextInt(max) + 1,
          (long) gen.nextInt(max) + 1
      }
  };

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true, 1234, "abcd", 5678, "charname", 8765, "corpname");
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IAsset makeAsset(
                          final Object[] instanceData) {
    return new IAsset() {
      private final Collection<IAsset> contained = new ArrayList<IAsset>();

      @Override
      public Collection<IAsset> getContainedAssets() {
        return contained;
      }

      @Override
      public int getFlag() {
        return (Integer) instanceData[0];
      }

      @Override
      public long getItemID() {
        return (Long) instanceData[1];
      }

      @Override
      public long getLocationID() {
        return (Long) instanceData[2];
      }

      @Override
      public long getQuantity() {
        return (Long) instanceData[3];
      }

      @Override
      public boolean isSingleton() {
        return (Boolean) instanceData[4];
      }

      @Override
      public int getTypeID() {
        return (Integer) instanceData[5];
      }

      @Override
      public long getRawQuantity() {
        return (Long) instanceData[6];
      }
    };
  }

  public Collection<IAsset> makeAssetTree() {
    // Create:
    // 1) three top level assets
    // 2) one top level asset has two children
    // 3) another top level asset has four children, two of which have a child
    Collection<IAsset> assets = new ArrayList<IAsset>();
    int i = 0;
    for (; i < 3; i++) {
      assets.add(makeAsset(testData[i]));
    }
    List<IAsset> container = (List<IAsset>) ((List<IAsset>) assets).get(1).getContainedAssets();
    for (; i < 5; i++) {
      container.add(makeAsset(testData[i]));
    }
    container = (List<IAsset>) ((List<IAsset>) assets).get(2).getContainedAssets();
    for (; i < 9; i++) {
      container.add(makeAsset(testData[i]));
    }
    container = (List<IAsset>) ((List<IAsset>) ((List<IAsset>) assets).get(2).getContainedAssets()).get(1).getContainedAssets();
    for (; i < 10; i++) {
      container.add(makeAsset(testData[i]));
    }
    container = (List<IAsset>) ((List<IAsset>) ((List<IAsset>) assets).get(2).getContainedAssets()).get(3).getContainedAssets();
    for (; i < 11; i++) {
      container.add(makeAsset(testData[i]));
    }

    return assets;
  }

  public void checkAsset(
                         Object[] instanceData,
                         Asset ref,
                         Asset container) {
    Assert.assertNotNull(ref);
    Assert.assertEquals(ref.getFlag(), Integer.parseInt(instanceData[0].toString()));
    Assert.assertEquals(ref.getItemID(), Long.parseLong(instanceData[1].toString()));
    Assert.assertEquals(ref.getLocationID(), Long.parseLong(instanceData[2].toString()));
    Assert.assertEquals(ref.getQuantity(), Integer.parseInt(instanceData[3].toString()));
    Assert.assertEquals(ref.isSingleton(), Boolean.parseBoolean(instanceData[4].toString()));
    Assert.assertEquals(ref.getTypeID(), Integer.parseInt(instanceData[5].toString()));
    Assert.assertEquals(ref.getRawQuantity(), Integer.parseInt(instanceData[6].toString()));
    Assert.assertEquals(container == null ? Asset.TOP_LEVEL : container.getItemID(), ref.getContainer());
  }

  public void checkAssetPair(
                             Asset testdata,
                             Asset ref,
                             Asset container) {
    Assert.assertNotNull(ref);
    Assert.assertEquals(ref.getFlag(), testdata.getFlag());
    Assert.assertEquals(ref.getItemID(), testdata.getItemID());
    Assert.assertEquals(ref.getLocationID(), testdata.getLocationID());
    Assert.assertEquals(ref.getQuantity(), testdata.getQuantity());
    Assert.assertEquals(ref.isSingleton(), testdata.isSingleton());
    Assert.assertEquals(ref.getTypeID(), testdata.getTypeID());
    Assert.assertEquals(ref.getRawQuantity(), testdata.getRawQuantity());
    Assert.assertEquals(container == null ? Asset.TOP_LEVEL : container.getItemID(), ref.getContainer());
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);

    EasyMock.expect(mockServer.requestAssets()).andReturn(makeAssetTree());
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  public Asset findWithId(
                          Collection<Asset> assets,
                          long itemID) {
    for (Asset i : assets) {
      if (i.getItemID() == itemID) { return i; }
    }
    return null;
  }

  public void checkAssetsMatchTestData(
                                       List<Asset> assets) {
    Assert.assertEquals(testData.length, assets.size());

    // First three assets are top level
    checkAsset(testData[0], findWithId(assets, (Long) testData[0][1]), null);
    checkAsset(testData[1], findWithId(assets, (Long) testData[1][1]), null);
    checkAsset(testData[2], findWithId(assets, (Long) testData[2][1]), null);

    // Second asset has two children
    Asset container = findWithId(assets, (Long) testData[1][1]);
    Assert.assertNotNull(container);
    checkAsset(testData[3], findWithId(assets, (Long) testData[3][1]), container);
    checkAsset(testData[4], findWithId(assets, (Long) testData[4][1]), container);

    // Third asset has four children
    container = findWithId(assets, (Long) testData[2][1]);
    Assert.assertNotNull(container);
    checkAsset(testData[5], findWithId(assets, (Long) testData[5][1]), container);
    checkAsset(testData[6], findWithId(assets, (Long) testData[6][1]), container);
    checkAsset(testData[7], findWithId(assets, (Long) testData[7][1]), container);
    checkAsset(testData[8], findWithId(assets, (Long) testData[8][1]), container);

    // Seventh asset has one child
    container = findWithId(assets, (Long) testData[6][1]);
    Assert.assertNotNull(container);
    checkAsset(testData[9], findWithId(assets, (Long) testData[9][1]), container);

    // Ninth asset has one child
    container = findWithId(assets, (Long) testData[8][1]);
    Assert.assertNotNull(container);
    checkAsset(testData[10], findWithId(assets, (Long) testData[10][1]), container);
  }

  public void checkAssetsMatchOtherData(
                                        List<Asset> ref,
                                        List<Asset> assets) {
    Assert.assertEquals(ref.size(), assets.size());

    // First three assets are top level
    checkAssetPair(ref.get(0), findWithId(assets, ref.get(0).getItemID()), null);
    checkAssetPair(ref.get(1), findWithId(assets, ref.get(1).getItemID()), null);
    checkAssetPair(ref.get(2), findWithId(assets, ref.get(2).getItemID()), null);

    // Second asset has two children
    Asset container = findWithId(assets, ref.get(1).getItemID());
    Assert.assertNotNull(container);
    checkAssetPair(ref.get(3), findWithId(assets, ref.get(3).getItemID()), container);
    checkAssetPair(ref.get(4), findWithId(assets, ref.get(4).getItemID()), container);

    // Third asset has four children
    container = findWithId(assets, ref.get(2).getItemID());
    Assert.assertNotNull(container);
    checkAssetPair(ref.get(5), findWithId(assets, ref.get(5).getItemID()), container);
    checkAssetPair(ref.get(6), findWithId(assets, ref.get(6).getItemID()), container);
    checkAssetPair(ref.get(7), findWithId(assets, ref.get(7).getItemID()), container);
    checkAssetPair(ref.get(8), findWithId(assets, ref.get(8).getItemID()), container);

    // Seventh asset has one child
    container = findWithId(assets, ref.get(6).getItemID());
    Assert.assertNotNull(container);
    checkAssetPair(ref.get(9), findWithId(assets, ref.get(9).getItemID()), container);

    // Ninth asset has one child
    container = findWithId(assets, ref.get(8).getItemID());
    Assert.assertNotNull(container);
    checkAssetPair(ref.get(10), findWithId(assets, ref.get(10).getItemID()), container);
  }

  // Test update with all new assets
  @Test
  public void testCharacterAssetSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterAssetsSync.syncAssets(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify assets were added correctly.
    List<Asset> assets = Asset.getAllAssets(syncAccount, testTime, 100, 0);
    checkAssetsMatchTestData(assets);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getAssetListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListDetail());
  }

  // Test update with assets already populated
  @Test
  public void testCharacterAssetSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing assets.
    List<Asset> allAssets = new ArrayList<Asset>();
    for (int i = 0; i < testData.length; i++) {
      long container = Asset.TOP_LEVEL;
      switch (i) {
      case 3:
        container = allAssets.get(1).getItemID();
        break;

      case 4:
        container = allAssets.get(1).getItemID();
        break;

      case 5:
        container = allAssets.get(2).getItemID();
        break;

      case 6:
        container = allAssets.get(2).getItemID();
        break;

      case 7:
        container = allAssets.get(2).getItemID();
        break;

      case 8:
        container = allAssets.get(2).getItemID();
        break;

      case 9:
        container = allAssets.get(6).getItemID();
        break;

      case 10:
        container = allAssets.get(8).getItemID();
        break;

      default:
      }

      Asset next = new Asset(
          (Long) testData[i][1], (Long) testData[i][2], (Integer) testData[i][5], (Long) testData[i][3], (Integer) testData[i][0], (Boolean) testData[i][4],
          (Long) testData[i][6], container);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
      allAssets.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterAssetsSync.syncAssets(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify assets were added correctly.
    List<Asset> assets = Asset.getAllAssets(syncAccount, testTime, 100, 0);
    checkAssetsMatchTestData(assets);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getAssetListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterAssetSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing assets
    List<Asset> allAssets = new ArrayList<Asset>();
    for (int i = 0; i < testData.length; i++) {
      long container = Asset.TOP_LEVEL;
      switch (i) {
      case 3:
        container = allAssets.get(1).getItemID();
        break;

      case 4:
        container = allAssets.get(1).getItemID();
        break;

      case 5:
        container = allAssets.get(2).getItemID();
        break;

      case 6:
        container = allAssets.get(2).getItemID();
        break;

      case 7:
        container = allAssets.get(2).getItemID();
        break;

      case 8:
        container = allAssets.get(2).getItemID();
        break;

      case 9:
        container = allAssets.get(6).getItemID();
        break;

      case 10:
        container = allAssets.get(8).getItemID();
        break;

      default:
      }

      Asset next = new Asset(
          (Long) testData[i][1], (Long) testData[i][2], (Integer) testData[i][5], (Long) testData[i][3], (Integer) testData[i][0], (Boolean) testData[i][4],
          (Long) testData[i][6], container);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
      allAssets.add(next);
    }

    // Set the tracker as already updated and populate the container
    tracker.setAssetListStatus(SyncState.UPDATED);
    tracker.setAssetListDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setAssetListExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterAssetsSync.syncAssets(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify assets unchanged
    List<Asset> assets = Asset.getAllAssets(syncAccount, testTime, 100, 0);
    checkAssetsMatchOtherData(allAssets, assets);

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getAssetListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListDetail());
  }

  // Verify no longer referenced assets are removed.
  @Test
  public void testCharacterAssetsSyncDeletesUnused() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Add some random assets that don't exist in the test data.
    List<Asset> gcAssets = new ArrayList<Asset>();
    for (int i = 0; i < 5; i++) {
      Asset next = new Asset(getUnusedItemID(), 1, 2, 3, 4, false, 5, Asset.TOP_LEVEL);
      next.setup(syncAccount, testTime);
      next = CachedData.updateData(next);
      gcAssets.add(next);
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterAssetsSync.syncAssets(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify assets were added correctly.
    List<Asset> assets = Asset.getAllAssets(syncAccount, testTime, 100, 0);
    checkAssetsMatchTestData(assets);

    // Verify all the garbage assets were removed.
    for (Asset next : gcAssets) {
      Assert.assertNull(Asset.get(syncAccount, testTime, next.getItemID()));
    }

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getAssetListExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getAssetListDetail());
  }
}
