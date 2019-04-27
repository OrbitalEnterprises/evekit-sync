package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.SkillsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdAttributesOk;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdSkillsOk;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdSkillsSkill;
import enterprises.orbital.evekit.TestBase;
import enterprises.orbital.evekit.account.EveKitUserAccountProvider;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheet;
import enterprises.orbital.evekit.model.character.CharacterSheetAttributes;
import enterprises.orbital.evekit.model.character.CharacterSheetSkillPoints;
import enterprises.orbital.evekit.model.character.CharacterSkill;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("Duplicates")
public class ESICharacterSkillsSyncTest extends SyncTestBase {

  // Local mocks and other objects
  private ESIAccountClientProvider mockServer;
  private SkillsApi mockEndpoint;
  private long testTime = 1238L;
  private GetCharactersCharacterIdAttributesOk testAttributes;
  private GetCharactersCharacterIdSkillsOk testSkills;

  private static Object[][] skillTestData;

  static {
    // Skill test data
    // 0 int typeID;
    // 1 int trainedSkillLevel;
    // 2 long skillpoints;
    // 3 int activeSkillLevel;
    int size = 50 + TestBase.getRandomInt(50);
    skillTestData = new Object[size][4];
    for (int i = 0; i < size; i++) {
      skillTestData[i][0] = TestBase.getUniqueRandomInteger();
      skillTestData[i][1] = TestBase.getRandomInt(5);
      skillTestData[i][2] = TestBase.getRandomLong();
      skillTestData[i][3] = TestBase.getRandomInt(5);
    }
  }

  @Override
  @Before
  public void setup() throws Exception {
    super.setup();

    // Prepare a test sync tracker
    ESIEndpointSyncTracker.getOrCreateUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SKILLS, 1234L, null);

    // Initialize time keeper
    OrbitalProperties.setTimeGenerator(() -> testTime);
  }

  @Override
  @After
  public void teardown() throws Exception {
    // Cleanup test specific tables after each test
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CharacterSheetAttributes ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CharacterSheetSkillPoints ")
                                                        .executeUpdate();
                             });
    EveKitUserAccountProvider.getFactory()
                             .runTransaction(() -> {
                               EveKitUserAccountProvider.getFactory()
                                                        .getEntityManager()
                                                        .createQuery("DELETE FROM CharacterSkill ")
                                                        .executeUpdate();
                             });
    OrbitalProperties.setTimeGenerator(null);
    super.teardown();
  }

  // Mock up server interface
  private void setupOkMock() throws Exception {
    mockEndpoint = EasyMock.createMock(SkillsApi.class);

    // Set up attributes
    testAttributes = new GetCharactersCharacterIdAttributesOk();
    testAttributes.setIntelligence(TestBase.getRandomInt());
    testAttributes.setMemory(TestBase.getRandomInt());
    testAttributes.setCharisma(TestBase.getRandomInt());
    testAttributes.setPerception(TestBase.getRandomInt());
    testAttributes.setWillpower(TestBase.getRandomInt());
    testAttributes.setBonusRemaps(TestBase.getRandomInt());
    testAttributes.setLastRemapDate(new DateTime(new Date(TestBase.getRandomLong())));
    testAttributes.setAccruedRemapCooldownDate(new DateTime(new Date(TestBase.getRandomLong())));

    // Set up skills page
    List<GetCharactersCharacterIdSkillsSkill> skillList = Arrays.stream(skillTestData)
                                                                .
                                                                    map(x -> {
                                                                      GetCharactersCharacterIdSkillsSkill nextSkill = new GetCharactersCharacterIdSkillsSkill();
                                                                      nextSkill.setSkillId((int) x[0]);
                                                                      nextSkill.setTrainedSkillLevel((int) x[1]);
                                                                      nextSkill.setSkillpointsInSkill((long) x[2]);
                                                                      nextSkill.setActiveSkillLevel((int) x[3]);
                                                                      return nextSkill;
                                                                    })
                                                                .collect(Collectors.toList());
    testSkills = new GetCharactersCharacterIdSkillsOk();
    testSkills.setSkills(skillList);
    testSkills.setTotalSp(TestBase.getRandomLong());
    testSkills.setUnallocatedSp(TestBase.getRandomInt());

    // Setup asset retrieval mock calls
    Map<String, List<String>> headers = createHeaders("Expires", "Thu, 21 Dec 2017 12:00:00 GMT");
    {
      ApiResponse<GetCharactersCharacterIdAttributesOk> apir = new ApiResponse<>(200, headers, testAttributes);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdAttributesWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }
    {
      ApiResponse<GetCharactersCharacterIdSkillsOk> apir = new ApiResponse<>(200, headers, testSkills);
      EasyMock.expect(mockEndpoint.getCharactersCharacterIdSkillsWithHttpInfo(
          EasyMock.eq((int) charSyncAccount.getEveCharacterID()),
          EasyMock.isNull(),
          EasyMock.isNull(),
          EasyMock.anyString()))
              .andReturn(apir);
    }
    // Setup server mock
    mockServer = EasyMock.createMock(ESIAccountClientProvider.class);
    EasyMock.expect(mockServer.getSkillsApi())
            .andReturn(mockEndpoint);
  }

  private void verifyDataUpdate() throws Exception {
    // Check attributes
    CharacterSheetAttributes storedAttr = CharacterSheetAttributes.get(charSyncAccount, testTime);
    Assert.assertNotNull(storedAttr);
    Assert.assertEquals((int) testAttributes.getIntelligence(), storedAttr.getIntelligence());
    Assert.assertEquals((int) testAttributes.getMemory(), storedAttr.getMemory());
    Assert.assertEquals((int) testAttributes.getCharisma(), storedAttr.getCharisma());
    Assert.assertEquals((int) testAttributes.getPerception(), storedAttr.getPerception());
    Assert.assertEquals((int) testAttributes.getWillpower(), storedAttr.getWillpower());
    Assert.assertEquals((int) testAttributes.getBonusRemaps(), storedAttr.getBonusRemaps());
    Assert.assertEquals(testAttributes.getLastRemapDate()
                                      .getMillis(), storedAttr.getLastRemapDate());
    Assert.assertEquals(testAttributes.getAccruedRemapCooldownDate()
                                      .getMillis(), storedAttr.getAccruedRemapCooldownDate());

    // Check skill points
    CharacterSheetSkillPoints storedSP = CharacterSheetSkillPoints.get(charSyncAccount, testTime);
    Assert.assertEquals((long) testSkills.getTotalSp(), storedSP.getTotalSkillPoints());
    Assert.assertEquals((int) testSkills.getUnallocatedSp(), storedSP.getUnallocatedSkillPoints());

    // Retrieve all stored data
    List<CharacterSkill> storedData = AbstractESIAccountSync.retrieveAll(testTime,
                                                                         (long contid, AttributeSelector at) ->
                                                                             CharacterSkill.accessQuery(charSyncAccount,
                                                                                                        contid, 1000,
                                                                                                        false, at,
                                                                                                        AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                        AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                        AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                        AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(skillTestData.length, storedData.size());

    // Check stored data
    for (int i = 0; i < skillTestData.length; i++) {
      int typeID = (int) skillTestData[i][0];
      CharacterSkill nextEl = null;
      for (CharacterSkill j : storedData) {
        if (j.getTypeID() == typeID) {
          nextEl = j;
          break;
        }
      }
      Assert.assertNotNull(nextEl);
      Assert.assertEquals((int) skillTestData[i][0], nextEl.getTypeID());
      Assert.assertEquals((int) skillTestData[i][1], nextEl.getTrainedSkillLevel());
      Assert.assertEquals((long) skillTestData[i][2], nextEl.getSkillpoints());
      Assert.assertEquals((int) skillTestData[i][3], nextEl.getActiveSkillLevel());
    }
  }

  @Test
  public void testSyncUpdate() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Perform the sync
    ESICharacterSkillsSync sync = new ESICharacterSkillsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify updated properly
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SKILLS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SKILLS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

  @Test
  public void testSyncUpdateExisting() throws Exception {
    setupOkMock();
    EasyMock.replay(mockServer, mockEndpoint);

    // Populate existing
    //
    // This update doesn't delete any data so we only need to test modification.
    //
    CharacterSheetAttributes storedAttr = new CharacterSheetAttributes(testAttributes.getIntelligence() + 1,
                                                                       testAttributes.getMemory() + 1,
                                                                       testAttributes.getCharisma() + 1,
                                                                       testAttributes.getPerception() + 1,
                                                                       testAttributes.getWillpower() + 1,
                                                                       testAttributes.getBonusRemaps() + 1,
                                                                       testAttributes.getLastRemapDate()
                                                                                     .getMillis() + 1,
                                                                       testAttributes.getAccruedRemapCooldownDate()
                                                                                     .getMillis() + 1);
    storedAttr.setup(charSyncAccount, testTime - 1);
    CachedData.update(storedAttr);

    CharacterSheetSkillPoints storedSP = new CharacterSheetSkillPoints(testSkills.getTotalSp() + 1,
                                                                       testSkills.getUnallocatedSp() + 1);
    storedSP.setup(charSyncAccount, testTime - 1);
    CachedData.update(storedSP);

    for (Object[] sk : skillTestData) {
      CharacterSkill storedSkill = new CharacterSkill((int) sk[0],
                                                      (int) sk[1] + 1,
                                                      (long) sk[2] + 1,
                                                      (int) sk[3] + 1);
      storedSkill.setup(charSyncAccount, testTime - 1);
      CachedData.update(storedSkill);
    }

    // Perform the sync
    ESICharacterSkillsSync sync = new ESICharacterSkillsSync(charSyncAccount);
    sync.synch(mockServer);
    EasyMock.verify(mockServer, mockEndpoint);

    // Verify old objects were evolved properly
    CharacterSheetAttributes oldAttr = CharacterSheetAttributes.get(charSyncAccount, testTime - 1);
    Assert.assertEquals(testAttributes.getIntelligence() + 1, oldAttr.getIntelligence());
    Assert.assertEquals(testAttributes.getMemory() + 1, oldAttr.getMemory());
    Assert.assertEquals(testAttributes.getCharisma() + 1, oldAttr.getCharisma());
    Assert.assertEquals(testAttributes.getPerception() + 1, oldAttr.getPerception());
    Assert.assertEquals(testAttributes.getWillpower() + 1, oldAttr.getWillpower());
    Assert.assertEquals(testAttributes.getBonusRemaps() + 1, oldAttr.getBonusRemaps());
    Assert.assertEquals(testAttributes.getLastRemapDate()
                                      .getMillis() + 1, oldAttr.getLastRemapDate());
    Assert.assertEquals(testAttributes.getAccruedRemapCooldownDate()
                                      .getMillis() + 1, oldAttr.getAccruedRemapCooldownDate());

    CharacterSheetSkillPoints oldSP = CharacterSheetSkillPoints.get(charSyncAccount, testTime - 1);
    Assert.assertEquals(testSkills.getTotalSp() + 1, oldSP.getTotalSkillPoints());
    Assert.assertEquals(testSkills.getUnallocatedSp() + 1, oldSP.getUnallocatedSkillPoints());

    List<CharacterSkill> oldEls = AbstractESIAccountSync.retrieveAll(testTime - 1,
                                                                     (long contid, AttributeSelector at) ->
                                                                         CharacterSkill.accessQuery(charSyncAccount,
                                                                                                    contid, 1000, false,
                                                                                                    at,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR,
                                                                                                    AbstractESIAccountSync.ANY_SELECTOR));

    // Check data matches test data
    Assert.assertEquals(skillTestData.length, oldEls.size());

    // Check old data
    for (int i = 0; i < skillTestData.length; i++) {
      CharacterSkill nextEl = oldEls.get(i);
      Assert.assertEquals(testTime, nextEl.getLifeEnd());
      Assert.assertEquals((int) skillTestData[i][0], nextEl.getTypeID());
      Assert.assertEquals((int) skillTestData[i][1] + 1, nextEl.getTrainedSkillLevel());
      Assert.assertEquals((long) skillTestData[i][2] + 1, nextEl.getSkillpoints());
      Assert.assertEquals((int) skillTestData[i][3] + 1, nextEl.getActiveSkillLevel());
    }

    // Verify updates which will also verify that all old alliances were properly end of life
    verifyDataUpdate();

    // Verify tracker was updated properly
    ESIEndpointSyncTracker syncTracker = ESIEndpointSyncTracker.getLatestFinishedTracker(charSyncAccount,
                                                                                         ESISyncEndpoint.CHAR_SKILLS);
    Assert.assertEquals(1234L, syncTracker.getScheduled());
    Assert.assertEquals(testTime, syncTracker.getSyncStart());
    Assert.assertEquals(ESISyncState.FINISHED, syncTracker.getStatus());
    Assert.assertEquals("Updated successfully", syncTracker.getDetail());
    Assert.assertEquals(testTime, syncTracker.getSyncEnd());

    // Verify new tracker was created with next sync time
    syncTracker = ESIEndpointSyncTracker.getUnfinishedTracker(charSyncAccount, ESISyncEndpoint.CHAR_SKILLS);
    long schedTime = (new DateTime(2017, 12, 21, 12, 0, 0, DateTimeZone.UTC)).getMillis();
    Assert.assertEquals(schedTime, syncTracker.getScheduled());
  }

}
