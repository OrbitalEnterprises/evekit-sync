package enterprises.orbital.evekit.model;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
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
    CachedData.cleanup(toDelete, "CalendarEventAttendee");
    CachedData.cleanup(toDelete, "Capsuleer");
    CachedData.cleanup(toDelete, "CharacterContactNotification");
    CachedData.cleanup(toDelete, "CharacterMailMessage");
    CachedData.cleanup(toDelete, "CharacterMedal");
    CachedData.cleanup(toDelete, "CharacterMedalGraphic");
    CachedData.cleanup(toDelete, "CharacterNotification");
    CachedData.cleanup(toDelete, "CharacterRole");
    CachedData.cleanup(toDelete, "CharacterLocation");
    CachedData.cleanup(toDelete, "CharacterShip");
    CachedData.cleanup(toDelete, "CharacterOnline");
    CachedData.cleanup(toDelete, "CharacterSheet");
    CachedData.cleanup(toDelete, "CharacterSheetAttributes");
    CachedData.cleanup(toDelete, "CharacterSheetSkillPoints");
    CachedData.cleanup(toDelete, "CharacterSheetClone");
    CachedData.cleanup(toDelete, "CharacterSheetJump");
    CachedData.cleanup(toDelete, "CharacterSkill");
    CachedData.cleanup(toDelete, "CharacterTitle");
    CachedData.cleanup(toDelete, "ChatChannel");
    CachedData.cleanup(toDelete, "ChatChannelMember");
    CachedData.cleanup(toDelete, "Implant");
    CachedData.cleanup(toDelete, "JumpClone");
    CachedData.cleanup(toDelete, "JumpCloneImplant");
    CachedData.cleanup(toDelete, "MailingList");
    CachedData.cleanup(toDelete, "MailLabel");
    CachedData.cleanup(toDelete, "PlanetaryColony");
    CachedData.cleanup(toDelete, "PlanetaryLink");
    CachedData.cleanup(toDelete, "PlanetaryPin");
    CachedData.cleanup(toDelete, "PlanetaryRoute");
    CachedData.cleanup(toDelete, "ResearchAgent");
    CachedData.cleanup(toDelete, "SkillInQueue");
    CachedData.cleanup(toDelete, "UpcomingCalendarEvent");
    CachedData.cleanup(toDelete, "AccountBalance");
    CachedData.cleanup(toDelete, "AccountStatus");
    CachedData.cleanup(toDelete, "Asset");
    CachedData.cleanup(toDelete, "Blueprint");
    CachedData.cleanup(toDelete, "Bookmark");
    CachedData.cleanup(toDelete, "Contact");
    CachedData.cleanup(toDelete, "ContactLabel");
    CachedData.cleanup(toDelete, "Contract");
    CachedData.cleanup(toDelete, "ContractBid");
    CachedData.cleanup(toDelete, "ContractItem");
    CachedData.cleanup(toDelete, "FacWarStats");
    CachedData.cleanup(toDelete, "IndustryJob");
    CachedData.cleanup(toDelete, "Kill");
    CachedData.cleanup(toDelete, "KillAttacker");
    CachedData.cleanup(toDelete, "KillItem");
    CachedData.cleanup(toDelete, "KillVictim");
    CachedData.cleanup(toDelete, "Location");
    CachedData.cleanup(toDelete, "MarketOrder");
    CachedData.cleanup(toDelete, "Standing");
    CachedData.cleanup(toDelete, "WalletJournal");
    CachedData.cleanup(toDelete, "WalletTransaction");
    CachedData.cleanup(toDelete, "ContainerLog");
    CachedData.cleanup(toDelete, "Corporation");
    CachedData.cleanup(toDelete, "CorporationMedal");
    CachedData.cleanup(toDelete, "CorporationMemberMedal");
    CachedData.cleanup(toDelete, "CorporationSheet");
    CachedData.cleanup(toDelete, "CorporationTitle");
    CachedData.cleanup(toDelete, "CorporationTitleRole");
    CachedData.cleanup(toDelete, "CustomsOffice");
    CachedData.cleanup(toDelete, "Division");
    CachedData.cleanup(toDelete, "Facility");
    CachedData.cleanup(toDelete, "Fuel");
    CachedData.cleanup(toDelete, "Member");
    CachedData.cleanup(toDelete, "MemberLimit");
    CachedData.cleanup(toDelete, "MemberRole");
    CachedData.cleanup(toDelete, "MemberRoleHistory");
    CachedData.cleanup(toDelete, "MemberTitle");
    CachedData.cleanup(toDelete, "MemberTracking");
    CachedData.cleanup(toDelete, "Outpost");
    CachedData.cleanup(toDelete, "OutpostServiceDetail");
    CachedData.cleanup(toDelete, "Shareholder");
    CachedData.cleanup(toDelete, "Starbase");
    CachedData.cleanup(toDelete, "StarbaseDetail");
    try {
      SynchronizedEveAccount.remove(toDelete);
    } catch (IOException e) {
      log.log(Level.SEVERE, "Failed to delete account", e);
    }
    log.info("Account deleted");
  }
}
