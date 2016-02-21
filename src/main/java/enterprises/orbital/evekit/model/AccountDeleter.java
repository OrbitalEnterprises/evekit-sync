package enterprises.orbital.evekit.model;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;

/**
 * Delete an account eligible for deletion.
 */
public class AccountDeleter {
  private static final Logger log = Logger.getLogger(AccountDeleter.class.getName());

  public void deleteMarked(SynchronizedEveAccount toDelete) {
    log.info("Attempting to delete account: " + toDelete);
    // Verify this account is actually eligible for deletion, which means:
    // 1) It is marked for deletion
    // 2) At least 24 hours have elapsed since it was marked
    // If these conditions pass, then we delete the account
    long markTime = toDelete.getMarkedForDelete();
    if (markTime <= 0) {
      log.warning("Account no longer marked for delete, not deleting");
      return;
    }
    long now = OrbitalProperties.getCurrentTime();
    long yesterday = now - TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS);
    if (markTime > yesterday) {
      log.info("Account was marked for deletion less than 24 hours, not deleting");
      return;
    }
    // Looks good, proceed to delete once we obtain a thread.
    CachedData.cleanup(toDelete);
    SynchronizedEveAccount.remove(toDelete);
    log.info("Account deleted");
  }
}
