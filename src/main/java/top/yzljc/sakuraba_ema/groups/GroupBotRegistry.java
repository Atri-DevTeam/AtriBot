package top.yzljc.sakuraba_ema.groups;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.ChatService;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves an app-scoped group OpenID to the bot instance that received it.
 */
@Slf4j
public final class GroupBotRegistry {

    private static final ConcurrentMap<String, GroupBotClient> GROUP_CLIENTS = new ConcurrentHashMap<>();

    private GroupBotRegistry() {
    }

    static boolean remember(String groupOpenId, GroupBotClient client) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            return false;
        }
        GroupBotClient previous = GROUP_CLIENTS.put(groupOpenId, client);
        if (previous != null && previous != client) {
            log.warn("群 OpenID {} 的实例路由由 {} 切换为 {}", groupOpenId, previous.key(), client.key());
        }
        return previous != client;
    }

    static void remove(GroupBotClient client) {
        GROUP_CLIENTS.entrySet().removeIf(entry -> entry.getValue() == client);
    }

    public static Optional<GroupBotClient> find(String groupOpenId) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(GROUP_CLIENTS.get(groupOpenId));
    }

    public static Optional<ChatService> findChatService(String groupOpenId) {
        return find(groupOpenId).map(GroupBotClient::getChatService);
    }
}
