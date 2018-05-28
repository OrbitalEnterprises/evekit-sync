package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.ClonesApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.Implant;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterSheetImplantsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private ClonesApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[] implantTestData;

  static {
    // Implant test data
    int size = 10 + TestBase.getRandomInt(10);
    implantTestData = new Object[size];
    for (int i = 0; i < size; i++) {
      implantTestData[i] = TestBase.getUniqueRandomInteger();
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_IMPLANTS, 1234L, null);

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
                                                        .createQuery("DELETE FROM Implant ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(ClonesApi.class);

    // Set up implant list
    List<Integer> implants = Arrays.stream(implantTestData)
                                   .map(x -> (int) x)
                                   .collect(Collectors.toList());

    // Setup mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<Integer>> apir = new ApiResponse<>(200, headers, implants);
    EasyMock.expect(mockEndpoint.getCharactersCharacterIdImplantsWithHttpInfo(
        EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString()))
            .andReturn(apir);
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getClonesApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Retrieve all stored data
    List<Implant> storedData = AbstractESIAccountSync.retrieveAll(testTime,
                                                                  (long contid, AttributeSelector at) ->
                                                                      Implant.accessQuery(charSyncAccount,
                                                                                          contid, 1000,
                                                                                          false, at,
                                                                                          AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(implantTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < implantTestData.length; i++) {
      Implant nextEl = storedData.get(i);
      Assert.assertEquals((int) implantTestData[i], nextEl.getTypeID());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterSheetImplantsSync sync = new ESICharacterSheetImplantsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_IMPLANTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_IMPLANTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // All of these should be removed on update.
    //
    int[] oldData = new int[implantTestData.length];
    for (int i = 0; i < oldData.length; i++) {
      oldData[i] = TestBase.getUniqueRandomInteger();
    }
    for (int ip : oldData) {
      Implant storedImplant = new Implant(ip);
      storedImplant.setup(charSyncAccount, testTime - 1);
      CachedData.update(storedImplant);
    }

    // Perform the sync
    ESICharacterSheetImplantsSync sync = new ESICharacterSheetImplantsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<Implant> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                              (long contid, AttributeSelector at) ->
                                                                  Implant.accessQuery(charSyncAccount,
                                                                                      contid, 1000, false,
                                                                                      at,
                                                                                      AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(oldData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < oldData.length; i++) {
      Implant nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals(oldData[i], nextEl.getTypeID());
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_IMPLANTS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_IMPLANTS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
