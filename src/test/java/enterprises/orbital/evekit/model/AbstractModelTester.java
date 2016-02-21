package enterprises.orbital.evekit.model;

import java.util.Arrays;

import org.junit.Assert;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;

public class AbstractModelTester<A extends CachedData> extends AbstractAccountBasedTest {

  public interface ClassUnderTestConstructor<A> {
    public A getCUT();
  }

  public interface ModelRetriever<A> {
    public A getModel(SynchronizedEveAccount account, long time);
  }

  public interface CtorVariants<A> {
    public A[] getVariants();
  }

  // Standard checks for all model types:
  //
  // @formatter:off
  // 1) Equiv check is correct
  // 2) Init sets proper owner, mask and timeline
  // 3) Get retrieval works for (this test uses two elements):
  //    a) EOL element at a given time
  //    b) Current live element
  //    c) No element at the given time
  // 4) Test other non-standard retrieval variants
  // @formatter:on

  protected void runBasicTests(ClassUnderTestConstructor<A> ctor, CtorVariants<A> vars, byte[] mask) {
    long time = TestBase.getRandomInt(100000000) + 5000L;
    A cut = ctor.getCUT();
    cut.setup(testAccount, time);

    A equiv = ctor.getCUT();
    equiv.setup(testAccount, time);
    Assert.assertTrue(cut.equivalent(equiv));
    for (A next : vars.getVariants()) {
      next.setup(testAccount, time);
      Assert.assertFalse(cut.equivalent(next));
    }

    Assert.assertEquals(testAccount, cut.getOwner());
    Assert.assertTrue(Arrays.equals(mask, cut.getAccessMask()));
    Assert.assertEquals(time, cut.getLifeStart());
    Assert.assertEquals(Long.MAX_VALUE, cut.getLifeEnd());
  }

  protected void runGetLifelineTest(ClassUnderTestConstructor<A> eolMaker, ClassUnderTestConstructor<A> liveMaker, ModelRetriever<A> modelGetter) {
    A eol, live;
    long t1 = TestBase.getRandomInt(10000000) + 5000L;
    long t2 = t1 + TestBase.getRandomInt(10000) + 5000L;
    eol = eolMaker.getCUT();
    eol.setup(testAccount, t1);
    live = liveMaker.getCUT();
    live.setup(testAccount, t2);
    eol.evolve(live, t2);
    eol = CachedData.updateData(eol);
    live = CachedData.updateData(live);

    A eolCheck = modelGetter.getModel(testAccount, t1 + 5);
    A liveCheck = modelGetter.getModel(testAccount, t2 + 5);
    A missingCheck = modelGetter.getModel(testAccount, t1 - 5);

    Assert.assertNotNull(eolCheck);
    Assert.assertEquals(eol, eolCheck);
    Assert.assertNotNull(liveCheck);
    Assert.assertEquals(live, liveCheck);
    Assert.assertNull(missingCheck);
  }

}
