package enterprises.orbital.evekit.model;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.db.ConnectionFactory.RunInTransaction;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;

/**
 * Utility methods for implementing data model synchronization pattern.
 */
public class SynchronizerUtil {
  protected static final Logger log               = Logger.getLogger(SynchronizerUtil.class.getName());

  public static final int       COMMIT_BATCH_SIZE = 200;

  public static enum SyncStatus {
                                 CONTINUE,
                                 DONE,
                                 ERROR
  }

  public static enum SynchOutcome {
                                   PROCEED, // Proceed with synchronization
                                   COMPLETE, // Synchronization already completed elsewhere
                                   WAITING_ON_PREREQ, // A required pre-req has not yet completed
                                   MISSING_CONTAINER, // We couldn't find an expected container for this
                                                      // sync object
                                   NOT_EXPIRED, // The underlying data object has not yet expired
                                   SYSTEM_ERROR, // A system error occurred during one of the sync operations
  }

  // Generic synchronized data update procedure:
  //
  // 1. Get the current tracker. If no tracker exists, assume we're done and exit.
  // 2. Check whether the tracker has already been refreshed for the current data. If yes, then we're done and exit.
  // 3. Check whether pre-reqs have been satisfied for the current data. If no, then queue up to try again later.
  // 4. Attempt to get a container for the current data. If missing, exit with "missing".
  // 5. Check whether the current data is expired. If no, then exit with "not expired".
  // 6. At this point, we proceed if the tracker is not done, the data is not expired, the container is not missing and we're not waiting on any pre-reqs.
  // 7. Interact with the EVE server to update data. If successful, create a data update object.
  // 8. Retrieve the tracker again. If no tracker exists, then someone else refreshed this data and we're done.
  // 9. Check whether the tracker has already been refreshed for the current data. If yes, then someone else refreshed this data and we're done.
  // 10. Attempt to get a container for the current data. If missing, exit with severe error.
  // 11. Update the status and expiry of the tracker.
  // 12. Merge any updates to the data, delete any data to be removed.

  /**
   * Check whether we should proceed with synchronizing a data model object.
   * 
   * @param trackerType
   *          the type of the tracker tracking synchronization for this object.
   * @param containerType
   *          the type of the container for this data model object.
   * @param accountKey
   *          a reference to the owning synchronized eve account.
   * @param msgInfo
   *          an arbitrary string to include in logged messages to help identify which data model we're processing.
   * @param handler
   *          SynchronizationHandler being used with this synchronization
   * @param <A>
   *          type of sync tracker, either Capsuleer or Corporation
   * @param <C>
   *          container type for synchronization, either Capsuleer or Corporation
   * @return an instance of SyncOutcome indicating the result of the check.
   * @throws IOException
   *           if an IO error occurs during synchronization
   */
  public <A extends SyncTracker, C extends CachedData> SynchOutcome checkProceedWithSynch(
                                                                                          Class<A> trackerType,
                                                                                          Class<C> containerType,
                                                                                          SynchronizedEveAccount accountKey,
                                                                                          String msgInfo,
                                                                                          SynchronizationHandler<A, C> handler) throws IOException {
    String context = "[accountKey=" + accountKey + ", msgInfo=" + msgInfo + "]";
    SynchOutcome result = SynchOutcome.COMPLETE;

    // Retrieve and determine the state of our sync tracker
    A tracker = handler.getCurrentTracker(accountKey);

    do {
      if (tracker == null) {
        // No more tracker so we're done!
        if (log.isLoggable(Level.FINE)) {
          log.fine(context + " no active tracker");
        }
        break;
      }

      if (handler.isRefreshed(tracker)) {
        // Another tracker already handled this refresh request
        if (log.isLoggable(Level.FINE)) {
          log.fine(context + " tracker already complete");
        }
        break;
      }

      if (!handler.prereqSatisfied(tracker)) {
        // Pre-reqs not yet satisfied. Should queue up to try later
        if (log.isLoggable(Level.FINE)) {
          log.fine(context + " waiting on a pre-req to complete");
        }
        result = SynchOutcome.WAITING_ON_PREREQ;
        break;
      }

      C container = handler.getExistingContainer(accountKey);
      if (container == null) {
        log.severe(context + " container of type " + containerType.getName() + " does not exist");
        result = SynchOutcome.MISSING_CONTAINER;
        break;
      }

      if (!ModelUtil.isExpired(handler.getExpiryTime(container))) {
        // Not expired yet so nothing to do BUT we do need to update
        // the tracker so we complete properly.
        if (log.isLoggable(Level.FINE)) {
          log.fine(context + " not expired");
        }
        handler.updateStatus(tracker, SyncTracker.SyncState.NOT_EXPIRED, null);
        result = SynchOutcome.NOT_EXPIRED;
        break;
      }

      result = SynchOutcome.PROCEED;

    } while (false);

    return result;
  }

  private interface CommitBlock<B, A extends SyncTracker, C extends CachedData> extends RunInTransaction<B> {
    public void setParams(
                          A tracker,
                          C container,
                          List<CachedData> elementSet);
  };

  private class TrackTriple<A extends SyncTracker, C extends CachedData> {
    public A           tracker;
    public C           container;
    public IOException e;

    public TrackTriple(A tracker, C container, IOException e) {
      this.tracker = tracker;
      this.container = container;
      this.e = e;
    }

    public A getTracker() {
      return tracker;
    }

    public C getContainer() {
      return container;
    }

    public IOException getException() {
      return e;
    }
  }

  /**
   * Store the results of data model synchronization assuming our tracker still needs to be updated.
   * 
   * @param time
   *          time for checking liveness or lifeStart for newly created entities.
   * @param trackerType
   *          the type of the tracker tracking synchronization for this account.
   * @param containerType
   *          the type of the container for this account.
   * @param accountKey
   *          a reference to the owning synchronized eve account.
   * @param requestStatus
   *          the status to store for the outcoming of this synchronization.
   * @param statusDetail
   *          status detail message
   * @param nextExpiry
   *          the time in millis UTC when we should next synchronization this data model.
   * @param msgInfo
   *          an arbitrary string to include in logged messages to help identify which data model we're processing.
   * @param toStore
   *          a list of CachedData objects to store if we still need to update this tracker.
   * @param handler
   *          SynchronizationHandler being used with this synchronization
   * @param <A>
   *          type of sync tracker, either Capsuleer or Corporation
   * @param <C>
   *          container type for synchronization, either Capsuleer or Corporation
   * @throws IOException
   *           if an IO error occurs during synchronization
   */
  public <A extends SyncTracker, C extends CachedData> void storeSynchResults(
                                                                              final long time,
                                                                              final Class<A> trackerType,
                                                                              final Class<C> containerType,
                                                                              final SynchronizedEveAccount accountKey,
                                                                              final SyncTracker.SyncState requestStatus,
                                                                              final String statusDetail,
                                                                              final long nextExpiry,
                                                                              final String msgInfo,
                                                                              List<CachedData> toStore,
                                                                              final SynchronizationHandler<A, C> handler) throws IOException {
    final String context = "[accountKey=" + accountKey + ", msgInfo=" + msgInfo + "]";

    CommitBlock<IOException, A, C> commitBlock = new CommitBlock<IOException, A, C>() {
      A                tracker;
      C                container;
      List<CachedData> els;

      @Override
      public void setParams(
                            A tracker,
                            C container,
                            List<CachedData> elementSet) {
        this.tracker = tracker;
        this.container = container;
        this.els = elementSet;
      }

      @Override
      public IOException run() {
        // Handle next block of stores.
        log.fine("Processing " + els.size() + " updates");
        long start = OrbitalProperties.getCurrentTime();
        for (CachedData i : els) {
          if (!handler.commit(time, tracker, container, accountKey, i))
            return new IOException(context + " DataCommitter returned false while committing: " + i);
        }
        long end = OrbitalProperties.getCurrentTime();
        if (log.isLoggable(Level.FINE)) {
          long delay = end - start;
          double rate = delay / (double) els.size();
          log.fine("Process rate = " + rate + " milliseconds/update");
        }

        return null;
      }

    };

    RunInTransaction<TrackTriple<A, C>> setup = new RunInTransaction<TrackTriple<A, C>>() {
      @Override
      public TrackTriple<A, C> run() {
        A tracker = handler.getCurrentTracker(accountKey);
        C container;

        if (tracker == null) {
          // No more tracker so we're done!
          log.fine(context + " no active tracker");
          return null;
        }
        log.fine("tracker: " + tracker);

        if (handler.isRefreshed(tracker)) {
          // Another tracker already handled this refresh request
          log.fine(context + " tracker already complete");
          return null;
        }

        container = handler.getExistingContainer(accountKey);
        if (container == null) {
          log.severe(context + " container of type " + containerType.getName() + " does not exist");
          return new TrackTriple<A, C>(null, null, new IOException(context + " container of type " + containerType.getName() + " does not exist"));
        }

        // Update sync state
        handler.updateStatus(tracker, requestStatus, statusDetail);
        handler.updateExpiry(container, nextExpiry);

        return new TrackTriple<A, C>(tracker, container, null);
      }
    };

    // Perform setup. Only proceed if we successfully obtain a tracker and container;
    A tracker;
    C container;
    TrackTriple<A, C> getSetup = null;
    try {
      getSetup = EveKitUserAccountProvider.getFactory().runTransaction(setup);
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    if (getSetup == null) {
      log.fine("Completed storeSyncResultsProcessing");
      return;
    }
    if (getSetup.getException() != null) { throw getSetup.getException(); }

    // Have a tracker and container. Now break up commits and deletes into batches and proceed. We do deletes first.
    tracker = getSetup.getTracker();
    container = getSetup.getContainer();
    if (toStore != null && toStore.size() > 0) {
      for (int i = 0, endIndex = Math.min(i + COMMIT_BATCH_SIZE, toStore.size()); i < toStore.size(); i = endIndex, endIndex = Math.min(i + COMMIT_BATCH_SIZE,
                                                                                                                                        toStore.size())) {
        commitBlock.setParams(tracker, container, toStore.subList(i, endIndex));
        IOException status = null;
        try {
          status = EveKitUserAccountProvider.getFactory().runTransaction(commitBlock);
        } catch (Exception e) {
          log.log(Level.SEVERE, "query error", e);
        }
        if (status != null) throw status;
      }
    }

    log.fine("Completed storeSyncResultsProcessing");
  }

  public <A extends SyncTracker, C extends CachedData> SyncStatus preSyncCheck(
                                                                               final Class<A> trackerType,
                                                                               final Class<C> containerType,
                                                                               final SynchronizedEveAccount accountKey,
                                                                               final String msgInfo,
                                                                               final SynchronizationHandler<A, C> handler) {

    SyncStatus result = null;
    try {
      result = EveKitUserAccountProvider.getFactory().runTransaction(new RunInTransaction<SyncStatus>() {
        @Override
        public SyncStatus run() {
          SyncStatus result = SyncStatus.ERROR;
          try {

            // Check whether to proceed with synchronization
            switch (checkProceedWithSynch(trackerType, containerType, accountKey, msgInfo, handler)) {
            case COMPLETE:
            case NOT_EXPIRED:
              result = SyncStatus.DONE;
              break;

            case MISSING_CONTAINER:
              // This is bad, log it and try again
              log.severe(containerType.getName() + " was missing, scheduling to try again later");
              break;

            case WAITING_ON_PREREQ:
              // Make sure we get rescheduled to try later
              log.fine("Pre-reqs not completed yet, rescheduling");
              break;

            case SYSTEM_ERROR:
              // Log and make sure we get rescheduled to try later.
              log.warning("Proceed checker failed with a system error, rescheduling");
              break;

            case PROCEED:
            default:
              // Good to go!
              result = SyncStatus.CONTINUE;
              break;
            }
          } catch (IOException e) {
            log.warning("IOError handling pre-sync check: " + e);
            result = SyncStatus.ERROR;
          } catch (RuntimeException e) {
            log.warning("Pre sync check failed: " + e);
            result = SyncStatus.ERROR;
          }

          return result;
        }
      });
    } catch (Exception e) {
      log.log(Level.SEVERE, "query error", e);
    }
    return result;
  }

}
