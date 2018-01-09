package enterprises.orbital.evekit.model.corporation.sync;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CorporationSyncTracker;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.corporation.Corporation;
import enterprises.orbital.evekit.model.corporation.CorporationSheet;
import enterprises.orbital.evekit.model.corporation.Division;
import enterprises.orbital.evexmlapi.crp.ICorporationAPI;
import enterprises.orbital.evexmlapi.crp.ICorporationSheet;
import enterprises.orbital.evexmlapi.crp.IDivision;

public class CorporationSheetSync extends AbstractCorporationSync {
  protected static final Logger log = Logger.getLogger(CorporationSheetSync.class.getName());

  @Override
  public boolean isRefreshed(CorporationSyncTracker tracker) {
    return tracker.getCorporationSheetStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public long getExpiryTime(Corporation container) {
    return container.getCorporationSheetExpiry();
  }

  @Override
  public void updateStatus(CorporationSyncTracker tracker, SyncState status, String detail) {
    tracker.setCorporationSheetStatus(status);
    tracker.setCorporationSheetDetail(detail);
    CorporationSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(Corporation container, long expiry) throws IOException {
    container.setCorporationSheetExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public boolean commit(long time, CorporationSyncTracker tracker, Corporation container, SynchronizedEveAccount accountKey, CachedData item) throws IOException {
    if (item instanceof Division) {
      Division api = (Division) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing Division to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        Division existing = Division.get(accountKey, time, api.isWallet(), api.getAccountKey());
        if (existing != null) {
          if (!existing.equivalent(api)) {
            // Evolve
            existing.evolve(api, time);
            super.commit(time, tracker, container, accountKey, existing);
            super.commit(time, tracker, container, accountKey, api);
          }
        } else {
          // New entity
          api.setup(accountKey, time);
          super.commit(time, tracker, container, accountKey, api);
        }
      }
    } else if (item instanceof CorporationSheet) {
      CorporationSheet api = (CorporationSheet) item;
      CorporationSheet existing = CorporationSheet.get(accountKey, time);
      if (existing != null) {
        if (!existing.equivalent(api)) {
          // Evolve
          existing.evolve(api, time);
          super.commit(time, tracker, container, accountKey, existing);
          super.commit(time, tracker, container, accountKey, api);
        }
      } else {
        // New entity
        api.setup(accountKey, time);
        super.commit(time, tracker, container, accountKey, api);
      }
    } else {
      // Should never happen!
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(ICorporationAPI corpRequest) throws IOException {
    return corpRequest.requestCorporationSheet();
  }

  @Override
  protected long processServerData(long time, SynchronizedEveAccount syncAccount, ICorporationAPI corpRequest, Object data, List<CachedData> updates)
    throws IOException {
    ICorporationSheet corpSheet = (ICorporationSheet) data;
    CorporationSheet sheet = new CorporationSheet(
        corpSheet.getAllianceID(), corpSheet.getAllianceName(), corpSheet.getCeoID(), corpSheet.getCeoName(), corpSheet.getCorporationID(),
        corpSheet.getCorporationName(), corpSheet.getDescription(), corpSheet.getLogoColor1(), corpSheet.getLogoColor2(), corpSheet.getLogoColor3(),
        corpSheet.getLogoGraphicID(), corpSheet.getLogoShape1(), corpSheet.getLogoShape2(), corpSheet.getLogoShape3(), corpSheet.getMemberCount(),
        corpSheet.getMemberLimit(), corpSheet.getShares(), corpSheet.getStationID(), corpSheet.getStationName(), corpSheet.getTaxRate(), corpSheet.getTicker(),
        corpSheet.getUrl());
    updates.add(sheet);

    // Add or update non-wallet divisions as needed.
    Set<Integer> usedKeys = new HashSet<Integer>();
    for (IDivision next : corpSheet.getDivisions()) {
      Division nextDiv = new Division(false, next.getAccountKey(), next.getDescription());
      updates.add(nextDiv);
      usedKeys.add(next.getAccountKey());
    }
    for (Division next : Division.getAllByType(syncAccount, time, false)) {
      int accountKey = next.getAccountKey();
      if (!usedKeys.contains(accountKey)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }

    // Add or update wallet divisions as needed.
    usedKeys.clear();
    for (IDivision next : corpSheet.getWalletDivisions()) {
      Division nextDiv = new Division(true, next.getAccountKey(), next.getDescription());
      updates.add(nextDiv);
      usedKeys.add(next.getAccountKey());
    }
    for (Division next : Division.getAllByType(syncAccount, time, true)) {
      int accountKey = next.getAccountKey();
      if (!usedKeys.contains(accountKey)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }

    return corpRequest.getCachedUntil().getTime();
  }

  private static final CorporationSheetSync syncher = new CorporationSheetSync();

  public static SyncStatus syncCorporationSheet(long time, SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil, ICorporationAPI corpRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, corpRequest, "CorporationSheet");
  }

  public static SyncStatus exclude(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationSheet", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(SynchronizedEveAccount syncAccount, SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "CorporationSheet", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
