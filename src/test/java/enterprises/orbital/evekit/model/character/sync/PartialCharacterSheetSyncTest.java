package enterprises.orbital.evekit.model.character.sync;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
import enterprises.orbital.evekit.model.character.CharacterSheetClone;
import enterprises.orbital.evekit.model.character.CharacterSheetJump;
import enterprises.orbital.evekit.model.character.Implant;
import enterprises.orbital.evekit.model.character.JumpClone;
import enterprises.orbital.evekit.model.character.JumpCloneImplant;
import enterprises.orbital.evexmlapi.chr.CharacterRoleCategory;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IImplant;
import enterprises.orbital.evexmlapi.chr.IJumpClone;
import enterprises.orbital.evexmlapi.chr.IJumpCloneImplant;
import enterprises.orbital.evexmlapi.chr.IPartialCharacterSheet;

public class PartialCharacterSheetSyncTest extends SyncTestBase {

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
    syncAccount = SynchronizedEveAccount.createSynchronizedEveAccount(userAccount, "testaccount", true, true);
    testDate = DateFormat.getDateInstance().parse("Nov 16, 2010").getTime();
    prevDate = DateFormat.getDateInstance().parse("Jan 10, 2009").getTime();

    // Prepare a test sync tracker
    tracker = CapsuleerSyncTracker.createOrGetUnfinishedTracker(syncAccount);

    // Prepare a container
    container = Capsuleer.getOrCreateCapsuleer(syncAccount);

    // Prepare the synchronizer util
    syncUtil = new SynchronizerUtil();
  }

  public void compareCharacterSheetJumpWithTestData(
                                                    CharacterSheetJump sheet) {
    Assert.assertEquals(sheet.getJumpActivation(), (long) ((Long) testData[0][33]));
    Assert.assertEquals(sheet.getJumpFatigue(), (long) ((Long) testData[0][34]));
    Assert.assertEquals(sheet.getJumpLastUpdate(), (long) ((Long) testData[0][35]));
  }

  public void compareCharacterSheetCloneWithTestData(
                                                     CharacterSheetClone sheet) {
    Assert.assertEquals(sheet.getCloneJumpDate(), (long) ((Long) testData[0][26]));
  }

  public void compareCharacterSheetWithTestData(
                                                CharacterSheet sheet,
                                                boolean unchanged) {
    Assert.assertEquals(sheet.getCorporationID(), unchanged ? (long) ((Long) testData[0][0]) : 0);
    Assert.assertEquals(sheet.getCorporationName(), unchanged ? testData[0][1] : "");
    Assert.assertEquals(sheet.getRace(), testData[0][2]);
    Assert.assertEquals(sheet.getDoB(), (long) ((Long) testData[0][3]));
    Assert.assertEquals(sheet.getBloodlineID(), (int) ((Integer) testData[0][39]));
    Assert.assertEquals(sheet.getBloodline(), testData[0][4]);
    Assert.assertEquals(sheet.getAncestryID(), (int) ((Integer) testData[0][40]));
    Assert.assertEquals(sheet.getAncestry(), testData[0][5]);
    Assert.assertEquals(sheet.getGender(), testData[0][6]);
    Assert.assertEquals(sheet.getAllianceName(), unchanged ? testData[0][7] : "");
    Assert.assertEquals(sheet.getAllianceID(), unchanged ? (long) ((Long) testData[0][8]) : 0);
    Assert.assertEquals(sheet.getIntelligence(), (int) ((Integer) testData[0][11]));
    Assert.assertEquals(sheet.getMemory(), (int) ((Integer) testData[0][12]));
    Assert.assertEquals(sheet.getCharisma(), (int) ((Integer) testData[0][13]));
    Assert.assertEquals(sheet.getPerception(), (int) ((Integer) testData[0][14]));
    Assert.assertEquals(sheet.getWillpower(), (int) ((Integer) testData[0][15]));
    Assert.assertEquals(sheet.getName(), unchanged ? testData[0][19] : "");
    Assert.assertEquals(sheet.getCharacterID(), unchanged ? (long) ((Long) testData[0][20]) : 0);
    Assert.assertEquals(sheet.getFactionName(), unchanged ? testData[0][23] : "");
    Assert.assertEquals(sheet.getFactionID(), unchanged ? (long) ((Long) testData[0][24]) : 0);
    Assert.assertEquals(sheet.getHomeStationID(), unchanged ? (long) ((Long) testData[0][25]) : 0);
    Assert.assertEquals(sheet.getLastRespecDate(), (long) ((Long) testData[0][27]));
    Assert.assertEquals(sheet.getLastTimedRespec(), (long) ((Long) testData[0][28]));
    Assert.assertEquals(sheet.getFreeRespecs(), (int) ((Integer) testData[0][29]));
    Assert.assertEquals(sheet.getFreeSkillPoints(), unchanged ? (int) ((Integer) testData[0][30]) : 0);
    Assert.assertEquals(sheet.getRemoteStationDate(), (long) ((Long) testData[0][32]));
  }

  public CharacterSheetJump makeCharacterSheetJumpObject(
                                                         long time,
                                                         Object[] instanceData)
    throws Exception {
    CharacterSheetJump sheet = new CharacterSheetJump((Long) testData[0][33], (Long) testData[0][34], (Long) testData[0][35]);
    sheet.setup(syncAccount, time);
    return sheet;
  }

  public CharacterSheetClone makeCharacterSheetCloneObject(
                                                           long time,
                                                           Object[] instanceData)
    throws Exception {
    CharacterSheetClone sheet = new CharacterSheetClone((Long) testData[0][26]);
    sheet.setup(syncAccount, time);
    return sheet;
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

  public void compareImplantWithTestData(
                                         List<Implant> implants) {
    Object[][] implantData = (Object[][]) testData[0][36];
    Assert.assertEquals(implants.size(), implantData.length);
    for (Implant next : implants) {
      boolean found = false;
      int typeID = next.getTypeID();
      for (int i = 0; i < implantData.length; i++) {
        if (typeID == (Integer) implantData[i][0]) {
          found = true;
          Assert.assertEquals(next.getTypeName(), implantData[i][1]);
          break;
        }
      }
      Assert.assertTrue(found);
    }
  }

  public Implant makeImplantObject(
                                   long time,
                                   Object[] instanceData)
    throws Exception {
    Implant implant = new Implant((Integer) instanceData[0], (String) instanceData[1]);
    implant.setup(syncAccount, time);
    return implant;
  }

  public void compareJumpCloneWithTestData(
                                           List<JumpClone> clones) {
    Object[][] cloneData = (Object[][]) testData[0][37];
    Assert.assertEquals(clones.size(), cloneData.length);
    for (JumpClone next : clones) {
      boolean found = false;
      int jumpCloneID = next.getJumpCloneID();
      for (int i = 0; i < cloneData.length; i++) {
        if (jumpCloneID == (Integer) cloneData[i][0]) {
          found = true;
          Assert.assertEquals(next.getTypeID(), (int) ((Integer) cloneData[i][1]));
          Assert.assertEquals(next.getLocationID(), (long) ((Long) cloneData[i][2]));
          Assert.assertEquals(next.getCloneName(), cloneData[i][3]);
          break;
        }
      }
      Assert.assertTrue(found);
    }
  }

  public JumpClone makeJumpCloneObject(
                                       long time,
                                       Object[] instanceData)
    throws Exception {
    JumpClone clone = new JumpClone((Integer) instanceData[0], (Integer) instanceData[1], (Long) instanceData[2], (String) instanceData[3]);
    clone.setup(syncAccount, time);
    return clone;
  }

  public void compareJumpCloneImplantWithTestData(
                                                  List<JumpCloneImplant> cloneImplants) {
    Object[][] cloneImplantData = (Object[][]) testData[0][38];
    Assert.assertEquals(cloneImplants.size(), cloneImplantData.length);
    for (JumpCloneImplant next : cloneImplants) {
      boolean found = false;
      int jumpCloneID = next.getJumpCloneID();
      for (int i = 0; i < cloneImplantData.length; i++) {
        if (jumpCloneID == (Integer) cloneImplantData[i][0]) {
          found = true;
          Assert.assertEquals(next.getTypeID(), (int) ((Integer) cloneImplantData[i][1]));
          Assert.assertEquals(next.getTypeName(), cloneImplantData[i][2]);
          break;
        }
      }
      Assert.assertTrue(found);
    }
  }

  public JumpCloneImplant makeJumpCloneImplantObject(
                                                     long time,
                                                     Object[] instanceData)
    throws Exception {
    JumpCloneImplant cloneImplant = new JumpCloneImplant((Integer) instanceData[0], (Integer) instanceData[1], (String) instanceData[2]);
    cloneImplant.setup(syncAccount, time);
    return cloneImplant;
  }

  public void setupOkMock() throws Exception {
    mockServer = EasyMock.createMock(ICharacterAPI.class);
    final Object[] instanceData = testData[0];
    IPartialCharacterSheet sheet = new IPartialCharacterSheet() {

      @Override
      public int getWillpower() {
        return (Integer) instanceData[15];
      }

      @Override
      public String getRace() {
        return (String) instanceData[2];
      }

      @Override
      public int getPerception() {
        return (Integer) instanceData[14];
      }

      @Override
      public int getMemory() {
        return (Integer) instanceData[12];
      }

      @Override
      public int getIntelligence() {
        return (Integer) instanceData[11];
      }

      @Override
      public String getGender() {
        return (String) instanceData[6];
      }

      @Override
      public Date getDoB() {
        return new Date((Long) instanceData[3]);
      }

      @Override
      public int getCharisma() {
        return (Integer) instanceData[13];
      }

      @Override
      public int getBloodlineID() {
        return (Integer) instanceData[39];
      }

      @Override
      public String getBloodline() {
        return (String) instanceData[4];
      }

      @Override
      public int getAncestryID() {
        return (Integer) instanceData[40];
      }

      @Override
      public String getAncestry() {
        return (String) instanceData[5];
      }

      @Override
      public Date getCloneJumpDate() {
        return new Date((Long) instanceData[26]);
      }

      @Override
      public Date getLastRespecDate() {
        return new Date((Long) instanceData[27]);
      }

      @Override
      public Date getLastTimedRespec() {
        return new Date((Long) instanceData[28]);
      }

      @Override
      public int getFreeRespecs() {
        return (Integer) instanceData[29];
      }

      @Override
      public Date getRemoteStationDate() {
        return new Date((Long) instanceData[32]);
      }

      @Override
      public Date getJumpActivation() {
        return new Date((Long) instanceData[33]);
      }

      @Override
      public Date getJumpFatigue() {
        return new Date((Long) instanceData[34]);
      }

      @Override
      public Date getJumpLastUpdate() {
        return new Date((Long) instanceData[35]);
      }

      @Override
      public List<IImplant> getImplants() {
        Object[][] implants = (Object[][]) instanceData[36];
        List<IImplant> result = new ArrayList<IImplant>();
        for (int i = 0; i < implants.length; i++) {
          final Object[] data = implants[i];
          result.add(new IImplant() {

            @Override
            public int getTypeID() {
              return (Integer) data[0];
            }

            @Override
            public String getTypeName() {
              return (String) data[1];
            }

          });
        }
        return result;
      }

      @Override
      public List<IJumpClone> getJumpClones() {
        Object[][] clones = (Object[][]) instanceData[37];
        List<IJumpClone> result = new ArrayList<IJumpClone>();
        for (int i = 0; i < clones.length; i++) {
          final Object[] data = clones[i];
          result.add(new IJumpClone() {

            @Override
            public int getJumpCloneID() {
              return (Integer) data[0];
            }

            @Override
            public int getTypeID() {
              return (Integer) data[1];
            }

            @Override
            public long getLocationID() {
              return (Long) data[2];
            }

            @Override
            public String getCloneName() {
              return (String) data[3];
            }
          });
        }
        return result;
      }

      @Override
      public List<IJumpCloneImplant> getJumpCloneImplants() {
        Object[][] clones = (Object[][]) instanceData[38];
        List<IJumpCloneImplant> result = new ArrayList<IJumpCloneImplant>();
        for (int i = 0; i < clones.length; i++) {
          final Object[] data = clones[i];
          result.add(new IJumpCloneImplant() {

            @Override
            public int getJumpCloneID() {
              return (Integer) data[0];
            }

            @Override
            public int getTypeID() {
              return (Integer) data[1];
            }

            @Override
            public String getTypeName() {
              return (String) data[2];
            }

          });
        }
        return result;
      }
    };

    EasyMock.expect(mockServer.requestClones()).andReturn(sheet);
    EasyMock.expect(mockServer.isError()).andReturn(false);
    EasyMock.expect(mockServer.getCachedUntil()).andReturn(new Date(testDate));
  }

  // Test update with new character sheet
  @Test
  public void testPartialCharacterSheetSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Perform the sync
    SyncStatus syncOutcome = PartialCharacterSheetSync.syncCharacterSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify the following elements were added correctly:
    // CharacterSheet
    // Implant
    // JumpClone
    // JumpCloneImplant
    // CharacterSheetJump
    // CharacterSheetClone
    CharacterSheet sheet = CharacterSheet.get(syncAccount, testTime);
    List<Implant> implants = Implant.getAll(syncAccount, testTime);
    List<JumpClone> clones = JumpClone.getAll(syncAccount, testTime);
    List<JumpCloneImplant> cloneImplants = JumpCloneImplant.getAll(syncAccount, testTime);
    CharacterSheetJump jump = CharacterSheetJump.get(syncAccount, testTime);
    CharacterSheetClone clone = CharacterSheetClone.get(syncAccount, testTime);
    Assert.assertNotNull(sheet);
    // No prior data so non-updated fields should have default values
    compareCharacterSheetWithTestData(sheet, false);
    compareImplantWithTestData(implants);
    compareJumpCloneWithTestData(clones);
    compareJumpCloneImplantWithTestData(cloneImplants);
    compareCharacterSheetJumpWithTestData(jump);
    compareCharacterSheetCloneWithTestData(clone);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getPartialCharacterSheetExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPartialCharacterSheetStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPartialCharacterSheetDetail());
  }

  // Test update with character sheet already populated
  @Test
  public void testCharacterSheetSyncUpdateExisting() throws Exception {
    // Prepare mock
    setupOkMock();
    EasyMock.replay(mockServer);
    long testTime = 1234L;

    // Populate character sheet, implants, jump clones, jump clone implants, jump and clone
    CachedData.updateData(makeCharacterSheetObject(testTime, testData[0]));
    Object[][] implantData = (Object[][]) testData[0][36];
    for (int i = 0; i < implantData.length; i++) {
      CachedData.updateData(makeImplantObject(testTime, implantData[i]));
    }
    Object[][] cloneData = (Object[][]) testData[0][37];
    for (int i = 0; i < cloneData.length; i++) {
      CachedData.updateData(makeJumpCloneObject(testTime, cloneData[i]));
    }
    Object[][] cloneImplantData = (Object[][]) testData[0][38];
    for (int i = 0; i < cloneImplantData.length; i++) {
      CachedData.updateData(makeJumpCloneImplantObject(testTime, cloneImplantData[i]));
    }
    CachedData.updateData(makeCharacterSheetJumpObject(testTime, testData[0]));
    CachedData.updateData(makeCharacterSheetCloneObject(testTime, testData[0]));

    // Perform the sync
    SyncStatus syncOutcome = PartialCharacterSheetSync.syncCharacterSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    EasyMock.verify(mockServer);

    // Verify the following elements are unchanged:
    // CharacterSheet
    // Implants
    // Jump Clones
    // Jump Clone Implants
    // CharacterSheetJump
    // CharacterSheetClone
    CharacterSheet sheet = CharacterSheet.get(syncAccount, testTime);
    List<Implant> implants = Implant.getAll(syncAccount, testTime);
    List<JumpClone> clones = JumpClone.getAll(syncAccount, testTime);
    List<JumpCloneImplant> cloneImplants = JumpCloneImplant.getAll(syncAccount, testTime);
    CharacterSheetJump jump = CharacterSheetJump.get(syncAccount, testTime);
    CharacterSheetClone clone = CharacterSheetClone.get(syncAccount, testTime);
    Assert.assertNotNull(sheet);
    // Existing data, even not populated, should not be changed
    compareCharacterSheetWithTestData(sheet, true);
    compareImplantWithTestData(implants);
    compareJumpCloneWithTestData(clones);
    compareJumpCloneImplantWithTestData(cloneImplants);
    compareCharacterSheetJumpWithTestData(jump);
    compareCharacterSheetCloneWithTestData(clone);

    // Verify tracker and container were updated properly
    Assert.assertEquals(testDate, Capsuleer.getCapsuleer(syncAccount).getPartialCharacterSheetExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPartialCharacterSheetStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPartialCharacterSheetDetail());
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
    Object[][] implantData = (Object[][]) testData[0][36];
    for (int i = 0; i < implantData.length; i++) {
      CachedData.updateData(makeImplantObject(testTime, implantData[i]));
    }
    Object[][] cloneData = (Object[][]) testData[0][37];
    for (int i = 0; i < cloneData.length; i++) {
      CachedData.updateData(makeJumpCloneObject(testTime, cloneData[i]));
    }
    Object[][] cloneImplantData = (Object[][]) testData[0][38];
    for (int i = 0; i < cloneImplantData.length; i++) {
      CachedData.updateData(makeJumpCloneImplantObject(testTime, cloneImplantData[i]));
    }
    CachedData.updateData(makeCharacterSheetJumpObject(testTime, testData[0]));
    CachedData.updateData(makeCharacterSheetCloneObject(testTime, testData[0]));

    // Set the tracker as already updated and populate the container
    tracker.setPartialCharacterSheetStatus(SyncState.UPDATED);
    tracker.setPartialCharacterSheetDetail(null);
    CapsuleerSyncTracker.updateTracker(tracker);
    container.setPartialCharacterSheetExpiry(prevDate);
    container = CachedData.updateData(container);

    // Perform the sync
    SyncStatus syncOutcome = PartialCharacterSheetSync.syncCharacterSheet(testTime, syncAccount, syncUtil, mockServer);
    Assert.assertEquals(SyncStatus.DONE, syncOutcome);
    // Skip the verify here since the calls should never be made

    // Verify the following elements are unchanged:
    // CharacterSheet
    // Implants
    // Jump Clones
    // Jump Clone Implants
    CharacterSheet sheet = CharacterSheet.get(syncAccount, testTime);
    List<Implant> implants = Implant.getAll(syncAccount, testTime);
    List<JumpClone> clones = JumpClone.getAll(syncAccount, testTime);
    List<JumpCloneImplant> cloneImplants = JumpCloneImplant.getAll(syncAccount, testTime);
    CharacterSheetJump jump = CharacterSheetJump.get(syncAccount, testTime);
    CharacterSheetClone clone = CharacterSheetClone.get(syncAccount, testTime);
    Assert.assertNotNull(sheet);
    // Existing population should not be changed
    compareCharacterSheetWithTestData(sheet, true);
    compareImplantWithTestData(implants);
    compareJumpCloneWithTestData(clones);
    compareJumpCloneImplantWithTestData(cloneImplants);
    compareCharacterSheetJumpWithTestData(jump);
    compareCharacterSheetCloneWithTestData(clone);

    // Verify tracker and container unchanged
    Assert.assertEquals(prevDate, Capsuleer.getCapsuleer(syncAccount).getPartialCharacterSheetExpiry());
    Assert.assertEquals(SyncState.UPDATED, CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPartialCharacterSheetStatus());
    Assert.assertNull(CapsuleerSyncTracker.getUnfinishedTracker(syncAccount).getPartialCharacterSheetDetail());
  }

}
