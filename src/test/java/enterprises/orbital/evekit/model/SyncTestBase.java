package enterprises.orbital.evekit.model;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import java.util.*;

public class SyncTestBase {

  protected EveKitUserAccount testUserAccount;
  protected SynchronizedEveAccount charSyncAccount;
  protected SynchronizedEveAccount corpSyncAccount;

  @Before
  public void setup() throws Exception {
    OrbitalProperties.addPropertyFile("SyncTest.properties");
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(EveKitUserAccountProvider.USER_ACCOUNT_PU_PROP)));
    testUserAccount = EveKitUserAccount.createNewUserAccount(true, true);
    charSyncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(testUserAccount, "chartestaccount", true, true);
    corpSyncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(testUserAccount, "corptestaccount", false, true);
    String charScopes = Arrays.stream(ESIScope.getCharScopes())
                              .map(x -> x.getName())
                              .reduce("", (a, b) -> a + " " + b);
    charSyncAccount = SynchronizedEveAccount.setESICredential(testUserAccount, charSyncAccount.getAid(),
                                                              "TESTACCESSTOKEN", System.currentTimeMillis(),
                                                              "TESTREFRESHTOKEN", charScopes,
                                                              1234L, "testcharname",
                                                              5678L, "testcorpname");
    String corpScopes = Arrays.stream(ESIScope.getCorpScopes())
                              .map(x -> x.getName())
                              .reduce("", (a, b) -> a + " " + b);
    corpSyncAccount = SynchronizedEveAccount.setESICredential(testUserAccount, corpSyncAccount.getAid(),
                                                              "TESTACCESSTOKEN", System.currentTimeMillis(),
                                                              "TESTREFRESHTOKEN", corpScopes,
                                                              1234L, "testcharname",
                                                              5678L, "testcorpname");
  }

  @After
  public void teardown() throws Exception {
    // Cleanup RefTracker, RefData and test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createQuery("DELETE FROM ESIEndpointSyncTracker")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createQuery("DELETE FROM CachedData")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createQuery("DELETE FROM SyncTracker")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createQuery("DELETE FROM SynchronizedEveAccount")
                                                                            .executeUpdate());
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> EveKitUserAccountProvider.getFactory()
                                                                            .getEntityManager()
                                                                            .createQuery("DELETE FROM EveKitUserAccount")
                                                                            .executeUpdate());
  }

  protected interface BatchRetriever<A extends CachedData> {
    List<A> getNextBatch(List<A> lastBatch);
  }

  protected <A extends CachedData> List<A> retrieveAll(
      BatchRetriever<A> retriever) {
    List<A> results = new ArrayList<>();
    List<A> nextBatch = retriever.getNextBatch(Collections.emptyList());
    while (!nextBatch.isEmpty()) {
      results.addAll(nextBatch);
      nextBatch = retriever.getNextBatch(nextBatch);
    }
    return results;
  }

  protected static Map<String, List<String>> createHeaders(String... pairs) {
    Assert.assertTrue(pairs.length % 2 == 0);
    Map<String, List<String>> mm = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      mm.put(pairs[i], Collections.singletonList(pairs[i + 1]));
    }
    return mm;
  }

}
