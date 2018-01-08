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

## Index

* Character Model Changes
  * **pending** [UpcomingCalendarEvent](#upcomingcalendarevent)
  * **pending** [CalendarEventAttendee](#calendareventattendee)
  * **pending** [CharacterContactNotification](#charactercontactnotification)
  * **pending** [CharacterMailMessage](#charactermailmessage)
  * **pending** [CharacterMailMessageBody](#charactermailmessagebody)
  * **pending** [CharacterMedal](#charactermedal)
  * **pending** [CharacterNotification](#characternotification)
  * **pending** [CharacterNotificationBody](#characternotificationbody)
  * **pending** [CharacterRole](#characterrole)
  * **pending** [CharacterSheet](#charactersheet)
  * **pending** [CharacterSheetBalance](#charactersheetbalance)
  * **pending** [CharacterSheetClone](#charactersheetclone)
  * **pending** [CharacterSheetJump](#charactersheetjump)
  * **pending** [CharacterSkill](#characterskill)
  * **pending** [CharacterSkillInTraining](#characterskillintraining)
  * **pending** [SkillInQueue](#skillinqueue)
  * **pending** [CharacterTitle](#charactertitle)
  * **pending** [ChatChannel](#chatchannel)
  * **pending** [ChatChannelMember](#chatchannelmember)
  * **pending** [Implant](#implant)
  * **pending** [JumpClone](#jumpclone)
  * **pending** [JumpCloneImplant](#jumpcloneimplant)
  * **pending** [MailingList](#mailinglist)
  * **pending** [PlanetaryColony](#planetarycolony)
  * **pending** [PlanetaryLink](#planetarylink)
  * **pending** [PlanetaryPin](#planetarypin)
  * **pending** [PlanetaryRoute](#planetaryroute)
  * **dev** [ResearchAgent](#researchagent)
* Corporation Model Changes
  * **pending** [ContainerLog](#containerlog)
  * **pending** [CorporationMedal](#corporationmedal)
  * **pending** [CorporationMemberMedal](#corporationmembermedal)
  * **pending** [CorporationSheet](#corporationsheet)
  * **pending** [CorporationTitle](#corporationtitle)
  * **pending** [CustomsOffice](#customsoffice)
  * **pending** [Division](#division)
  * **pending** [Facility](#facility)
  * **pending** [Fuel](#fuel)
  * **pending** [MemberSecurity](#membersecurity)
  * **pending** [MemberSecurityLog](#membersecuritylog)
  * **pending** [MemberTracking](#membertracking)
  * **pending** [Outpost](#outpost)
  * **pending** [OutpostServiceDetail](#outpostservicedetail)
  * **pending** [Role](#role)
  * **pending** [SecurityRole](#securityrole)
  * **pending** [SecurityTitle](#securitytitle)
  * **pending** [Shareholder](#shareholder)
  * **pending** [Starbase](#starbase)
  * **pending** [StarbaseDetail](#starbasedetail)
* Common Model Changes
  * **dev** [AccountBalance](#accountbalance)
  * **N/A** [AccountStatus](#accountstatus)
  * **pending** [Asset](#asset)
  * **pending** [Blueprint](#blueprint)
  * **pending** [Bookmark](#bookmark)
  * **pending** [Contact](#contact)
  * **pending** [ContactLabel](#contactlabel)
  * **pending** [Contract](#contract)
  * **pending** [ContractBid](#contractbid)
  * **pending** [ContractItem](#contractitem)
  * **pending** [FacWarStats](#facwarstats)
  * **pending** [IndustryJob](#industryjob)
  * **pending** [Kill](#kill)
  * **pending** [KillAttacker](#killattacker)
  * **pending** [KillItem](#killitem)
  * **pending** [KillVictim](#killvictim)
  * **pending** [Location](#location)
  * **pending** [MarketOrder](#marketorder)
  * **pending** [Standing](#standing)
  * **dev** [WalletJournal](#walletjournal)
  * **dev** [WalletTransaction](#wallettransaction)

## Character Model Changes

### UpcomingCalendarEvent
### CalendarEventAttendee
### CharacterContactNotification
### CharacterMailMessage
### CharacterMailMessageBody
### CharacterMedal
### CharacterNotification
### CharacterNotificationBody
### CharacterRole
### CharacterSheet
### CharacterSheetBalance
### CharacterSheetClone
### CharacterSheetJump
### CharacterSkill
### CharacterSkillInTraining
### SkillInQueue
### CharacterTitle
### ChatChannel
### ChatChannelMember
### Implant
### JumpClone
### JumpCloneImplant
### MailingList
### PlanetaryColony
### PlanetaryLink
### PlanetaryPin
### PlanetaryRoute
### ResearchAgent

ESI endpoint(s):
* `/characters/{character_id}/agents_research/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
agentID | agentID | agent_id |
currentPoints | (deleted) | *N/A* | This field will be removed and deleted from historical data since this value can be computed with the formula: `currentPoints = remainderPoints + pointsPerDay * days(currentTime - researchStartDate)`
pointsPerDay | pointsPerDay | points_per_day |
remainderPoints | remainderPoints | remainder_points |
researchStartDate | resarchStartDate | started_at | ESI stores this as a text string, which EveKit converts to milliseconds UTC.  `researchStartDateDate` can be used if a string-valued date is required
skillTypeID | skillTypeID | skill_type_id |
researchStartDateDate (generated) | researchStartDateDate (generated) | *N/A* | This is a convenient string representation of the date field, rendered for human readability.

## Corporation Model Changes

### ContainerLog
### Corporation
### CorporationMedal
### CorporationMemberMedal
### CorporationSheet
### CorporationTitle
### CustomsOffice
### Division
### Facility
### Fuel
### MemberSecurity
### MemberSecurityLog
### MemberTracking
### Outpost
### OutpostServiceDetail
### Role
### SecurityRole
### SecurityTitle
### Shareholder
### Starbase
### StarbaseDetail

## Common Model Changes

### AccountBalance

ESI endpoint(s):
* `/characters/{character_id}/wallet/`
* `/corporations/{corporation_id}/wallets/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
accountID | (deleted) | *N/A* | This field does not exist in the ESI and will be removed from historic data.
*N/A* | division | division  | Only exists for corporations. Set to 1 for characters (but can be ignored).  For historic data, this field will be computed as `division = accountKey - 1000 + 1`.
accountKey | accountKey (generated) | N/A | `Division` replaces this field in the ESI and is numbered from 1-7 instead of the previous numbering scheme.  For now, we'll generate this field from division using the equation `accountKey = division - + 1000` 
balance | balance | balance | 

### AccountStatus

Removed.  Account status is not supported from the ESI.  Similar information can be recovered from the `/characters/{character_id}/online/` endpoint which we may support in the future.

### Asset
### Blueprint
### Bookmark
### Contact
### ContactLabel
### Contract
### ContractBid
### ContractItem
### FacWarStats
### IndustryJob
### Kill
### KillAttacker
### KillItem
### KillVictim
### Location
### MarketOrder
### Standing
### WalletJournal

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
argName1 | argName1 | *N/A* | This data is now populated in extra_info in the ESI.  Historical data will be unchanged until we determine how to map into the new extra info fields.
argID1 | argID1 | *N/A* | This data is now populated in extra_info in the ESI.  Historical data will be unchanged until we determine how to map into the new extra info fields.
amount | amount | amount |
balance | balance | balance |
reason | reason | reason |
taxReceiverID | taxReceiverID | tax_receiver_id |
taxAmount | taxAmount | tax |
dateDate (generated) | dateDate (generated) | *N/A* | This is a convenient string representation of the date field, rendered for human readability.
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

### WalletTransaction

ESI endpoint(s):
* `/characters/{character_id}/wallet/transactions/`
* `/corporations/{corporation_id}/wallets/{division}/transactions/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
accountKey | accountKey (generated) | *N/A* | This field doesn't exist in the XML API or the ESI, it is inserted by EveKit.  We're changing the name to division.  For now, we'll retain the field but generate it with the equation `accessKey = division - 1 + 1000`
*N/A* | division | *N/A* | This field doesn't exist in ESI, EveKit will add it.  The value will be 1 for characters, and 1-7 for corporations
transactionID | transactionID | transaction_id |
date | date | date | ESI stores this as a text string, which EveKit converts to milliseconds UTC.  `dateDate` can be used if a string-valued date is required
quantity | quantity | quantity | 
typeName | (deleted) | *N/A* | ESI leaves lookup from ID to user
typeID | typeID | type_id |
price | price | unit_price |
clientID | clientID | client_id |
clientName | (deleted) | *N/A* | ESI leaves lookup from ID to user
stationID | stationID (generated) | *N/A* | This is just a copy of locationID for now, which we'll remove at a future date
*N/A* | locationID | location_id | This is the ESI replacement for station ID which we'll use going forward.  Historical data will be converted by setting locationID = stationID
stationName | (deleted) | *N/A* | ESI leaves lookup from ID to user
transactionType | transactionType (generated) | *N/A* | We'll generate this from is_buy for now (see next entry), and remove at a future date
*N/A* | isBuy | is_buy | This is new in the ESI and we'll use it going forward.  Historical data will be converted with is_buy = true if transactionType = "buy", otherwise is_buy = false.
transactionFor | transactionFor (generated) | *N/A* | We'll generate this from is_personal for now (see next entry), and remove at a future date
*N/A* | isPersonal | is_personal | This is new in the ESI for characters and we'll use it going forward.  Historical data will be converted with is_personal = true if transactionFor = "personal", otherwise is_personal = false.
journalTransactionID | journalTransactionID | journal_ref_id |
clientTypeID | (deleted) | *N/A* | ESI has no analog, not clear what this was used for (even 3rd party docs aren't clear).
characterID | (deleted) | *N/A* | Bogus field left over from earlier version, will be removed.
characterName | (deleted) | *N/A* | Bogus field left over from earlier version, will be removed.
dateDate (generated) | dateDate (generated) | *N/A* | This is a convenient string representation of the date field, rendered for human readability.
