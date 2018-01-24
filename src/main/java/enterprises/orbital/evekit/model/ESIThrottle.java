package enterprises.orbital.evekit.model;

import com.sun.org.apache.xpath.internal.operations.Or;
import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to enforce specific request rates.  The API maps an arbitrary string key and
 * a synchronized account to a token bucket which is used to enforce rate.  Each token
 * bucket has a default rate that can be customized according to the string key and/or
 * the specific account.
 */
public class ESIThrottle {

  // Default rate throttle
  private static final String PROP_DEFAULT_ESI_RATE = "enterprises.orbital.evekit.sync.default_esi_rate";
  private static final int DEF_DEFAULT_ESI_RATE = 3; // 3 calls/second

  // Throttle map
  private static Map<String, Map<SynchronizedEveAccount, Bucket>> throttleMap = new HashMap<>();

  // Singleton
  private ESIThrottle() {}

  protected static Bucket get(String cls, SynchronizedEveAccount acct) {
    synchronized (throttleMap) {
      Map<SynchronizedEveAccount, Bucket> bMap = throttleMap.get(cls);
      if (bMap == null) {
        bMap = new HashMap<>();
        throttleMap.put(cls, bMap);
      }
      Bucket b = bMap.get(acct);
      if (b == null) {
        int rate = PersistentProperty.getIntegerPropertyWithFallback(acct, cls + "_esi_rate",
                                                                   PersistentProperty.getIntegerPropertyWithFallback(PROP_DEFAULT_ESI_RATE + "." + cls,
                                                                                                                  PersistentProperty.getIntegerPropertyWithFallback(PROP_DEFAULT_ESI_RATE, DEF_DEFAULT_ESI_RATE)));
        Bandwidth limit = Bandwidth.simple(rate, Duration.ofSeconds(1));
        b = Bucket4j.builder().addLimit(limit).build();
        bMap.put(acct, b);
      }
      return b;
    }
  }

  public static void throttle(String cls, SynchronizedEveAccount acct) {
    get(cls, acct).tryConsume(1);
  }
}
