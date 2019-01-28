package enterprises.orbital.evekit.model;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * A cache composed of weak references to the latest version of cached data objects.
 * We use this cache to avoid hitting the database for data which doesn't change as frequently
 * and therefore likely doesn't need an update.  We use weak references so that the garbage
 * collector is free to empty the cache as needed.
 */
public class ModelCache {

  private final static Map<Pair<SynchronizedEveAccount, ESISyncEndpoint>, WeakReference<ModelCacheData>> modelMap =
      new HashMap<>();

  public static WeakReference<ModelCacheData> get(SynchronizedEveAccount owner, ESISyncEndpoint strategy) {
    synchronized (modelMap) {
      return modelMap.get(Pair.of(owner, strategy));
    }
  }

  public static void set(SynchronizedEveAccount owner, ESISyncEndpoint strategy, ModelCacheData data) {
    synchronized (modelMap) {
      modelMap.put(Pair.of(owner, strategy), new WeakReference<>(data));
    }
  }

  public static void clear(SynchronizedEveAccount owner, ESISyncEndpoint strategy) {
    synchronized (modelMap) {
      modelMap.remove(Pair.of(owner, strategy));
    }
  }

}
