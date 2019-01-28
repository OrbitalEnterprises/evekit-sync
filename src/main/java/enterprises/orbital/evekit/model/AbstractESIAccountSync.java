package enterprises.orbital.evekit.model;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.base.PersistentProperty;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.evekit.account.AccountNotFoundException;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.DateUtils;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract base class for ESI account endpoint synchronizers.
 * <p>
 * In general, synchronization occurs as follows:
 * <p>
 * <ol>
 * <li>Get the current tracker. If no tracker exists, then exit.</li>
 * <li>Check whether the tracker has already been refreshed for the current data. If yes, then we're done and exit.</li>
 * <li>Check whether pre-reqs have been satisfied for the current data. If no, then queue up to try again later.</li>
 * <li>At this point, we proceed if the tracker is not done, the data is not expired and we're not waiting on any pre-reqs.</li>
 * <li>Interact with the EVE server to update data. If successful, create a data update object.</li>
 * <li>Retrieve the tracker again. If no tracker exists, then someone else refreshed this data and we're done.</li>
 * <li>Check whether the tracker has already been refreshed for the current data. If yes, then someone else refreshed this data and we're done.</li>
 * <li>Update the status and expiry of the tracker.</li>
 * <li>Merge any updates to the data, delete any data to be removed.</li>
 * <li>Create a new unfinished tracker for the next update based on cache expiry time, only if the attached account is not suspended and has the required scopes.</li>
 * </ol>
 */
public abstract class AbstractESIAccountSync<ServerDataType> implements ESIAccountSynchronizationHandler {
  private static final Logger log = Logger.getLogger(AbstractESIAccountSync.class.getName());

  // Default delay for future sync events
  private static final String PROP_DEFAULT_SYNC_DELAY = "enterprises.orbital.evekit.sync_mgr.default_sync_delay";
  private static final long DEF_DEFAULT_SYNC_DELAY = TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);

  // Default maximum delay for an in-progress synchronization
  private static final String PROP_MAX_DELAY = "enterprises.orbital.evekit.sync_mgr.max_sync_delay";
  private static final long DEF_MAX_DELAY = TimeUnit.MILLISECONDS.convert(20, TimeUnit.MINUTES);

  // Batch size for bulk commits
  private static final String PROP_REF_COMMIT_BATCH_SIZE = "enterprises.orbital.evekit.sync_mgr.commit_batch_size";
  private static final int DEF_REF_COMMIT_BATCH_SIZE = 200;

  // Default required valid time for ESI token
  private static final String PROP_MIN_ESI_VALID_TIME = "enterprises.orbital.evekit.sync_mgr.min_token_valid";
  private static final long DEF_MIN_ESI_VALID_TIME = TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES);

  // ESI client ID and key
  private static final String PROP_ESI_CLIENT_ID = "enterprises.orbital.token.eve_client_id";
  private static final String PROP_ESI_SECRET_KEY = "enterprises.orbital.token.eve_secret_key";

  // Convenient attribute selector which matches any attribute
  public static final AttributeSelector ANY_SELECTOR = new AttributeSelector("{ any: true }");

  // List of endpoints we should skip during synchronization (separate with '|')
  public static final String PROP_EXCLUDE_SYNC = "enterprises.orbital.evekit.account.exclude_sync";

  // Simple interface for overriding the cache creator.
  public interface SDECacheCreator {
    SDECache createCache();
  }

  // Cache creator.  Can be overridden for testing.
  private static SDECacheCreator sdeCacheCreator;

  // Shared cache for SDE data
  private static SDECache sdeCache;

  // Account to be synchronized
  protected SynchronizedEveAccount account;

  public static void setCacheCreator(SDECacheCreator creator) {
    synchronized (AbstractESIAccountSync.class) {
      sdeCacheCreator = creator;
    }
  }

  protected SDECache getSDECache() {
    synchronized (AbstractESIAccountSync.class) {
      if (sdeCache == null) {
        sdeCache = sdeCacheCreator == null ? new StandardSDECache() : sdeCacheCreator.createCache();
      }
      return sdeCache;
    }
  }

  /**
   * All synchronizer instances must be initialized with the account they will sync.
   *
   * @param account account to be synchronized.
   */
  public AbstractESIAccountSync(SynchronizedEveAccount account) {
    this.account = account;
  }

  /**
   * Retrieve the ESI endpoints that have been excluded from synchronization by the admin.
   *
   * @return the set of excluded ESI endpoints.
   */
  public static Set<ESISyncEndpoint> getExcludedEndpoints() {
    String[] excludedStates = PersistentProperty.getPropertyWithFallback(PROP_EXCLUDE_SYNC, "")
                                                .split("\\|");
    Set<ESISyncEndpoint> excluded = new HashSet<>();
    for (String next : excludedStates) {
      if (!next.isEmpty()) {
        try {
          ESISyncEndpoint val = ESISyncEndpoint.valueOf(next);
          excluded.add(val);
        } catch (IllegalArgumentException e) {
          // Unknown value type, skip
          log.warning("Unknown endpoint name " + next + ", ignoring.");
        }
      }
    }
    return excluded;
  }

  /**
   * Retrieve an access token guaranteed to be valid for at least DEF_MIN_ESI_VALID_TIME milliseconds.
   *
   * @return a valid access token.
   * @throws IOException if a failure occurs while refreshing the access token.
   */
  protected String accessToken() throws IOException {
    return account.refreshToken(
        PersistentProperty.getLongPropertyWithFallback(PROP_MIN_ESI_VALID_TIME, DEF_MIN_ESI_VALID_TIME),
        OrbitalProperties.getGlobalProperty(PROP_ESI_CLIENT_ID),
        OrbitalProperties.getGlobalProperty(PROP_ESI_SECRET_KEY));
  }

  // Convenience function to construct a time selector for the give time.
  public static AttributeSelector makeAtSelector(long time) {
    return new AttributeSelector("{values: [" + time + "]}");
  }

  // Interface which forwards a call to the class specific query function to retrieve data
  public interface QueryCaller<A extends CachedData> {
    List<A> query(long contid, AttributeSelector at) throws IOException;
  }

  /**
   * Retrieve all data items of the specified type live at the specified time.
   * This function continues to accumulate results until a query returns no results.
   *
   * @param time  the "live" time for the retrieval.
   * @param query an interface which performs the type appropriate query call.
   * @param <A>   class of the object which will be returned.
   * @return the list of results.
   * @throws IOException on any DB error.
   */
  @SuppressWarnings("Duplicates")
  public static <A extends CachedData> List<A> retrieveAll(long time, QueryCaller<A> query) throws IOException {
    final AttributeSelector ats = makeAtSelector(time);
    long contid = 0;
    List<A> results = new ArrayList<>();
    List<A> nextBatch = query.query(contid, ats);
    while (!nextBatch.isEmpty()) {
      results.addAll(nextBatch);
      contid = nextBatch.get(nextBatch.size() - 1)
                        .getCid();
      nextBatch = query.query(contid, ats);
    }
    return results;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getContext() {
    return "[" + getClass().getSimpleName() + "-" + String.valueOf(account) + "]";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ESIEndpointSyncTracker getCurrentTracker() throws IOException, TrackerNotFoundException {
    return ESIEndpointSyncTracker.getUnfinishedTracker(account, endpoint());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizedEveAccount account() {
    return account;
  }

  /**
   * A default time in the future when the next event for this handler should be scheduled.  This method
   * is called when it is otherwise not possible to determine an appropriate next event time.  This normally
   * happens when an error occurs during synchronization.  Sub-classes should override as appropriate.
   *
   * @return a time in the future when the next synchronization should be scheduled.
   */
  protected long defaultNextEvent() {
    return OrbitalProperties.getCurrentTime() + OrbitalProperties.getLongGlobalProperty(PROP_DEFAULT_SYNC_DELAY,
                                                                                        DEF_DEFAULT_SYNC_DELAY);
  }

  /**
   * {@inheritDoc}
   */
  public long maxDelay() {
    return PersistentProperty.getLongPropertyWithFallback(PROP_MAX_DELAY, DEF_MAX_DELAY);
  }

  /**
   * Check whether any pre-requisites have been satisfied.  Sub-classes should override as appropriate.
   *
   * @return true if all pre-reqs have been satisfied, false otherwise.
   */
  protected boolean prereqSatisfied() {
    return true;
  }

  /**
   * Commit a data item at the specified synchronization time.  Sub-classes will normally override this method
   * and check whether it is necessary to update or evolve an existing item.
   *
   * @param time synchronization time at which this update will occur.
   * @param item item to update or commit
   * @throws IOException on any error (usually a database error)
   */
  protected void commit(
      long time,
      CachedData item) throws IOException {
    CachedData.update(item);
  }

  /**
   * Retrieve server data needed to process this update.  We structure the retrieval of server data in this way
   * to allow for uniform handling of client errors.
   *
   * @return a mostly opaque object containing server data to be used for the update.
   * @throws ApiException if a client error occurs while retrieving data.
   * @throws IOException  on any other error which occurs while retrieving data.
   */
  protected abstract ESIAccountServerResult<ServerDataType> getServerData(ESIAccountClientProvider cp)
      throws ApiException, IOException;

  /**
   * Process server data.  Normally, the subclass will extract server data into appropriate types
   * which are added to the update list (and later processed in the "commit" call).
   *
   * @param time    synchronization time.
   * @param data    server result previously retrieved via getServerData
   * @param updates list of objects to be updated as a result of processing.
   * @throws IOException on any error which occurs while processing server data
   */
  protected abstract void processServerData(
      long time,
      ESIAccountServerResult<ServerDataType> data,
      List<CachedData> updates)
      throws IOException;

  /**
   * Convenience method for handling the common case where we should commit and EOL item
   * (if update.getLifeStart() != 0), evolve an existing item if it is different from an
   * update, or initialize and store a new item if no existing item is present.
   *
   * @param time     synchronization time at which this update will occur.
   * @param existing existing data item, if any.
   * @param update   new data item.
   * @throws IOException on any database error
   */
  protected void evolveOrAdd(long time, CachedData existing, CachedData update) throws IOException {
    if (update.getLifeStart() != 0) {
      // Existing element that is end of life (basically a delete).
      CachedData.update(update);
    } else if (existing != null) {
      if (!existing.equivalent(update)) {
        // Evolve
        existing.evolve(update, time);
        CachedData.update(existing);
        CachedData.update(update);
      }
    } else {
      // New entity
      update.setup(account, time);
      CachedData.update(update);
    }
  }

  /**
   * Utility method to extract expiry time from an ESI ApiResponse into milliseconds since the epoch UTC.
   *
   * @param result the ApiResponse which may contain an "expires" header.
   * @param def    value to return if header does not contain "expires" or the header can not be parsed properly.
   * @return expires header in milliseconds UTC, or the default.
   */
  protected static long extractExpiry(ApiResponse<?> result, long def) {
    try {
      String expireHeader = result.getHeaders()
                                  .get("Expires")
                                  .get(0);
      return DateUtils.parseDate(expireHeader)
                      .getTime();
    } catch (Exception e) {
      log.log(Level.FINE, "Error parsing header, will return default: " + def, e);
    }
    return def;
  }

  /**
   * Utility method to extract X-Pages header from an ESI ApiResponse.
   *
   * @param result the ApiResponse which may contain an "x-pages" header.
   * @param def    value to return if header does not contain "x-pages" or the header can not be parsed properly.
   * @return x-pages value as an integer, or the default.
   */
  protected static int extractXPages(ApiResponse<?> result, int def) {
    try {
      String expireHeader = result.getHeaders()
                                  .get("X-Pages")
                                  .get(0);
      return Integer.valueOf(expireHeader);
    } catch (Exception e) {
      log.log(Level.FINE, "Error parsing header, will return default: " + def, e);
    }
    return def;
  }

  /**
   * Utility method to extract ETag from an ESI ApiResponse.
   *
   * @param result the ApiResponse which may contain an "ETag" header.
   * @param def    value to return if header does not contain "ETag".
   * @return ETag header, or the default.
   */
  protected static String extractETag(ApiResponse<?> result, String def) {
    try {
      return result.getHeaders()
                   .get("ETag")
                   .get(0);
    } catch (Exception e) {
      log.log(Level.FINE, "Error parsing header, will return default: " + def, e);
    }
    return def;
  }

  /**
   * Utility method to check for common problems with API responses.  The current list of common problems are:
   * <p>
   * <ul>
   * <li>A return code other than 200.</li>
   * <li>A null data response.</li>
   * </ul>
   *
   * @param response the API response to check.
   * @throws IOException if a common problem is found in the response.
   */
  protected static void checkCommonProblems(ApiResponse<?> response) throws IOException {
    if (response.getStatusCode() != HttpStatus.SC_OK)
      throw new IOException("Unexpected return code: " + response.getStatusCode());
    if (response.getData() == null) throw new IOException("Response data is null");
  }

  /**
   * Retrieve context to be stored with the next tracker we create for this synchronizer.
   * Context is only attached if the current synchronization succeeds.  Otherwise, the
   * context for the next tracker is left at null.  Subclasses should override as
   * appropriate.
   *
   * @return the context to be attached to the next tracker.
   */
  protected String getNextSyncContext() {
    return null;
  }

  /**
   * This method is called if the commit phase completes without exception.
   * This gives the concrete subclass a chance to update any commit-dependent state
   * (e.g. refreshing cached values).
   */
  protected void commitComplete() {}

  // Cache counters for stats during synchronization
  private int cacheHits;
  private int cacheMisses;
  private int cacheInit;

  protected void resetCacheCounters() {
    cacheHits = cacheMisses = cacheInit = 0;
  }

  protected void cacheHit() {
    cacheHits++;
  }

  protected void cacheMiss() {
    cacheMisses++;
  }

  protected void cacheInit() {
    cacheInit++;
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("Duplicates")
  @Override
  public void synch(ESIAccountClientProvider cp) {
    log.fine("Starting synchronization: " + getContext());
    long syncStart = OrbitalProperties.getCurrentTime();
    long syncEnd = 0;
    long syncServerCallStart = 0;
    long syncServerCallEnd = 0;
    long syncProcessDataStart = 0;
    long syncProcessDataEnd = 0;
    long syncCommitStart = 0;
    long syncCommitEnd = 0;
    resetCacheCounters();

    try {
      // We may have been queued for a while and may have a stale account reference.
      // Refresh to make sure we have proper credentials.
      try {
        account = SynchronizedEveAccount.getSynchronizedAccount(account.getUserAccount(), account.getAid(), false);
      } catch (AccountNotFoundException e) {
        // This could potentially happen if this account was marked for delete while we had a tracker
        // queued.  In this case, just finish the tracker and exit.
        log.log(Level.FINE, "Error refreshing account, ending synch: " + getContext(), e);
        ESIEndpointSyncTracker tracker = getCurrentTracker();
        tracker.setStatus(ESISyncState.ERROR);
        tracker.setDetail("Account appears to be in a bad state, this synch will be skipped");
        ESIEndpointSyncTracker.finishTracker(tracker);
        return;
      }

      // Get the current tracker.  If no tracker exists, then we'll exit in the catch block below.
      ESIEndpointSyncTracker tracker = getCurrentTracker();

      // If the tracker is already refreshed, then exit
      if (tracker.isRefreshed()) {
        log.fine("Tracker is already refreshed: " + getContext());
        return;
      }

      // Check whether this tracker has been in progress too long.  If so, then close it down and exit.
      if (tracker.getSyncStart() > 0) {
        long delaySinceStart = OrbitalProperties.getCurrentTime() - tracker.getSyncStart();
        if (delaySinceStart > maxDelay()) {
          log.fine("Forcing tracker " + tracker + " to terminate due to delay: " + getContext());
          tracker.setStatus(ESISyncState.WARNING);
          tracker.setDetail("Terminated due to excessive delay");
          ESIEndpointSyncTracker.finishTracker(tracker);
          return;
        }
      }

      // Verify all pre-reqs have been satisfied.  If not then exit and scheduler will try again later.
      if (!prereqSatisfied()) {
        log.fine("Pre-reqs not satisfied: " + getContext());
        return;
      }

      // Start sync for this endpoint
      if (tracker.getSyncStart() <= 0) {
        tracker.setSyncStart(OrbitalProperties.getCurrentTime());
        tracker = EveKitUserAccountProvider.update(tracker);
      }

      // Set syncTime to the start of the current tracker
      long syncTime = tracker.getSyncStart();
      long nextEvent;
      String nextContext;

      try {
        // Retrieve server and process server data.  Any client or processing
        // errors will result in marking the tracker as in error with an endpoint
        // specific time for the next scheduled event.  Otherwise, the schedule time
        // returned by the data processor is used.
        List<CachedData> updateList = new ArrayList<>();
        log.fine("Retrieving server data: " + getContext());
        syncServerCallStart = OrbitalProperties.getCurrentTime();
        ESIAccountServerResult<ServerDataType> serverData = getServerData(cp);
        syncServerCallEnd = OrbitalProperties.getCurrentTime();
        nextEvent = serverData.getExpiryTime();
        log.fine("Processing server data: " + getContext());
        syncProcessDataStart = OrbitalProperties.getCurrentTime();
        processServerData(syncTime, serverData, updateList);
        syncProcessDataEnd = OrbitalProperties.getCurrentTime();
        nextContext = getNextSyncContext();

        // Commit all updates.  We process updates in batches with sizes that can be varied dynamically by the
        // admin as needed.  Smaller batches prevent long running transactions from tying up contended resources.
        log.fine("Storing updates: " + getContext());
        syncCommitStart = OrbitalProperties.getCurrentTime();
        int batchSize = PersistentProperty.getIntegerPropertyWithFallback(PROP_REF_COMMIT_BATCH_SIZE,
                                                                          DEF_REF_COMMIT_BATCH_SIZE);
        int count = updateList.size();
        if (count > 0) {
          log.fine("Processing " + updateList.size() + " total updates: " + getContext());
          for (int i = 0, endIndex = Math.min(i + batchSize, count); i < count; i = endIndex, endIndex = Math.min(
              i + batchSize, count)) {
            List<CachedData> nextBlock = updateList.subList(i, endIndex);
            try {
              EveKitUserAccountProvider.getFactory()
                                       .runTransaction(() -> {
                                         // Handle next block of commits.
                                         log.fine("Processing " + nextBlock.size() + " updates: " + getContext());
                                         long start = OrbitalProperties.getCurrentTime();
                                         for (CachedData obj : nextBlock) {
                                           commit(syncTime, obj);
                                         }
                                         long end = OrbitalProperties.getCurrentTime();
                                         if (log.isLoggable(Level.FINE)) {
                                           // Commit commit rate if FINE if debugging
                                           long delay = end - start;
                                           double rate = delay / (double) nextBlock.size();
                                           log.fine(
                                               "Process rate = " + rate + " milliseconds/update: " + getContext());
                                         }
                                       });
            } catch (Exception e) {
              if (e.getCause() instanceof IOException) throw (IOException) e.getCause();
              log.log(Level.SEVERE, "query error: " + getContext(), e);
              throw new IOException(e.getCause());
            }
          }
        }
        syncCommitEnd = OrbitalProperties.getCurrentTime();
        commitComplete();

        log.fine("Update and store finished normally: " + getContext());
        tracker.setStatus(ESISyncState.FINISHED);
        tracker.setDetail("Updated successfully");
      } catch (ApiException e) {
        // Client error while updating, mark the error in the tracker and exit
        log.log(Level.WARNING, "ESI client error: " + getContext(), e);
        nextEvent = -1;
        nextContext = null;
        tracker.setStatus(ESISyncState.ERROR);
        tracker.setDetail("ESI client error, contact the site admin if this problem persists");
        // Throttle in case we're about to exhaust the error limit
        ESIThrottle.throttle(e);
      } catch (IOException e) {
        // Other error while updating, mark the error in the tracker and exit
        // Database errors during the update should end up here.
        log.log(Level.WARNING, "Error during update: " + getContext(), e);
        nextEvent = -1;
        nextContext = null;
        tracker.setStatus(ESISyncState.ERROR);
        tracker.setDetail("Server error, contact the site admin if this problem persists");
      }

      // Complete the tracker
      ESIEndpointSyncTracker.finishTracker(tracker);
      syncEnd = OrbitalProperties.getCurrentTime();

      // Report timing
      if (log.isLoggable(Level.INFO)) {
        long totalSync = syncEnd - syncStart;
        long serverTime = syncServerCallEnd > 0 ? syncServerCallEnd - syncServerCallStart : -1;
        long processTime = syncProcessDataEnd > 0 ? syncProcessDataEnd - syncProcessDataStart : -1;
        long commitTime = syncCommitEnd > 0 ? syncCommitEnd - syncCommitStart : -1;
        StringBuilder builder = new StringBuilder();
        builder.append("SYNC STATS:");
        builder.append(" ENDPOINT: ").append(endpoint().name());
        builder.append(" OUTCOME: ").append(tracker.getStatus().name());
        builder.append(" AID: ").append(account.getAid());
        builder.append(" TOTAL SYNC (ms): ").append(totalSync);
        if (serverTime > -1) builder.append(" SERVER (ms): ").append(serverTime);
        if (processTime > -1) builder.append(" PROCESS (ms): ").append(processTime);
        if (commitTime > -1) builder.append(" COMMIT (ms): ").append(commitTime);
        builder.append(" CACHE HITS: ").append(cacheHits);
        builder.append(" CACHE MISSES: ").append(cacheMisses);
        builder.append(" CACHE INITS: ").append(cacheInit);
        log.log(Level.INFO, builder.toString());
      }

      // Exit without scheduling if:
      // - account no longer has credentials
      // - account no longer has required scopes
      // - owning user is no longer active.
      // - endpoint is now excluded
      if (account.getEveCharacterID() == -1 ||
          (endpoint().getScope() != null && !account.hasScope(endpoint().getScope()
                                                                        .getName())) ||
          !account.getUserAccount()
                  .isActive() ||
          getExcludedEndpoints().contains(endpoint())) {
        log.fine(getContext() + " conditions not satisfied, not scheduling a future sync");
        return;
      }

      // Schedule the next event
      nextEvent = nextEvent < 0 ? defaultNextEvent() : nextEvent;
      ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(account, endpoint(), nextEvent, nextContext);

    } catch (TrackerNotFoundException e) {
      // No action to take, exit
      log.fine("No unfinished tracker: " + getContext());
    } catch (IOException e) {
      // Database errors during the update or access to the tracker will end up here.
      log.log(Level.WARNING, "Error during synchronization, tracker may not be updated: " + getContext(), e);
    }
  }

  // Convenience methods for dealing with missing data from api calls
  public static int nullSafeInteger(Integer value, int def) {
    if (value == null) return def;
    return value;
  }

  public static long nullSafeLong(Long value, long def) {
    if (value == null) return def;
    return value;
  }

  public static float nullSafeFloat(Float value, float def) {
    if (value == null) return def;
    return value;
  }

  public static DateTime nullSafeDateTime(DateTime value, DateTime def) {
    if (value == null) return def;
    return value;
  }

  public static double nullSafeDouble(Double value, double def) {
    if (value == null) return def;
    return value;
  }

  public static boolean nullSafeBoolean(Boolean value, boolean def) {
    if (value == null) return def;
    return value;
  }

  public static String nullSafeEnum(Enum<?> value, String def) {
    return value == null ? def : value.toString();
  }

  public interface GetNextPage<A> {
    ApiResponse<List<A>> retrievePage(int page) throws ApiException, IOException;
  }

  protected static <A> Pair<Long, List<A>> pagedResultRetriever(
      GetNextPage<A> pageFetcher) throws ApiException, IOException {
    List<A> results = new ArrayList<>();
    int page = 1, maxPages = 1;
    long expiry = 0L;
    while (page <= maxPages) {
      ApiResponse<List<A>> result = pageFetcher.retrievePage(page);
      checkCommonProblems(result);
      expiry = extractExpiry(result, -1);
      maxPages = extractXPages(result, 1);
      results.addAll(result.getData());
      page++;
    }
    return Pair.of(expiry, results);
  }

}
