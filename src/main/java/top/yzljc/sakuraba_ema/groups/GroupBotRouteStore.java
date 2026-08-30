package top.yzljc.sakuraba_ema.groups;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Persists group-to-instance routes so proactive and delayed messages still
 * select the correct credentials after a process restart.
 */
@Slf4j
public final class GroupBotRouteStore {

    private static final String TABLE = "official_group_bot_routes";
    private static final ConcurrentMap<String, String> PENDING_ROUTES = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> PERSISTED_ROUTES = new ConcurrentHashMap<>();

    private static volatile boolean initialized;

    private GroupBotRouteStore() {
    }

    public static synchronized void initialize(List<GroupBotClient> clients) {
        if (initialized || clients == null || clients.isEmpty()) {
            return;
        }

        Map<String, GroupBotClient> clientsByKey = new HashMap<>();
        for (GroupBotClient client : clients) {
            clientsByKey.put(client.key(), client);
        }

        String createSql = "CREATE TABLE IF NOT EXISTS `" + TABLE + "` (" +
                "  `group_openid` VARCHAR(256) NOT NULL," +
                "  `bot_key` VARCHAR(64) NOT NULL," +
                "  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`group_openid`)," +
                "  KEY `idx_bot_key` (`bot_key`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        int loaded = 0;
        try (var connection = DatabaseManager.getConnection()) {
            try (var statement = connection.prepareStatement(createSql)) {
                statement.execute();
            }
            String loadSql = "SELECT `group_openid`, `bot_key` FROM `" + TABLE + "`";
            try (var statement = connection.prepareStatement(loadSql);
                 var result = statement.executeQuery()) {
                while (result.next()) {
                    String botKey = result.getString("bot_key");
                    GroupBotClient client = clientsByKey.get(botKey);
                    if (client == null) {
                        continue;
                    }
                    String groupOpenId = result.getString("group_openid");
                    PERSISTED_ROUTES.put(groupOpenId, botKey);
                    if (GroupBotRegistry.remember(groupOpenId, client)) {
                        loaded++;
                    }
                }
            }
            initialized = true;
        } catch (Exception e) {
            log.error("初始化 QQ 群聊 Bot 实例路由表失败", e);
            return;
        }

        Map<String, String> pending = Map.copyOf(PENDING_ROUTES);
        PENDING_ROUTES.clear();
        int restoredPending = 0;
        for (Map.Entry<String, String> entry : pending.entrySet()) {
            GroupBotClient client = clientsByKey.get(entry.getValue());
            if (client != null) {
                GroupBotRegistry.remember(entry.getKey(), client);
                if (upsert(entry.getKey(), entry.getValue())) {
                    PERSISTED_ROUTES.put(entry.getKey(), entry.getValue());
                    restoredPending++;
                }
            }
        }
        log.info("QQ 群聊 Bot 实例路由加载完成，共 {} 条", loaded + restoredPending);
    }

    static synchronized void remember(String groupOpenId, GroupBotClient client) {
        if (groupOpenId == null || groupOpenId.isBlank()) {
            return;
        }
        // Initialization may have loaded an older route while this event waited for the store lock.
        GroupBotRegistry.remember(groupOpenId, client);
        if (!initialized) {
            PENDING_ROUTES.put(groupOpenId, client.key());
            return;
        }
        if (client.key().equals(PERSISTED_ROUTES.get(groupOpenId))) {
            return;
        }
        if (upsert(groupOpenId, client.key())) {
            PERSISTED_ROUTES.put(groupOpenId, client.key());
        }
    }

    private static boolean upsert(String groupOpenId, String botKey) {
        String sql = "INSERT INTO `" + TABLE + "` (`group_openid`, `bot_key`) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE `bot_key` = VALUES(`bot_key`), `updated_at` = CURRENT_TIMESTAMP";
        try (var connection = DatabaseManager.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, groupOpenId);
            statement.setString(2, botKey);
            statement.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("保存 QQ 群聊 Bot 实例路由失败: groupOpenId={}, botKey={}", groupOpenId, botKey, e);
            return false;
        }
    }
}
