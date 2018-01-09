package enterprises.orbital.evekit.model;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;

import java.io.IOException;

/**
 * Interface for synchronization handlers which handle ESI account endpoints.
 */
public interface ESIAccountSynchronizationHandler {

  /**
   * The endpoint synchronized by this handler.
   *
   * @return the endpoint synchronized by this handler.
   */
  ESISyncEndpoint endpoint();

  /**
   * The account synchronized by this handler.
   *
   * @return the account synchronized by this handler.
   */
  SynchronizedEveAccount account();

  /**
   * A string description of the current processing context.  Used to improve log messages.
   *
   * @return a string describing the context of the current synchronization.
   */
  String getContext();

  /**
   * Return the currently active sync tracker.
   *
   * @return the currently active and unfinished sync tracker, or null if there is no active sync tracker.
   * @throws TrackerNotFoundException if no unfinished tracker exists for this handler.
   * @throws IOException on any database error.
   */
  ESIEndpointSyncTracker getCurrentTracker() throws IOException, TrackerNotFoundException;

  /**
   * Maximum time (in milliseconds) that a synchronization tracker can be in progress.  A tracker found to be
   * "open" longer than this time will automatically be terminated with an error.
   *
   * @return max "open" time for a sync tracker (in milliseconds).
   */
  long maxDelay();

  /**
   * Main synchronization entry point for an ESI reference endpoint.  Note that all errors
   * are expected to be caught within the synch call.
   *
   * @param cp implementation of a client provider for this call.
   */
  void synch(ESIAccountClientProvider cp);

}
