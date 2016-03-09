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

  public static String deletable(
                                 SynchronizedEveAccount toDelete) {
    // Verify this account is actually eligible for deletion, which means:
    // 1) It is marked for deletion
    // 2) At least 24 hours have elapsed since it was marked
    // If these conditions pass, then we delete the account
    long markTime = toDelete.getMarkedForDelete();
    if (markTime <= 0) return "not marked for delete";
    long now = OrbitalProperties.getCurrentTime();
    long yesterday = now - TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS);
    if (markTime > yesterday) { return "marked for deletion less than 24 hours ago"; }
    return null;
  }

  public void deleteMarked(
                           SynchronizedEveAccount toDelete) {
    // Verify account is deletable
    log.info("Attempting to delete account: " + toDelete);
    String msg = deletable(toDelete);
    if (msg != null) {
      log.warning("Account not eligible for deletion: " + msg);
      return;
    }
    // Looks good, proceed to delete once we obtain a thread.
    CachedData.cleanup(toDelete);
    SynchronizedEveAccount.remove(toDelete);
    log.info("Account deleted");
  }
}
