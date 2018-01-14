package enterprises.orbital.evekit.model;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.db.DBPropertyProvider;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;

public abstract class AbstractAccountBasedTest {
  public EveKitUserAccount      userAccount;
  public EveKitUserAccount      userAccount2;
  public SynchronizedEveAccount testAccount;
  public SynchronizedEveAccount testAccount2;
  public SynchronizedEveAccount otherAccount;

  @Before
  public void setUp() throws Exception {
    OrbitalProperties.addPropertyFile("SyncTest.properties");
    PersistentProperty.setProvider(new DBPropertyProvider(OrbitalProperties.getGlobalProperty(EveKitUserAccountProvider.USER_ACCOUNT_PU_PROP)));
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    userAccount2 = EveKitUserAccount.createNewUserAccount(false, true);
    testAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true);
    otherAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "otheraccount", true);
    testAccount2 = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount2, "testaccount2", true);
  }

  @After
  public void tearDown() throws IOException {}
}
