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
accountKey | accountKey (generated) | N/A | `Division` replaces this field in the ESI and is numbered from 1-7 instead of the previous numbering scheme.  For now, we'll generate this field from division using the equation `accountKey = division - + 1000` 
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
### WalletJournal (dev)

ESI endpoint(s):
* `/characters/{character_id}/wallet/journal/`
* `/corporations/{corporation_id}/wallets/{division}/journal/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
accountKey | accountKey (generated) | *N/A* | This field doesn't exist in the XML API or the ESI, it is inserted by EveKit.  We're changing the name to division.  For now, we'll retain the field but generate it with the equation `accessKey = division - 1 + 1000`
*N/A* | division | *N/A* | This field doesn't exist in ESI, EveKit will add it.  The value will be 1 for characters, and 1-7 for corporations
refID | refID | refID | 
date | date | date | ESI stores this as a text string, which EveKit converts to milliseconds UTC.  `dateDate` can be used if a string-valued date is required
refTypeID | (deleted) | *N/A* | ESI moves ref types to an enumerated string field
*N/A* | refType | ref_type | This is now an enuerated string field.  Historical data will be converted from refTypeID using the mapping in https://gist.github.com/ccp-zoetrope/c03db66d90c2148724c06171bc52e0ec
ownerName1 | (deleted) | *N/A* | ESI leaves lookup from ID to user
ownerID1 | ownerID1 (generated) | *N/A* | Replaced by first_party_id in the ESI.  This will be copied from firstPartyID for now, and removed at a later date.
*N/A* | firstPartyID | first_party_id | Replaces ownerID1, historical data will be copied over to firstPartyID.
owner1TypeID | (deleted) | *N/A* | Replaced in ESI by string enumerated first_party_type
*N/A* | firstPartyType | first_party_type | New field in ESI.  To convert historical data, the owner1TypeID field has to be resolved to one of characte, corporation, alliance, faction, system.
ownerName2 | (deleted) | *N/A* | ESI leaves lookup from ID to user
ownerID2 | ownerID2 (generated) | *N/A* | Replaced by second_party_id in the ESI.  This will be copied from secondPartyID for now, and removed at a later date.
*N/A* | secondPartyID | second_party_id | Replaces ownerID2, historical data will be copied over to secondPartyID.
owner2TypeID | (deleted) | *N/A* | Replaced in ESI by string enmerated second_party_type
*N/A* | secondPartyType | second_party_type | New field in ESI.  To convert historical data, the owner2TypeID field has to be resolved to one of characte, corporation, alliance, faction, system.
argName1 | | | **TODO**
argID1 | | | **TODO**
amount | amount | amount |
balance | balance | balance |
reason | reason | reason |
taxReceiverID | taxReceiverID | tax_receiver_id |
taxAmount | taxAmount | tax |
dateDate (transient) | | |
*N/A* | locationID | extra_info -> location_id | New field in ESI.
*N/A* | transactionID | extra_info -> transaction_id | New field in ESI.
*N/A* | npcName | extra_info -> npc_name | New field in ESI.
*N/A* | npcID | extra_info -> npc_id | New field in ESI.
*N/A* | destroyedShipTypeID | extra_info -> destroyed_ship_type_id | New field in ESI.
*N/A* | characerID | extra_info -> character_id | New field in ESI.
*N/A* | corporationID | extra_info -> corporation_id | New field in ESI.
*N/A* | allianceID | extra_info -> alliance_id | New field in ESI.
*N/A* | jobID | extra_info -> job_id | New field in ESI.
*N/A* | contractID | extra_info -> contract_id | New field in ESI.
*N/A* | systemID | extra_info -> system_id | New field in ESI.
*N/A* | planetID | extra_info -> planet_id | New field in ESI.

### WalletTransaction (pending)
