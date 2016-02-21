package enterprises.orbital.evekit.model;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;

public interface SynchronizationHandler<A extends SyncTracker, C extends CachedData> {

  /**
   * Return the currently active sync tracker for this account, or null if there is no active sync tracker.
   * 
   * @param owner
   *          the account on whose behalf the tracker is sync'ing.
   * @param joinTransaction
   *          if true, a transaction is already in progress which should be joined.
   * @return the currently active and unfinished sync tracker for the owner, or null if there is no active sync tracker.
   */
  public A getCurrentTracker(SynchronizedEveAccount owner);

  /**
   * Return the existing container of a synchronized account, or null if no container exists.
   * 
   * @param owner
   *          the account which owns the container.
   * @param joinTransaction
   *          if true, a transaction is already in progress which should be joined.
   * @return the existing container for this account, or null if no container exists.
   */
  public C getExistingContainer(SynchronizedEveAccount owner);

  public boolean isRefreshed(A tracker);

  public void updateStatus(A tracker, SyncTracker.SyncState status, String detail);

  public boolean prereqSatisfied(A tracker);

  public void updateExpiry(C container, long expiry);

  public long getExpiryTime(C container);

  public boolean commit(long time, A tracker, C container, SynchronizedEveAccount accountKey, CachedData item);

}
