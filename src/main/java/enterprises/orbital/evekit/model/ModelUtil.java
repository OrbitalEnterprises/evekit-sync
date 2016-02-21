package enterprises.orbital.evekit.model;

import java.text.SimpleDateFormat;
import java.util.Date;

import enterprises.orbital.base.OrbitalProperties;

/**
 * Useful utility methods for dealing with model objects.
 */
public class ModelUtil {

  private static final SimpleDateFormat dateformatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");

  public static boolean isExpired(long cachedUntil) {
    long now = OrbitalProperties.getCurrentTime();
    return cachedUntil < now;
  }

  public long expiresIn(long cachedUntil) {
    return Math.max(cachedUntil - OrbitalProperties.getCurrentTime(), 0);
  }

  public static Date convertEpochToDate(long epochTime) {
    return new Date(epochTime);
  }

  public static String formatDate(Date asDate) {
    return dateformatter.format(asDate);
  }

  public static String formatDate(long epochTime) {
    return formatDate(convertEpochToDate(epochTime));
  }
}
