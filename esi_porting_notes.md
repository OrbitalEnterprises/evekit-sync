# ESI Porting Notes

EveKit manages and evolves "model" objects which represent data about EVE characters and corporations.
These models are updated from data retrieved from the XML API.  Now that the XML API has been deprecated,
EveKit will instead use the EVE Swagger Interface (ESI) to update models.  A number of changes have been
made between the XML and ESI APIs.  As a result, EveKit's models will also need to change.  This
document describes the various decisions we have made in updating models to use the ESI.

EveKit models are organized into three groups:

1. Character specific models;
2. Corporaton specific models; and,
3. Common models shared between both characters and corporations.

Each change can be in one of the following states:

* N/A - not relevant or not being upgraded
* pending - not started yet
* dev - in development
* beta - live on the beta site (https://evekit-beta.orbital.enterprises)
* prod - live on the main site (https://evekit.orbital.enterprises)

## Character Model Changes

### UpcomingCalendarEvent (pending)
### CalendarEventAttendee (pending)
### CharacterContactNotification (pending)
### CharacterMailMessage (pending)
### CharacterMailMessageBody (pending)
### CharacterMedal (pending)
### CharacterNotification (pending)
### CharacterNotificationBody (pending)
### CharacterRole (pending)
### CharacterSheet (pending)
### CharacterSheetBalance (pending)
### CharacterSheetClone (pending)
### CharacterSheetJump (pending)
### CharacterSkill (pending)
### CharacterSkillInTraining (pending)
### SkillInQueue (pending)
### CharacterTitle (pending)
### ChatChannel (pending)
### ChatChannelMember (pending)
### Implant (pending)
### JumpClone (pending)
### JumpCloneImplant (pending)
### MailingList (pending)
### PlanetaryColony (pending)
### PlanetaryLink (pending)
### PlanetaryPin (pending)
### PlanetaryRoute (pending)
### ResearchAgent (pending)

## Corporation Model Changes

### ContainerLog (pending)
### Corporation (pending)
### CorporationMedal (pending)
### CorporationMemberMedal (pending)
### CorporationSheet (pending)
### CorporationTitle (pending)
### CustomsOffice (pending)
### Division (pending)
### Facility (pending)
### Fuel (pending)
### MemberSecurity (pending)
### MemberSecurityLog (pending)
### MemberTracking (pending)
### Outpost (pending)
### OutpostServiceDetail (pending)
### Role (pending)
### SecurityRole (pending)
### SecurityTitle (pending)
### Shareholder (pending)
### Starbase (pending)
### StarbaseDetail (pending)

## Common Model Changes

### AccountBalance (dev)

ESI endpoint(s):
* `/characters/{character_id}/wallet/`
* `/corporations/{corporation_id}/wallets/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
accountID | (deleted) | *N/A* | This field does not exist in the ESI and will be removed from historic data.
*N/A* | division | division  | Only exists for corporations. Set to 1 for characters (but can be ignored).  For historic data, this field will be computed as `division = accountKey - 1000 + 1`.
accountKey | accountKey (generated) | N/A | `Division` replaces this field in the ESI and is numbered from 1-8 instead of the previous numbering scheme.  For now, we'll generate this field from division using the equation `accountKey = division - + 1000` 
balance | balance | balance | 

### AccountStatus (N/A)

Removed.  Account status is not supported from the ESI.  Similar information can be recovered from the `/characters/{character_id}/online/` endpoint which we may support in the future.

### Asset (pending)
### Blueprint (pending)
### Bookmark (pending)
### Contact (pending)
### ContactLabel (pending)
### Contract (pending)
### ContractBid (pending)
### ContractItem (pending)
### FacWarStats (pending)
### IndustryJob (pending)
### Kill (pending)
### KillAttacker (pending)
### KillItem (pending)
### KillVictim (pending)
### Location (pending)
### MarketOrder (pending)
### Standing (pending)
### WalletJournal (pending)
### WalletTransaction (pending)
