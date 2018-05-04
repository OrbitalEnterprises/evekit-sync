package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.AssetsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdAssets200Ok;
import enterprises.orbital.eve.esi.client.model.PostCorporationsCorporationIdAssetsLocations200Ok;
import enterprises.orbital.eve.esi.client.model.PostCorporationsCorporationIdAssetsLocationsPosition;
import enterprises.orbital.eve.esi.client.model.PostCorporationsCorporationIdAssetsNames200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.Asset;
import enterprises.orbital.evekit.model.common.Location;
import enterprises.orbital.evekit.sde.client.model.InvCategory;
import enterprises.orbital.evekit.sde.client.model.InvGroup;
import enterprises.orbital.evekit.sde.client.model.InvType;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICorporationAssetsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private AssetsApi mockEndpoint;
  private SDECache mockCache;
  private long testTime = 1238L;

  private static Object[][] assetsTestData;
  private static Object[][] locationsTestData;
  private static int[] pages;

  static {
    // Assets test data
    // 0 long itemID;
    // 1 long locationID;
    // 2 String locationType;
    // 3 String locationFlag;
    // 4 int typeID;
    // 5 int quantity;
    // 6 boolean singleton;
    // 7 String blueprintType;
    int size = 1000 + TestBase.getRandomInt(500);
    assetsTestData = new Object[size][8];
    int locationTypeLen = GetCorporationsCorporationIdAssets200Ok.LocationTypeEnum.values().length;
    int locationFlagLen = GetCorporationsCorporationIdAssets200Ok.LocationFlagEnum.values().length;
    for (int i = 0; i < size; i++) {
      assetsTestData[i][0] = TestBase.getUniqueRandomLong();
      assetsTestData[i][1] = TestBase.getRandomLong();
      assetsTestData[i][2] = GetCorporationsCorporationIdAssets200Ok.LocationTypeEnum.values()[TestBase.getRandomInt(
          locationTypeLen)];
      assetsTestData[i][3] = GetCorporationsCorporationIdAssets200Ok.LocationFlagEnum.values()[TestBase.getRandomInt(
          locationFlagLen)];
      assetsTestData[i][4] = TestBase.getRandomInt();
      assetsTestData[i][5] = TestBase.getRandomInt();
      assetsTestData[i][6] = true;
      assetsTestData[i][7] = TestBase.getRandomText(50);
    }

    // Locations test data
    // 0 long itemID;
    // 1 String itemName;
    // 2 double x;
    // 3 double y;
    // 4 double z;
    locationsTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      locationsTestData[i][0] = assetsTestData[i][0];
      locationsTestData[i][1] = TestBase.getRandomText(50);
      locationsTestData[i][2] = TestBase.getRandomDouble(10000);
      locationsTestData[i][3] = TestBase.getRandomDouble(10000);
      locationsTestData[i][4] = TestBase.getRandomDouble(10000);
    }

    int pageCount = 2 + TestBase.getRandomInt(4);
    pages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--)
      pages[i] = size - (pageCount - 1 - i) * (size / pageCount);
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_ASSETS, 1234L, null);

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
                                                        .createQuery("DELETE FROM Asset ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM Location ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    AbstractESIAccountSync.setCacheCreator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(AssetsApi.class);
    // Setup asset retrieval mock calls
    List<GetCorporationsCorporationIdAssets200Ok> assetList =
        Arrays.stream(assetsTestData)
              .map(x -> {
                GetCorporationsCorporationIdAssets200Ok nextAsset = new GetCorporationsCorporationIdAssets200Ok();
                nextAsset.setItemId((Long) x[0]);
                nextAsset.setLocationId((Long) x[1]);
                nextAsset.setLocationType((GetCorporationsCorporationIdAssets200Ok.LocationTypeEnum) x[2]);
                nextAsset.setLocationFlag((GetCorporationsCorporationIdAssets200Ok.LocationFlagEnum) x[3]);
                nextAsset.setTypeId((Integer) x[4]);
                nextAsset.setQuantity((Integer) x[5]);
                nextAsset.setIsSingleton((Boolean) x[6]);
                return nextAsset;
              })
              .collect(Collectors.toList());
    int last = 0;
    for (int i = 0; i < pages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(pages.length));
      ApiResponse<List<GetCorporationsCorporationIdAssets200Ok>> apir = new ApiResponse<>(200, headers,
                                                                                          assetList.subList(last,
                                                                                                            pages[i]));
      EasyMock.expect(mockEndpoint.getCorporationsCorporationIdAssetsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apir);
      last = pages[i];
    }
    // Setup name and location retrieval mock calls
    int BATCH_SIZE = 500;
    List<PostCorporationsCorporationIdAssetsLocations200Ok> locationList =
        Arrays.stream(locationsTestData)
              .map(x -> {
                PostCorporationsCorporationIdAssetsLocations200Ok nextLocation = new PostCorporationsCorporationIdAssetsLocations200Ok();
                nextLocation.setItemId((Long) x[0]);
                PostCorporationsCorporationIdAssetsLocationsPosition pos = new PostCorporationsCorporationIdAssetsLocationsPosition();
                pos.setX((Double) x[2]);
                pos.setY((Double) x[3]);
                pos.setZ((Double) x[4]);
                nextLocation.setPosition(pos);
                return nextLocation;
              })
              .collect(Collectors.toList());
    List<PostCorporationsCorporationIdAssetsNames200Ok> nameList =
        Arrays.stream(locationsTestData)
              .map(x -> {
                PostCorporationsCorporationIdAssetsNames200Ok nextName = new PostCorporationsCorporationIdAssetsNames200Ok();
                nextName.setItemId((Long) x[0]);
                nextName.setName((String) x[1]);
                return nextName;
              })
              .collect(Collectors.toList());
    for (int i = 0; i < locationsTestData.length; i += BATCH_SIZE) {
      last = Math.min(i + BATCH_SIZE, locationsTestData.length);
      List<PostCorporationsCorporationIdAssetsLocations200Ok> locationBatch = locationList.subList(i, last);
      List<PostCorporationsCorporationIdAssetsNames200Ok> nameBatch = nameList.subList(i, last);
      List<Long> idList = locationBatch.stream()
                                       .map(PostCorporationsCorporationIdAssetsLocations200Ok::getItemId)
                                       .collect(
                                           Collectors.toList());
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
      //noinspection ConstantConditions
      EasyMock.expect(mockEndpoint.postCorporationsCorporationIdAssetsLocationsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.eq(idList),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(new ApiResponse<>(200, headers, locationBatch));
      //noinspection ConstantConditions
      EasyMock.expect(mockEndpoint.postCorporationsCorporationIdAssetsNamesWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.eq(idList),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(new ApiResponse<>(200, headers, nameBatch));
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getAssetsApi())
            .andReturn(mockEndpoint);
    // Install a mock SDE cache
    mockCache = EasyMock.createMock(SDECache.class);
    InvType dummyType = new InvType();
    dummyType.setTypeID(1234);
    dummyType.setGroupID(5678);
    dummyType.setTypeName("dummyType");
    dummyType.setDescription("dummy");
    dummyType.setMass(1234D);
    dummyType.setVolume(1234D);
    dummyType.setCapacity(10D);
    dummyType.setPortionSize(123);
    dummyType.setRaceID(123);
    dummyType.setBasePrice(BigDecimal.ONE);
    dummyType.setMarketGroupID(1234);
    dummyType.setIconID(1234);
    dummyType.setSoundID(1234);
    dummyType.setGraphicID(1234);
    InvGroup dummyGroup = new InvGroup();
    dummyGroup.setGroupID(1234);
    dummyGroup.setCategoryID(1234);
    dummyGroup.setGroupName("Dummy Container");
    dummyGroup.setIconID(1234);
    InvCategory dummyCategory = new InvCategory();
    dummyCategory.setCategoryID(1234);
    dummyCategory.setCategoryName("Ship");
    dummyCategory.setIconID(1234);
    EasyMock.expect(mockCache.getType(EasyMock.anyInt())).andReturn(dummyType).anyTimes();
    EasyMock.expect(mockCache.getGroup(EasyMock.anyInt())).andReturn(dummyGroup).anyTimes();
    EasyMock.expect(mockCache.getCategory(EasyMock.anyInt())).andReturn(dummyCategory).anyTimes();
    AbstractESIAccountSync.setCacheCreator(() -> mockCache);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Asset> storedData = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Asset.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(assetsTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < assetsTestData.length; i++) {
      Asset nextEl = storedData.get(i);
      Assert.assertEquals((long) (Long) assetsTestData[i][0], nextEl.getItemID());
      Assert.assertEquals((long) (Long) assetsTestData[i][1], nextEl.getLocationID());
      Assert.assertEquals(String.valueOf(assetsTestData[i][2]), nextEl.getLocationType());
      Assert.assertEquals(String.valueOf(assetsTestData[i][3]), nextEl.getLocationFlag());
      Assert.assertEquals((int) (Integer) assetsTestData[i][4], nextEl.getTypeID());
      Assert.assertEquals((int) (Integer) assetsTestData[i][5], nextEl.getQuantity());
      Assert.assertEquals(assetsTestData[i][6], nextEl.isSingleton());
      Assert.assertNull(nextEl.getBlueprintType());
    }

    // Retrieve all stored data
    List<Location> storedData2 = AbstractESIAccountSync.retrieveAll(testTime, (long contid, AttributeSelector at) ->
        Location.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(assetsTestData.length, storedData2.size());

    // Check stored data
    Map<Long, Location> locMap = storedData2.stream()
                                            .collect(Collectors.toMap(Location::getItemID, Function.identity()));
    for (int i = 0; i < assetsTestData.length; i++) {
      long itemID = storedData.get(i)
                              .getItemID();
      Location nextEl = locMap.get(itemID);
      Assert.assertNotNull(nextEl);
      Assert.assertEquals((long) (Long) locationsTestData[i][0], nextEl.getItemID());
      Assert.assertEquals(locationsTestData[i][1], nextEl.getItemName());
      Assert.assertEquals((Double) locationsTestData[i][2], nextEl.getX(), 0.001);
      Assert.assertEquals((Double) locationsTestData[i][3], nextEl.getY(), 0.001);
      Assert.assertEquals((Double) locationsTestData[i][4], nextEl.getZ(), 0.001);
    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint, mockCache);

    // Perform the sync
    ESICorporationAssetsSync sync = new ESICorporationAssetsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint, mockCache);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_ASSETS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_ASSETS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint, mockCache);

    // Populate existing
    //
    // Half the entries we insert are purposely not present in the server data so we can test deletion.
    // Compute the modified IDs
    long[] modifiedIDs = new long[assetsTestData.length];
    for (int i = 0; i < modifiedIDs.length; i++) {
      if (i % 2 == 0)
        modifiedIDs[i] = TestBase.getUniqueRandomLong();
      else
        modifiedIDs[i] = (long) (Long) assetsTestData[i][0];
    }
    for (int i = 0; i < assetsTestData.length; i++) {
      Asset newEl = new Asset(modifiedIDs[i],
                              (Long) assetsTestData[i][1] + 1L,
                              String.valueOf(assetsTestData[i][2]),
                              String.valueOf(assetsTestData[i][3]),
                              (Integer) assetsTestData[i][4] + 1,
                              (Integer) assetsTestData[i][5] + 1,
                              (Boolean) assetsTestData[i][6],
                              (String) assetsTestData[i][7]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);

      Location newL = new Location(modifiedIDs[i],
                                   String.valueOf(locationsTestData[i][1]),
                                   (Double) locationsTestData[i][2] + 1.0D,
                                   (Double) locationsTestData[i][3] + 1.0D,
                                   (Double) locationsTestData[i][4] + 1.0D);
      newL.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newL);
    }

    // Perform the sync
    ESICorporationAssetsSync sync = new ESICorporationAssetsSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint, mockCache);

    // Verify old objects were evolved properly
    List<Asset> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        Asset.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                          AbstractESIAccountSync.ANY_SELECTOR));
    List<Location> oldLs = AbstractESIAccountSync.retrieveAll(testTime - 1, (long contid, AttributeSelector at) ->
        Location.accessQuery(corpSyncAccount, contid, 1000, false, at, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR,
                             AbstractESIAccountSync.ANY_SELECTOR, AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(assetsTestData.length, oldEls.size());
    Assert.assertEquals(assetsTestData.length, oldLs.size());

    // Check old data
    Map<Long, Location> locMap = oldLs.stream()
                                      .collect(Collectors.toMap(Location::getItemID, Function.identity()));
    for (int i = 0; i < assetsTestData.length; i++) {
      Asset nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals(modifiedIDs[i], nextEl.getItemID());
      Assert.assertEquals((Long) assetsTestData[i][1] + 1L, nextEl.getLocationID());
      Assert.assertEquals(String.valueOf(assetsTestData[i][2]), nextEl.getLocationType());
      Assert.assertEquals(String.valueOf(assetsTestData[i][3]), nextEl.getLocationFlag());
      Assert.assertEquals((Integer) assetsTestData[i][4] + 1, nextEl.getTypeID());
      Assert.assertEquals((Integer) assetsTestData[i][5] + 1, nextEl.getQuantity());
      Assert.assertEquals(assetsTestData[i][6], nextEl.isSingleton());
      Assert.assertEquals(assetsTestData[i][7], nextEl.getBlueprintType());

      Location nextL = locMap.get(nextEl.getItemID());
      Assert.assertNotNull(nextL);
      Assert.assertEquals(testTime, nextL.getLifeEnd());
      Assert.assertEquals(modifiedIDs[i], nextL.getItemID());
      Assert.assertEquals(locationsTestData[i][1], nextL.getItemName());
      Assert.assertEquals((Double) locationsTestData[i][2] + 1.0D, nextL.getX(), 0.001);
      Assert.assertEquals((Double) locationsTestData[i][3] + 1.0D, nextL.getY(), 0.001);
      Assert.assertEquals((Double) locationsTestData[i][4] + 1.0D, nextL.getZ(), 0.001);
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_ASSETS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_ASSETS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
