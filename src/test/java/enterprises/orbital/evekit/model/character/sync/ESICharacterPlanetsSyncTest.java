package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.PlanetaryInteractionApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.*;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

public class ESICharacterPlanetsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private PlanetaryInteractionApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] coloniesTestData;
  private static Object[][] planetsTestData;

  static {

    // Total number of planets
    int size = 30 + TestBase.getRandomInt(30);

    // Colony Data
    // 0 int planetID
    // 1 int solarSystemID
    // 2 String planetType
    // 3 int ownerID
    // 4 long lastUpdate
    // 5 int upgradeLevel
    // 6 int numberOfPins
    coloniesTestData = new Object[size][7];
    int typeSize = GetCharactersCharacterIdPlanets200Ok.PlanetTypeEnum.values().length;
    for (int i = 0; i < size; i++) {
      coloniesTestData[i][0] = TestBase.getUniqueRandomInteger();
      coloniesTestData[i][1] = TestBase.getRandomInt();
      coloniesTestData[i][2] = GetCharactersCharacterIdPlanets200Ok.PlanetTypeEnum.values()[TestBase.getRandomInt(
          typeSize)];
      coloniesTestData[i][3] = TestBase.getRandomInt();
      coloniesTestData[i][4] = TestBase.getRandomLong();
      coloniesTestData[i][5] = TestBase.getRandomInt();
      coloniesTestData[i][6] = TestBase.getRandomInt();
    }

    // Planet Data
    // 0 int planetID
    // 1 Object[][] links
    // 2 Object[][] pins
    // 3 Object[][] routes
    planetsTestData = new Object[size][4];
    for (int i = 0; i < size; i++) {
      planetsTestData[i][0] = coloniesTestData[i][0];

      // 0 int planetID
      // 1 long sourcePinID
      // 2 long destinationPinID
      // 3 int linkLevel
      int linkCount = 10 + TestBase.getRandomInt(10);
      Object[][] linkData = new Object[linkCount][4];
      planetsTestData[i][1] = linkData;
      for (int j = 0; j < linkCount; j++) {
        linkData[j][0] = planetsTestData[i][0];
        linkData[j][1] = TestBase.getUniqueRandomLong();
        linkData[j][2] = TestBase.getUniqueRandomLong();
        linkData[j][3] = TestBase.getRandomInt();
      }

      // 0 int planetID
      // 1 long pinID
      // 2 int typeID
      // 3 int schematicID
      // 4 long lastCycleStart
      // 5 int cycleTime
      // 6 int quantityPerCycle
      // 7 long installTime
      // 8 long expiryTime
      // 9 int productTypeID
      // 10 float longitude
      // 11 float latitude
      // 12 float headRadius
      // 13 Set<PlanetaryPinHead> heads
      // 14 Set<PlanetaryPinContent> contents
      int pinCount = 10 + TestBase.getRandomInt(10);
      Object[][] pinData = new Object[pinCount][15];
      planetsTestData[i][2] = pinData;
      for (int j = 0; j < pinCount; j++) {
        pinData[j][0] = planetsTestData[i][0];
        pinData[j][1] = TestBase.getUniqueRandomLong();
        pinData[j][2] = TestBase.getRandomInt();
        pinData[j][3] = TestBase.getRandomInt();
        pinData[j][4] = TestBase.getRandomLong();
        pinData[j][5] = TestBase.getRandomInt();
        pinData[j][6] = TestBase.getRandomInt();
        pinData[j][7] = TestBase.getRandomLong();
        pinData[j][8] = TestBase.getRandomLong();
        pinData[j][9] = TestBase.getRandomInt();
        pinData[j][10] = TestBase.getRandomFloat(1000);
        pinData[j][11] = TestBase.getRandomFloat(1000);
        pinData[j][12] = TestBase.getRandomFloat(1000);

        // 0 int headID
        // 1 float latitude
        // 2 float longitude
        int headCount = 5 + TestBase.getRandomInt(5);
        Object[][] heads = new Object[headCount][3];
        pinData[j][13] = heads;
        for (int k = 0; k < headCount; k++) {
          heads[k][0] = TestBase.getUniqueRandomInteger();
          heads[k][1] = TestBase.getRandomFloat(1000);
          heads[k][2] = TestBase.getRandomFloat(1000);
        }

        // 0 int typeID
        // 1 long amount
        int contentCount = 5 + TestBase.getRandomInt(5);
        Object[][] contents = new Object[contentCount][2];
        pinData[j][14] = contents;
        for (int k = 0; k < contentCount; k++) {
          contents[k][0] = TestBase.getUniqueRandomInteger();
          contents[k][1] = TestBase.getRandomLong();
        }
      }

      // 0 int planetID
      // 1 long routeID
      // 2 long sourcePinID
      // 3 long destinationPinID
      // 4 int contentTypeID
      // 5 float quantity
      // 6 List<Long> waypoints
      int routeCount = 10 + TestBase.getRandomInt(10);
      Object[][] routeData = new Object[routeCount][7];
      planetsTestData[i][3] = routeData;
      for (int j = 0; j < routeCount; j++) {
        routeData[j][0] = planetsTestData[i][0];
        routeData[j][1] = TestBase.getUniqueRandomLong();
        routeData[j][2] = TestBase.getUniqueRandomLong();
        routeData[j][3] = TestBase.getUniqueRandomLong();
        routeData[j][4] = TestBase.getRandomInt();
        routeData[j][5] = TestBase.getRandomFloat(1000);

        int waypointCount = 5 + TestBase.getRandomInt(5);
        Object[] waypoints = new Object[waypointCount];
        routeData[j][6] = waypoints;
        for (int k = 0; k < waypointCount; k++) {
          waypoints[k] = TestBase.getUniqueRandomLong();
        }
      }

    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_PLANETS, 1234L);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @SuppressWarnings("SqlNoDataSourceInspection")
  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createNativeQuery("DELETE FROM planet_pin_head ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createNativeQuery("DELETE FROM planet_pin_content ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createNativeQuery("DELETE FROM planet_route_waypoint ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM PlanetaryColony ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM PlanetaryLink ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM PlanetaryPin ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM PlanetaryRoute ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(PlanetaryInteractionApi.class);

    // Setup planet list
    List<GetCharactersCharacterIdPlanets200Ok> planetList =
        Arrays.stream(coloniesTestData)
              .map(x -> {
                GetCharactersCharacterIdPlanets200Ok newColony = new GetCharactersCharacterIdPlanets200Ok();
                newColony.setPlanetId((int) x[0]);
                newColony.setSolarSystemId((int) x[1]);
                newColony.setPlanetType((GetCharactersCharacterIdPlanets200Ok.PlanetTypeEnum) x[2]);
                newColony.setOwnerId((int) x[3]);
                newColony.setLastUpdate(new DateTime(new Date((long) x[4])));
                newColony.setUpgradeLevel((int) x[5]);
                newColony.setNumPins((int) x[6]);
                return newColony;
              })
              .collect(Collectors.toList());

    // Setup colonies mock
    ApiResponse<List<GetCharactersCharacterIdPlanets200Ok>> apir = new ApiResponse<>(200,
                                                                                     createHeaders("Expires",
                                                                                                   "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                     planetList);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdPlanetsWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.anyString(),
        EasyMock.isNull(),
        EasyMock.isNull()))
            .andReturn(apir);

    // Setup planet info calls
    for (Object[] nk : planetsTestData) {
      int planetID = (int) nk[0];
      GetCharactersCharacterIdPlanetsPlanetIdOk pinfo = new GetCharactersCharacterIdPlanetsPlanetIdOk();

      // Setup links
      for (Object[] li : (Object[][]) nk[1]) {
        GetCharactersCharacterIdPlanetsPlanetIdLink newLink = new GetCharactersCharacterIdPlanetsPlanetIdLink();
        pinfo.getLinks()
             .add(newLink);
        newLink.setSourcePinId((long) li[1]);
        newLink.setDestinationPinId((long) li[2]);
        newLink.setLinkLevel((int) li[3]);
      }

      // Setup pins
      for (Object[] pi : (Object[][]) nk[2]) {
        GetCharactersCharacterIdPlanetsPlanetIdPin newPin = new GetCharactersCharacterIdPlanetsPlanetIdPin();
        pinfo.getPins()
             .add(newPin);
        GetCharactersCharacterIdPlanetsPlanetIdExtractorDetails extractorDetails = new GetCharactersCharacterIdPlanetsPlanetIdExtractorDetails();
        newPin.setExtractorDetails(extractorDetails);
        newPin.setPinId((long) pi[1]);
        newPin.setTypeId((int) pi[2]);
        newPin.setSchematicId((int) pi[3]);
        newPin.setLastCycleStart(new DateTime(new Date((long) pi[4])));
        extractorDetails.setCycleTime((int) pi[5]);
        extractorDetails.setQtyPerCycle((int) pi[6]);
        newPin.setInstallTime(new DateTime(new Date((long) pi[7])));
        newPin.setExpiryTime(new DateTime(new Date((long) pi[8])));
        extractorDetails.setProductTypeId((int) pi[9]);
        newPin.setLongitude((float) pi[10]);
        newPin.setLatitude((float) pi[11]);
        extractorDetails.setHeadRadius((float) pi[12]);

        for (Object[] headInfo : (Object[][]) pi[13]) {
          GetCharactersCharacterIdPlanetsPlanetIdHead newHead = new GetCharactersCharacterIdPlanetsPlanetIdHead();
          newHead.setHeadId((int) headInfo[0]);
          newHead.setLatitude((float) headInfo[1]);
          newHead.setLongitude((float) headInfo[2]);
          extractorDetails.getHeads()
                          .add(newHead);
        }

        for (Object[] contentInfo : (Object[][]) pi[14]) {
          GetCharactersCharacterIdPlanetsPlanetIdContent newContent = new GetCharactersCharacterIdPlanetsPlanetIdContent();
          newContent.setTypeId((int) contentInfo[0]);
          newContent.setAmount((long) contentInfo[1]);
          newPin.getContents()
                .add(newContent);
        }
      }

      // Setup routes
      for (Object[] ri : (Object[][]) nk[3]) {
        GetCharactersCharacterIdPlanetsPlanetIdRoute newRoute = new GetCharactersCharacterIdPlanetsPlanetIdRoute();
        pinfo.getRoutes()
             .add(newRoute);
        newRoute.setRouteId((long) ri[1]);
        newRoute.setSourcePinId((long) ri[2]);
        newRoute.setDestinationPinId((long) ri[3]);
        newRoute.setContentTypeId((int) ri[4]);
        newRoute.setQuantity((float) ri[5]);

        for (Object k : (Object[]) ri[6])
          newRoute.getWaypoints()
                  .add((long) k);
      }

      // Insert mock call and response
      ApiResponse<GetCharactersCharacterIdPlanetsPlanetIdOk> apirPlanet = new ApiResponse<>(200,
                                                                                            createHeaders("Expires",
                                                                                                          "Thu, 21 Dec 2017 12:00:00 GMT"),
                                                                                            pinfo);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdPlanetsPlanetIdWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.eq(planetID),
          EasyMock.isNull(),
          EasyMock.anyString(),
          EasyMock.isNull(),
          EasyMock.isNull()))
              .andReturn(apirPlanet);
    }


    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getPlanetaryInteractionApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long time, Object[][] cData, Object[][] pData) throws Exception {

    // Check colonies against stored data
    List<PlanetaryColony> storedColonies = CachedData.retrieveAll(time,
                                                                  (contid, at) -> PlanetaryColony.accessQuery(
                                                                      charSyncAccount, contid, 1000, false, at,
                                                                      AttributeSelector.any(), AttributeSelector.any(),
                                                                      AttributeSelector.any(),
                                                                      AttributeSelector.any(), AttributeSelector.any(),
                                                                      AttributeSelector.any(),
                                                                      AttributeSelector.any()));
    Assert.assertEquals(cData.length, storedColonies.size());

    for (int i = 0; i < cData.length; i++) {
      PlanetaryColony nextColony = storedColonies.get(i);
      Assert.assertEquals((int) cData[i][0], nextColony.getPlanetID());
      Assert.assertEquals((int) cData[i][1], nextColony.getSolarSystemID());
      Assert.assertEquals(String.valueOf(cData[i][2]), nextColony.getPlanetType());
      Assert.assertEquals((int) cData[i][3], nextColony.getOwnerID());
      Assert.assertEquals((long) cData[i][4], nextColony.getLastUpdate());
      Assert.assertEquals((int) cData[i][5], nextColony.getUpgradeLevel());
      Assert.assertEquals((int) cData[i][6], nextColony.getNumberOfPins());
    }

    // Check links against stored data
    List<PlanetaryLink> storedLinks = CachedData.retrieveAll(time,
                                                             (contid, at) -> PlanetaryLink.accessQuery(charSyncAccount,
                                                                                                       contid, 1000,
                                                                                                       false, at,
                                                                                                       AttributeSelector.any(),
                                                                                                       AttributeSelector.any(),
                                                                                                       AttributeSelector.any(),
                                                                                                       AttributeSelector.any()));

    int totalLinkCount = 0;

    for (int i = 0, j = 0; i < pData.length; i++) {
      Object[][] linkData = (Object[][]) pData[i][1];
      totalLinkCount += linkData.length;

      for (int k = 0; k < linkData.length; k++, j++) {
        PlanetaryLink nextLink = storedLinks.get(j);
        Assert.assertEquals((int) linkData[k][0], nextLink.getPlanetID());
        Assert.assertEquals((long) linkData[k][1], nextLink.getSourcePinID());
        Assert.assertEquals((long) linkData[k][2], nextLink.getDestinationPinID());
        Assert.assertEquals((int) linkData[k][3], nextLink.getLinkLevel());
      }
    }

    Assert.assertEquals(totalLinkCount, storedLinks.size());

    // Check pins against stored data
    List<PlanetaryPin> storedPins = CachedData.retrieveAll(time,
                                                           (contid, at) -> PlanetaryPin.accessQuery(charSyncAccount,
                                                                                                    contid, 1000, false,
                                                                                                    at,
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any(),
                                                                                                    AttributeSelector.any()));

    int totalPinCount = 0;

    for (int i = 0, j = 0; i < pData.length; i++) {
      Object[][] pinData = (Object[][]) pData[i][2];
      totalPinCount += pinData.length;

      for (int k = 0; k < pinData.length; k++, j++) {
        PlanetaryPin nextPin = storedPins.get(j);
        Assert.assertEquals((int) pinData[k][0], nextPin.getPlanetID());
        Assert.assertEquals((long) pinData[k][1], nextPin.getPinID());
        Assert.assertEquals((int) pinData[k][2], nextPin.getTypeID());
        Assert.assertEquals((int) pinData[k][3], nextPin.getSchematicID());
        Assert.assertEquals((long) pinData[k][4], nextPin.getLastCycleStart());
        Assert.assertEquals((int) pinData[k][5], nextPin.getCycleTime());
        Assert.assertEquals((int) pinData[k][6], nextPin.getQuantityPerCycle());
        Assert.assertEquals((long) pinData[k][7], nextPin.getInstallTime());
        Assert.assertEquals((long) pinData[k][8], nextPin.getExpiryTime());
        Assert.assertEquals((int) pinData[k][9], nextPin.getProductTypeID());
        Assert.assertEquals((float) pinData[k][10], nextPin.getLongitude(), 0.01);
        Assert.assertEquals((float) pinData[k][11], nextPin.getLatitude(), 0.01);
        Assert.assertEquals((float) pinData[k][12], nextPin.getHeadRadius(), 0.01);

        Object[][] headData = (Object[][]) pinData[k][13];
        Object[][] contentData = (Object[][]) pinData[k][14];
        Assert.assertEquals(headData.length, nextPin.getHeads()
                                                    .size());
        Assert.assertEquals(contentData.length, nextPin.getContents()
                                                       .size());
        for (Object[] aHeadData : headData) {
          PlanetaryPinHead check = new PlanetaryPinHead(
              (int) aHeadData[0],
              (float) aHeadData[1],
              (float) aHeadData[2]
          );
          Assert.assertTrue(nextPin.getHeads()
                                   .contains(check));
        }
        for (Object[] aContentData : contentData) {
          PlanetaryPinContent check = new PlanetaryPinContent(
              (int) aContentData[0],
              (long) aContentData[1]
          );
          Assert.assertTrue(nextPin.getContents()
                                   .contains(check));
        }
      }
    }

    Assert.assertEquals(totalPinCount, storedPins.size());

    // Check routes against stored data
    List<PlanetaryRoute> storedRoutes = CachedData.retrieveAll(time,
                                                               (contid, at) -> PlanetaryRoute.accessQuery(
                                                                   charSyncAccount, contid, 1000, false, at,
                                                                   AttributeSelector.any(), AttributeSelector.any(),
                                                                   AttributeSelector.any(),
                                                                   AttributeSelector.any(), AttributeSelector.any(),
                                                                   AttributeSelector.any(),
                                                                   AttributeSelector.any()));

    int totalRouteCount = 0;

    for (int i = 0, j = 0; i < pData.length; i++) {
      Object[][] routeData = (Object[][]) pData[i][3];
      totalRouteCount += routeData.length;

      for (int k = 0; k < routeData.length; k++, j++) {
        PlanetaryRoute nextRoute = storedRoutes.get(j);
        Assert.assertEquals((int) routeData[k][0], nextRoute.getPlanetID());
        Assert.assertEquals((long) routeData[k][1], nextRoute.getRouteID());
        Assert.assertEquals((long) routeData[k][2], nextRoute.getSourcePinID());
        Assert.assertEquals((long) routeData[k][3], nextRoute.getDestinationPinID());
        Assert.assertEquals((int) routeData[k][4], nextRoute.getContentTypeID());
        Assert.assertEquals((float) routeData[k][5], nextRoute.getQuantity(), 0.01);
        Object[] waypoints = (Object[]) routeData[k][6];
        Assert.assertEquals(waypoints.length, nextRoute.getWaypoints()
                                                       .size());
        for (int l = 0; l < waypoints.length; l++) {
          Assert.assertEquals((long) waypoints[l], (long) nextRoute.getWaypoints()
                                                                   .get(l));
        }
      }
    }

    Assert.assertEquals(totalRouteCount, storedRoutes.size());

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterPlanetsSync sync = new ESICharacterPlanetsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, coloniesTestData, planetsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_PLANETS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_PLANETS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @SuppressWarnings("Duplicates")
  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // Half the entries we insert are purposely not present in the server data so we can test deletion.
    // Compute the modified IDs
    int[] modifiedPlanetIDs = new int[coloniesTestData.length];
    for (int i = 0; i < modifiedPlanetIDs.length; i++) {
      if (i % 2 == 0)
        modifiedPlanetIDs[i] = TestBase.getUniqueRandomInteger();
      else
        modifiedPlanetIDs[i] = (int) coloniesTestData[i][0];
    }

    Object[][] modifiedColonies = new Object[coloniesTestData.length][7];
    Object[][] modifiedPlanets = new Object[planetsTestData.length][4];

    // Insert old colonies, links, pins and routes
    for (int i = 0; i < coloniesTestData.length; i++) {
      Object[] oc = coloniesTestData[i];
      Object[] mc = modifiedColonies[i];
      mc[0] = modifiedPlanetIDs[i];
      mc[1] = (int) oc[1] + 1;
      mc[2] = oc[2];
      mc[3] = (int) oc[3] + 1;
      mc[4] = (long) oc[4] + 1;
      mc[5] = (int) oc[5] + 1;
      mc[6] = (int) oc[6] + 1;

      PlanetaryColony newColony = new PlanetaryColony(
          (int) mc[0],
          (int) mc[1],
          String.valueOf(mc[2]),
          (int) mc[3],
          (long) mc[4],
          (int) mc[5],
          (int) mc[6]
      );

      newColony.setup(charSyncAccount, testTime - 1);
      CachedData.update(newColony);
    }

    for (int i = 0; i < planetsTestData.length; i++) {
      Object[] modPlanet = modifiedPlanets[i];
      modPlanet[0] = modifiedPlanetIDs[i];
      Object[][] origLinks = (Object[][]) planetsTestData[i][1];
      Object[][] origPins = (Object[][]) planetsTestData[i][2];
      Object[][] origRoutes = (Object[][]) planetsTestData[i][3];
      Object[][] modLinks = new Object[origLinks.length][4];
      Object[][] modPins = new Object[origPins.length][15];
      Object[][] modRoutes = new Object[origRoutes.length][7];
      modPlanet[1] = modLinks;
      modPlanet[2] = modPins;
      modPlanet[3] = modRoutes;

      for (int j = 0; j < modLinks.length; j++) {
        modLinks[j][0] = modifiedPlanetIDs[i];
        modLinks[j][1] = origLinks[j][1];
        modLinks[j][2] = origLinks[j][2];
        modLinks[j][3] = (int) origLinks[j][3] + 1;

        PlanetaryLink newLink = new PlanetaryLink(
            (int) modLinks[j][0],
            (long) modLinks[j][1],
            (long) modLinks[j][2],
            (int) modLinks[j][3]
        );

        newLink.setup(charSyncAccount, testTime - 1);
        CachedData.update(newLink);
      }

      for (int j = 0; j < modPins.length; j++) {
        modPins[j][0] = modifiedPlanetIDs[i];
        modPins[j][1] = origPins[j][1];
        modPins[j][2] = (int) origPins[j][2] + 1;
        modPins[j][3] = (int) origPins[j][3] + 1;
        modPins[j][4] = (long) origPins[j][4] + 1;
        modPins[j][5] = (int) origPins[j][5] + 1;
        modPins[j][6] = (int) origPins[j][6] + 1;
        modPins[j][7] = (long) origPins[j][7] + 1;
        modPins[j][8] = (long) origPins[j][8] + 1;
        modPins[j][9] = (int) origPins[j][9] + 1;
        modPins[j][10] = (float) origPins[j][10] + 1;
        modPins[j][11] = (float) origPins[j][11] + 1;
        modPins[j][12] = (float) origPins[j][12] + 1;

        Object[][] origHeads = (Object[][]) origPins[j][13];
        Object[][] origContents = (Object[][]) origPins[j][14];
        Object[][] modHeads = new Object[origHeads.length][3];
        Object[][] modContents = new Object[origContents.length][2];
        modPins[j][13] = modHeads;
        modPins[j][14] = modContents;

        for (int k = 0; k < modHeads.length; k++) {
          modHeads[k][0] = (int) origHeads[k][0] + 1;
          modHeads[k][1] = (float) origHeads[k][1] + 1;
          modHeads[k][2] = (float) origHeads[k][2] + 1;
        }

        for (int k = 0; k < modContents.length; k++) {
          modContents[k][0] = (int) origContents[k][0] + 1;
          modContents[k][1] = (long) origContents[k][1] + 1;
        }

        PlanetaryPin newPin = new PlanetaryPin(
            (int) modPins[j][0],
            (long) modPins[j][1],
            (int) modPins[j][2],
            (int) modPins[j][3],
            (long) modPins[j][4],
            (int) modPins[j][5],
            (int) modPins[j][6],
            (long) modPins[j][7],
            (long) modPins[j][8],
            (int) modPins[j][9],
            (float) modPins[j][10],
            (float) modPins[j][11],
            (float) modPins[j][12],
            new HashSet<>(),
            new HashSet<>()
        );

        for (Object[] modHead : modHeads) {
          newPin.getHeads()
                .add(new PlanetaryPinHead(
                    (int) modHead[0],
                    (float) modHead[1],
                    (float) modHead[2]
                ));
        }

        for (Object[] modContent : modContents) {
          newPin.getContents()
                .add(new PlanetaryPinContent(
                    (int) modContent[0],
                    (long) modContent[1]
                ));
        }

        newPin.setup(charSyncAccount, testTime - 1);
        CachedData.update(newPin);
      }

      for (int j = 0; j < modRoutes.length; j++) {
        modRoutes[j][0] = modifiedPlanetIDs[i];
        modRoutes[j][1] = origRoutes[j][1];
        modRoutes[j][2] = (long) origRoutes[j][2] + 1;
        modRoutes[j][3] = (long) origRoutes[j][3] + 1;
        modRoutes[j][4] = (int) origRoutes[j][4] + 1;
        modRoutes[j][5] = (float) origRoutes[j][5] + 1;

        Object[] origWaypoints = (Object[]) origRoutes[j][6];
        Object[] modWaypoints = new Object[origWaypoints.length];
        modRoutes[j][6] = modWaypoints;
        for (int k = 0; k < modWaypoints.length; k++) {
          modWaypoints[k] = (long) origWaypoints[k] + 1;
        }

        PlanetaryRoute newRoute = new PlanetaryRoute(
            (int) modRoutes[j][0],
            (long) modRoutes[j][1],
            (long) modRoutes[j][2],
            (long) modRoutes[j][3],
            (int) modRoutes[j][4],
            (float) modRoutes[j][5],
            new ArrayList<>()
        );

        for (Object modWaypoint : modWaypoints) {
          newRoute.getWaypoints()
                  .add((long) modWaypoint);
        }

        newRoute.setup(charSyncAccount, testTime - 1);
        CachedData.update(newRoute);
      }
    }

    // Perform the sync
    ESICharacterPlanetsSync sync = new ESICharacterPlanetsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old data
    verifyDataUpdate(testTime - 1, modifiedColonies, modifiedPlanets);

    // Verify updates
    verifyDataUpdate(testTime, coloniesTestData, planetsTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_PLANETS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_PLANETS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
