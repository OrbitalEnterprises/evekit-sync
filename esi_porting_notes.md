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
  * **beta** [ResearchAgent](#researchagent)
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
  * **beta** [AccountBalance](#accountbalance)
  * **N/A** [AccountStatus](#accountstatus)
  * **dev** [Asset](#asset)
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
  * **beta** [WalletJournal](#walletjournal)
  * **beta** [WalletTransaction](#wallettransaction)

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
pointsPerDay | pointsPerDay | points\_per\_day |
remainderPoints | remainderPoints | remainder_points |
researchStartDate | resarchStartDate | started_at | ESI stores this as a text string, which EveKit converts to milliseconds UTC.  `researchStartDateDate` can be used if a string-valued date is required
skillTypeID | skillTypeID | skill\_type\_id |
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

ESI endpoint(s):

* `/characters/{character_id}/assets/`
* `/corporations/{corporation_id}/assets/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
typeID | typeID | type\_id | 
quantity | quantity | quantity | For historic data, quantity was overloaded to encode singletons and blueprints.  See conversion notes below.
locationID | locationID | location\_id | In the ESI, this can be the `itemID` of a parent asset (e.g. container) which allows us to recover the asset tree as was provided by the XML API.  In historic data, this is zero for a contained asset.  See conversion notes below.
*N/A* | locationType | location\_type | See conversion notes below for historic data.
itemID | itemID | item\_id |
flag | (deleted) | *N/A* | This is replaced by the enumerated type location\_flag.
*N/A* | locationFlag | location\_flag | Replaces `flag`.  See conversion notes below for historic data.
singleton | singleton | is\_singleton | Replaces overloading in quantity for historic data.  See conversion notes below.
rawQuantity | (deleted) | *N/A* | This field is non-null in historic data when quantity < 0.  See conversion notes below. 
*N/A* | blueprint | *N/A* | New field to retain historic information.  See conversion notes below.
container | (deleted) | *N/A* | This is an EveKit specific encoding which gives the `itemID` of the container in which this asset is stored.  In the ESI, the `itemID` of the parent of a contained asset is now stored in `locationID`.  See conversion notes below.

#### Historical Conversion Notes
* Quantity can be negative in historical data to indicate either a singleton (-1), or a blueprint (-1 = original, -2 = copy).  To preserve this distinction but remain consistent with future data, we'll make the following conversions:
  * We'll introduce a new String field called `blueprint` which is only populated for historic data.  `blueprint = "copy"` if `quantity = -2`.  Otherwise, `blueprint = "original"` if `quantity = -1` and `itemID` is a blueprint.   `blueprint = null` otherwise, or if we can't resolve `typeID` (e.g. deleted from the SDE).
  * We'll set `singleton = true` if `quantity = -1` representing either an unpackaged (non-stackable) singleton element and/or a blueprint original (note that blueprint originals are also unstackable, so this is consistent).
  * We'll set `quantity = rawQuantity` in any case where `quantity < 0`.  This is to remain consistent with `quantity` going forward which will always be positive.
  * If/when CCP introduces a proper blueprint field for assets, we'll populate the `blueprint` field appropriately.  Otherwise, it will be null for ESI data.
* `locationType` will be populated for historic data using the technique described [here](https://gist.github.com/a-tal/5ff5199fdbeb745b77cb633b7f4400bb#file-id_ranges-md).  Specifically:

```
if (locationID >= 30000000 && locationID <= 32000000) { 
  location_flag = solar_system 
} else if (locationID >= 60000000 && locationID <= 64000000) { 
  location_flag = station 
} else { 
  location_flag = other 
}
```
* In historic data, contained assets would set `locationID = 0`.  In the ESI, `locationID` is now the `itemID` of the containing asset.  To maintain consistency with the ESI going forward, we'll set `locationID = container` when `locationID = 0` for historic data.  We can then remove the `container` field.

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
ownerID1 | ownerID1 (generated) | *N/A* | Replaced by first\_party\_id in the ESI.  This will be copied from firstPartyID for now, and removed at a later date.
*N/A* | firstPartyID | first\_party\_id | Replaces ownerID1, historical data will be copied over to firstPartyID.
owner1TypeID | (deleted) | *N/A* | Replaced in ESI by string enumerated first\_party\_type
*N/A* | firstPartyType | first\_party\_type | New field in ESI.  To convert historical data, the owner1TypeID field has to be resolved to one of character, corporation, alliance, faction, system.
ownerName2 | (deleted) | *N/A* | ESI leaves lookup from ID to user
ownerID2 | ownerID2 (generated) | *N/A* | Replaced by second\_party\_id in the ESI.  This will be copied from secondPartyID for now, and removed at a later date.
*N/A* | secondPartyID | second\_party\_id | Replaces ownerID2, historical data will be copied over to secondPartyID.
owner2TypeID | (deleted) | *N/A* | Replaced in ESI by string enmerated second\_party\_type
*N/A* | secondPartyType | second\_party\_type | New field in ESI.  To convert historical data, the owner2TypeID field has to be resolved to one of characte, corporation, alliance, faction, system.
argName1 | (deleted) | *N/A* | ESI leaves lookup from ID to user.
argID1 | argID1 | *N/A* | This data is now populated in extra\_info in the ESI.  See conversion notes below.
amount | amount | amount |
balance | balance | balance |
reason | reason | reason |
taxReceiverID | taxReceiverID | tax\_receiver\_id |
taxAmount | taxAmount | tax |
dateDate (generated) | dateDate (generated) | *N/A* | This is a convenient string representation of the date field, rendered for human readability.
*N/A* | locationID | extra\_info -> location_id | New field in ESI.
*N/A* | transactionID | extra\_info -> transaction_id | New field in ESI.
*N/A* | npcName | extra\_info -> npc_name | New field in ESI.
*N/A* | npcID | extra\_info -> npc_id | New field in ESI.
*N/A* | destroyedShipTypeID | extra\_info -> destroyed\_ship\_type\_id | New field in ESI.
*N/A* | characerID | extra\_info -> character_id | New field in ESI.
*N/A* | corporationID | extra\_info -> corporation_id | New field in ESI.
*N/A* | allianceID | extra\_info -> alliance_id | New field in ESI.
*N/A* | jobID | extra\_info -> job_id | New field in ESI.
*N/A* | contractID | extra\_info -> contract_id | New field in ESI.
*N/A* | systemID | extra\_info -> system_id | New field in ESI.
*N/A* | planetID | extra\_info -> planet_id | New field in ESI.

#### Historic Conversion Notes
* `argID1` in historic data is now separated into the "extra info" fields in the ESI.  We can convert most historic data by mapping from `refType` as shown [here](https://eveonline-third-party-documentation.readthedocs.io/en/latest/xmlapi/constants.html#reference-type).  We'll perform this conversion on historic data over time.

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
transactionType | transactionType (generated) | *N/A* | We'll generate this from is\_buy for now (see next entry), and remove at a future date
*N/A* | isBuy | is_buy | This is new in the ESI and we'll use it going forward.  Historical data will be converted with is\_buy = true if transactionType = "buy", otherwise is\_buy = false.
transactionFor | transactionFor (generated) | *N/A* | We'll generate this from is\_personal for now (see next entry), and remove at a future date
*N/A* | isPersonal | is_personal | This is new in the ESI for characters and we'll use it going forward.  Historical data will be converted with is\_personal = true if transactionFor = "personal", otherwise is\_personal = false.
journalTransactionID | journalTransactionID | journal\_ref\_id |
clientTypeID | (deleted) | *N/A* | ESI has no analog, not clear what this was used for (even 3rd party docs aren't clear).
characterID | (deleted) | *N/A* | Bogus field left over from earlier version, will be removed.
characterName | (deleted) | *N/A* | Bogus field left over from earlier version, will be removed.
dateDate (generated) | dateDate (generated) | *N/A* | This is a convenient string representation of the date field, rendered for human readability.
