package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.GetCharactersCharacterIdTitles200Ok;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.CharacterTitle;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterTitlesSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdTitles200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterTitlesSync.class.getName());

  public ESICharacterTitlesSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_TITLES;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert item instanceof CharacterTitle;
    CachedData existing = CharacterTitle.get(account, time, ((CharacterTitle) item).getTitleID());
    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdTitles200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdTitles200Ok>> result = apiInstance.getCharactersCharacterIdTitlesWithHttpInfo(
        (int) account.getEveCharacterID(), null, null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time, ESIAccountServerResult<List<GetCharactersCharacterIdTitles200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    Set<Integer> seenTitles = new HashSet<>();
    for (GetCharactersCharacterIdTitles200Ok next : data.getData()) {
      updates.add(new CharacterTitle(nullSafeInteger(next.getTitleId(), 0),
                                     next.getName()));
      seenTitles.add(nullSafeInteger(next.getTitleId(), 0));
    }
    for (CharacterTitle stored : CachedData.retrieveAll(time,
                                                        (contid, at) -> CharacterTitle.accessQuery(account, contid,
                                                                                                   1000, false, at,
                                                                                                   AttributeSelector.any(),
                                                                                                   AttributeSelector.any()))) {
      if (!seenTitles.contains(stored.getTitleID())) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

  }

}
