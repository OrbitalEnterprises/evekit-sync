package enterprises.orbital.evekit.model;

import enterprises.orbital.eve.esi.client.api.*;

import java.util.concurrent.ExecutorService;

/**
 * Implementations of this class are used to provide properly configured ESI clients
 * to synchronization code.  This allows for the abstraction of initialization code, and
 * also makes it easier to inject test code.
 */
public interface ESIAccountClientProvider {
  ExecutorService getScheduler();

  WalletApi getWalletApi();

  CharacterApi getCharacterApi();
}
