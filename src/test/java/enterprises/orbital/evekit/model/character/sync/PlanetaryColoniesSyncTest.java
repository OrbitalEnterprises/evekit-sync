package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

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
import enterprises.orbital.evekit.model.character.PlanetaryColony;
import enterprises.orbital.evekit.model.character.PlanetaryLink;
import enterprises.orbital.evekit.model.character.PlanetaryPin;
import enterprises.orbital.evekit.model.character.PlanetaryRoute;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IPlanetaryColony;
import enterprises.orbital.evexmlapi.chr.IPlanetaryLink;
import enterprises.orbital.evexmlapi.chr.IPlanetaryPin;
import enterprises.orbital.evexmlapi.chr.IPlanetaryRoute;

public class PlanetaryColoniesSyncTest extends SyncTestBase {

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

  // Generate random test data
  // for each colony:
  // colony data: indexes 0 through 10
  // array of pins: 11
  // array of links: 12
  // array of routes: 13
  static {
    int size = 10 + TestBase.getRandomInt(10);
    testData = new Object[size][14];
    for (int i = 0; i < size; i++) {
      // Generate colony data first
      testData[i][0] = TestBase.getUniqueRandomLong();
      testData[i][1] = TestBase.getRandomInt();
      testData[i][2] = TestBase.getRandomText(50);
      testData[i][3] = TestBase.getRandomText(50);
      testData[i][4] = TestBase.getRandomInt();
      testData[i][5] = TestBase.getRandomText(50);
      testData[i][6] = TestBase.getRandomLong();
      testData[i][7] = TestBase.getRandomText(50);
      testData[i][8] = TestBase.getRandomLong();
      testData[i][9] = TestBase.getRandomInt();
      testData[i][10] = TestBase.getRandomInt();
      // Generate pins
      int numPins = 3 + TestBase.getRandomInt(5);
      Object[][] pins = new Object[numPins][15];
      testData[i][11] = pins;
      for (int j = 0; j < numPins; j++) {
        pins[j][0] = testData[i][0];
        pins[j][1] = TestBase.getUniqueRandomLong();
        pins[j][2] = TestBase.getRandomInt();
        pins[j][3] = TestBase.getRandomText(50);
        pins[j][4] = TestBase.getRandomInt();
        pins[j][5] = TestBase.getRandomLong();
        pins[j][6] = TestBase.getRandomInt();
        pins[j][7] = TestBase.getRandomInt();
        pins[j][8] = TestBase.getRandomLong();
        pins[j][9] = TestBase.getRandomLong();
        pins[j][10] = TestBase.getRandomInt();
        pins[j][11] = TestBase.getRandomText(50);
        pins[j][12] = TestBase.getRandomInt();
        pins[j][13] = TestBase.getRandomDouble(100);
        pins[j][14] = TestBase.getRandomDouble(100);
      }
      // Generate links
      int numLinks = 5 + TestBase.getRandomInt(5);
      Object[][] links = new Object[numLinks][4];
      testData[i][12] = links;
      for (int j = 0; j < numLinks; j++) {
        links[j][0] = testData[i][0];
        links[j][1] = TestBase.getUniqueRandomLong();
        links[j][2] = TestBase.getUniqueRandomLong();
        links[j][3] = TestBase.getRandomInt();
      }
      // Generate routes
      int numRoutes = 10 + TestBase.getRandomInt(10);
      Object[][] routes = new Object[numRoutes][12];
      testData[i][13] = routes;
      for (int j = 0; j < numRoutes; j++) {
        routes[j][0] = testData[i][0];
        routes[j][1] = TestBase.getUniqueRandomLong();
        routes[j][2] = TestBase.getRandomLong();
        routes[j][3] = TestBase.getRandomLong();
        routes[j][4] = TestBase.getRandomInt();
        routes[j][5] = TestBase.getRandomText(50);
        routes[j][6] = TestBase.getRandomInt();
        routes[j][7] = TestBase.getRandomLong();
        routes[j][8] = TestBase.getRandomLong();
        routes[j][9] = TestBase.getRandomLong();
        routes[j][10] = TestBase.getRandomLong();
        routes[j][11] = TestBase.getRandomLong();
      }
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
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public IPlanetaryColony makeColony(
                                     final Object[] instanceData,
                                     final long pidAdjustment,
                                     final String tweak) {
    IPlanetaryColony colony = new IPlanetaryColony() {

      @Override
      public long getPlanetID() {
        return (Long) instanceData[0] + pidAdjustment;
      }

      @Override
      public int getSolarSystemID() {
        return (Integer) instanceData[1];
      }

      @Override
      public String getSolarSystemName() {
        return (String) instanceData[2] + tweak;
      }

      @Override
      public String getPlanetName() {
        return (String) instanceData[3] + tweak;
      }

      @Override
      public int getPlanetTypeID() {
        return (Integer) instanceData[4];
      }

      @Override
      public String getPlanetTypeName() {
        return (String) instanceData[5] + tweak;
      }

      @Override
      public long getOwnerID() {
        return (Long) instanceData[6];
      }

      @Override
      public String getOwnerName() {
        return (String) instanceData[7] + tweak;
      }

      @Override
      public Date getLastUpdate() {
        return new Date((Long) instanceData[8]);
      }

      @Override
      public int getUpgradeLevel() {
        return (Integer) instanceData[9];
      }

      @Override
      public int getNumberOfPins() {
        return (Integer) instanceData[10];
      }
    };

    return colony;
  }

  public IPlanetaryPin makePin(
                               final Object[] instanceData,
                               final long pidAdjustment,
                               final String tweak) {
    IPlanetaryPin pin = new IPlanetaryPin() {

      @Override
      public long getPinID() {
        return (Long) instanceData[1];
      }

      @Override
      public int getTypeID() {
        return (Integer) instanceData[2];
      }

      @Override
      public String getTypeName() {
        return (String) instanceData[3] + tweak;
      }

      @Override
      public int getSchematicID() {
        return (Integer) instanceData[4];
      }

      @Override
      public Date getLastLaunchTime() {
        return new Date((Long) instanceData[5]);
      }

      @Override
      public int getCycleTime() {
        return (Integer) instanceData[6];
      }

      @Override
      public int getQuantityPerCycle() {
        return (Integer) instanceData[7];
      }

      @Override
      public Date getInstallTime() {
        return new Date((Long) instanceData[8]);
      }

      @Override
      public Date getExpiryTime() {
        return new Date((Long) instanceData[9]);
      }

      @Override
      public int getContentTypeID() {
        return (Integer) instanceData[10];
      }

      @Override
      public String getContentTypeName() {
        return (String) instanceData[11] + tweak;
      }

      @Override
      public int getContentQuantity() {
        return (Integer) instanceData[12];
      }

      @Override
      public double getLongitude() {
        return (Double) instanceData[13];
      }

      @Override
      public double getLatitude() {
        return (Double) instanceData[14];
      }

    };

    return pin;
  }

  public IPlanetaryRoute makeRoute(
                                   final Object[] instanceData,
                                   final long pidAdjustment,
                                   final String tweak) {
    IPlanetaryRoute route = new IPlanetaryRoute() {

      @Override
      public long getRouteID() {
        return (Long) instanceData[1];
      }

      @Override
      public long getSourcePinID() {
        return (Long) instanceData[2];
      }

      @Override
      public long getDestinationPinID() {
        return (Long) instanceData[3];
      }

      @Override
      public int getContentTypeID() {
        return (Integer) instanceData[4];
      }

      @Override
      public String getContentTypeName() {
        return (String) instanceData[5] + tweak;
      }

      @Override
      public int getQuantity() {
        return (Integer) instanceData[6];
      }

      @Override
      public long getWaypoint1() {
        return (Long) instanceData[7];
      }

      @Override
      public long getWaypoint2() {
        return (Long) instanceData[8];
      }

      @Override
      public long getWaypoint3() {
        return (Long) instanceData[9];
      }

      @Override
      public long getWaypoint4() {
        return (Long) instanceData[10];
      }

      @Override
      public long getWaypoint5() {
        return (Long) instanceData[11];
      }

    };

    return route;
  }

  public IPlanetaryLink makeLink(
                                 final Object[] instanceData,
                                 final long pidAdjustment,
                                 final String tweak) {
    IPlanetaryLink link = new IPlanetaryLink() {

      @Override
      public long getSourcePinID() {
        return (Long) instanceData[1];
      }

      @Override
      public long getDestinationPinID() {
        return (Long) instanceData[2];
      }

      @Override
      public int getLinkLevel() {
        return (Integer) instanceData[3];
      }

    };

    return link;
  }

  public Collection<IPlanetaryColony> assembleColonies(
                                                       long pidAdjustment,
                                                       String tweak) {
    List<IPlanetaryColony> colonies = new ArrayList<IPlanetaryColony>();
    for (int i = 0; i < testData.length; i++) {
      colonies.add(makeColony(testData[i], pidAdjustment, tweak));
    }

    return colonies;
  }

  public Collection<IPlanetaryPin> assemblePins(
                                                long planetID,
                                                String tweak) {
    List<IPlanetaryPin> pins = new ArrayList<IPlanetaryPin>();
    for (int i = 0; i < testData.length; i++) {
      long nextPID = (Long) testData[i][0];
      if (nextPID == planetID) {
        Object[][] pinData = (Object[][]) testData[i][11];
        for (int j = 0; j < pinData.length; j++) {
          pins.add(makePin(pinData[j], 0, tweak));
        }
        break;
      }
    }

    return pins;
  }

  public Collection<IPlanetaryLink> assembleLinks(
                                                  long planetID,
                                                  String tweak) {
    List<IPlanetaryLink> links = new ArrayList<IPlanetaryLink>();
    for (int i = 0; i < testData.length; i++) {
      long nextPID = (Long) testData[i][0];
      if (nextPID == planetID) {
        Object[][] linkData = (Object[][]) testData[i][12];
        for (int j = 0; j < linkData.length; j++) {
          links.add(makeLink(linkData[j], 0, tweak));
        }
        break;
      }
    }

    return links;
  }

  public Collection<IPlanetaryRoute> assembleRoutes(
                                                    long planetID,
                                                    String tweak) {
    List<IPlanetaryRoute> routes = new ArrayList<IPlanetaryRoute>();
    for (int i = 0; i < testData.length; i++) {
      long nextPID = (Long) testData[i][0];
      if (nextPID == planetID) {
        Object[][] routeData = (Object[][]) testData[i][13];
        for (int j = 0; j < routeData.length; j++) {
          routes.add(makeRoute(routeData[j], 0, tweak));
        }
        break;
      }
    }

    return routes;
  }

  public PlanetaryColony makePlanetaryColonyObject(
                                                   long time,
                                                   final Object[] instanceData,
                                                   long pidAdjustment,
                                                   String tweak) throws Exception {
    IPlanetaryColony template = makeColony(instanceData, pidAdjustment, tweak);
    long pid = template.getPlanetID();
    PlanetaryColony colony = new PlanetaryColony(
        pid, template.getSolarSystemID(), template.getSolarSystemName(), template.getPlanetName(), template.getPlanetTypeID(), template.getPlanetTypeName(),
        template.getOwnerID(), template.getOwnerName(), template.getLastUpdate().getTime(), template.getUpgradeLevel(), template.getNumberOfPins());
    colony.setup(syncAccount, time);
    return colony;
  }

  public PlanetaryPin makePlanetaryPinObject(
                                             long time,
                                             final Object[] instanceData,
                                             long pid,
                                             long pidAdjustment,
                                             String tweak) throws Exception {
    IPlanetaryPin template = makePin(instanceData, pidAdjustment, tweak);
    PlanetaryPin pin = new PlanetaryPin(
        pid, template.getPinID(), template.getTypeID(), template.getTypeName(), template.getSchematicID(), template.getLastLaunchTime().getTime(),
        template.getCycleTime(), template.getQuantityPerCycle(), template.getInstallTime().getTime(), template.getExpiryTime().getTime(),
        template.getContentTypeID(), template.getContentTypeName(), template.getContentQuantity(), template.getLongitude(), template.getLatitude());
    pin.setup(syncAccount, time);
    return pin;
  }

  public PlanetaryRoute makePlanetaryRouteObject(
                                                 long time,
                                                 final Object[] instanceData,
                                                 long pid,
                                                 long pidAdjustment,
                                                 String tweak) throws Exception {
    IPlanetaryRoute template = makeRoute(instanceData, pidAdjustment, tweak);
    PlanetaryRoute route = new PlanetaryRoute(
        pid, template.getRouteID(), template.getSourcePinID(), template.getDestinationPinID(), template.getContentTypeID(), template.getContentTypeName(),
        template.getQuantity(), template.getWaypoint1(), template.getWaypoint2(), template.getWaypoint3(), template.getWaypoint4(), template.getWaypoint5());
    route.setup(syncAccount, time);
    return route;
  }

  public PlanetaryLink makePlanetaryLinkObject(
                                               long time,
                                               final Object[] instanceData,
                                               long pid,
                                               long pidAdjustment,
                                               String tweak) throws Exception {
    IPlanetaryLink template = makeLink(instanceData, pidAdjustment, tweak);
    PlanetaryLink link = new PlanetaryLink(pid, template.getSourcePinID(), template.getDestinationPinID(), template.getLinkLevel());
    link.setup(syncAccount, time);
    return link;
  }

  public void setupOkMock(
                          final long pidAdjustment,
                          final String tweak) throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);

    // There should be one request for colonies, followed at least one request for pins, links and routes for each colony.
    EasyMock.expect(mockServer.requestPlanetaryColonies()).andReturn(assembleColonies(pidAdjustment, tweak));
    EasyMock.expectLastCall().once();
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
    EasyMock.expectLastCall().anyTimes();

    for (int i = 0; i < testData.length; i++) {
      long pid = (Long) testData[i][0];
      EasyMock.expect(mockServer.requestPlanetaryLinks(pid)).andReturn(assembleLinks(pid, tweak));
      EasyMock.expectLastCall().once();
      EasyMock.expect(mockServer.requestPlanetaryRoutes(pid)).andReturn(assembleRoutes(pid, tweak));
      EasyMock.expectLastCall().once();
      EasyMock.expect(mockServer.requestPlanetaryPins(pid)).andReturn(assemblePins(pid, tweak));
      EasyMock.expectLastCall().once();
    }
  }

  public void checkNoColoniesFromTestData(
                                          long time,
                                          long pidAdjustment,
                                          String tweak) throws Exception {
    for (int i = 0; i < testData.length; i++) {
      long pid = (Long) testData[i][0] + pidAdjustment;
      PlanetaryColony check = PlanetaryColony.get(syncAccount, time, pid);
      Assert.assertNull(check);
      // Check pins
      Object[][] subData = (Object[][]) testData[i][11];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryPin.get(syncAccount, time, pid, (Long) subData[j][1], (Integer) subData[j][10]);
        Assert.assertNull(check);
      }
      // Check links
      subData = (Object[][]) testData[i][12];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryLink.get(syncAccount, time, pid, (Long) subData[j][1], (Long) subData[j][2]);
        Assert.assertNull(check);
      }
      // Check routes
      subData = (Object[][]) testData[i][13];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryRoute.get(syncAccount, time, pid, (Long) subData[j][1]);
        Assert.assertNull(check);
      }
    }
  }

  public void checkColoniesMatchTestData(
                                         long time,
                                         long pidAdjustment,
                                         String tweak) throws Exception {
    for (int i = 0; i < testData.length; i++) {
      // Check colony
      long pid = (Long) testData[i][0] + pidAdjustment;
      IPlanetaryColony next = makeColony(testData[i], pidAdjustment, tweak);
      PlanetaryColony check = PlanetaryColony.get(syncAccount, time, next.getPlanetID());
      Assert.assertNotNull(check);
      Assert.assertEquals(next.getPlanetID(), check.getPlanetID());
      Assert.assertEquals(next.getSolarSystemID(), check.getSolarSystemID());
      Assert.assertEquals(next.getSolarSystemName(), check.getSolarSystemName());
      Assert.assertEquals(next.getPlanetName(), check.getPlanetName());
      Assert.assertEquals(next.getPlanetTypeID(), check.getPlanetTypeID());
      Assert.assertEquals(next.getPlanetTypeName(), check.getPlanetTypeName());
      Assert.assertEquals(next.getOwnerID(), check.getOwnerID());
      Assert.assertEquals(next.getOwnerName(), check.getOwnerName());
      Assert.assertEquals(next.getLastUpdate().getTime(), check.getLastUpdate());
      Assert.assertEquals(next.getUpgradeLevel(), check.getUpgradeLevel());
      Assert.assertEquals(next.getNumberOfPins(), check.getNumberOfPins());
      // Check pins
      Object[][] subData = (Object[][]) testData[i][11];
      for (int j = 0; j < subData.length; j++) {
        IPlanetaryPin nextObj = makePin(subData[j], pidAdjustment, tweak);
        PlanetaryPin checkObj = PlanetaryPin.get(syncAccount, time, pid, nextObj.getPinID(), nextObj.getContentTypeID());
        Assert.assertNotNull(checkObj);
        Assert.assertEquals(pid, checkObj.getPlanetID());
        Assert.assertEquals(nextObj.getPinID(), checkObj.getPinID());
        Assert.assertEquals(nextObj.getTypeID(), checkObj.getTypeID());
        Assert.assertEquals(nextObj.getTypeName(), checkObj.getTypeName());
        Assert.assertEquals(nextObj.getSchematicID(), checkObj.getSchematicID());
        Assert.assertEquals(nextObj.getLastLaunchTime().getTime(), checkObj.getLastLaunchTime());
        Assert.assertEquals(nextObj.getCycleTime(), checkObj.getCycleTime());
        Assert.assertEquals(nextObj.getQuantityPerCycle(), checkObj.getQuantityPerCycle());
        Assert.assertEquals(nextObj.getInstallTime().getTime(), checkObj.getInstallTime());
        Assert.assertEquals(nextObj.getExpiryTime().getTime(), checkObj.getExpiryTime());
        Assert.assertEquals(nextObj.getContentTypeID(), checkObj.getContentTypeID());
        Assert.assertEquals(nextObj.getContentTypeName(), checkObj.getContentTypeName());
        Assert.assertEquals(nextObj.getContentQuantity(), checkObj.getContentQuantity());
        Assert.assertEquals(nextObj.getLongitude(), checkObj.getLongitude(), 0.0);
        Assert.assertEquals(nextObj.getLatitude(), checkObj.getLatitude(), 0.0);
      }
      // Check links
      subData = (Object[][]) testData[i][12];
      for (int j = 0; j < subData.length; j++) {
        IPlanetaryLink nextObj = makeLink(subData[j], pidAdjustment, tweak);
        PlanetaryLink checkObj = PlanetaryLink.get(syncAccount, time, pid, nextObj.getSourcePinID(), nextObj.getDestinationPinID());
        Assert.assertNotNull(checkObj);
        Assert.assertEquals(pid, checkObj.getPlanetID());
        Assert.assertEquals(nextObj.getSourcePinID(), checkObj.getSourcePinID());
        Assert.assertEquals(nextObj.getDestinationPinID(), checkObj.getDestinationPinID());
        Assert.assertEquals(nextObj.getLinkLevel(), checkObj.getLinkLevel());
      }
      // Check routes
      subData = (Object[][]) testData[i][13];
      for (int j = 0; j < subData.length; j++) {
        IPlanetaryRoute nextObj = makeRoute(subData[j], pidAdjustment, tweak);
        PlanetaryRoute checkObj = PlanetaryRoute.get(syncAccount, time, pid, nextObj.getRouteID());
        Assert.assertNotNull(checkObj);
        Assert.assertEquals(pid, checkObj.getPlanetID());
        Assert.assertEquals(nextObj.getRouteID(), checkObj.getRouteID());
        Assert.assertEquals(nextObj.getSourcePinID(), checkObj.getSourcePinID());
        Assert.assertEquals(nextObj.getDestinationPinID(), checkObj.getDestinationPinID());
        Assert.assertEquals(nextObj.getContentTypeID(), checkObj.getContentTypeID());
        Assert.assertEquals(nextObj.getContentTypeName(), checkObj.getContentTypeName());
        Assert.assertEquals(nextObj.getQuantity(), checkObj.getQuantity());
        Assert.assertEquals(nextObj.getWaypoint1(), checkObj.getWaypoint1());
        Assert.assertEquals(nextObj.getWaypoint2(), checkObj.getWaypoint2());
        Assert.assertEquals(nextObj.getWaypoint3(), checkObj.getWaypoint3());
        Assert.assertEquals(nextObj.getWaypoint4(), checkObj.getWaypoint4());
        Assert.assertEquals(nextObj.getWaypoint5(), checkObj.getWaypoint5());
      }
    }
  }

  // Test update with all new colonies
  @Test
  public void testPlanetaryColoniesSyncUpdate() throws Exception {
    setupOkMock(0, "");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterPlanetaryColoniesSync.syncPlanetaryColonies(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify colonies were added correctly.
    checkColoniesMatchTestData(testTime, 0, "");

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getPlanetaryColoniesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPlanetaryColoniesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPlanetaryColoniesDetail());
  }

  // Test update with colonies already populated
  @Test
  public void testPlanetaryColoniesSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock(0, "");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing colonies which should not be modified.
    for (int i = 0; i < testData.length; i++) {
      // Make colony
      long pid = (Long) testData[i][0];
      PlanetaryColony colony = makePlanetaryColonyObject(testTime, testData[i], 25, "foo");
      colony = CachedData.updateData(colony);
      // Make pins
      Object[][] subData = (Object[][]) testData[i][11];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryPin pin = makePlanetaryPinObject(testTime, subData[j], pid + 25, 0, "foo");
        pin = CachedData.updateData(pin);
      }
      // Make links
      subData = (Object[][]) testData[i][12];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryLink link = makePlanetaryLinkObject(testTime, subData[j], pid + 25, 0, "foo");
        link = CachedData.updateData(link);
      }
      // Make routes
      subData = (Object[][]) testData[i][13];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryRoute route = makePlanetaryRouteObject(testTime, subData[j], pid + 25, 0, "foo");
        route = CachedData.updateData(route);
      }
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterPlanetaryColoniesSync.syncPlanetaryColonies(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify previously added colonies are updated.
    checkColoniesMatchTestData(testTime, 0, "");

    // Verify old colonies were removed.
    checkNoColoniesFromTestData(testTime, 25, "foo");

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getPlanetaryColoniesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPlanetaryColoniesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPlanetaryColoniesDetail());
  }

  // Test skips update when already updated
  @Test
  public void testPlanetaryColoniesSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock(0, "");
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing colonies with adjusted planet IDs.
    for (int i = 0; i < testData.length; i++) {
      // Make colony
      long pid = (Long) testData[i][0];
      PlanetaryColony colony = makePlanetaryColonyObject(testTime, testData[i], 25, "foo");
      colony = CachedData.updateData(colony);
      // Make pins
      Object[][] subData = (Object[][]) testData[i][11];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryPin pin = makePlanetaryPinObject(testTime, subData[j], pid + 25, 0, "foo");
        pin = CachedData.updateData(pin);
      }
      // Make links
      subData = (Object[][]) testData[i][12];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryLink link = makePlanetaryLinkObject(testTime, subData[j], pid + 25, 0, "foo");
        link = CachedData.updateData(link);
      }
      // Make routes
      subData = (Object[][]) testData[i][13];
      for (int j = 0; j < subData.length; j++) {
        PlanetaryRoute route = makePlanetaryRouteObject(testTime, subData[j], pid + 25, 0, "foo");
        route = CachedData.updateData(route);
      }
    }

    // Set the tracker as already updated and populate the container
    tracker.setPlanetaryColoniesStatus(SyncState.UPDATED);
    tracker.setPlanetaryColoniesDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setPlanetaryColoniesExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterPlanetaryColoniesSync.syncPlanetaryColonies(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify previously added colonies are unmodified.
    checkColoniesMatchTestData(testTime, 25, "foo");

    // Verify no colonies were added from unmodified test data.
    checkNoColoniesFromTestData(testTime, 0, "");

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getPlanetaryColoniesExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPlanetaryColoniesStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPlanetaryColoniesDetail());
  }

}
