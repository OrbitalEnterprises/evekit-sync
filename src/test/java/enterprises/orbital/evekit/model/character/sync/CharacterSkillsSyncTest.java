package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccount;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.SyncTestBase;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.CharacterSheet;
import enterprises.orbital.evekit.model.character.CharacterSkill;
import enterprises.orbital.evexmlapi.chr.CharacterRoleCategory;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.ISkill;
import enterprises.orbital.evexmlapi.chr.ISkillInfo;

public class CharacterSkillsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  long                   testDate;
  long                   prevDate;
  EveKitUserAccount      userAccount;
  SynchronizedEveAccount syncAccount;
  Capsuleer              container;
  CapsuleerSyncTracker   tracker;
  SynchronizerUtil       syncUtil;
  ICharacterAPI          mockServer;

  static Object[][]      testData;

  static {
    // generate random data
    testData = new Object[1][41];

    testData[0][0] = TestBase.getRandomLong();
    testData[0][1] = TestBase.getRandomText(50);
    testData[0][2] = TestBase.getRandomText(30);
    testData[0][3] = TestBase.getRandomLong();
    testData[0][4] = TestBase.getRandomText(30);
    testData[0][5] = TestBase.getRandomText(30);
    testData[0][6] = TestBase.getRandomText(30);
    testData[0][7] = TestBase.getRandomText(50);
    testData[0][8] = TestBase.getRandomLong();
    testData[0][9] = TestBase.getRandomText(50);
    testData[0][10] = TestBase.getRandomInt();
    testData[0][11] = TestBase.getRandomInt();
    testData[0][12] = TestBase.getRandomInt();
    testData[0][13] = TestBase.getRandomInt();
    testData[0][14] = TestBase.getRandomInt();
    testData[0][15] = TestBase.getRandomInt();
    // bloodlineID and ancestryID
    testData[0][39] = TestBase.getRandomInt();
    testData[0][40] = TestBase.getRandomInt();

    // Generate random titles
    int numTitles = 3 + TestBase.getRandomInt(3);
    Object[][] titles = new Object[numTitles][2];
    for (int i = 0; i < numTitles; i++) {
      titles[i][0] = TestBase.getUniqueRandomLong();
      titles[i][1] = TestBase.getRandomText(50);
    }
    testData[0][16] = titles;

    // Generate random skills
    int numSkills = 20 + TestBase.getRandomInt(20);
    Object[][] skills = new Object[numSkills][4];
    for (int i = 0; i < numSkills; i++) {
      skills[i][0] = TestBase.getRandomInt(5) + 1;
      skills[i][1] = TestBase.getRandomInt();
      skills[i][2] = TestBase.getUniqueRandomInteger();
      skills[i][3] = TestBase.getRandomBoolean();
    }
    testData[0][17] = skills;

    // Generate random roles
    final CharacterRoleCategory[] roleCategories = {
        CharacterRoleCategory.CORPORATION, CharacterRoleCategory.CORPORATION_AT_HQ, CharacterRoleCategory.CORPORATION_AT_BASE,
        CharacterRoleCategory.CORPORATION_AT_OTHER
    };
    int numRoles = TestBase.getRandomInt(3) + 1;
    Object[][] roles = new Object[numRoles][3];
    for (int i = 0; i < numRoles; i++) {
      roles[i][0] = roleCategories[TestBase.getRandomInt(4)];
      roles[i][1] = TestBase.getUniqueRandomLong();
      roles[i][2] = TestBase.getRandomText(50);
    }
    testData[0][18] = roles;

    testData[0][19] = TestBase.getRandomText(50);
    testData[0][20] = TestBase.getUniqueRandomLong();

    // Generate random certs
    int numCerts = 10 + TestBase.getRandomInt(10);
    Object[][] certs = new Object[numCerts][1];
    for (int i = 0; i < numCerts; i++) {
      certs[i][0] = TestBase.getUniqueRandomInteger();
    }
    testData[0][21] = certs;

    testData[0][22] = TestBase.getRandomDouble(1000000);

    testData[0][23] = TestBase.getRandomText(50);
    testData[0][24] = TestBase.getRandomLong();
    testData[0][25] = TestBase.getRandomLong();
    testData[0][26] = TestBase.getRandomLong();
    testData[0][27] = TestBase.getRandomLong();
    testData[0][28] = TestBase.getRandomLong();
    testData[0][29] = TestBase.getRandomInt();
    testData[0][30] = TestBase.getRandomInt();
    testData[0][31] = TestBase.getRandomInt();
    testData[0][32] = TestBase.getRandomLong();
    testData[0][33] = TestBase.getRandomLong();
    testData[0][34] = TestBase.getRandomLong();
    testData[0][35] = TestBase.getRandomLong();

    // Generate random implants
    int numImplants = 3 + TestBase.getRandomInt(5);
    Object[][] implants = new Object[numImplants][2];
    for (int i = 0; i < numImplants; i++) {
      implants[i][0] = TestBase.getUniqueRandomInteger();
      implants[i][1] = TestBase.getRandomText(50);
    }
    testData[0][36] = implants;

    // Generate random clones
    int numClones = 3 + TestBase.getRandomInt(7);
    Object[][] clones = new Object[numClones][4];
    for (int i = 0; i < numClones; i++) {
      clones[i][0] = TestBase.getUniqueRandomInteger();
      clones[i][1] = TestBase.getRandomInt();
      clones[i][2] = TestBase.getRandomLong();
      clones[i][3] = TestBase.getRandomText(50);
    }
    testData[0][37] = clones;

    // Generate random clone implants
    int numCloneImplants = 3 + TestBase.getRandomInt(15);
    Object[][] cloneImplants = new Object[numCloneImplants][3];
    for (int i = 0; i < numCloneImplants; i++) {
      cloneImplants[i][0] = TestBase.getUniqueRandomInteger();
      cloneImplants[i][1] = TestBase.getRandomInt();
      cloneImplants[i][2] = TestBase.getRandomText(50);
    }
    testData[0][38] = cloneImplants;

  }

  // Mock up server interface
  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test user and sync account
    userAccount = EveKitUserAccount.createNewUserAccount(true, true);
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true, 1234, "abcd", 5678, "charname", 8765, "corpname");
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void compareCharacterSheetWithTestData(
                                                CharacterSheet sheet,
                                                boolean unchanged) {
    Assert.assertEquals(sheet.getCorporationID(), unchanged ? (long) ((Long) testData[0][0]) : 0);
    Assert.assertEquals(sheet.getCorporationName(), unchanged ? testData[0][1] : "");
    Assert.assertEquals(sheet.getRace(), unchanged ? testData[0][2] : "");
    Assert.assertEquals(sheet.getDoB(), unchanged ? (long) ((Long) testData[0][3]) : 0);
    Assert.assertEquals(sheet.getBloodlineID(), unchanged ? (int) ((Integer) testData[0][39]) : 0);
    Assert.assertEquals(sheet.getBloodline(), unchanged ? testData[0][4] : "");
    Assert.assertEquals(sheet.getAncestryID(), unchanged ? (int) ((Integer) testData[0][40]) : 0);
    Assert.assertEquals(sheet.getAncestry(), unchanged ? testData[0][5] : "");
    Assert.assertEquals(sheet.getGender(), unchanged ? testData[0][6] : "");
    Assert.assertEquals(sheet.getAllianceName(), unchanged ? testData[0][7] : "");
    Assert.assertEquals(sheet.getAllianceID(), unchanged ? (long) ((Long) testData[0][8]) : 0);
    Assert.assertEquals(sheet.getIntelligence(), unchanged ? (int) ((Integer) testData[0][11]) : 0);
    Assert.assertEquals(sheet.getMemory(), unchanged ? (int) ((Integer) testData[0][12]) : 0);
    Assert.assertEquals(sheet.getCharisma(), unchanged ? (int) ((Integer) testData[0][13]) : 0);
    Assert.assertEquals(sheet.getPerception(), unchanged ? (int) ((Integer) testData[0][14]) : 0);
    Assert.assertEquals(sheet.getWillpower(), unchanged ? (int) ((Integer) testData[0][15]) : 0);
    Assert.assertEquals(sheet.getName(), unchanged ? testData[0][19] : "");
    Assert.assertEquals(sheet.getCharacterID(), unchanged ? (long) ((Long) testData[0][20]) : 0);
    Assert.assertEquals(sheet.getFactionName(), unchanged ? testData[0][23] : "");
    Assert.assertEquals(sheet.getFactionID(), unchanged ? (long) ((Long) testData[0][24]) : 0);
    Assert.assertEquals(sheet.getHomeStationID(), unchanged ? (long) ((Long) testData[0][25]) : 0);
    Assert.assertEquals(sheet.getLastRespecDate(), unchanged ? (long) ((Long) testData[0][27]) : 0);
    Assert.assertEquals(sheet.getLastTimedRespec(), unchanged ? (long) ((Long) testData[0][28]) : 0);
    Assert.assertEquals(sheet.getFreeRespecs(), unchanged ? (int) ((Integer) testData[0][29]) : 0);
    Assert.assertEquals(sheet.getFreeSkillPoints(), (int) ((Integer) testData[0][30]));
    Assert.assertEquals(sheet.getRemoteStationDate(), unchanged ? (long) ((Long) testData[0][32]) : 0);
  }

  public CharacterSheet makeCharacterSheetObject(
                                                 long time,
                                                 Object[] instanceData)
    throws Exception {
    CharacterSheet sheet = new CharacterSheet(
        (Long) testData[0][20], (String) testData[0][19], (Long) instanceData[0], (String) instanceData[1], (String) instanceData[2], (Long) instanceData[3],
        (Integer) instanceData[39], (String) instanceData[4], (Integer) instanceData[40], (String) instanceData[5], (String) instanceData[6],
        (String) instanceData[7], (Long) instanceData[8], (String) testData[0][23], (Long) testData[0][24], (Integer) instanceData[11],
        (Integer) instanceData[12], (Integer) instanceData[13], (Integer) instanceData[14], (Integer) instanceData[15], (Long) testData[0][25],
        (Long) testData[0][27], (Long) testData[0][28], (Integer) testData[0][29], (Integer) testData[0][30], (Long) testData[0][32]);
    sheet.setup(syncAccount, time);
    return sheet;
  }

  public void compareSkillWithTestData(
                                       Collection<CharacterSkill> skills) {
    Object[][] skillData = (Object[][]) testData[0][17];
    Assert.assertEquals(skills.size(), skillData.length);
    for (CharacterSkill next : skills) {
      boolean found = false;
      int typeID = next.getTypeID();
      for (int i = 0; i < skillData.length; i++) {
        if (typeID == (Integer) skillData[i][2]) {
          found = true;
          Assert.assertEquals(next.isPublished(), (boolean) ((Boolean) skillData[i][3]));
          Assert.assertEquals(next.getSkillpoints(), (int) ((Integer) skillData[i][1]));
          Assert.assertEquals(next.getLevel(), (int) ((Integer) skillData[i][0]));
          break;
        }
      }
      Assert.assertTrue(found);
    }
  }

  public CharacterSkill makeSkillObject(
                                        long time,
                                        Object[] instanceData)
    throws Exception {
    CharacterSkill skill = new CharacterSkill((Integer) instanceData[2], (Integer) instanceData[0], (Integer) instanceData[1], (Boolean) instanceData[3]);
    skill.setup(syncAccount, time);
    return skill;
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    final Object[] instanceData = testData[0];
    ISkillInfo sheet = new ISkillInfo() {

      @Override
      public Set<ISkill> getSkills() {
        final Object[][] skillData = (Object[][]) instanceData[17];
        Set<ISkill> skills = new HashSet<ISkill>();
        for (int i = 0; i < skillData.length; i++) {
          final int j = i;
          skills.add(new ISkill() {

            @Override
            public boolean isPublished() {
              return (Boolean) skillData[j][3];
            }

            @Override
            public int getTypeID() {
              return (Integer) skillData[j][2];
            }

            @Override
            public int getSkillpoints() {
              return (Integer) skillData[j][1];
            }

            @Override
            public int getLevel() {
              return (Integer) skillData[j][0];
            }
          });
        }
        return skills;
      }

      @Override
      public int getFreeSkillPoints() {
        return (Integer) instanceData[30];
      }

    };

    EasyMock.expect(mockServer.requestSkills()).andReturn(sheet);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with new character sheet
  @Test
  public void testSkillsSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillsSync.syncCharacterSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify the following elements were added correctly:
    // CharacterSheet
    // CharacterSkill
    CharacterSheet sheet = CharacterSheet.get(syncAccount, testTime);
    Collection<CharacterSkill> skills = CharacterSkill.getAll(syncAccount, testTime, -1, 0);
    Assert.assertNotNull(sheet);
    compareCharacterSheetWithTestData(sheet, false);
    compareSkillWithTestData(skills);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getSkillsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillsDetail());
  }

  // Test update with character sheet already populated
  @Test
  public void testCharacterSheetSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate character sheet, skills, certificates, implants, jump clones, jump clone implants, balance, jump and clone
    CachedData.updateData(makeCharacterSheetObject(testTime, testData[0]));
    Object[][] skillData = (Object[][]) testData[0][17];
    for (int i = 0; i < skillData.length; i++) {
      CachedData.updateData(makeSkillObject(testTime, skillData[i]));
    }

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillsSync.syncCharacterSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify the following elements are unchanged:
    // CharacterSheet
    // CharacterSkill
    CharacterSheet sheet = CharacterSheet.get(syncAccount, testTime);
    Collection<CharacterSkill> skills = CharacterSkill.getAll(syncAccount, testTime, -1, 0);
    Assert.assertNotNull(sheet);
    compareCharacterSheetWithTestData(sheet, true);
    compareSkillWithTestData(skills);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getSkillsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillsDetail());
  }

  // Test skips update when already updated
  @Test
  public void testCharacterSheetSyncUpdateSkip() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate existing character sheet
    CachedData.updateData(makeCharacterSheetObject(testTime, testData[0]));
    Object[][] skillData = (Object[][]) testData[0][17];
    for (int i = 0; i < skillData.length; i++) {
      CachedData.updateData(makeSkillObject(testTime, skillData[i]));
    }

    // Set the tracker as already updated and populate the container
    tracker.setSkillsStatus(SyncState.UPDATED);
    tracker.setSkillsDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setSkillsExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = CharacterSkillsSync.syncCharacterSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify the following elements are unchanged:
    // CharacterSheet
    // CharacterSkill
    CharacterSheet sheet = CharacterSheet.get(syncAccount, testTime);
    Collection<CharacterSkill> skills = CharacterSkill.getAll(syncAccount, testTime, -1, 0);
    Assert.assertNotNull(sheet);
    compareCharacterSheetWithTestData(sheet, true);
    compareSkillWithTestData(skills);

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getSkillsExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillsStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getSkillsDetail());
  }

}
