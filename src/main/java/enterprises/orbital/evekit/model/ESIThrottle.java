package enterprises.orbital.evekit.model;

import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to enforce specific request rates.  The API maps an arbitrary string key and
 * a synchronized account to a token bucket which is used to enforce rate.  Each token
 * bucket has a default rate that can be customized according to the string key and/or
 * the specific account.
 */
public class ESIThrottle {
  private static final Logger log = Logger.getLogger(ESIThrottle.class.getName());

  // Default rate throttle
  private static final String PROP_DEFAULT_ESI_RATE = "enterprises.orbital.evekit.sync.default_esi_rate";
  private static final int DEF_DEFAULT_ESI_RATE = 3; // 3 calls/second

  // Default error limit remaining threshold
  private static final String PROP_DEFAULT_ERROR_LIMIT_REMAIN = "enterprises.orbital.evekit.sync.default_esi_error_limit_remain";
  private static final int DEF_DEFAULT_ERROR_LIMIT_REMAIN = 5;

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

  protected static int extractErrorLimitRemain(ApiException e, int def) {
    try {
      String expireHeader = e.getResponseHeaders()
                                  .get("X-Esi-Error-Limit-Remain")
                                  .get(0);
      return Integer.valueOf(expireHeader);
    } catch (Exception f) {
      log.log(Level.FINE, "Error parsing header, will return default: " + def, f);
    }
    return def;
  }

  protected static int extractErrorLimitReset(ApiException e, int def) {
    try {
      String expireHeader = e.getResponseHeaders()
                             .get("X-Esi-Error-Limit-Reset")
                             .get(0);
      return Integer.valueOf(expireHeader);
    } catch (Exception f) {
      log.log(Level.FINE, "Error parsing header, will return default: " + def, f);
    }
    return def;
  }

  /**
   * Observe the rate limit for the next call of the current class and account.
   *
   * @param cls class for next call
   * @param acct account for next call
   */
  public static void throttle(String cls, SynchronizedEveAccount acct) {
    // If an exception reveals that we're close to exhausting the error limit,
    // then this synchronize call will block until the thread which hit the error
    // limit has finished sleeping.
    synchronized (ESIThrottle.class) {}
    get(cls, acct).tryConsume(1);
  }

  /**
   * Observe the rate limit if an exception includes a low remaining error limit.
   * @param e ApiException we recently caught
   */
  public static void throttle(ApiException e) {
    int remain = extractErrorLimitRemain(e, Integer.MAX_VALUE);
    if (remain < PersistentProperty.getIntegerPropertyWithFallback(PROP_DEFAULT_ERROR_LIMIT_REMAIN, DEF_DEFAULT_ERROR_LIMIT_REMAIN)) {
      // Too close to error limit, force current thread to sleep
      long delay = extractErrorLimitReset(e, 5) * 1000 + 5000;
      synchronized (ESIThrottle.class) {
        log.fine("Near error rate threshold, throttling thread: " + Thread.currentThread().getName());
        try {
          Thread.sleep(delay);
        } catch (InterruptedException f) {
          // NOP
        }
      }
    }
  }
}
