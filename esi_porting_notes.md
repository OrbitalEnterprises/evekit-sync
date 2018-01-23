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
  * **beta** [Asset](#asset)
  * **beta** [Blueprint](#blueprint)
  * **pending** [Bookmark](#bookmark)
  * **pending** [Contact](#contact)
  * **pending** [ContactLabel](#contactlabel)
  * **dev** [Contract](#contract)
  * **dev** [ContractBid](#contractbid)
  * **dev** [ContractItem](#contractitem)
  * **pending** [FacWarStats](#facwarstats)
  * **dev** [IndustryJob](#industryjob)
  * **pending** [Kill](#kill)
  * **pending** [KillAttacker](#killattacker)
  * **pending** [KillItem](#killitem)
  * **pending** [KillVictim](#killvictim)
  * **beta** [Location](#location)
  * **beta** [MarketOrder](#marketorder)
  * **beta** [Standing](#standing)
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
accountKey | accountKey (generated) | N/A | `Division` replaces this field in the ESI and is numbered from 1-7 instead of the previous numbering scheme.  For now, we'll generate this field from division using the equation `accountKey = division - 1 + 1000` 
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
quantity | quantity | quantity | 
locationID | locationID | location\_id | In the ESI, this can be the `itemID` of a parent asset (e.g. container) which allows us to recover the asset tree as was provided by the XML API.  In historic data, this is zero for a contained asset.  See conversion notes below.
*N/A* | locationType | location\_type | See conversion notes below for historic data.
itemID | itemID | item\_id |
flag | (deleted) | *N/A* | This is replaced by the enumerated type location\_flag.
*N/A* | locationFlag | location\_flag | Replaces `flag`.  See conversion notes below for historic data.
singleton | singleton | is\_singleton | 
rawQuantity | (deleted) | *N/A* | This field is negative when encoding blueprint and singleton information.  See conversion notes below. 
*N/A* | blueprintType | *N/A* | New field to retain historic information.  See conversion notes below.
container | (deleted) | *N/A* | This is an EveKit specific encoding which gives the `itemID` of the container in which this asset is stored.  In the ESI, the `itemID` of the parent of a contained asset is now stored in `locationID`.  See conversion notes below.

#### Raw Quantity and Quantity in Historic Data

* Source: https://eveonline-third-party-documentation.readthedocs.io/en/latest/xmlapi/character/char_assetlist.html
* Source: conversations with GoldenGnu (jEveAssets dev)
* Source: historical data stored in EveKit

Raw Quantity | Quantity | Meaning 
---|---|---
0 | anything | A standard stack of assets.
-1 | 1 | Either a blueprint original or a non-stackable (unpackaged) asset.
-1 | > 1 | A stack of blueprint originals.
-2 | 1 | A blueprint copy.  These can't be stacked.
< -2 | 1 | Weird suff, probably bugs.  Example (from EveKit): flag:62, itemID:1021197887400, locationID:60014818, quantity:1, rawQuantity:-380, singleton:1, typeID:3468 (plastic wrap)

We have enough information in historic EveKit data to preserve which assets were blueprint copies and which were originals, except for one weird case (plastic wrap).  The best we can likely do is record this information for historical purposes and rely on the ESI blueprint endpoint going forward.

#### Historic Conversion Notes
* To retain historic blueprint information, we'll introduce a new String field called `blueprintType`.  We'll set `blueprintType = "copy"` if `rawQuantity = -2`, `blueprintType = "original"` if `rawQuantity = -1` and `itemID` is a blueprint, otherwise `blueprintType = null`.
* `locationType` will be populated for historic data using the technique described [here](https://gist.github.com/a-tal/5ff5199fdbeb745b77cb633b7f4400bb#file-id_ranges-md).  Specifically:

```
if (locationID >= 30000000 && locationID <= 32000000) { 
  location_type = solar_system 
} else if (locationID >= 60000000 && locationID <= 64000000) { 
  location_type = station 
} else { 
  location_type = other 
}
```
* In historic data, contained assets would set `locationID = 0`.  In the ESI, `locationID` is now the `itemID` of the containing asset.  To maintain consistency with the ESI going forward, we'll set `locationID = container` when `locationID = 0` for historic data.  We can then remove the `container` field.
* `locationFlag` will be set from `flag` using the mapping [here](https://github.com/ccpgames/eve-glue/blob/master/eve_glue/location_flag.py).

### Blueprint

ESI endpoint(s):

* `/characters/{character_id}/blueprints/`
* `/corporations/{corporation_id}/blueprints/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
itemID | itemID | item\_id |
locationID | locationID | location\_id |
typeID | typeID | type\_id |
typeName | (deleted) | *N/A* | ESI requires lookup from typeID.
flagID | (deleted) | *N/A* | Inventory flag, now represented by the enumerated type `location_flag`.  For historic data, `location_flag` will be populated by mapping `flagID` using the [inventory types table](https://github.com/ccpgames/eve-glue/blob/master/eve_glue/location_flag.py).
*N/A* | locationFlag | location\_flag | Enumerated type describing location.
quantity | quantity | quantity | 
timeEfficiency | timeEfficiency | time_efficiency |
materialEfficiency | materialEfficiency | material_efficiency | 
runs | runs | runs | 

### Bookmark
### Contact
### ContactLabel
### Contract

ESI endpoint(s):

* `/characters/{character_id}/contracts/`
* `/corporations/{corporation_id}/contracts/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
contractID | contractID | contract\_id |
issuerID | issuerID | issuer\_id |
issuerCorpID | issuerCorpID | issuer\_corporation\_id |
assigneeID | assigneeID | assignee\_id |
acceptorID | acceptorID | acceptor\_id |
startStationID | startStationID | start\_location\_id |
endStationID | endStationID | end\_location\_id |
type | type | type | Enumerated type in ESI.
status | status | status | Enumerated type in ESI.
title | title | title |
forCorp | forCorp | for\_corporation |
availability | availability | availability | Enumerated type in ESI.
dateIssued | dateIssued | date\_issued |
dateExpired | dateExpired | date\_expired | 
dateAccepted | dateAccepted | date\_accepted |
numDays | numDays | days\_to\_complete |
dateCompleted | dateCompleted | date\_completed |
price | price | price |
reward | reward | reward |
collateral | collateral | collateral |
buyout | buyout | buyout |
volume | volume | volume |

#### Historic Conversion Notes
* Historic `type` will be mapped to the enumerated type as follows:
  * Unknown = unknown
  * ItemExchange = item\_exchange
  * Auction = auction
  * Courier = courier
  * Loan = loan
* Historic `status` will be mapped to the enumerated type as follows:
  * Outstanding = outstanding
  * InProgress = in\_progress
  * FinishedIssuer = finished\_issuer
  * FinishedContractor = finished\_contractor
  * Finished = finished
  * Cancelled = cancelled
  * Rejected = rejected
  * Failed = failed
  * Deleted = deleted
  * Reversed = reversed
* Historic `availability` will be mapped to the enumerated type as follows:
  * Public = public
  * Personal = personal
  * Corporation = corporation
  * Alliance = alliance
  * Private = personal
    * This is not always correct.  The proper way to convert historically is to determine whether the assignee is a character, corporation or alliance so that Private = personal for characters, Private = corporation for corporations, and Private = alliance for alliances.

### ContractBid

ESI endpoint(s):

* `/characters/{character_id}/contracts/{contract_id}/bids/`
* `/corporations/{corporation_id}/contracts/{contract_id}/bids/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
bidID | bidID | bid\_id |
contractID | contractID | *N/A* | Inserted by EveKit since this model has its own table.
bidderID | bidderID | bidder\_id |
dateBid | dateBid | date\_bid |
amount | amount | amount |

### ContractItem

ESI endpoint(s):

* `/characters/{character_id}/contracts/{contract_id}/items/`
* `/corporations/{corporation_id}/contracts/{contract_id}/items/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
contractID | contractID | *N/A* | Inserted by EveKit since ths model has its own table.
recordID | recordID | record\_id |
typeID | typeID | type\_id |
quantity | quantity | quantity |
rawQuantity | rawQuantity | raw\_quantity | 
singleton | singleton | is\_singleton |
included | included | is\_included |

### FacWarStats
### IndustryJob

ESI endpoint(s):

* `/characters/{character_id}/industry/jobs/`
* `/corporations/{corporation_id}/industry/jobs/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
jobID | jobID | job\_id  |
installerID | installerID | installer\_id |
installerName | (deleted) | *N/A* | ESI expects lookup from `installerID`
facilityID | facilityID | facility\_id |
solarSystemID | (deleted) | *N/A* | Removed in ESI
solarSystemName | (deleted) | *N/A* | Removed in ESI
stationID | stationID | station\_id or location\_id | Character and corporation endpoints name this inconsistently.  We'll use `stationID` for now.
activityID | activityID | activity\_id |
blueprintID | blueprintID | blueprint\_id |
blueprintTypeID | blueprintTypeID | blueprint\_type\_id |
blueprintTypeName | (deleted) | *N/A*| ESI expects lookup from `blueprintTypeID`
blueprintLocationID | blueprintLocationID | blueprint\_location\_id |
outputLocationID | outputLocationID | output\_location\_id |
runs | runs | runs |
cost | cost | cost |
teamID | (deleted) | *N/A*| Removed in ESI
licensedRuns | licensedRuns | licensed\_runs |
probability | probability | probability |
productTypeID | productTypeID | product\_type\_id |
productTypeName | (deleted) | *N/A* | ESI expects lookup from `productTypeID`
status | status | status | Now an enumerated typr
timeInSeconds | timeInSeconds | duration |
startDate | startDate | start\_date |
endDate | endDate | end\_date |
pauseDate | pauseDate | pause\_date |
completedDate | completedDate | completed\_date |
completedCharacterID | completedCharacterID | completed\_character\_id |
successfulRuns | successfulRuns | successful\_runs |

#### Historic Conversion Notes
* Historic `status` will be mapped to the enumerated type using [this table](https://github.com/ccpgames/eve-glue/blob/master/eve_glue/industry_job_status.py).

### Kill
### KillAttacker
### KillItem
### KillVictim
### Location

ESI endpoint(s):

* `/characters/{character_id}/assets/names/`
* `/characters/{character_id}/assets/locations/`
* `/corporations/{corporation_id}/assets/names/`
* `/corporations/{corporation_id}/assets/locations/`

**Note:** this model is a special case because it combines the information from two separate ESI endpoints (location and name).  We combine the data into a single model for convenience.  

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
itemID | itemID | item_id | Included in both location and name endpoints.
itemName | itemName | name | Only included in the name endpoint.
x | x | x | Only included in the location endpoint.
y | y | y | Only included in the location endpoint.
z | z | z | Only included in the location endpoint.

### MarketOrder

* `/characters/{character_id}/orders/`
* `/corporations/{corporation_id}/orders/`


Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
orderID | orderID | order_id |
accountKey | accountKey (generated) | *N/A* | This is replaced by `wallet_division`.  For now, we generate this field as `accountKey = division - 1 + 1000`.  We'll remove this field in the future.
*N/A* | walletDivision | wallet\_division *or* account\_id | Replaces `accountKey`.  For historic data, we set `division = accountKey - 1000 + 1`.  Note: for characters, the ESI field is called `account_id` and is numbered the same was as `accountKey`.  In other words, we need to set `division = account_id - 1000 + 1`.
bid | bid | is\_buy\_order |
charID | charID (historic only) | *N/A* | This information no longer exists in the ESI.  It is implied for character orders, but can not be determined for corporation orders.  We will retain the field in historic data but not populate it going forward.
duration | duration | duration | 
escrow | escrow | escrow | 
issued | issued | issued |
minVolume | minVolume | min\_volume |
orderState | orderState | state | Now an enumerated type instead of an integer.
price | price | price | 
orderRange | orderRange | range | Now an enumerated type instead of an integer.
stationID | (deleted) | *N/A* | Replaced by locationID.
typeID | typeID | type\_id | 
volEntered | volEntered | volume\_total |
volRemaining | volRemaining | volume\_remain | 
*N/A* | regionID | region\_id | New field introduced in ESI.  Historical data will be populated based on stationID where possible.  This won't be possible with certain player-owned stations due to access control rules.
*N/A* | locationID | location\_id | New field introduced in ESI which replaces stationID.  For historic data, we'll set `locationID = stationID`.
*N/A* | isCorp | is\_corp | New for the ESI, true when the order is placed on behalf of the player's corporation.  For historic data, we set this to true if `division` is greater than 1.  Otherwise, the result is ambiguous and `isCorp` will be set to false.

#### Historic Conversion Notes
* `walletDivision` will be populated historically from `accountKey`.
* `accountKey` will be generated on the server for now (based on `walletDivision`), but will be removed in the future.
* `charID` will be retained on historic entries, but not populated going forward.
* `locationID` will be set to `stationID`.
* `regionID` will be set to the region where `stationID` resides.  This may fail for recent orders if the station is a player-owned structure which can not be resolved.  In these cases, `regionID` will be 0.
* `isCorp` will be set to true if `division > 1` and false otherwise.  This is the best we can do since this distinction is missing from historic data.

### Standing

* `/characters/{character_id}/standings/`
* `/corporations/{corporation_id}/standings/`


Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
standingEntity | standingEntity | from\_type |
fromID | fromID | from\_id |
fromName | (deleted) | *N/A* | ESI expects lookup from `fromID`.
standing | standing | standing |

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
