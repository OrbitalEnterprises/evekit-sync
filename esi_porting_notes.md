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
  * **beta** [UpcomingCalendarEvent](#upcomingcalendarevent)
  * **beta** [CalendarEventAttendee](#calendareventattendee)
  * **beta** [CharacterContactNotification](#charactercontactnotification)
  * **beta** [CharacterLocation](#characterlocation) (new)
  * **beta** [CharacterMailMessage](#charactermailmessage)
  * **N/A** [CharacterMailMessageBody](#charactermailmessagebody)
  * **beta** [CharacterMedal](#charactermedal)
  * **beta** [CharacterMedalGraphic](#charactermedalgraphic) (new)
  * **beta** [CharacterNotification](#characternotification)
  * **beta** [CharacterNotificationBody](#characternotificationbody)
  * **beta** [CharacterOnline](#characteronline) (new)
  * **beta** [CharacterRole](#characterrole)
  * **beta** [CharacterSheet](#charactersheet)
  * **beta** [CharacterSheetAttributes](#charactersheetattributes) (new)
  * **N/A** [CharacterSheetBalance](#charactersheetbalance)
  * **beta** [CharacterSheetClone](#charactersheetclone)
  * **beta** [CharacterSheetJump](#charactersheetjump)
  * **beta** [CharacterSheetSkillPoints](#charactersheetskillpoints) (new)
  * **beta** [CharacterShip](#charactership) (new)
  * **beta** [CharacterSkill](#characterskill)
  * **N/A** [CharacterSkillInTraining](#characterskillintraining)
  * **beta** [SkillInQueue](#skillinqueue)
  * **beta** [CharacterTitle](#charactertitle)
  * **beta** [ChatChannel](#chatchannel)
  * **beta** [ChatChannelMember](#chatchannelmember)
  * **beta** [Implant](#implant)
  * **beta** [JumpClone](#jumpclone)
  * **beta** [JumpCloneImplant](#jumpcloneimplant)
  * **beta** [MailingList](#mailinglist)
  * **beta** [MailLabel](#maillabel) (new)
  * **beta** [PlanetaryColony](#planetarycolony)
  * **beta** [PlanetaryLink](#planetarylink)
  * **beta** [PlanetaryPin](#planetarypin)
  * **beta** [PlanetaryRoute](#planetaryroute)
  * **beta** [ResearchAgent](#researchagent)
* Corporation Model Changes
  * **beta** [ContainerLog](#containerlog)
  * **beta** [CorporationMedal](#corporationmedal)
  * **beta** [CorporationMemberMedal](#corporationmembermedal)
  * **beta** [CorporationSheet](#corporationsheet)
  * **pending** [CorporationTitle](#corporationtitle)
  * **pending** [CustomsOffice](#customsoffice)
  * **pending** [Division](#division)
  * **pending** [Facility](#facility)
  * **pending** [Fuel](#fuel)
  * **beta** [MemberLimit](#memberlimit) (new)
  * **pending** [MemberSecurity](#membersecurity)
  * **pending** [MemberSecurityLog](#membersecuritylog)
  * **pending** [MemberTitle](#membertitle) (new)
  * **beta** [MemberTracking](#membertracking)
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
  * **beta** [Contact](#contact)
  * **beta** [ContactLabel](#contactlabel)
  * **beta** [Contract](#contract)
  * **beta** [ContractBid](#contractbid)
  * **beta** [ContractItem](#contractitem)
  * **beta** [FacWarStats](#facwarstats)
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

ESI endpoint(s):

* `/characters/{character_id}/calendar/`
* `/characters/{character_id}/calendar/{event_id}/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
duration | duration | duration |
eventDate | eventDate | event\_date |
eventID | eventID | event\_id | Change type from long to int.
eventText | eventText | text |
eventTitle | eventTitle | title |
ownerID | ownerID | owner\_id | Change type from long to int.
ownerName | ownerName | owner\_name |
response | response | event\_response | Now an enumerated type stored as a string.
important | (deleted) | *N/A* | Renamed to `importance`.
*N/A* | importance | importance | Renamed from `important`, with true equal to 1.  Type is int. 
ownerTypeID | (deleted) | *N/A* | Converted to enumerated type `ownerType`.  See conversion notes below.
*N/A* | ownerType | owner\_type | Converted from `ownerTypeID`.  Now an enumerated type stored as a string.

#### Historic Conversion Notes

* `response` is now an enumerated type storead as a string.  The old response field should be converted as follows:

```sql
UPDATE `evekit_data_upcoming_calendar_event` SET response = 'declined' WHERE response = 'Declined';
UPDATE `evekit_data_upcoming_calendar_event` SET response = 'not_responded' WHERE response = 'Undecided';
UPDATE `evekit_data_upcoming_calendar_event` SET response = 'accepted' WHERE response = 'Accepted';
UPDATE `evekit_data_upcoming_calendar_event` SET response = 'tentative' WHERE response = 'Tentative';
```

* `ownerTypeID` is now an enumerated type stored as a string.  We can convert the existing values as follows:

```sql
UPDATE `evekit_data_upcoming_calendar_event` SET ownerType = 'eve_server' WHERE ownerTypeID = 0;
UPDATE `evekit_data_upcoming_calendar_event` SET ownerType = 'corporation' WHERE ownerTypeID = 2;
UPDATE `evekit_data_upcoming_calendar_event` SET ownerType = 'faction' WHERE ownerTypeID = 30;
UPDATE `evekit_data_upcoming_calendar_event` SET ownerType = 'character' WHERE ownerTypeID in (1373,1374,1375,1376,1377,1378,1379,1380,1381,1382,1383,1384,1385,1386,34574);
UPDATE `evekit_data_upcoming_calendar_event` SET ownerType = 'alliance' WHERE ownerTypeID = 16159;
```

### CalendarEventAttendee

* `/characters/{character_id}/calendar/{event_id}/attendees/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
eventID | eventID | *N/A* | Injected by EveKit.  Change type from long to int.
characterID | characterID | character\_id | Change type from long to int.
characterName | (deleted) | *N/A* | ESI expects lookup from character ID.
response | response | event\_response | Now an enumerated type stored as a string.

#### Historic Conversion Notes

* `response` is now an enumerated type storead as a string.  The old response field should be converted as follows:

```sql
UPDATE `evekit_data_calendar_event_attendee` SET response = 'declined' WHERE response = 'Declined';
UPDATE `evekit_data_calendar_event_attendee` SET response = 'not_responded' WHERE response = 'Undecided';
UPDATE `evekit_data_calendar_event_attendee` SET response = 'accepted' WHERE response = 'Accepted';
UPDATE `evekit_data_calendar_event_attendee` SET response = 'tentative' WHERE response = 'Tentative';
```

### CharacterContactNotification

* `/characters/{character_id}/notifications/contacts/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
notificationID | notificationID | notification\_id | Change type from long to int.
senderID | senderID | sender\_character\_id | Change type from long to int.
senderName | (deleted) | *N/A* | ESI expects lookup from `senderID`.
sentDate | sentDate | send\_date |
*N/A* | standingLevel | standing\_level | New field introduced by ESI.  Type is float.
messageData | messageData | message |

### CharacterLocation (new)

ESI endpoint(s):

* `/characters/{character_id}/location/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | solarSystemID | solar\_system\_id |
*N/A* | stationID | station\_id |
*N/A* | structureID | structure\_id |

### CharacterMailMessage

ESI endpoint(s):

* `/characters/{character_id}/mail/`
* `/characters/{character_id}/mail/{mail_id}/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
messageID | messageID | mail_id | 
senderID | senderID | from | 
senderName | (deleted) | *N/A* | ESI expects lookup from senderID
title | title | subject |
msgRead | msgRead | is_read |
sentDate | sentDate | timestamp |
toCharacterID | (deleted) | *N/A* | Moved to `recipients` element collection
toCorpOrAllianceID | (deleted) | *N/A* | Moved to `recipients` element collection
toListID | (deleted) | *N/A* | Moved to `recipients` element collection
senderTypeID | (deleted) | *N/A* | ESI does not record this information.
*N/A* | recipients | recipients | Element collection of recipient data.
*N/A* | labels | labels | Element collection of labels.
*N/A* | body | body | We combine header and message into a single model since we synchronize these values at the same time.

#### Historic Conversion Notes

##### Recipients

Recipient data must be copied from the old recipient fields to a new recipient
collection.  Likewise, a new `labels` collection is now also used (not present
in XML API).  Finally, the previous recipient element collections can be deleted.

The two new tables are created as follows:

```sql
CREATE TABLE `mail_message_recipient` (
       `mail_cid` BIGINT(20) NOT NULL,
       `recipientType` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
       `recipientID` INT(11) NOT NULL,
       KEY `mail_cid_index` (`mail_cid`),
       CONSTRAINT `mail_cid_fk` FOREIGN KEY (`mail_cid`) REFERENCES `evekit_data_character_mail_message` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

and

```sql
CREATE TABLE `mail_message_label` (
       `mail_cid` BIGINT(20) NOT NULL,
       `labelID` INT(11) NOT NULL,
       KEY `mail_cid_index` (`mail_cid`),
       CONSTRAINT `mail_cid_fk` FOREIGN KEY (`mail_cid`) REFERENCES `evekit_data_character_mail_message` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

The two tables which can be deleted are:

`charactermailmessage_tocharacterid`
`charactermailmessage_tolistid`

During the conversion, the new element collection tables must be created first and populated from
`charactermailmessage_tocharacterid` and `charactermailmessage_tolistid`.  After this is done,
the old collections can be deleted.  Population can proceed as follows:

```sql
-- Copy over corp or alliance destinations
INSERT `mail_message_recipient` (mail_cid, recipientType, recipientID)
SELECT cid, 'corporation', toCorpOrAllianceID FROM `evekit_data_character_mail_message`;

-- Copy over character recipients
INSERT `mail_message_recipient` (mail_cid, recipientType, recipientID)
SELECT CharacterMailMessage_cid, 'character', toCharacterID from `charactermailmessage_tocharacterid`;

-- Copy over mailing list recipients
INSERT `mail_message_recipient` (mail_cid, recipientType, recipientID)
SELECT CharacterMailMessage_cid, 'mailing_list', toListID from 'charactermailmessage_tolistid';
```

##### Message Bodies

Since we synchronize both headers and bodies at the same time, we now collect both values
into a single model object.  Once the schema is updated, we simply copy over the body values
from existing mail messages as follows:

```sql
-- NOTE: this works because mail bodies are immutable and messageIDs are globally unique
UPDATE `evekit_data_character_mail_message` AS a, `evekit_data_character_mail_message_body` AS b
SET a.body = b.body
WHERE a.messageID = b.messageID;
```

### CharacterMailMessageBody

Not used with the ESI.  Can be dropped once schema updates have been made for CharacterMailMessage.

### CharacterMedal

* `/characters/{character_id}/medals/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
description | description | description |
medalID | medalID | medal\_id |
title | title | title |
corporationID | corporationID | corporation\_id | Change type from long to int.
issued | issued | date |
issuerID | issuerID | issuer\_id | Change type from long to int.
reason | reason | reason |
status | status | status | Now an enumerated type stored as a string.

### CharacterMedalGraphic (new)

* `/characters/{character_id}/medals/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | medalID | *N/A* | Inserted by EveKit.  Integer type.
*N/A* | issued | *N/A* | Inserted by EveKit.  Long type.
*N/A* | part | part | Integer type.
*N/A* | layer | layer | Integer type.
*N/A* | graphic | graphic | String type.
*N/A* | color | color | Integer type.

```sql
CREATE TABLE `evekit_data_character_medal_graphic` (
       `cid` BIGINT(20) NOT NULL,
       `medalID` INT(11) NOT NULL,
       `issued` BIGINT(20) NOT NULL,
       `part` INT(11) NOT NULL,
       `layer` INT(11) NOT NULL,
       `graphic` VARCHAR(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
       `color` INT(11) NOT NULL,
       PRIMARY KEY (`cid`),
       KEY `medalIDGraphicIndex` (`medalID`),
       KEY `issuedGraphicIndex` (`issued`),
       CONSTRAINT `character_medal_graphic_fk` FOREIGN KEY (`cid`) REFERENCES `evekit_cached_data` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### CharacterNotification

* `/characters/{character_id}/notifications/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
notificationID | notificationID | notification\_id |
typeID | (deleted) | *N/A* | Replaced with `type`.  See conversion notes below.
*N/A* | type | type | Enumerated field replacing `typeID`.
senderID | senderID | sender\_id | Change type from long to int.
*N/A* | senderType | sender\_type | Enumerated field.
sentDate | sentDate | timestamp |
msgRead | msgRead | is\_read |
text | text | text |

#### Historic Conversion Notes

##### Sender Types

There is no easy mapping from `senderID` to `senderType` so these will be left as null in historic data.

##### Notification Types

Notification types are set from `typeID` using the data [here](https://github.com/ccpgames/eve-glue/blob/master/eve_glue/notification_type.py).  Specifically:

```sql
UPDATE `evekit_data_character_notification` SET type = 'OldLscMessages' WHERE typeID = 1;
UPDATE `evekit_data_character_notification` SET type = 'CharTerminationMsg' WHERE typeID = 2;
UPDATE `evekit_data_character_notification` SET type = 'CharMedalMsg' WHERE typeID = 3;
UPDATE `evekit_data_character_notification` SET type = 'AllMaintenanceBillMsg' WHERE typeID = 4;
UPDATE `evekit_data_character_notification` SET type = 'AllWarDeclaredMsg' WHERE typeID = 5;
UPDATE `evekit_data_character_notification` SET type = 'AllWarSurrenderMsg' WHERE typeID = 6;
UPDATE `evekit_data_character_notification` SET type = 'AllWarRetractedMsg' WHERE typeID = 7;
UPDATE `evekit_data_character_notification` SET type = 'AllWarInvalidatedMsg' WHERE typeID = 8;
UPDATE `evekit_data_character_notification` SET type = 'CorpAllBillMsg' WHERE typeID = 10;
UPDATE `evekit_data_character_notification` SET type = 'BillOutOfMoneyMsg' WHERE typeID = 11;
UPDATE `evekit_data_character_notification` SET type = 'BillPaidCorpAllMsg' WHERE typeID = 13;
UPDATE `evekit_data_character_notification` SET type = 'BountyClaimMsg' WHERE typeID = 14;
UPDATE `evekit_data_character_notification` SET type = 'CloneActivationMsg' WHERE typeID = 15;
UPDATE `evekit_data_character_notification` SET type = 'CorpAppNewMsg' WHERE typeID = 16;
UPDATE `evekit_data_character_notification` SET type = 'CorpAppRejectMsg' WHERE typeID = 17;
UPDATE `evekit_data_character_notification` SET type = 'CorpAppAcceptMsg' WHERE typeID = 18;
UPDATE `evekit_data_character_notification` SET type = 'CorpTaxChangeMsg' WHERE typeID = 19;
UPDATE `evekit_data_character_notification` SET type = 'CorpNewsMsg' WHERE typeID = 20;
UPDATE `evekit_data_character_notification` SET type = 'CharLeftCorpMsg' WHERE typeID = 21;
UPDATE `evekit_data_character_notification` SET type = 'CorpNewCEOMsg' WHERE typeID = 22;
UPDATE `evekit_data_character_notification` SET type = 'CorpDividendMsg' WHERE typeID = 23;
UPDATE `evekit_data_character_notification` SET type = 'CorpVoteMsg' WHERE typeID = 25;
UPDATE `evekit_data_character_notification` SET type = 'CorpVoteCEORevokedMsg' WHERE typeID = 26;
UPDATE `evekit_data_character_notification` SET type = 'CorpWarDeclaredMsg' WHERE typeID = 27;
UPDATE `evekit_data_character_notification` SET type = 'CorpWarFightingLegalMsg' WHERE typeID = 28;
UPDATE `evekit_data_character_notification` SET type = 'CorpWarSurrenderMsg' WHERE typeID = 29;
UPDATE `evekit_data_character_notification` SET type = 'CorpWarRetractedMsg' WHERE typeID = 30;
UPDATE `evekit_data_character_notification` SET type = 'CorpWarInvalidatedMsg' WHERE typeID = 31;
UPDATE `evekit_data_character_notification` SET type = 'ContainerPasswordMsg' WHERE typeID = 32;
UPDATE `evekit_data_character_notification` SET type = 'CustomsMsg' WHERE typeID = 33;
UPDATE `evekit_data_character_notification` SET type = 'InsuranceFirstShipMsg' WHERE typeID = 34;
UPDATE `evekit_data_character_notification` SET type = 'InsurancePayoutMsg' WHERE typeID = 35;
UPDATE `evekit_data_character_notification` SET type = 'InsuranceInvalidatedMsg' WHERE typeID = 36;
UPDATE `evekit_data_character_notification` SET type = 'SovCorpClaimFailMsg' WHERE typeID = 38;
UPDATE `evekit_data_character_notification` SET type = 'SovCorpBillLateMsg' WHERE typeID = 40;
UPDATE `evekit_data_character_notification` SET type = 'SovAllClaimLostMsg' WHERE typeID = 41;
UPDATE `evekit_data_character_notification` SET type = 'SovAllClaimAquiredMsg' WHERE typeID = 43;
UPDATE `evekit_data_character_notification` SET type = 'AllAnchoringMsg' WHERE typeID = 45;
UPDATE `evekit_data_character_notification` SET type = 'AllStructVulnerableMsg' WHERE typeID = 46;
UPDATE `evekit_data_character_notification` SET type = 'AllStrucInvulnerableMsg' WHERE typeID = 47;
UPDATE `evekit_data_character_notification` SET type = 'SovDisruptorMsg' WHERE typeID = 48;
UPDATE `evekit_data_character_notification` SET type = 'CorpStructLostMsg' WHERE typeID = 49;
UPDATE `evekit_data_character_notification` SET type = 'CorpOfficeExpirationMsg' WHERE typeID = 50;
UPDATE `evekit_data_character_notification` SET type = 'CloneRevokedMsg1' WHERE typeID = 51;
UPDATE `evekit_data_character_notification` SET type = 'CloneMovedMsg' WHERE typeID = 52;
UPDATE `evekit_data_character_notification` SET type = 'CloneRevokedMsg2' WHERE typeID = 53;
UPDATE `evekit_data_character_notification` SET type = 'InsuranceExpirationMsg' WHERE typeID = 54;
UPDATE `evekit_data_character_notification` SET type = 'InsuranceIssuedMsg' WHERE typeID = 55;
UPDATE `evekit_data_character_notification` SET type = 'JumpCloneDeletedMsg1' WHERE typeID = 56;
UPDATE `evekit_data_character_notification` SET type = 'JumpCloneDeletedMsg2' WHERE typeID = 57;
UPDATE `evekit_data_character_notification` SET type = 'FWCorpJoinMsg' WHERE typeID = 58;
UPDATE `evekit_data_character_notification` SET type = 'FWCorpLeaveMsg' WHERE typeID = 59;
UPDATE `evekit_data_character_notification` SET type = 'FWCorpKickMsg' WHERE typeID = 60;
UPDATE `evekit_data_character_notification` SET type = 'FWCharKickMsg' WHERE typeID = 61;
UPDATE `evekit_data_character_notification` SET type = 'FWCorpWarningMsg' WHERE typeID = 62;
UPDATE `evekit_data_character_notification` SET type = 'FWCharWarningMsg' WHERE typeID = 63;
UPDATE `evekit_data_character_notification` SET type = 'FWCharRankLossMsg' WHERE typeID = 64;
UPDATE `evekit_data_character_notification` SET type = 'FWCharRankGainMsg' WHERE typeID = 65;
UPDATE `evekit_data_character_notification` SET type = 'TransactionReversalMsg' WHERE typeID = 67;
UPDATE `evekit_data_character_notification` SET type = 'ReimbursementMsg' WHERE typeID = 68;
UPDATE `evekit_data_character_notification` SET type = 'LocateCharMsg' WHERE typeID = 69;
UPDATE `evekit_data_character_notification` SET type = 'ResearchMissionAvailableMsg' WHERE typeID = 70;
UPDATE `evekit_data_character_notification` SET type = 'MissionOfferExpirationMsg' WHERE typeID = 71;
UPDATE `evekit_data_character_notification` SET type = 'MissionTimeoutMsg' WHERE typeID = 72;
UPDATE `evekit_data_character_notification` SET type = 'StoryLineMissionAvailableMsg' WHERE typeID = 73;
UPDATE `evekit_data_character_notification` SET type = 'TutorialMsg' WHERE typeID = 74;
UPDATE `evekit_data_character_notification` SET type = 'TowerAlertMsg' WHERE typeID = 75;
UPDATE `evekit_data_character_notification` SET type = 'TowerResourceAlertMsg' WHERE typeID = 76;
UPDATE `evekit_data_character_notification` SET type = 'StationAggressionMsg1' WHERE typeID = 77;
UPDATE `evekit_data_character_notification` SET type = 'StationStateChangeMsg' WHERE typeID = 78;
UPDATE `evekit_data_character_notification` SET type = 'StationConquerMsg' WHERE typeID = 79;
UPDATE `evekit_data_character_notification` SET type = 'StationAggressionMsg2' WHERE typeID = 80;
UPDATE `evekit_data_character_notification` SET type = 'FacWarCorpJoinRequestMsg' WHERE typeID = 81;
UPDATE `evekit_data_character_notification` SET type = 'FacWarCorpLeaveRequestMsg' WHERE typeID = 82;
UPDATE `evekit_data_character_notification` SET type = 'FacWarCorpJoinWithdrawMsg' WHERE typeID = 83;
UPDATE `evekit_data_character_notification` SET type = 'FacWarCorpLeaveWithdrawMsg' WHERE typeID = 84;
UPDATE `evekit_data_character_notification` SET type = 'CorpLiquidationMsg' WHERE typeID = 85;
UPDATE `evekit_data_character_notification` SET type = 'SovereigntyTCUDamageMsg' WHERE typeID = 86;
UPDATE `evekit_data_character_notification` SET type = 'SovereigntySBUDamageMsg' WHERE typeID = 87;
UPDATE `evekit_data_character_notification` SET type = 'SovereigntyIHDamageMsg' WHERE typeID = 88;
UPDATE `evekit_data_character_notification` SET type = 'ContactAdd' WHERE typeID = 89;
UPDATE `evekit_data_character_notification` SET type = 'ContactEdit' WHERE typeID = 90;
UPDATE `evekit_data_character_notification` SET type = 'IncursionCompletedMsg' WHERE typeID = 91;
UPDATE `evekit_data_character_notification` SET type = 'CorpKicked' WHERE typeID = 92;
UPDATE `evekit_data_character_notification` SET type = 'OrbitalAttacked' WHERE typeID = 93;
UPDATE `evekit_data_character_notification` SET type = 'OrbitalReinforced' WHERE typeID = 94;
UPDATE `evekit_data_character_notification` SET type = 'OwnershipTransferred' WHERE typeID = 95;
UPDATE `evekit_data_character_notification` SET type = 'FWAllianceWarningMsg' WHERE typeID = 96;
UPDATE `evekit_data_character_notification` SET type = 'FWAllianceKickMsg' WHERE typeID = 97;
UPDATE `evekit_data_character_notification` SET type = 'AllWarCorpJoinedAllianceMsg' WHERE typeID = 98;
UPDATE `evekit_data_character_notification` SET type = 'AllyJoinedWarDefenderMsg' WHERE typeID = 99;
UPDATE `evekit_data_character_notification` SET type = 'AllyJoinedWarAggressorMsg' WHERE typeID = 100;
UPDATE `evekit_data_character_notification` SET type = 'AllyJoinedWarAllyMsg' WHERE typeID = 101;
UPDATE `evekit_data_character_notification` SET type = 'MercOfferedNegotiationMsg' WHERE typeID = 102;
UPDATE `evekit_data_character_notification` SET type = 'WarSurrenderOfferMsg' WHERE typeID = 103;
UPDATE `evekit_data_character_notification` SET type = 'WarSurrenderDeclinedMsg' WHERE typeID = 104;
UPDATE `evekit_data_character_notification` SET type = 'FacWarLPPayoutKill' WHERE typeID = 105;
UPDATE `evekit_data_character_notification` SET type = 'FacWarLPPayoutEvent' WHERE typeID = 106;
UPDATE `evekit_data_character_notification` SET type = 'FacWarLPDisqualifiedEvent' WHERE typeID = 107;
UPDATE `evekit_data_character_notification` SET type = 'FacWarLPDisqualifiedKill' WHERE typeID = 108;
UPDATE `evekit_data_character_notification` SET type = 'AllyContractCancelled' WHERE typeID = 109;
UPDATE `evekit_data_character_notification` SET type = 'WarAllyOfferDeclinedMsg' WHERE typeID = 110;
UPDATE `evekit_data_character_notification` SET type = 'BountyYourBountyClaimed' WHERE typeID = 111;
UPDATE `evekit_data_character_notification` SET type = 'BountyPlacedChar' WHERE typeID = 112;
UPDATE `evekit_data_character_notification` SET type = 'BountyPlacedCorp' WHERE typeID = 113;
UPDATE `evekit_data_character_notification` SET type = 'BountyPlacedAlliance' WHERE typeID = 114;
UPDATE `evekit_data_character_notification` SET type = 'KillRightAvailable' WHERE typeID = 115;
UPDATE `evekit_data_character_notification` SET type = 'KillRightAvailableOpen' WHERE typeID = 116;
UPDATE `evekit_data_character_notification` SET type = 'KillRightEarned' WHERE typeID = 117;
UPDATE `evekit_data_character_notification` SET type = 'KillRightUsed' WHERE typeID = 118;
UPDATE `evekit_data_character_notification` SET type = 'KillRightUnavailable' WHERE typeID = 119;
UPDATE `evekit_data_character_notification` SET type = 'KillRightUnavailableOpen' WHERE typeID = 120;
UPDATE `evekit_data_character_notification` SET type = 'DeclareWar' WHERE typeID = 121;
UPDATE `evekit_data_character_notification` SET type = 'OfferedSurrender' WHERE typeID = 122;
UPDATE `evekit_data_character_notification` SET type = 'AcceptedSurrender' WHERE typeID = 123;
UPDATE `evekit_data_character_notification` SET type = 'MadeWarMutual' WHERE typeID = 124;
UPDATE `evekit_data_character_notification` SET type = 'RetractsWar' WHERE typeID = 125;
UPDATE `evekit_data_character_notification` SET type = 'OfferedToAlly' WHERE typeID = 126;
UPDATE `evekit_data_character_notification` SET type = 'AcceptedAlly' WHERE typeID = 127;
UPDATE `evekit_data_character_notification` SET type = 'CharAppAcceptMsg' WHERE typeID = 128;
UPDATE `evekit_data_character_notification` SET type = 'CharAppRejectMsg' WHERE typeID = 129;
UPDATE `evekit_data_character_notification` SET type = 'CharAppWithdrawMsg' WHERE typeID = 130;
UPDATE `evekit_data_character_notification` SET type = 'DustAppAcceptedMsg' WHERE typeID = 131;
UPDATE `evekit_data_character_notification` SET type = 'DistrictAttacked' WHERE typeID = 132;
UPDATE `evekit_data_character_notification` SET type = 'BattlePunishFriendlyFire' WHERE typeID = 133;
UPDATE `evekit_data_character_notification` SET type = 'BountyESSTaken' WHERE typeID = 134;
UPDATE `evekit_data_character_notification` SET type = 'BountyESSShared' WHERE typeID = 135;
UPDATE `evekit_data_character_notification` SET type = 'IndustryTeamAuctionWon' WHERE typeID = 136;
UPDATE `evekit_data_character_notification` SET type = 'IndustryTeamAuctionLost' WHERE typeID = 137;
UPDATE `evekit_data_character_notification` SET type = 'CloneActivationMsg2' WHERE typeID = 138;
UPDATE `evekit_data_character_notification` SET type = 'CorpAppInvitedMsg' WHERE typeID = 139;
UPDATE `evekit_data_character_notification` SET type = 'KillReportVictim' WHERE typeID = 140;
UPDATE `evekit_data_character_notification` SET type = 'KillReportFinalBlow' WHERE typeID = 141;
UPDATE `evekit_data_character_notification` SET type = 'CorpAppRejectCustomMsg' WHERE typeID = 142;
UPDATE `evekit_data_character_notification` SET type = 'CorpFriendlyFireEnableTimerStarted' WHERE typeID = 143;
UPDATE `evekit_data_character_notification` SET type = 'CorpFriendlyFireDisableTimerStarted' WHERE typeID = 144;
UPDATE `evekit_data_character_notification` SET type = 'CorpFriendlyFireEnableTimerCompleted' WHERE typeID = 145;
UPDATE `evekit_data_character_notification` SET type = 'CorpFriendlyFireDisableTimerCompleted' WHERE typeID = 146;
UPDATE `evekit_data_character_notification` SET type = 'EntosisCaptureStarted' WHERE typeID = 147;
UPDATE `evekit_data_character_notification` SET type = 'StationServiceEnabled' WHERE typeID = 148;
UPDATE `evekit_data_character_notification` SET type = 'StationServiceDisabled' WHERE typeID = 149;
UPDATE `evekit_data_character_notification` SET type = 'InfrastructureHubBillAboutToExpire' WHERE typeID = 152;
UPDATE `evekit_data_character_notification` SET type = 'SovStructureReinforced' WHERE typeID = 160;
UPDATE `evekit_data_character_notification` SET type = 'SovCommandNodeEventStarted' WHERE typeID = 161;
UPDATE `evekit_data_character_notification` SET type = 'SovStructureDestroyed' WHERE typeID = 162;
UPDATE `evekit_data_character_notification` SET type = 'SovStationEnteredFreeport' WHERE typeID = 163;
UPDATE `evekit_data_character_notification` SET type = 'IHubDestroyedByBillFailure' WHERE typeID = 164;
UPDATE `evekit_data_character_notification` SET type = 'AllianceCapitalChanged' WHERE typeID = 165;
UPDATE `evekit_data_character_notification` SET type = 'BuddyConnectContactAdd' WHERE typeID = 166;
UPDATE `evekit_data_character_notification` SET type = 'SovStructureSelfDestructRequested' WHERE typeID = 167;
UPDATE `evekit_data_character_notification` SET type = 'SovStructureSelfDestructCancel' WHERE typeID = 168;
UPDATE `evekit_data_character_notification` SET type = 'SovStructureSelfDestructFinished' WHERE typeID = 169;
UPDATE `evekit_data_character_notification` SET type = 'StructureFuelAlert' WHERE typeID = 181;
UPDATE `evekit_data_character_notification` SET type = 'StructureAnchoring' WHERE typeID = 182;
UPDATE `evekit_data_character_notification` SET type = 'StructureUnanchoring' WHERE typeID = 183;
UPDATE `evekit_data_character_notification` SET type = 'StructureUnderAttack' WHERE typeID = 184;
UPDATE `evekit_data_character_notification` SET type = 'StructureOnline' WHERE typeID = 185;
UPDATE `evekit_data_character_notification` SET type = 'StructureLostShields' WHERE typeID = 186;
UPDATE `evekit_data_character_notification` SET type = 'StructureLostArmor' WHERE typeID = 187;
UPDATE `evekit_data_character_notification` SET type = 'StructureDestroyed' WHERE typeID = 188;
UPDATE `evekit_data_character_notification` SET type = 'StructureServicesOffline' WHERE typeID = 198;
UPDATE `evekit_data_character_notification` SET type = 'StructureItemsDelivered' WHERE typeID = 199;
UPDATE `evekit_data_character_notification` SET type = 'SeasonalChallengeCompleted' WHERE typeID = 200;
UPDATE `evekit_data_character_notification` SET type = 'StructureCourierContractChanged' WHERE typeID = 201;
UPDATE `evekit_data_character_notification` SET type = 'OperationFinished' WHERE typeID = 1012;
UPDATE `evekit_data_character_notification` SET type = 'GiftReceived' WHERE typeID = 1022;
UPDATE `evekit_data_character_notification` SET type = 'GameTimeReceived' WHERE typeID = 1030;
UPDATE `evekit_data_character_notification` SET type = 'GameTimeSent' WHERE typeID = 1031;
UPDATE `evekit_data_character_notification` SET type = 'GameTimeAdded' WHERE typeID = 1032;
UPDATE `evekit_data_character_notification` SET type = 'NPCStandingsLost' WHERE typeID = 3001;
UPDATE `evekit_data_character_notification` SET type = 'NPCStandingsGained' WHERE typeID = 3002;
UPDATE `evekit_data_character_notification` SET type = 'MoonminingExtractionCancelled' WHERE typeID = 203;
UPDATE `evekit_data_character_notification` SET type = 'MoonminingExtractionFinished' WHERE typeID = 204;
UPDATE `evekit_data_character_notification` SET type = 'MoonminingLaserFired' WHERE typeID = 205;
UPDATE `evekit_data_character_notification` SET type = 'MoonminingAutomaticFracture' WHERE typeID = 206;
UPDATE `evekit_data_character_notification` SET type = 'MoonminingExtractionStarted' =  202
UPDATE `evekit_data_character_notification` SET type = 'StructureWentLowPower' WHERE typeID = 207;
UPDATE `evekit_data_character_notification` SET type = 'StructureWentHighPower' WHERE typeID = 208;
UPDATE `evekit_data_character_notification` SET type = 'StructuresReinforcementChanged' WHERE typeID = 209;
```

##### Notification Bodies

Since we synchronize both headers and bodies at the same time, we now collect both values
into a single model object.  Once the schema is updated, we simply copy over the body values
from existing notifications as follows:

```sql
UPDATE `evekit_data_character_notification` AS a, `evekit_data_character_notification_body` AS b
SET a.text = b.text
WHERE a.notificationID = b.notificationID;
```

Once this transfer is complete, the notifications bodies table can be deleted.

### CharacterNotificationBody

Not used with the ESI.  Can be dropped once schema updates have been made for CharacterMailMessage.

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

ESI endpoint(s):

* `/characters/{character_id}/roles/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
roleCategory | roleCategory | *N/A* | Artificial field to distinguish between role type.  One of: CORPORATION, CORPORATION\_AT\_HQ, CORPORATION\_AT\_BASE and CORPORATION\_AT\_OTHER.
roleID | (deleted) | *N/A* | Not provided by ESI.  Extraneous.
roleName | roleName | roles, roles\_at\_hq, roles\_at\_base, roles\_at\_other | Enumerated types stored as a string by EveKit.  See conversion guide below.

#### Historic Conversion Notes

* Role names have changed to enumerated fields and will be converted as follows:

```sql
UPDATE `evekit_data_character_role` SET roleName = 'Account_Take_1' WHERE roleName = 'roleAccountTake1';
UPDATE `evekit_data_character_role` SET roleName = 'Account_Take_2' WHERE roleName = 'roleAccountTake2';
UPDATE `evekit_data_character_role` SET roleName = 'Account_Take_3' WHERE roleName = 'roleAccountTake3';
UPDATE `evekit_data_character_role` SET roleName = 'Account_Take_4' WHERE roleName = 'roleAccountTake4';
UPDATE `evekit_data_character_role` SET roleName = 'Account_Take_5' WHERE roleName = 'roleAccountTake5';
UPDATE `evekit_data_character_role` SET roleName = 'Account_Take_6' WHERE roleName = 'roleAccountTake6';
UPDATE `evekit_data_character_role` SET roleName = 'Account_Take_7' WHERE roleName = 'roleAccountTake7';
UPDATE `evekit_data_character_role` SET roleName = 'Accountant' WHERE roleName = 'roleAccountant';
UPDATE `evekit_data_character_role` SET roleName = 'Auditor' WHERE roleName = 'roleAuditor';
UPDATE `evekit_data_character_role` SET roleName = 'Communications_Officer' WHERE roleName = 'roleCommunicationsOfficer';
UPDATE `evekit_data_character_role` SET roleName = 'Config_Equipment' WHERE roleName = 'roleConfigEquipment';
UPDATE `evekit_data_character_role` SET roleName = 'Config_Starbase_Equipment' WHERE roleName = 'roleConfigStarbaseEquipment';
UPDATE `evekit_data_character_role` SET roleName = 'Container_Take_1' WHERE roleName = 'roleContainerTake1';
UPDATE `evekit_data_character_role` SET roleName = 'Container_Take_2' WHERE roleName = 'roleContainerTake2';
UPDATE `evekit_data_character_role` SET roleName = 'Container_Take_3' WHERE roleName = 'roleContainerTake3';
UPDATE `evekit_data_character_role` SET roleName = 'Container_Take_4' WHERE roleName = 'roleContainerTake4';
UPDATE `evekit_data_character_role` SET roleName = 'Container_Take_5' WHERE roleName = 'roleContainerTake5';
UPDATE `evekit_data_character_role` SET roleName = 'Container_Take_6' WHERE roleName = 'roleContainerTake6';
UPDATE `evekit_data_character_role` SET roleName = 'Container_Take_7' WHERE roleName = 'roleContainerTake7';
UPDATE `evekit_data_character_role` SET roleName = 'Contract_Manager' WHERE roleName = 'roleContractManager';
UPDATE `evekit_data_character_role` SET roleName = 'Diplomat' WHERE roleName = 'roleDiplomat';
UPDATE `evekit_data_character_role` SET roleName = 'Director' WHERE roleName = 'roleDirector';
UPDATE `evekit_data_character_role` SET roleName = 'Factory_Manager' WHERE roleName = 'roleFactoryManager';
UPDATE `evekit_data_character_role` SET roleName = 'Fitting_Manager' WHERE roleName = 'roleFittingManager';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Query_1' WHERE roleName = 'roleHangarQuery1';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Query_2' WHERE roleName = 'roleHangarQuery2';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Query_3' WHERE roleName = 'roleHangarQuery3';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Query_4' WHERE roleName = 'roleHangarQuery4';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Query_5' WHERE roleName = 'roleHangarQuery5';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Query_6' WHERE roleName = 'roleHangarQuery6';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Query_7' WHERE roleName = 'roleHangarQuery7';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Take_1' WHERE roleName = 'roleHangarTake1';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Take_2' WHERE roleName = 'roleHangarTake2';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Take_3' WHERE roleName = 'roleHangarTake3';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Take_4' WHERE roleName = 'roleHangarTake4';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Take_5' WHERE roleName = 'roleHangarTake5';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Take_6' WHERE roleName = 'roleHangarTake6';
UPDATE `evekit_data_character_role` SET roleName = 'Hangar_Take_7' WHERE roleName = 'roleHangarTake7';
UPDATE `evekit_data_character_role` SET roleName = 'Junior_Accountant' WHERE roleName = 'roleJuniorAccountant';
UPDATE `evekit_data_character_role` SET roleName = 'Personnel_Manager' WHERE roleName = 'rolePersonnelManager';
UPDATE `evekit_data_character_role` SET roleName = 'Rent_Factory_Facility' WHERE roleName = 'roleRentFactoryFacility';
UPDATE `evekit_data_character_role` SET roleName = 'Rent_Office' WHERE roleName = 'roleRentOffice';
UPDATE `evekit_data_character_role` SET roleName = 'Rent_Research_Facility' WHERE roleName = 'roleRentResearchFacility';
UPDATE `evekit_data_character_role` SET roleName = 'Security_Officer' WHERE roleName = 'roleSecurityOfficer';
UPDATE `evekit_data_character_role` SET roleName = 'Starbase_Defense_Operator' WHERE roleName = 'roleStarbaseDefenseOperator';
UPDATE `evekit_data_character_role` SET roleName = 'Starbase_Fuel_Technician' WHERE roleName = 'roleStarbaseFuelTechnician';
UPDATE `evekit_data_character_role` SET roleName = 'Station_Manager' WHERE roleName = 'roleStationManager';
UPDATE `evekit_data_character_role` SET roleName = 'Terrestrial_Combat_Officer' WHERE roleName = 'roleTerrestrialCombatOfficer';
UPDATE `evekit_data_character_role` SET roleName = 'Terrestrial_Logistics_Officer' WHERE roleName = 'roleTerrestrialLogisticsOfficer';
UPDATE `evekit_data_character_role` SET roleName = 'Trader' WHERE roleName = 'roleTrader';
```

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

* `/characters/{character_id}/titles/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
titleID | titleID | title\_id | Change type from long to int.
titleName | titleName | name 

### ChatChannel

* `/characters/{character_id}/chat_channels/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
channelID | channelID | channel\_id | Change type from long to int.
ownerID | ownerID | owner\_id | Change type from long to int.
ownerName | (deleted) | *N/A* | ESI expects lookup from `ownerID`.
displayName | displayName | name |
comparisonKey | comparisonKey | comparison\_key |
hasPassword | hasPassword | has\_password |
motd | motd | motd |

### ChatChannelMember

* `/characters/{character_id}/chat_channels/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
channelID | channelID | *N/A* | Inserted by EveKit.  Change type from long to int.
category | category | *N/A* | Inserted by EveKit.  One of 'allowed', 'blocked', 'muted' or 'operator'.
accessorID | accessorID | accessor\_id | Change type from long to int.  Available on all categories.
accessorName | (deleted) | *N/A* | ESI expects lookup from `accessorID`.
*N/A* | accessorType | accessor\_type | Enumerated type giving the type of accessor (character, corporation or alliance).
untilWhen | untilWhen | end\_at |
reason | reason | reason |

#### Historic Conversion Notes

* The `accessorType` will not be populated historically on day one as it will take substantial
effort to resolve all missing accessor IDs.  We will do this incrementally after the main switchover to ESI.
* In `category`, change "operators" to "operator":

```sql
UPDATE `evekit_data_chatchannel_member` SET category = 'operator' WHERE category = 'operators';
```

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

ESI endpoint(s):

* `/characters/{character_id}/mail/lists/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
displayName | displayName | name |
listID | listID | mailing\_list\_id | Type change from long to int

### MailLabel (new)

ESI endpoint(s):

* `/characters/{character_id}/mail/labels/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | unreadCount | unread\_count | Integer field (optional)
*N/A* | labelID | label\_id | Integer field (optional?)
*N/A* | name | name | String field (optional)
*N/A* | color | color | String field (optional, source is enumerated)

### PlanetaryColony

ESI endpoint(s):

* `/characters/{character_id}/planets/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
planetID | planetID | planet\_id | Type change from long to int.
solarSystemID | solarSystemID | solar\_system\_id |
solarSystemName | (deleted) | *N/A* | ESI expects lookup from `solarSystemID`.
planetName | (deleted) | *N/A* | ESI expects lookup from `planetID`.
planetTypeID | (deleted) | *N/A* | Replaced by enumerated type `planetType`.
planetTypeName | (deleted) | *N/A* | Renamed to `planetType`.
*N/A* | planetType | planet\_type | Renamed from `planetTypeName`.  See conversion notes below.
ownerID | ownerID | owner\_id | Type change from long to int.
ownerName | (deleted) | *N/A* | ESI expects lookup from `ownerID`.
lastUpdate | lastUpdate | last\_update |
upgradeLevel | upgradeLevel | upgrade\_level |
numberOfPins | numberOfPins | num\_pins |

#### Historic Conversion Notes

* `planetTypeName` will be renamed to `planetType`.  This requires the following conversion:
```sql
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'temperate' WHERE `planetType` = 'Planet(Temperate)';
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'barren' WHERE `planetType` = 'Planet(Barren)';
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'oceanic' WHERE `planetType` = 'Planet(Oceanic)';
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'ice' WHERE `planetType` = 'Planet(Ice)';
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'gas' WHERE `planetType` = 'Planet(Gas)';
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'lava' WHERE `planetType` = 'Planet(Lava)';
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'storm' WHERE `planetType` = 'Planet(Storm)';
UPDATE `evekit_data_planetary_colory` SET `planetType` = 'plasma' WHERE `planetType` = 'Planet(Plasma)';
```

### PlanetaryLink

ESI endpoint(s):

* `/characters/{character_id}/planets/{planet_id}/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
planetID | planetID | *N/A* | EveKit injected field, type change from long to int.
sourcePinID | sourcePinID | source\_pin\_id |
destinationPinID | destinationPinID | destination\_pin\_id |
linkLevel | linkLevel | link\_level |

### PlanetaryPin

ESI endpoint(s):

* `/characters/{character_id}/planets/{planet_id}/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
planetID | planetID | *N/A* | EveKit injected field, type change from long to int.
pinID | pinID | pin\_id |
typeID | typeID | type\_id |
typeName | (deleted) | *N/A* | ESI expects lookup from `typeID`.
schematicID | schematicID | schematic\_id |
lastLaunchTime | (deleted) | *N/A* | Renamed to `lastCycleStart`.
*N/A* | lastCycleStart | last\_cycle\_start | Renamed from `lastLaunchTime`.
cycleTime | cycleTime | extractor\_details.cycle\_time |
quantityPerCycle | quantityPerCycle | extractor\_details.qty\_per\_cycle |
installTime | installTime | install\_time |
expiryTime | expiryTime | expiry\_time |
contentTypeID | (deleted) | *N/A* | Renamed to `productTypeID`.
*N/A* | productTypeID | extractor\_details.product\_type\_id | Renamed from `contentTypeID`.
contentTypeName | (deleted) | *N/A* | ESI expects lookup from `productTypeID`.
contentQuantity | (deleted) | *N/A* | No real analog, replaced by `contents` array.
longitude | longitude | longitude | Type change from double to float.
latitude | latitude | latitude | Type change from double to float.
*N/A* | headRadius | extractor\_details.head\_radius | 
*N/A* | heads | extractor\_details.heads | Set of new `PlanetaryPinHead` embeddable object.
*N/A* | contents | contents | Set of new `PlanetaryPinContent` embeddable object.

#### New Embeddable Classes

```java
public class PlanetaryPinHead {
  int headID;
  float latitude;
  float longitude;
}
```

```sql
CREATE TABLE `planet_pin_head` (
       `planet_pin_cid` BIGINT(20) NOT NULL,
       `headID` INT(11) NOT NULL,
       `latitude` FLOAT NOT NULL,
       `longitude` FLOAT NOT NULL,
       KEY `planet_pin_head_cid_index` (`planet_pin_cid`),
       CONSTRAINT `planet_pin_head_fk` FOREIGN KEY (`planet_pin_cid`) REFERENCES `evekit_data_planetary_pin` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

```java
public class PlanetaryPinContent {
  int typeID;
  long amount;
}
```

```sql
CREATE TABLE `planet_pin_content` (
       `planet_pin_cid` BIGINT(20) NOT NULL,
       `typeID` INT(11) NOT NULL,
       `amount` BIGINT(20) NOT NULL,
       KEY `planet_pin_content_cid_index` (`planet_pin_cid`),
       CONSTRAINT `planet_pin_content_fk` FOREIGN KEY (`planet_pin_cid`) REFERENCES `evekit_data_planetary_pin` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### PlanetaryRoute

ESI endpoint(s):

* `/characters/{character_id}/planets/{planet_id}/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
planetID | planetID | *N/A* | EveKit injected field, type change from long to int.
routeID | routeID | route\_id |
sourcePinID | sourcePinID | source\_pin\_id |
destinationPinID | destinationPinID | destination\_pin\_id |
contentTypeID | contentTypeID | content\_type\_id |
contentTypeName | (deleted) | *N/A* | ESI expects lookup from `contentTypeID`.
quantity | quantity | quantity | Type change from int to float.
waypoint1 | (deleted) | *N/A* | Removed in favor of waypoints list.
waypoint2 | (deleted) | *N/A* | Removed in favor of waypoints list.
waypoint3 | (deleted) | *N/A* | Removed in favor of waypoints list.
waypoint4 | (deleted) | *N/A* | Removed in favor of waypoints list.
waypoint5 | (deleted) | *N/A* | Removed in favor of waypoints list.
*N/A* | waypoints | waypoints | List of waypoint IDs (ordered).

#### New Embeddable Classes

* Waypoints is a native list of long values requiring the following table:
```

```sql
CREATE TABLE `planet_route_waypoint` (
       `planet_route_cid` BIGINT(20) NOT NULL,
       `waypointID` BIGINT(20) NOT NULL,
       KEY `planet_pin_head_cid_index` (`planet_pin_cid`),
       CONSTRAINT `planet_pin_head_fk` FOREIGN KEY (`planet_pin_cid`) REFERENCES `evekit_data_planetary_pin` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### Historic Conversion Notes

Once the `planet_route_waypoint` table is created, historic waypoints can be populated.  This should be done before
removing `waypoint1` through `waypoint5`:

```sql
INSERT INTO `planet_route_waypoint` FROM (planet_route_cid, waypointID)
SELECT cid, waypoint1 FROM `evekit_data_planetary_route`;
INSERT INTO `planet_route_waypoint` FROM (planet_route_cid, waypointID)
SELECT cid, waypoint2 FROM `evekit_data_planetary_route`;
INSERT INTO `planet_route_waypoint` FROM (planet_route_cid, waypointID)
SELECT cid, waypoint3 FROM `evekit_data_planetary_route`;
INSERT INTO `planet_route_waypoint` FROM (planet_route_cid, waypointID)
SELECT cid, waypoint4 FROM `evekit_data_planetary_route`;
INSERT INTO `planet_route_waypoint` FROM (planet_route_cid, waypointID)
SELECT cid, waypoint5 FROM `evekit_data_planetary_route`;
```

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

ESI endpoint(s):

* `/corporations/{corporation_id}/medals/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
medalID | medalID | medal\_id |
description | description | description |
title | title | title |
created | created | created |
creatorID | creatorID | creator\_id | Change type from long to int.

### CorporationMemberMedal

ESI endpoint(s):

* `/corporations/{corporation_id}/medals/issued/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
medalID | medalID | medal\_id |
characterID | characterID | character\_id | Change type from long to int.
issued | issued | issued_at |
issuerID | issuerID | issuer\_id | Change type from long to int.
reason | reason | reason |
status | status | status | Now an enumerated type stored as a string.

### CorporationSheet

ESI endpoint(s):

* `/corporations/{corporation_id}/`
* `/corporations/{corporation_id}/icons/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
allianceID | allianceID | alliance\_id | Change type from long to int.
allianceName | (deleted) | *N/A* | ESI expects lookup from `allianceID`.
ceoID | ceoID | ceo\_id | Change type from long to int.
ceoName | (deleted) | *N/A* | ESI expects lookup from `ceoID`.
corporationID | corporationID | *N/A* | Pulled from the synchronized EVE account.
corporationName | corporationName | name |
description | description | description |
logoColor1 | (deleted) | *N/A* | Removed in favor of icon URLs.
logoColor2 | (deleted) | *N/A* | Removed in favor of icon URLs.
logoColor3 | (deleted) | *N/A* | Removed in favor of icon URLs.
logoGraphicID | (deleted) | *N/A* | Removed in favor of icon URLs.
logoShape1 | (deleted) | *N/A* | Removed in favor of icon URLs.
logoShape2 | (deleted) | *N/A* | Removed in favor of icon URLs.
logoShape3 | (deleted) | *N/A* | Removed in favor of icon URLs.
memberCount | memberCount | member\_count |
memberLimit | (deleted) | *N/A* | Moved to the new `MemberLimit` model.  See conversion notes.
shares | shares | shares | Change type from int to long.
stationID | stationID | home\_station\_id | Change type from long to int.
stationName | (deleted) | *N/A* | ESI expects lookup from `stationID`.
taxRate | taxRate | tax\_rate | Change type from double to float.
ticker | ticker | ticker |
url | url | url |
*N/A* | dateFounded | date\_founded | Founding date.  Type is long.
*N/A* | creatorID | creator\_id | Type is int.
*N/A* | factionID | faction\_id | Type is int.
*N/A* | px64x64 | px64x64 | Type is string.
*N/A* | px128x128 | px128x128 | Type is string.
*N/A* | px256x256 | px256x256 | Type is string.

#### Historic Conversion Notes

* `memberLimit` is moved to the new `MemberLimit` model.  This data won't be populated
historically.  We'll start tracking member limits on the first ESI sync.
* There is no easy conversion for the local color and shape fields so these will not be populated historically.  Instead, we'll rely on the new icon URL fields going forward.

### CorporationTitle
### CustomsOffice
### Division
### Facility
### Fuel
### MemberLimit (new) 

ESI endpoint(s):

* `/corporations/{corporation_id}/members/limit/`

This model is updated as part of `MemberTracking` is it requires the same authorization.

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | limit | *N/A* | ESI result is a simple int32.

```sql
CREATE TABLE `evekit_data_corporation_member_limit` (
       `cid` BIGINT(20) NOT NULL,
       `memberLimit` INT(11) NOT NULL,
       PRIMARY KEY (`cid`),
       CONSTRAINT `corporation_member_limit_fk` FOREIGN KEY (`cid`) REFERENCES `evekit_cached_data` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### MemberSecurity

Member security is now divided into three models as follows:

1. MemberSecurity - lists the roles associated with characters in the corporation.
2. CorporationTitle - named collection of roles that can be assigned to a character.
3. MemberTitle - lists the title IDs associated with a character.

### MemberSecurityLog
### MemberTitle (new) 

ESI endpoint(s):

* `/corporations/{corporation_id}/members/titles/` (esi-corporations.read\_titles.v1)

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
*N/A* | characterID | character\_id | 

```sql
CREATE TABLE `evekit_data_corporation_member_title` (
       `cid` BIGINT(20) NOT NULL,
       `titleID` INT(11) NOT NULL,
       PRIMARY KEY (`cid`),
       CONSTRAINT `corporation_member_title_fk` FOREIGN KEY (`cid`) REFERENCES `evekit_cached_data` (`cid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### MemberTracking

ESI endpoint(s):

* `/corporations/{corporation_id}/membertracking/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
characterID | characterID | character\_id | Change type from long to int.
base | (deleted) | *N/A* | ESI expects lookup from `baseID`.
baseID | baseID | base\_id | Change type from long to int.
grantableRoles | (deleted) | Now only stored in `MemberSecurity`.
location | (deleted) | *N/A* | ESI expects lookup from `locationID`.
locationID | locationID | location\_id |
logoffDateTime | logoffDateTime | logoff\_date |
logonDateTime | logonDateTime | logon\_date |
name | (deleted) | *N/A* | ESI expects lookup from `characterID`.
roles | (deleted) | *N/A* | Now only stored in `MemberSecurity`.
shipType | (deleted) | *N/A* | ESI expects lookup from `shipTypeID`.
shipTypeID | shipTypeID | ship\_type |
startDateTime | startDateTime | start\_date |
title | (deleted) | *N/A* | Moved to new `MemberTitle` model.

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

ESI endpoint(s):

* `/characters/{character_id}/contacts/`
* `/corporations/{corporation_id}/contacts/`
* `/alliances/{alliance_id}/contacts/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
list | list | *N/A* | EveKit injected field, one of "character", "corporation" or "alliance".
contactID | contactID | contact\_id |
contactName | (deleted) | *N/A* | ESI expects lookup from `contact\_id`
standing | standing | standing | Type change from double to float.
contactTypeID | (deleted) | *N/A* | Replaced by `contactType`.
*N/A* | contactType | contact\_type | Enumerated type which replaces `contactTypeID`.
inWatchlist | inWatchlist | is\_watched | Only valid for character and corporation contact list. 
*N/A* | isBlocked | is\_blocked | Only valid for character contact list.
labelMask | (deleted) | *N/A* | Replaced by `labelID`.
*N/A* | labelID | label\_id | Replaces `labelMask`.  See conversion notes below.

#### Historic Conversion Notes

* `contactType` is populated from `contactTypeID` as follows:
```sql
UPDATE `evekit_data_contact` SET `contactType` = 'corporation' WHERE `contactTypeID` = 2;
UPDATE `evekit_data_contact` SET `contactType` = 'character' WHERE `contactTypeID` BETWEEN 1373 AND 1386;
UPDATE `evekit_data_contact` SET `contactType` = 'alliance' WHERE `contactTypeID` = 16159;
```

* `labelID` is set from `labelMask` as a simple column rename.

* in `list` we change "corporate" to "corporation"
```sql
UPDATE `evekit_data_contact` SET `list` = 'corporation' WHERE `list` = 'corporate';
```

### ContactLabel

ESI endpoint(s):

* `/characters/{character_id}/contacts/labels/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
list | list | *N/A* | EveKit injected field, one of "character" or "corporation".
labelID | labelID | label\_id |
name | name | label\_name |

#### Historic Conversion Notes

* in `list` we change "corporate" to "corporation"

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

ESI endpoint(s):

* `/characters/{character_id}/fw/stats/`
* `/corporations/{corporation_id}/fw/stats/`

Old Model Field | New Model Field | ESI Field | Notes
---|---|---|---
currentRank | currentRank | current\_rank | characters only
enlisted | enlisted | enlisted\_on |
factionID | factionID | faction\_id | 
factionName | (deleted) | *N/A* | ESI expects lookup from `factionID`
highestRank | highestRank | highest\_rank | characters only 
killsLastWeek | killsLastWeek | kills.lastWeek |
killsTotal | killsTotal | kills.total |
killsYesterday | killsYesterday | kills.yesterday |
pilots | pilots | pilots | corporation only
victoryPointsLastWeek | victoryPointsLastWeek | victoryPoints.lastWeek |
victoryPointsTotal | victoryPointsTotal | victoryPoints.total |
victoryPointsYesterday | victoryPointsYesterday | victoryPoints.yesterday |

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

