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
  * **beta** [CharacterLocation](#characterlocation) (new)
  * **pending** [CharacterMailMessage](#charactermailmessage)
  * **pending** [CharacterMailMessageBody](#charactermailmessagebody)
  * **pending** [CharacterMedal](#charactermedal)
  * **pending** [CharacterNotification](#characternotification)
  * **pending** [CharacterNotificationBody](#characternotificationbody)
  * **beta** [CharacterOnline](#characteronline) (new)
  * **pending** [CharacterRole](#characterrole)
  * **dev** [CharacterSheet](#charactersheet)
  * **dev** [CharacterSheetAttributes](#charactersheetattributes) (new)
  * **N/A** [CharacterSheetBalance](#charactersheetbalance)
  * **dev** [CharacterSheetClone](#charactersheetclone)
  * **dev** [CharacterSheetJump](#charactersheetjump)
  * **dev** [CharacterSheetSkillPoints](#charactersheetskillpoints) (new)
  * **beta** [CharacterShip](#charactership) (new)
  * **dev** [CharacterSkill](#characterskill)
  * **N/A** [CharacterSkillInTraining](#characterskillintraining)
  * **dev** [SkillInQueue](#skillinqueue)
  * **pending** [CharacterTitle](#charactertitle)
  * **pending** [ChatChannel](#chatchannel)
  * **pending** [ChatChannelMember](#chatchannelmember)
  * **dev** [Implant](#implant)
  * **dev** [JumpClone](#jumpclone)
  * **dev** [JumpCloneImplant](#jumpcloneimplant)
  * **pending** [MailingList](#mailinglist)
  * **pending** [PlanetaryColony](#planetarycolony)
  * **pending** [PlanetaryLink](#planetarylink)
  * **pending** [PlanetaryPin](#planetarypin)
  * **pending** [PlanetaryRoute](#planetaryroute)
  * **beta** [ResearchAgent](#researchagent)
* Corporation Model Changes
  * **beta** [ContainerLog](#containerlog)
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
  * **beta** [Bookmark](#bookmark)
  * **pending** [Contact](#contact)
  * **pending** [ContactLabel](#contactlabel)
  * **beta** [Contract](#contract)
  * **beta** [ContractBid](#contractbid)
  * **beta** [ContractItem](#contractitem)
  * **pending** [FacWarStats](#facwarstats)
  * **beta** [IndustryJob](#industryjob)
  * **beta** [Kill](#kill)
  * **beta** [KillAttacker](#killattacker)
  * **beta** [KillItem](#killitem)
  * **beta** [KillVictim](#killvictim)
  * **beta** [Location](#location)
  * **beta** [MarketOrder](#marketorder)
  * **beta** [Standing](#standing)
  * **beta** [WalletJournal](#walletjournal)
  * **beta** [WalletTransaction](#wallettransaction)

## Character Model Changes

### UpcomingCalendarEvent
### CalendarEventAttendee
### CharacterContactNotification

### CharacterLocation (new)

ESI endpoint(s):

* `/characters/{character_id}/location/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | solarSystemID | solar\_system\_id |
*N/A* | stationID | station\_id |
*N/A* | structureID | structure\_id |

### CharacterMailMessage
### CharacterMailMessageBody
### CharacterMedal
### CharacterNotification
### CharacterNotificationBody

### CharacterOnline (new)

ESI endpoint(s):

* `/characters/{character_id}/online/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | online | online |
*N/A* | lastLogin | last\_login |
*N/A* | lastLogout | last\_logout |
*N/A* | logins | logins |

### CharacterRole
### CharacterSheet

ESI endpoint(s):

* `/characters/{character_id}/` - no scope required

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
characterID | characterID | *N/A* | Never changes and will be copied from SynchronizedEveAccount.
name | name | name | From `/characters/{character_id}/`
corporationID | corporationID | corporation\_id | From `/characters/{character_id}/`.  Type change from long to int. 
corporationName | (deleted) | *N/A* | ESI expects lookup from `corporationID`.
*N/A* | raceID | race\_id | From `/characters/{character_id}/`.  Replaces `race`.  Type is int.
race | (deleted) | *N/A* | ESI expects lookup from `raceID`.
doB | doB | birthday | From `/characters/{character_id}/`
bloodlineID | bloodlineID | bloodline\_id | From `/characters/{character_id}/`
bloodline | (deleted) | *N/A* | ESI expects lookup from `bloodlineID`.
ancestryID | ancestryID | ancestry\_id | From `/characters/{character_id}/`
ancestry | (deleted) | *N/A* | ESI expects lookup from `ancestryID`.
gender | gender | gender | Now an enumerated type with conversion `Male = male` and `Female = female`.
allianceName | (deleted) | *N/A* | ESI expects lookup from `allianceID`
allianceID | allianceID | alliance\_id | From `/characters/{character_id}/`.  Type change from long to int.
factionName | (deleted) | *N/A* | ESI expects lookup from `factionID`
factionID | factionID | faction\_id | From `/characters/{character_id}/`.  Type change from long to int.
*N/A* | description | description | From `/characters/{character_id}/`.  Type is string.
*N/A* | securityStatus | security\_status | From `/characters/{character_id}/`.  Type is float.
intelligence | (deleted) | *N/A* | Moved to `CharacterSheetAttributes`.
memory	     | (deleted)	    | *N/A*	   | Moved to `CharacterSheetAttributes`.
charisma     | (deleted)     | *N/A*     | Moved to `CharacterSheetAttributes`.
perception   | (deleted)   | *N/A*   | Moved to `CharacterSheetAttributes`.
willpower    | (deleted)    | *N/A*    | Moved to `CharacterSheetAttributes`.
homeStationID | (deleted) | *N/A* | Moved to `CharacterSheetClone`.
lastRespecDate | (deleted) | *N/A* | Moved to `CharacterSheetAttributes` as `lastRemapDate`.
lastTimedRespec | (deleted) | *N/A* | Moved to `CharacterSheetAttributes` as `accruedRemapCooldownDate`.
freeRespecs | (deleted) | *N/A* | Moved to `CharacterSheetAttributes` as `bonusRemaps`.
remoteStationDate | (deleted) | *N/A* | Moved to `CharacterSheetClone`.
freeSkillPoints | (deleted) | *N/A* | Moved to `CharacterSheetSkillPoints`.

#### Historic Conversion Notes

* `gender` case conversion, `Male = male` and `Female = female`
*  We can't preserve `homeStationID` and `remoteStationDate` as part of the update.  These fields will be updated the first time a sync occurs after the ESI port.

### CharacterSheetAttributes (new)

ESI endpoint(s):

* `/characters/{character_id}/attributes/` - requires esi-skills.read\_skills.v1

#### General Notes

This model replaces the attributes portion of the character sheet since these fields are retrieved through a new endpoint.

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | intelligence | intelligence | Moved from `CharacterSheet`
*N/A*	     | memory	    | memory	   | Moved from `CharacterSheet`
*N/A*     | charisma     | charisma     | Moved from `CharacterSheet`
*N/A*   | perception   | perception   | Moved from `CharacterSheet`
*N/A*    | willpower    | willpower    | Moved from `CharacterSheet`
*N/A* | lastRemapDate | last\_remap\_date | Moved from `CharacterSheet`
*N/A* | accruedRemapCooldownDate | accrued\_remap\_cooldown\_date | Moved from `CharacterSheet`
*N/A* | bonusRemaps | bonus\_remaps | Moved from `CharacterSheet`

### CharacterSheetBalance

Removed.  Replaced by `AccountBalance`.

### CharacterSheetClone

ESI endpoint(s):

* `/characters/{character_id}/clones/` - requires esi-clones.read\_clones.v1

#### General Notes

We've augmented this model to include information that we used to track as part of the character sheet.
We'll now update this information as part of updating the clones endpoint.

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
cloneJumpDate | cloneJumpDate | last\_clone\_jump\_date |
*N/A* | homeStationID | location\_id | Moved from `CharacterSheet`
*N/A* | homeStationType | location\_type | String type (from enumerated)
*N/A* | lastStationChangeDate | last\_station\_change\_date |

### CharacterSheetJump

ESI endpoint(s):

* `/characters/{character_id}/fatigue/` - esi-characters.read\_fatigue.v1

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
jumpActivation | jumpActivation | last\_jump\_date |
jumpFatigue | jumpFatigue | jump\_fatigue\_expire\_date |
jumpLastUpdate | jumpLastUpdate | last\_update\_date |

### CharacterSheetSkillPoints (new)

* `/characters/{character_id}/skills/` - requires esi-skills.read\_skills.v1

#### General Notes

This is a new model which holds character skill point information, some of which was
previously stored in `CharacterSheet`.

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | totalSkillPoints | total\_sp | Type is long.
*N/A* | unallocatedSkillPoints | unallocated\_sp | Moved from `CharacterSheet`.  Type change from long to int.

### CharacterShip (new)

ESI endpoint(s):

* `/characters/{character_id}/ship/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | shipTypeID | ship\_type\_id |
*N/A* | shipItemID | ship\_item\_id |
*N/A* | shipName | ship\_name |

### CharacterSkill

* `/characters/{character_id}/skills/` - requires esi-skills.read\_skills.v1

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
typeID | typeID | skill\_id |
level | (deleted) | *N/A* | Copied to `trainedSkillLevel`.
skillpoints | skillpoints | skillpoints\_in\_skill |
published | (deleted) | *N/A* | No longer in ESI.
*N/A* | trainedSkillLevel | trained\_skill\_level |
*N/A* | activeSkillLevel | active\_skill\_level |

#### Historic Conversion Notes

Due to alpha clones, a character may not have access to all the levels it has trained.  The ESI exposes
this disinction by reporting both a "trained skill level", which is the highest level trained on
an account, versus an "active skill level" which is the highest level the character may use
based on their clone status.

For historic data, we remove the old `level` field and copy it to the `trainedSkillLevel` field.  We will initially set `activeSkillLevel` to
`trainedSkillLevel`.  This will be updated on future syncs according
to the actual state of the character.  We have no way to determine
the proper historic setting for this field.

### CharacterSkillInTraining

Removed.  Can be inferred from `SkillInQueue`.

### SkillInQueue

* `/characters/{character_id}/skillqueue/` - requires esi-skills.read\_skillqueue.v1

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
endSP | endSP | level\_end\_sp |
endTime | endTime | finish\_date |
level | level | finished\_level |
queuePosition | queuePosition | queue\_position |
startSP | startSP | level\_start\_sp |
startTime | startTime | start\_date |
typeID | typeID | skill\_id |
*N/A* | trainingStartSP | training\_start\_sp | New in ESI.  Type is int.

### CharacterTitle
### ChatChannel
### ChatChannelMember
### Implant

ESI endpoint(s):

* `/characters/{character_id}/implants/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
typeID | typeID | id | ESI returns results as an array of type IDs.
typeName | (deleted) | *N/A* | ESI expects lookup from `typeID`

### JumpClone

ESI endpoint(s):

* `/characters/{character_id}/clones/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
jumpCloneID | jumpCloneID | jump\_clone\_id |
typeID | (deleted) | *N/A* | Not present in ESI.
locationID | locationID | location\_id |
cloneName | cloneName | name |
*N/A* | locationType | location\_type | New enumerated field.

### JumpCloneImplant

ESI endpoint(s):

* `/characters/{character_id}/clones/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
jumpCloneID | jumpCloneID | *N/A* | Inserted by EveKit as a foreign key for the JumpClone table.
typeID | typeID | *N/A* | Implant array value from ESI clones endpoint.
typeName | (deleted) | *N/A* | ESI expects lookup from `typeID`.

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

ESI endpoint(s):

* `/corporations/{corporation_id}/containers/logs/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
logTime | logTime | logged\_at |
action | action | action | Now an enumerated type, see conversion notes below.
actorID | characterID | character\_id | Name changed in ESI.
actorName | (deleted) | *N/A* | ESI expects lookup from `actorID`
flag | (deleted) | *N/A* | Removed in favor of `location\_flag`.  See conversion notes below.
*N/A* | locationFlag | location\_flag | Now an enumerated type.  See convrersion notes below.
itemID | containerID | container\_id | Name appears to have changed in the ESI.
itemTypeID | containerTypeID | container\_type\_id | Name appears to have changed in the ESI.
locationID | locationID | location\_id |
newConfiguration | newConfiguration | new\_config\_bitmask | 
oldConfiguration | oldConfiguration | old\_config\_bitmask | 
passwordType | passwordType | password\_type | Now an enumerated type, see conversion notes below.  May be null.
quantity | quantity | quantity |
typeID | typeID | type\_id |

#### Historic Conversion Notes
* `action` is now an enumerated type which will be converted as follows:
  * `Add` becomes `add`
  * `Assemble` becomes `assemble`
  * `Configure` becomes `configure`
  * `Enter Password` becomes `enter_password`
  * `Lock` becomes `lock`
  * `Move` becomes `move`
  * `Repackage` becomes `repackage`
  * `Set Name` becomes `set_name`
  * `Set Password` becomes `set_password`
  * `Unlock` becomes `unlock`
* `locationFlag` will be populated from `flag` using [this conversion table](https://github.com/ccpgames/eve-glue/blob/master/eve_glue/location_flag.py).
* `passwordType` is now an enumerated type which will be converted as follows:
  * **null** - unchanged
  * `Config` becomes `config`
  * `General` becomes `general`

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

ESI endpoint(s):

* `/characters/{character_id}/bookmarks/`
* `/characters/{character_id}/bookmarks/folders/`
* `/corporations/{corporation_id}/bookmarks/`
* `/corporations/{corporation_id}/bookmarks/folders/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
folderID | folderID | folder\_id |
folderName | folderName | name | This field populated from the folders endpoint.
folderCreatorID | folderCreatorID | creator\_id | This field populated from the folders endpoint.  ESI does not populate this field for characters, in which case EveKit sets the value to the character ID.  For corporations, this field is optional and will be set to 0 if not populated by ESI.
bookmarkID | bookmarkID | bookmark\_id |
bookmarkCreatorID | bookmarkCreatorID | creator\_id | 
created | created | created |
itemID | itemID | item\_id | This field is populated from the optional `item` field.  If not present, the value is set to 0.
typeID | typeID | type\_id | This field is populated from the optional `item` field.  If not present, the value is set to 0.
locationID | locationID | location\_id |
x | x | x | This field is populated from the optional `coordinates` field.  If not present, the value is set to 0.
y | y | y | This field is populated from the optional `coordinates` field.  If not present, the value is set to 0.
z | z | z | This field is populated from the optional `coordinates` field.  If not present, the value is set to 0.
memo | memo | label | The XML API called this field `memo`.  We've retained the name but the data is now populated by the ESI `label` field.
note | note | notes |

#### Synchronization Notes

The ESI splits bookmark data into two endpoints versus the XML API which only used a single endpoint.  We call both ESI endpoints during synchronization and populate a single `Bookmark` model just as we did using the XML API.

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
probability | probability | probability | This is a float in the ESI, instead of a double.
productTypeID | productTypeID | product\_type\_id |
productTypeName | (deleted) | *N/A* | ESI expects lookup from `productTypeID`
status | status | status | Now an enumerated type
timeInSeconds | timeInSeconds | duration |
startDate | startDate | start\_date |
endDate | endDate | end\_date |
pauseDate | pauseDate | pause\_date |
completedDate | completedDate | completed\_date |
completedCharacterID | completedCharacterID | completed\_character\_id | This is an int in the ESI, instead of a long.
successfulRuns | successfulRuns | successful\_runs |

#### Historic Conversion Notes
* Historic `status` will be mapped to the enumerated type using [this table](https://github.com/ccpgames/eve-glue/blob/master/eve_glue/industry_job_status.py).

### Kill

ESI endpoint(s):

* `/killmails/{killmail_id}/{killmail_hash}/`
* `/characters/{character_id}/killmails/recent/`
* `/corporations/{corporation_id}/killmails/recent/`

**Note:** All of the kill models are populated at the same time during synchronziation since the data is returned by a single ESI endpoint (`/killmails`)  

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
killID | killID | killmail\_id | Type changes from long to int.
killTime | killTime | killmail\_time |
moonID | moonID | moon\_id | Optional in ESI.  EveKit will populate with 0 if not present.
solarSystemID | solarSystemID | solar\_system\_id | Type changes from long to int.
*N/A* | warID | war\_id | A new int field in the ESI.  Not present in the XML API.

#### Synchronization Notes

Two calls are necessary for this population:

1. The `killmails/recent` endpoint retrieves a list of recent killmails and their hashes.
2. The `/killmails` endpoint retrieves detailed killmail information by hash.

### KillAttacker

ESI endpoint(s):

* `/killmails/{killmail_id}/{killmail_hash}/`
* `/characters/{character_id}/killmails/recent/`
* `/corporations/{corporation_id}/killmails/recent/`

**Note:** All of the kill models are populated at the same time during synchronziation since the data is returned by a single ESI endpoint (`/killmails`)  

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
killID | killID | *N/A* | Not in ESI.  Populated by EveKit for schema purposes.  Type change from long to int.
attackerCharacterID | attackerCharacterID | character\_id | Optional in ESI, set to 0 if not populated.  Type change from long to int.
allianceID | allianceID | alliance\_id | Optional in ESI, set to 0 if not populated.  Type change from long to int.
allianceName | (deleted) | *N/A* | ESI expects lookup from `allianceID`.
attackerCharacterName | (deleted) | *N/A* | ESI expects lookup from `attackerCharacterID`.
attackerCorporationID | attackerCorporationID | corporation\_id | Optional in ESI, set to 0 if not populated.  Type change from long to int.
attackerCorporationName | (deleeted) | *N/A* | ESI expects lookup from `attackerCorporationID`.
damageDone | damageDone | damage\_done |
factionID | factionID | faction\_id | Optional in ESI, set to 0 if not populated.
factionName | (deleted) | *N/A*| ESI expects lookup from `factionID`.
securityStatus | securityStatus | security\_status | Type change from double to float.
shipTypeID | shipTypeID | ship\_type\_id | Optional in ESI, set to 0 if not populated.
weaponTypeID | weaponTypeID | weapon\_type\_id | Optional in ESI, set to 0 if not populated.
finalBlow |finalBlow | final\_blow |

#### Synchronization Notes

Two calls are necessary for this population:

1. The `killmails/recent` endpoint retrieves a list of recent killmails and their hashes.
2. The `/killmails` endpoint retrieves detailed killmail information by hash.

Attacker data is a structured contained within the killmail report.

### KillItem

* `/killmails/{killmail_id}/{killmail_hash}/`
* `/characters/{character_id}/killmails/recent/`
* `/corporations/{corporation_id}/killmails/recent/`

**Note:** All of the kill models are populated at the same time during synchronziation since the data is returned by a single ESI endpoint (`/killmails`)  

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
killID | killID | *N/A* | Not in ESI.  Populated by EveKit for schema purposes.  Type change from long to int.
typeID | typeID | item\_type\_id |
flag | flag | flag |
qtyDestroyed | qtyDestroyed | quantity\_destroyed | Optional in ESI, set to 0 if not populated.  Type change from int to long.
qtyDropped | qtyDropped | quantity\_dropped | Optional in ESI, set to 0 if not populated.  Type change from int to long.
singleton | singleton | singleton | Type change from boolean to int.  See historic conversion notes below.
sequence | sequence | *N/A* | This is an artifical field we introduce to enumerate kill items since otherwise there is no way to uniquely identify to kill items with the same stats.
containerSequence | containerSequence | *N/A* | This is an artifical field we introduce to record the hierarchy of kill items (e.g. which kill items are contained in others).  If the value is -1, then this is a "top level" kill item, otherwise the value gives the sequence number of the parent kill item.

#### Synchronization Notes

Two calls are necessary for this population:

1. The `killmails/recent` endpoint retrieves a list of recent killmails and their hashes.
2. The `/killmails` endpoint retrieves detailed killmail information by hash.

Attacker data is a structured contained within the killmail report.

#### Historic Conversion Notes

Historic EveKit data incorrectly treated singleton as a boolean value.  This should actually be an integer which historically can only havc the values "0" or "2" (blueprint copy).  We'll convert historic false values to "0" and historic true values to "2".

### KillVictim

* `/killmails/{killmail_id}/{killmail_hash}/`
* `/characters/{character_id}/killmails/recent/`
* `/corporations/{corporation_id}/killmails/recent/`

**Note:** All of the kill models are populated at the same time during synchronziation since the data is returned by a single ESI endpoint (`/killmails`)  

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
killID | killID | *N/A* | Not in ESI.  Populated by EveKit for schema purposes.  Type change from long to int.
allianceID | allianceID | alliance\_id | Optional in ESI, set to 0 if not populated.  Type change from long to int.
allianceName | (deleted) | *N/A* | ESI expects lookup from `allianceID`
killCharacterID | killCharacterID | character\_id | Optional in ESI, set to 0 if not populated.  Type change from long to int.
killCharacterName | (deleted) | *N/A* | ESI expects lookup from `killCharacterID`
killCorporationID | killCorporationID | corporation\_id | Optional in ESI, set to 0 if not populated.  Type change from long to int.
killCorporationName | (deleted) | *N/A* | ESI expects lookup from `killCorporationID`
damageTaken | damageTaken | damage\_taken | Type change from long to int.
factionID | factionID | faction\_id | Optional in ESI, set to 0 if not populated.  Type change from long to int.
factionName | (deleted) | *N/A* | ESI expects lookup from `factionID`
shipTypeID | shipTypeID | ship\_type\_id |
*N/A* | x | position.x | Optional in ESI, set to 0 if not populated.  Type is double.
*N/A* | y | position.y | Optional in ESI, set to 0 if not populated.  Type is double.
*N/A* | z | position.z | Optional in ESI, set to 0 if not populated.  Type is double.

#### Synchronization Notes

Two calls are necessary for this population:

1. The `killmails/recent` endpoint retrieves a list of recent killmails and their hashes.
2. The `/killmails` endpoint retrieves detailed killmail information by hash.

Attacker data is a structured contained within the killmail report.

#### Historic Conversion Notes

Position fields are new in the ESI and will be set to 0 for historic kill data.

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
