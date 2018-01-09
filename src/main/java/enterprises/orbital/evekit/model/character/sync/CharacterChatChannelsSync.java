package enterprises.orbital.evekit.model.character.sync;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import enterprises.orbital.evekit.account.SynchronizedEveAccount;
import enterprises.orbital.evekit.model.CachedData;
import enterprises.orbital.evekit.model.CapsuleerSyncTracker;
import enterprises.orbital.evekit.model.ModelUtil;
import enterprises.orbital.evekit.model.SyncTracker;
import enterprises.orbital.evekit.model.SyncTracker.SyncState;
import enterprises.orbital.evekit.model.SynchronizerUtil;
import enterprises.orbital.evekit.model.SynchronizerUtil.SyncStatus;
import enterprises.orbital.evekit.model.character.Capsuleer;
import enterprises.orbital.evekit.model.character.ChatChannel;
import enterprises.orbital.evekit.model.character.ChatChannelMember;
import enterprises.orbital.evexmlapi.chr.ICharacterAPI;
import enterprises.orbital.evexmlapi.chr.IChatChannel;
import enterprises.orbital.evexmlapi.chr.IChatChannelMember;

public class CharacterChatChannelsSync extends AbstractCharacterSync {

  protected static final Logger log = Logger.getLogger(CharacterChatChannelsSync.class.getName());

  @Override
  public boolean isRefreshed(
                             CapsuleerSyncTracker tracker) {
    return tracker.getChatChannelsStatus() != SyncTracker.SyncState.NOT_PROCESSED;
  }

  @Override
  public void updateStatus(
                           CapsuleerSyncTracker tracker,
                           SyncState status,
                           String detail) {
    tracker.setChatChannelsStatus(status);
    tracker.setChatChannelsDetail(detail);
    CapsuleerSyncTracker.updateTracker(tracker);
  }

  @Override
  public void updateExpiry(
                           Capsuleer container,
                           long expiry) throws IOException {
    container.setChatChannelsExpiry(expiry);
    CachedData.update(container);
  }

  @Override
  public long getExpiryTime(
                            Capsuleer container) {
    return container.getChatChannelsExpiry();
  }

  @Override
  public boolean commit(
                        long time,
                        CapsuleerSyncTracker tracker,
                        Capsuleer container,
                        SynchronizedEveAccount accountKey,
                        CachedData item) throws IOException {
    // Handle both chat channels and chat channel members
    if (item instanceof ChatChannel) {
      ChatChannel api = (ChatChannel) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing ChatChannel to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        ChatChannel existing = ChatChannel.get(accountKey, time, api.getChannelID());
        if (existing != null) {
          // Existing, evolve if changed
          if (!existing.equivalent(api)) {
            // Evolve
            existing.evolve(api, time);
            super.commit(time, tracker, container, accountKey, existing);
            super.commit(time, tracker, container, accountKey, api);
          }
        } else {
          // New entity
          api.setup(accountKey, time);
          super.commit(time, tracker, container, accountKey, api);
        }
      }

    } else if (item instanceof ChatChannelMember) {
      ChatChannelMember api = (ChatChannelMember) item;

      if (api.getLifeStart() != 0) {
        // Special case. This is an existing ChatChannelMember to be EOL
        super.commit(time, tracker, container, accountKey, api);
      } else {
        ChatChannelMember existing = ChatChannelMember.get(accountKey, time, api.getChannelID(), api.getCategory(), api.getAccessorID());
        if (existing != null) {
          // Existing, evolve if changed
          if (!existing.equivalent(api)) {
            // Evolve
            existing.evolve(api, time);
            super.commit(time, tracker, container, accountKey, existing);
            super.commit(time, tracker, container, accountKey, api);
          }
        } else {
          // New entity
          api.setup(accountKey, time);
          super.commit(time, tracker, container, accountKey, api);
        }
      }

    } else {
      // This should never happen!
      assert false;
    }

    return true;
  }

  @Override
  protected Object getServerData(
                                 ICharacterAPI charRequest) throws IOException {
    return charRequest.requestChatChannels();
  }

  protected void updateChannelMemberMap(
                                        Map<Long, Map<String, Set<Long>>> map,
                                        long channelID,
                                        String category,
                                        long accessorID) {
    Map<String, Set<Long>> byCat = map.get(channelID);
    if (byCat == null) {
      byCat = new HashMap<String, Set<Long>>();
      map.put(channelID, byCat);
    }
    Set<Long> members = byCat.get(category);
    if (members == null) {
      members = new HashSet<Long>();
      byCat.put(category, members);
    }
    members.add(accessorID);
  }

  @Override
  protected long processServerData(
                                   long time,
                                   SynchronizedEveAccount syncAccount,
                                   ICharacterAPI charRequest,
                                   Object data,
                                   List<CachedData> updates) throws IOException {
    @SuppressWarnings("unchecked")
    Collection<IChatChannel> channels = (Collection<IChatChannel>) data;
    Set<Long> channelSet = new HashSet<Long>();
    Map<Long, Map<String, Set<Long>>> channelMemberMap = new HashMap<Long, Map<String, Set<Long>>>();
    for (IChatChannel nextChannel : channels) {
      ChatChannel cc = new ChatChannel(
          nextChannel.getChannelID(), nextChannel.getOwnerID(), nextChannel.getOwnerName(), nextChannel.getDisplayName(), nextChannel.getComparisonKey(),
          nextChannel.hasPassword(), nextChannel.getMOTD());
      updates.add(cc);
      channelSet.add(nextChannel.getChannelID());
      // Process channel member sets
      for (IChatChannelMember nextMember : nextChannel.getAllowed()) {
        ChatChannelMember cm = new ChatChannelMember(
            nextChannel.getChannelID(), "allowed", nextMember.getAccessorID(), nextMember.getAccessorName(),
            ModelUtil.safeConvertDate(nextMember.getUntilWhen()), nextMember.getReason());
        updates.add(cm);
        updateChannelMemberMap(channelMemberMap, nextChannel.getChannelID(), "allowed", nextMember.getAccessorID());
      }
      for (IChatChannelMember nextMember : nextChannel.getBlocked()) {
        ChatChannelMember cm = new ChatChannelMember(
            nextChannel.getChannelID(), "blocked", nextMember.getAccessorID(), nextMember.getAccessorName(),
            ModelUtil.safeConvertDate(nextMember.getUntilWhen()), nextMember.getReason());
        updates.add(cm);
        updateChannelMemberMap(channelMemberMap, nextChannel.getChannelID(), "blocked", nextMember.getAccessorID());
      }
      for (IChatChannelMember nextMember : nextChannel.getMuted()) {
        ChatChannelMember cm = new ChatChannelMember(
            nextChannel.getChannelID(), "muted", nextMember.getAccessorID(), nextMember.getAccessorName(), ModelUtil.safeConvertDate(nextMember.getUntilWhen()),
            nextMember.getReason());
        updates.add(cm);
        updateChannelMemberMap(channelMemberMap, nextChannel.getChannelID(), "muted", nextMember.getAccessorID());
      }
      for (IChatChannelMember nextMember : nextChannel.getOperators()) {
        ChatChannelMember cm = new ChatChannelMember(
            nextChannel.getChannelID(), "operators", nextMember.getAccessorID(), nextMember.getAccessorName(),
            ModelUtil.safeConvertDate(nextMember.getUntilWhen()), nextMember.getReason());
        updates.add(cm);
        updateChannelMemberMap(channelMemberMap, nextChannel.getChannelID(), "operators", nextMember.getAccessorID());
      }
    }
    // Find and EOL channels no longer in the list.
    for (ChatChannel next : ChatChannel.getAllChatChannels(syncAccount, time)) {
      long channelID = next.getChannelID();
      if (!channelSet.contains(channelID)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }
    // Find and EOL channel members no longer in the list.
    for (ChatChannelMember next : ChatChannelMember.getAllChatChannelMembers(syncAccount, time)) {
      long channelID = next.getChannelID();
      String category = next.getCategory();
      long accessorID = next.getAccessorID();
      if (!channelMemberMap.containsKey(channelID) || !channelMemberMap.get(channelID).containsKey(category)
          || !channelMemberMap.get(channelID).get(category).contains(accessorID)) {
        next.evolve(null, time);
        updates.add(next);
      }
    }
    return charRequest.getCachedUntil().getTime();
  }

  private static final CharacterChatChannelsSync syncher = new CharacterChatChannelsSync();

  public static SyncStatus syncChatChannels(
                                            long time,
                                            SynchronizedEveAccount syncAccount,
                                            SynchronizerUtil syncUtil,
                                            ICharacterAPI charRequest) {
    return syncher.syncData(time, syncAccount, syncUtil, charRequest, "ChatChannels");

  }

  public static SyncStatus exclude(
                                   SynchronizedEveAccount syncAccount,
                                   SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "ChatChannels", SyncTracker.SyncState.SYNC_ERROR);
  }

  public static SyncStatus notAllowed(
                                      SynchronizedEveAccount syncAccount,
                                      SynchronizerUtil syncUtil) {
    return syncher.excludeState(syncAccount, syncUtil, "ChatChannels", SyncTracker.SyncState.NOT_ALLOWED);
  }

}
