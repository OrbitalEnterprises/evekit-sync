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
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class ESICharacterSkillsSync extends AbstractESIAccountSync<ESICharacterSkillsSync.SkillData> {
  protected static final Logger log = Logger.getLogger(ESICharacterSkillsSync.class.getName());

  class SkillData {
    GetCharactersCharacterIdSkillsOk skillInfo;
    GetCharactersCharacterIdAttributesOk attributeInfo;
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
        (int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(resultS);
    expiry = extractExpiry(resultS, OrbitalProperties.getCurrentTime() + maxDelay());
    resultData.skillInfo = resultS.getData();

    // Retrieve attribute info
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<GetCharactersCharacterIdAttributesOk> resultA = apiInstance.getCharactersCharacterIdAttributesWithHttpInfo(
        (int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(resultA);
    expiry = Math.max(expiry, extractExpiry(resultA, OrbitalProperties.getCurrentTime() + maxDelay()));
    resultData.attributeInfo = resultA.getData();

    return new ESIAccountServerResult<>(expiry, resultData);
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<SkillData> data,
                                   List<CachedData> updates) throws IOException {

    // Add attributes and skill points
    updates.add(new CharacterSheetAttributes(data.getData().attributeInfo.getIntelligence(),
                                             data.getData().attributeInfo.getMemory(),
                                             data.getData().attributeInfo.getCharisma(),
                                             data.getData().attributeInfo.getPerception(),
                                             data.getData().attributeInfo.getWillpower(),
                                             nullSafeInteger(data.getData().attributeInfo.getBonusRemaps(), 0),
                                             nullSafeDateTime(data.getData().attributeInfo.getLastRemapDate(),
                                                              new DateTime(new Date(0))).getMillis(),
                                             nullSafeDateTime(
                                                 data.getData().attributeInfo.getAccruedRemapCooldownDate(),
                                                 new DateTime(new Date(0))).getMillis()));
    updates.add(new CharacterSheetSkillPoints(data.getData().skillInfo.getTotalSp(),
                                              nullSafeInteger(data.getData().skillInfo.getUnallocatedSp(), 0)));

    // Add skills
    for (GetCharactersCharacterIdSkillsSkill ns : data.getData().skillInfo.getSkills()) {
      updates.add(new CharacterSkill(ns.getSkillId(),
                                     ns.getTrainedSkillLevel(),
                                     ns.getSkillpointsInSkill(),
                                     ns.getActiveSkillLevel()));
    }
  }

}
