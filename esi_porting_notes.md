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
### WalletTransaction
