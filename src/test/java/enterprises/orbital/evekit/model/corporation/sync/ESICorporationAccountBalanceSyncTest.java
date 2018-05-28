package enterprises.orbital.evekit.model.corporation.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.WalletApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCorporationsCorporationIdWallets200Ok;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.common.AccountBalance;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ESICorporationAccountBalanceSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private WalletApi mockEndpoint;
  private long testTime = 1238L;

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_BALANCE, 1234L,
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
                                                        .createQuery("DELETE FROM AccountBalance ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  private static Object[][] mockData = {
      {
          1, (new BigDecimal(12994.75)).setScale(2, RoundingMode.HALF_UP)
      }, {
      2, (new BigDecimal(82114.78)).setScale(2, RoundingMode.HALF_UP)
  }, {
      3, (new BigDecimal(85.23)).setScale(2, RoundingMode.HALF_UP)
  }, {
      4, (new BigDecimal(0)).setScale(2, RoundingMode.HALF_UP)
  }, {
      5, (new BigDecimal(88313.21)).setScale(2, RoundingMode.HALF_UP)
  }, {
      6, (new BigDecimal(459988234.12)).setScale(2, RoundingMode.HALF_UP)
  }, {
      7, (new BigDecimal(984123483.20)).setScale(2, RoundingMode.HALF_UP)
  }, {
      8, (new BigDecimal(0.0)).setScale(2, RoundingMode.HALF_UP)
  }
  };

  // Mock up server interface
  private void setupOkMock() throws Exception {
    List<GetCorporationsCorporationIdWallets200Ok> balances = Arrays.stream(mockData)
                                                                    .map(x -> {
                                                                      GetCorporationsCorporationIdWallets200Ok wal = new GetCorporationsCorporationIdWallets200Ok();
                                                                      wal.setDivision((int) x[0]);
                                                                      wal.setBalance(((BigDecimal) x[1]).doubleValue());
                                                                      return wal;
                                                                    })
                                                                    .collect(Collectors.toList());
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    ApiResponse<List<GetCorporationsCorporationIdWallets200Ok>> apir = new ApiResponse<>(200, headers, balances);
    mockEndpoint = EasyMock.createMock(WalletApi.class);
    EasyMock.expect(mockEndpoint.getCorporationsCorporationIdWalletsWithHttpInfo(
        EasyMock.eq((int) corpSyncAccount.getEveCorporationID()),
        EasyMock.isNull(),
        EasyMock.isNull(),
        EasyMock.anyString()))
            .andReturn(apir);
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getWalletApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    List<AccountBalance> storedBalances = AbstractESIAccountSync.retrieveAll(testTime,
                                                                             (long contid, AttributeSelector at) ->
                                                                                 AccountBalance.accessQuery(
                                                                                     corpSyncAccount, contid, 1000,
                                                                                     false, at,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR,
                                                                                     AbstractESIAccountSync.ANY_SELECTOR));

    Assert.assertEquals(mockData.length, storedBalances.size());

    for (int i = 0; i < mockData.length; i++) {
      AccountBalance nextBalance = storedBalances.get(i);
      Assert.assertEquals((int) (Integer) mockData[i][0], nextBalance.getDivision());
      Assert.assertEquals(mockData[i][1], nextBalance.getBalance());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICorporationWalletBalanceSync sync = new ESICorporationWalletBalanceSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_WALLET_BALANCE);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_BALANCE);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    for (Object[] next : mockData) {
      AccountBalance existing = new AccountBalance((int) next[0], ((BigDecimal) next[1]).add(BigDecimal.TEN));
      existing.setup(corpSyncAccount, testTime - 1);
      CachedData.update(existing);
    }

    // Perform the sync
    ESICorporationWalletBalanceSync sync = new ESICorporationWalletBalanceSync(corpSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    List<AccountBalance> oldBalances = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                          (long contid, AttributeSelector at) ->
                                                                              AccountBalance.accessQuery(
                                                                                  corpSyncAccount, contid, 1000, false,
                                                                                  at,
                                                                                  AbstractESIAccountSync.ANY_SELECTOR,
                                                                                  AbstractESIAccountSync.ANY_SELECTOR));

    Assert.assertEquals(mockData.length, oldBalances.size());

    for (int i = 0; i < mockData.length; i++) {
      AccountBalance nextBalance = oldBalances.get(i);
      Assert.assertEquals((int) (Integer) mockData[i][0], nextBalance.getDivision());
      Assert.assertEquals(mockData[i][1], nextBalance.getBalance()
                                                     .subtract(BigDecimal.TEN));
      Assert.assertEquals(testTime - 1, nextBalance.getLifeStart());
      Assert.assertEquals(testTime, nextBalance.getLifeEnd());
    }

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(corpSyncAccount,
                                                                                         ESISyncEndpoint.CORP_WALLET_BALANCE);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(corpSyncAccount, ESISyncEndpoint.CORP_WALLET_BALANCE);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
