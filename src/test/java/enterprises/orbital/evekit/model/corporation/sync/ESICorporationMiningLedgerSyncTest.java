package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.IndustryApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationCorporationIdMiningExtractions200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationCorporationIdMiningObservers200Ok;
import enterprises.orbital.eve.esi.client.model.GetCorporationCorporationIdMiningObserversObserverId200Ok;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.corporation.MiningExtraction;
import enterprises.orbital.evekit.model.corporation.MiningObservation;
import enterprises.orbital.evekit.model.corporation.MiningObserver;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ESICorporationMiningLedgerSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private IndustryApi mockEndpoint;
  private long testTime = 1238L;

  private static Object[][] extractionTestData;
  private static int[] extractionPages;
  private static Object[][] observerTestData;
  private static int[] observerPages;

  static {
    // MiningExtraction data
    //
    // 0 int moonID;
    // 1 long structureID;
    // 2 long extractionStartTime;
    // 3 long chunkArrivalTime;
    // 4 long naturalDecayTime;
    int size = 100 + TestBase.getRandomInt(100);
    extractionTestData = new Object[size][5];
    for (int i = 0; i < size; i++) {
      extractionTestData[i][0] = TestBase.getUniqueRandomInteger();
      extractionTestData[i][1] = TestBase.getRandomLong();
      extractionTestData[i][2] = TestBase.getRandomLong();
      extractionTestData[i][3] = TestBase.getRandomLong();
      extractionTestData[i][4] = TestBase.getRandomLong();
    }

    // Divide data into pages
    int pageCount = 3 + TestBase.getRandomInt(3);
    extractionPages = new int[pageCount];
    for (int i = pageCount - 1; i >= 0; i--) {
      extractionPages[i] = size - (pageCount - i - 1) * (size / pageCount);
    }

    // MiningObserver data
    //
    // 0 long observerID;
    // 1 String observerType;
    // 2 long lastUpdated;
    // 3 MiningObservation
    // 4 MiningObservation pages
    int obsSize = 100 + TestBase.getRandomInt(100);
    int obsTypeLen = GetCorporationCorporationIdMiningObservers200Ok.ObserverTypeEnum.values().length;
    observerTestData = new Object[obsSize][5];
    for (int i = 0; i < obsSize; i++) {
      observerTestData[i][0] = TestBase.getUniqueRandomLong();
      observerTestData[i][1] = GetCorporationCorporationIdMiningObservers200Ok.ObserverTypeEnum.values()[TestBase.getRandomInt(
          obsTypeLen)];
      LocalDate dt = LocalDate.fromDateFields(new Date(TestBase.getRandomLong()));
      observerTestData[i][2] = dt.toDate()
                                 .getTime();

      // MiningObservation data
      //
      // 0 long observerID;
      // 1 int characterID;
      // 2 int typeID;
      // 3 int recordedCorporationID;
      // 4 long quantity;
      // 5 long lastUpdated;
      int obvCount = 50 + TestBase.getRandomInt(50);
      Object[][] obvList = new Object[obvCount][6];
      observerTestData[i][3] = obvList;
      for (int j = 0; j < obvCount; j++) {
        obvList[j][0] = observerTestData[i][0];
        obvList[j][1] = TestBase.getUniqueRandomInteger();
        obvList[j][2] = TestBase.getRandomInt();
        obvList[j][3] = TestBase.getRandomInt();
        obvList[j][4] = TestBase.getRandomLong();
        LocalDate dt2 = LocalDate.fromDateFields(new Date(TestBase.getRandomLong()));
        obvList[j][5] = dt2.toDate()
                           .getTime();
      }

      int obvPageCount = 3 + TestBase.getRandomInt(3);
      int[] obvPages = new int[obvPageCount];
      observerTestData[i][4] = obvPages;
      for (int j = obvPageCount - 1; j >= 0; j--) {
        obvPages[j] = obvCount - (obvPageCount - j - 1) * (obvCount / obvPageCount);
      }

    }

    // Divide data into pages
    int obsPageCount = 3 + TestBase.getRandomInt(3);
    observerPages = new int[obsPageCount];
    for (int i = obsPageCount - 1; i >= 0; i--) {
      observerPages[i] = obsSize - (obsPageCount - i - 1) * (obsSize / obsPageCount);
    }

  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MINING, 1234L,
                                                        null);

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
                                                        .createQuery("DELETE FROM MiningExtraction ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MiningObserver ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM MiningObservation ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {

    mockEndpoint = EasyMock.createMock(IndustryApi.class);

    List<GetCorporationCorporationIdMiningExtractions200Ok> extractionList =
        Arrays.stream(extractionTestData)
              .map(dt -> {
                GetCorporationCorporationIdMiningExtractions200Ok nextExtraction = new GetCorporationCorporationIdMiningExtractions200Ok();
                nextExtraction.setMoonId((int) dt[0]);
                nextExtraction.setStructureId((long) dt[1]);
                nextExtraction.setExtractionStartTime(new DateTime(new Date((long) dt[2])));
                nextExtraction.setChunkArrivalTime(new DateTime(new Date((long) dt[3])));
                nextExtraction.setNaturalDecayTime(new DateTime(new Date((long) dt[4])));
                return nextExtraction;
              })
              .collect(Collectors.toList());

    int last = 0;
    for (int i = 0; i < extractionPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(extractionPages.length));
      ApiResponse<List<GetCorporationCorporationIdMiningExtractions200Ok>> apir = new ApiResponse<>(200,
                                                                                                    headers,
                                                                                                    extractionList.subList(
                                                                                                        last,
                                                                                                        extractionPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationCorporationIdMiningExtractionsWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(apir);
      last = extractionPages[i];
    }

    List<GetCorporationCorporationIdMiningObservers200Ok> observerList =
        Arrays.stream(observerTestData)
              .map(dt -> {
                GetCorporationCorporationIdMiningObservers200Ok nextObserver = new GetCorporationCorporationIdMiningObservers200Ok();
                nextObserver.setObserverId((long) dt[0]);
                nextObserver.setObserverType((GetCorporationCorporationIdMiningObservers200Ok.ObserverTypeEnum) dt[1]);
                nextObserver.setLastUpdated(LocalDate.fromDateFields(new Date((long) dt[2])));
                return nextObserver;
              })
              .collect(Collectors.toList());

    last = 0;
    for (int i = 0; i < observerPages.length; i++) {
      Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                        String.valueOf(observerPages.length));
      ApiResponse<List<GetCorporationCorporationIdMiningObservers200Ok>> apir = new ApiResponse<>(200,
                                                                                                  headers,
                                                                                                  observerList.subList(
                                                                                                      last,
                                                                                                      observerPages[i]));
      EasyMock.expect(mockEndpoint.getCorporationCorporationIdMiningObserversWithHttpInfo(
          EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.eq(i + 1),
          EasyMock.anyString()))
              .andReturn(apir);

      for (int j = last; j < observerPages[i]; j++) {
        List<GetCorporationCorporationIdMiningObserversObserverId200Ok> observationList =
            Arrays.stream((Object[][]) observerTestData[j][3])
                  .map(dt -> {
                    GetCorporationCorporationIdMiningObserversObserverId200Ok nextObservation = new GetCorporationCorporationIdMiningObserversObserverId200Ok();
                    nextObservation.setCharacterId((int) dt[1]);
                    nextObservation.setTypeId((int) dt[2]);
                    nextObservation.setRecordedCorporationId((int) dt[3]);
                    nextObservation.setQuantity((long) dt[4]);
                    nextObservation.setLastUpdated(LocalDate.fromDateFields(new Date((long) dt[5])));
                    return nextObservation;
                  })
                  .collect(Collectors.toList());

        int[] obvPages = (int[]) observerTestData[j][4];
        int obvLast = 0;
        for (int k = 0; k < obvPages.length; k++) {
          Map<String, List<String>> obvHeaders = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT", "X-Pages",
                                                               String.valueOf(obvPages.length));
          ApiResponse<List<GetCorporationCorporationIdMiningObserversObserverId200Ok>> obvApir = new ApiResponse<>(200,
                                                                                                                   obvHeaders,
                                                                                                                   observationList.subList(
                                                                                                                       obvLast,
                                                                                                                       obvPages[k]));
          EasyMock.expect(mockEndpoint.getCorporationCorporationIdMiningObserversObserverIdWithHttpInfo(
              EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
              EasyMock.eq((long) observerTestData[j][0]),
              EasyMock.isNull(),
              EasyMock.isNull(),
              EasyMock.eq(k + 1),
              EasyMock.anyString()))
                  .andReturn(obvApir);

          obvLast = obvPages[k];
        }

      }

      last = observerPages[i];
    }

    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getIndustryApi())
            .andReturn(mockEndpoint);
  }

  @SuppressWarnings("Duplicates")
  private void verifyDataUpdate(long time, Object[][] testExtractionsData,
                                Object[][] testObserversData) throws Exception {
    // Retrieve all stored data
    List<MiningExtraction> storedExtraction = AbstractESIAccountSync.retrieveAll(time,
                                                                                 (long contid, AttributeSelector at) ->
                                                                                     MiningExtraction.accessQuery(
                                                                                         corpSyncAccount, contid, 1000,
                                                                                         false, at,
                                                                                         AbstractESIAccountSync.ANY_SELECTOR,
                                                                                         AbstractESIAccountSync.ANY_SELECTOR,
                                                                                         AbstractESIAccountSync.ANY_SELECTOR,
                                                                                         AbstractESIAccountSync.ANY_SELECTOR,
                                                                                         AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testExtractionsData.length, storedExtraction.size());

    // Check stored data
    for (int i = 0; i < testExtractionsData.length; i++) {
      MiningExtraction nextEl = storedExtraction.get(i);
      Object[] dt = testExtractionsData[i];
      Assert.assertEquals((int) dt[0], nextEl.getMoonID());
      Assert.assertEquals((long) dt[1], nextEl.getStructureID());
      Assert.assertEquals((long) dt[2], nextEl.getExtractionStartTime());
      Assert.assertEquals((long) dt[3], nextEl.getChunkArrivalTime());
      Assert.assertEquals((long) dt[4], nextEl.getNaturalDecayTime());
    }

    // Retrieve all stored data
    List<MiningObserver> storedObserver = AbstractESIAccountSync.retrieveAll(time,
                                                                             (long contid, AttributeSelector at) ->
                                                                                 MiningObserver.accessQuery(
                                                                                     corpSyncAccount, contid, 1000,
                                                                                     false, at,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(testObserversData.length, storedObserver.size());

    // Check stored data
    for (int i = 0; i < testObserversData.length; i++) {
      MiningObserver nextEl = storedObserver.get(i);
      Object[] dt = testObserversData[i];
      Assert.assertEquals((long) dt[0], nextEl.getObserverID());
      Assert.assertEquals(String.valueOf(dt[1]), nextEl.getObserverType());
      Assert.assertEquals((long) dt[2], nextEl.getLastUpdated());

      Object[][] obvData = (Object[][]) dt[3];
      List<MiningObservation> storedObservation = AbstractESIAccountSync.retrieveAll(time,
                                                                                     (long contid, AttributeSelector at) ->
                                                                                         MiningObservation.accessQuery(
                                                                                             corpSyncAccount, contid,
                                                                                             1000, false, at,
                                                                                             AttributeSelector.values(nextEl.getObserverID()),
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR,
                                                                                             AbstractESIAccountSync.ANY_SELECTOR));
      Assert.assertEquals(obvData.length, storedObservation.size());
      for (int j = 0; j < obvData.length; j++) {
        MiningObservation nextObv = storedObservation.get(j);
        Object[] el = obvData[j];
        Assert.assertEquals((long) dt[0], nextObv.getObserverID());
        Assert.assertEquals((int) el[1], nextObv.getCharacterID());
        Assert.assertEquals((int) el[2], nextObv.getTypeID());
        Assert.assertEquals((int) el[3], nextObv.getRecordedCorporationID());
        Assert.assertEquals((long) el[4], nextObv.getQuantity());
        Assert.assertEquals((long) el[5], nextObv.getLastUpdated());
      }

    }

  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationMiningLedgerSync sync = new ESICorporationMiningLedgerSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate(testTime, extractionTestData, observerTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MINING);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MINING);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate historic data that should be updated.
    Object[][] newExtractionData = new Object[extractionTestData.length][5];
    for (int i = 0; i < newExtractionData.length; i++) {
      newExtractionData[i][0] = extractionTestData[i][0];
      newExtractionData[i][1] = extractionTestData[i][1];
      newExtractionData[i][2] = extractionTestData[i][2];
      newExtractionData[i][3] = (long) extractionTestData[i][3] + 1;
      newExtractionData[i][4] = (long) extractionTestData[i][4] + 1;

      MiningExtraction newEl = new MiningExtraction((int) newExtractionData[i][0],
                                                    (long) newExtractionData[i][1],
                                                    (long) newExtractionData[i][2],
                                                    (long) newExtractionData[i][3],
                                                    (long) newExtractionData[i][4]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);
    }

    Object[][] newObserverData = new Object[observerTestData.length][5];
    for (int i = 0; i < newObserverData.length; i++) {
      newObserverData[i][0] = observerTestData[i][0];
      newObserverData[i][1] = observerTestData[i][1];
      newObserverData[i][2] = (long) observerTestData[i][2] + 1;

      MiningObserver newEl = new MiningObserver((long) newObserverData[i][0],
                                                String.valueOf(newObserverData[i][1]),
                                                (long) newObserverData[i][2]);
      newEl.setup(corpSyncAccount, testTime - 1);
      CachedData.update(newEl);

      Object[][] oldObvData = (Object[][]) observerTestData[i][3];
      Object[][] obvData = new Object[oldObvData.length][6];
      newObserverData[i][3] = obvData;
      for (int j = 0; j < obvData.length; j++) {
        obvData[j][0] = oldObvData[j][0];
        obvData[j][1] = oldObvData[j][1];
        obvData[j][2] = oldObvData[j][2];
        obvData[j][3] = (int) oldObvData[j][3] + 1;
        obvData[j][4] = (long) oldObvData[j][4] + 1;
        obvData[j][5] = (long) oldObvData[j][5] + 1;

        MiningObservation obsEl = new MiningObservation((long) obvData[j][0],
                                                        (int) obvData[j][1],
                                                        (int) obvData[j][2],
                                                        (int) obvData[j][3],
                                                        (long) obvData[j][4],
                                                        (long) obvData[j][5]);
        obsEl.setup(corpSyncAccount, testTime - 1);
        CachedData.update(obsEl);

      }
    }

    // Perform the sync
    ESICorporationMiningLedgerSync sync = new ESICorporationMiningLedgerSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updates
    verifyDataUpdate(testTime - 1, newExtractionData, newObserverData);
    verifyDataUpdate(testTime, extractionTestData, observerTestData);

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_MINING);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_MINING);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
