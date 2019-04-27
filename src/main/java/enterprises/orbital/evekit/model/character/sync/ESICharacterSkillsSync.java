package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.SkillsApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdAttributesOk;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdSkillsOk;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdSkillsSkill;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterSheetAttributes;
import enterprises.orbital.evekit.model.character.CharacterSheetSkillPoints;
import enterprises.orbital.evekit.model.character.CharacterSkill;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterSkillsSync extends AbstractESIAccountSync<ESICharacterSkillsSync.SkillData> {
  protected static final Logger log = Logger.getLogger(ESICharacterSkillsSync.class.getName());

  class SkillData {
    GetCharactersCharacterIdSkillsOk skillInfo;
    GetCharactersCharacterIdAttributesOk attributeInfo;
  }

  // Current ETag.  On successful commit, this will be copied to nextETag.
  private String currentETag;

  // ETag to save for next tracker.
  private String nextETag;

  @Override
  protected void commitComplete() {
    nextETag = currentETag;
  }

  @Override
  protected String getNextSyncContext() {
    return nextETag;
  }

  public ESICharacterSkillsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_SKILLS;
  }


  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof CharacterSheetAttributes) ||
        (item instanceof CharacterSheetSkillPoints) ||
        (item instanceof CharacterSkill);
    CachedData existing = null;
    if (item.getLifeStart() == 0) {
      // Only need to check for existing item if current item is an update
      if (item instanceof CharacterSheetAttributes)
        existing = CharacterSheetAttributes.get(account, time);
      else if (item instanceof CharacterSheetSkillPoints)
        existing = CharacterSheetSkillPoints.get(account, time);
      else
        existing = CharacterSkill.get(account, time, ((CharacterSkill) item).getTypeID());
    }
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<SkillData> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    SkillsApi apiInstance = cp.getSkillsApi();
    SkillData resultData = new SkillData();
    long expiry;

    // Retrieve skill info
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdSkillsOk> resultS = apiInstance.getCharactersCharacterIdSkillsWithHttpInfo(
        (int) account.getEveCharacterID(), null, currentETag, accessToken());
    checkCommonProblems(resultS);
    expiry = extractExpiry(resultS, OrbitalProperties.getCurrentTime() + maxDelay());
    resultData.skillInfo = resultS.getData();

    // Retrieve attribute info
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdAttributesOk> resultA = apiInstance.getCharactersCharacterIdAttributesWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken());
    checkCommonProblems(resultA);
    expiry = Math.max(expiry, extractExpiry(resultA, OrbitalProperties.getCurrentTime() + maxDelay()));
    resultData.attributeInfo = resultA.getData();

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<SkillData> data,
                                   List<CachedData> updates) throws IOException {

    // If we have tracker context, then it will be the hash of any previous call to this endpoint.
    // Check to see if the most recent data has a different hash.  If not, then results haven't changed
    // and we can skip this update.
    String[] cachedHash = splitCachedContext(3);

    CharacterSheetAttributes attributes = new CharacterSheetAttributes(data.getData().attributeInfo.getIntelligence(),
                                                                       data.getData().attributeInfo.getMemory(),
                                                                       data.getData().attributeInfo.getCharisma(),
                                                                       data.getData().attributeInfo.getPerception(),
                                                                       data.getData().attributeInfo.getWillpower(),
                                                                       nullSafeInteger(
                                                                           data.getData().attributeInfo.getBonusRemaps(),
                                                                           0),
                                                                       nullSafeDateTime(
                                                                           data.getData().attributeInfo.getLastRemapDate(),
                                                                           new DateTime(new Date(0))).getMillis(),
                                                                       nullSafeDateTime(
                                                                           data.getData().attributeInfo.getAccruedRemapCooldownDate(),
                                                                           new DateTime(new Date(0))).getMillis());
    String attributesHash = attributes.dataHash();
    CharacterSheetSkillPoints skillPoints = new CharacterSheetSkillPoints(data.getData().skillInfo.getTotalSp(),
                                                                          nullSafeInteger(data.getData().skillInfo.getUnallocatedSp(), 0));
    String skillPointsHash = skillPoints.dataHash();
    List<CharacterSkill> retrievedSkills = new ArrayList<>();
    for (GetCharactersCharacterIdSkillsSkill ns : data.getData().skillInfo.getSkills()) {
      retrievedSkills.add(new CharacterSkill(ns.getSkillId(),
                                     ns.getTrainedSkillLevel(),
                                     ns.getSkillpointsInSkill(),
                                     ns.getActiveSkillLevel()));
    }
    retrievedSkills.sort(Comparator.comparingInt(CharacterSkill::getTypeID));
    String skillsHash = CachedData.dataHashHelper(retrievedSkills.stream().map(CharacterSkill::dataHash).toArray());

    // Check hash for attributes
    if (cachedHash[0] == null || !cachedHash[0].equals(attributesHash)) {
      // New attributes, process
      cacheMiss();
      cachedHash[0] = attributesHash;
      updates.add(attributes);
    } else {
      cacheHit();
    }

    // Check hash for skill points
    if (cachedHash[1] == null || !cachedHash[1].equals(skillPointsHash)) {
      // New skill points, process
      cacheMiss();
      cachedHash[1] = skillPointsHash;
      updates.add(skillPoints);
    } else {
      cacheHit();
    }

    // Check hash for skills
    if (cachedHash[2] == null || !cachedHash[2].equals(skillsHash)) {
      // New skills, process
      cacheMiss();
      cachedHash[2] = skillsHash;
      updates.addAll(retrievedSkills);
    } else {
      cacheHit();
    }

    // Save hashes for next execution
    currentETag = String.join("|", cachedHash);
  }


}
