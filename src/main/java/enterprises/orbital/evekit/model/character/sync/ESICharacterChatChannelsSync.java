package enterprises.orbital.evekit.model.character.sync;

import enterprises.orbital.base.OrbitalProperties;
import enterprises.orbital.eve.esi.client.api.CharacterApi;
import enterprises.orbital.eve.esi.client.invoker.ApiException;
import enterprises.orbital.eve.esi.client.invoker.ApiResponse;
import enterprises.orbital.eve.esi.client.model.*;
import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.*;
import enterprises.orbital.evekit.model.character.ChatChannel;
import enterprises.orbital.evekit.model.character.ChatChannelMember;
import org.apache.commons.lang3.tuple.Triple;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ESICharacterChatChannelsSync extends AbstractESIAccountSync<List<GetCharactersCharacterIdChatChannels200Ok>> {
  protected static final Logger log = Logger.getLogger(ESICharacterChatChannelsSync.class.getName());

  public ESICharacterChatChannelsSync(SynchronizedEveAccount account) {
    super(account);
  }

  @Override
  public ESISyncEndpoint endpoint() {
    return ESISyncEndpoint.CHAR_CHANNELS;
  }

  @Override
  protected void commit(long time,
                        CachedData item) throws IOException {
    assert (item instanceof ChatChannel) || (item instanceof ChatChannelMember);

    CachedData existing;
    if (item instanceof ChatChannel) {
      existing = ChatChannel.get(account, time, ((ChatChannel) item).getChannelID());
    } else {
      ChatChannelMember mem = (ChatChannelMember) item;
      existing = ChatChannelMember.get(account, time, mem.getChannelID(), mem.getCategory(), mem.getAccessorID());
    }

    evolveOrAdd(time, existing, item);
  }

  @Override
  protected ESIAccountServerResult<List<GetCharactersCharacterIdChatChannels200Ok>> getServerData(
      ESIAccountClientProvider cp) throws ApiException, IOException {
    CharacterApi apiInstance = cp.getCharacterApi();
    ESIThrottle.throttle(endpoint().name(), account);
    ApiResponse<List<GetCharactersCharacterIdChatChannels200Ok>> result = apiInstance.getCharactersCharacterIdChatChannelsWithHttpInfo(
        (int) account.getEveCharacterID(), null, accessToken(), null, null);
    checkCommonProblems(result);
    return new ESIAccountServerResult<>(extractExpiry(result, OrbitalProperties.getCurrentTime() + maxDelay()),
                                        result.getData());
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void processServerData(long time,
                                   ESIAccountServerResult<List<GetCharactersCharacterIdChatChannels200Ok>> data,
                                   List<CachedData> updates) throws IOException {
    Set<Integer> seenChannels = new HashSet<>();
    Set<Triple<Integer, String, Integer>> seenMembers = new HashSet<>();
    for (GetCharactersCharacterIdChatChannels200Ok next : data.getData()) {
      updates.add(new ChatChannel(next.getChannelId(),
                                  next.getOwnerId(),
                                  next.getName(),
                                  next.getComparisonKey(),
                                  next.getHasPassword(),
                                  next.getMotd()));
      seenChannels.add(next.getChannelId());

      // Allowed, Operators, Blocked, Muted
      for (GetCharactersCharacterIdChatChannelsAllowed mem : next.getAllowed()) {
        updates.add(new ChatChannelMember(next.getChannelId(),
                                          ChatChannelMember.CAT_ALLOWED,
                                          mem.getAccessorId(),
                                          mem.getAccessorType()
                                             .toString(),
                                          0L,
                                          null));
        seenMembers.add(Triple.of(next.getChannelId(), ChatChannelMember.CAT_ALLOWED, mem.getAccessorId()));
      }

      for (GetCharactersCharacterIdChatChannelsOperator mem : next.getOperators()) {
        updates.add(new ChatChannelMember(next.getChannelId(),
                                          ChatChannelMember.CAT_OPERATOR,
                                          mem.getAccessorId(),
                                          mem.getAccessorType()
                                             .toString(),
                                          0L,
                                          null));
        seenMembers.add(Triple.of(next.getChannelId(), ChatChannelMember.CAT_OPERATOR, mem.getAccessorId()));
      }

      for (GetCharactersCharacterIdChatChannelsBlocked mem : next.getBlocked()) {
        updates.add(new ChatChannelMember(next.getChannelId(),
                                          ChatChannelMember.CAT_BLOCKED,
                                          mem.getAccessorId(),
                                          mem.getAccessorType()
                                             .toString(),
                                          nullSafeDateTime(mem.getEndAt(), new DateTime(new Date(0))).getMillis(),
                                          mem.getReason()));
        seenMembers.add(Triple.of(next.getChannelId(), ChatChannelMember.CAT_BLOCKED, mem.getAccessorId()));
      }

      for (GetCharactersCharacterIdChatChannelsMuted mem : next.getMuted()) {
        updates.add(new ChatChannelMember(next.getChannelId(),
                                          ChatChannelMember.CAT_MUTED,
                                          mem.getAccessorId(),
                                          mem.getAccessorType()
                                             .toString(),
                                          nullSafeDateTime(mem.getEndAt(), new DateTime(new Date(0))).getMillis(),
                                          mem.getReason()));
        seenMembers.add(Triple.of(next.getChannelId(), ChatChannelMember.CAT_MUTED, mem.getAccessorId()));
      }
    }

    // Check for deleted channels
    for (ChatChannel stored : CachedData.retrieveAll(time,
                                                     (contid, at) -> ChatChannel.accessQuery(account, contid, 1000,
                                                                                             false, at,
                                                                                             AttributeSelector.any(),
                                                                                             AttributeSelector.any(),
                                                                                             AttributeSelector.any(),
                                                                                             AttributeSelector.any(),
                                                                                             AttributeSelector.any(),
                                                                                             AttributeSelector.any()))) {
      if (!seenChannels.contains(stored.getChannelID())) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

    // Check for deleted members
    for (ChatChannelMember stored : CachedData.retrieveAll(time, (contid, at) -> ChatChannelMember.accessQuery(account,
                                                                                                               contid,
                                                                                                               1000,
                                                                                                               false,
                                                                                                               at,
                                                                                                               AttributeSelector.any(),
                                                                                                               AttributeSelector.any(),
                                                                                                               AttributeSelector.any(),
                                                                                                               AttributeSelector.any(),
                                                                                                               AttributeSelector.any(),
                                                                                                               AttributeSelector.any()))) {
      if (!seenMembers.contains(Triple.of(stored.getChannelID(), stored.getCategory(), stored.getAccessorID()))) {
        stored.evolve(null, time);
        updates.add(stored);
      }
    }

  }

}
