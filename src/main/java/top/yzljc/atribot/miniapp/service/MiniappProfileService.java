package top.yzljc.atribot.miniapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atribot.platform.qq.QQBot;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 只读的个人档案聚合；所有查询均以已验证的官机用户 ID 为范围。
 *
 * @Author YZ_Ljc_
 * @ClassName MiniappProfileService
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public final class MiniappProfileService {
    private static final Logger log = LoggerFactory.getLogger(MiniappProfileService.class);
    private final Connections connections;
    private final Supplier<String> appId;
    private final Supplier<Bot> bot;

    @FunctionalInterface
    public interface Connections {
        Connection open() throws SQLException;
    }

    @FunctionalInterface
    private interface Read<T> {
        T map(ResultSet rows) throws SQLException;
    }

    public record Account(String uuid, String username, String createdAt, String minecraftUuid, String qqNumber) {
    }

    public record Messages(long count, String firstAt, String lastAt) {
    }

    public record SignIn(int days, String lastDate) {
    }

    public record Collection(long total, long kinds) {
    }

    public record Bot(String name, String avatarUrl) {
    }

    public record Profile(String userId, String username, String avatarUrl, Account account,
                          Messages privateMessages, Messages groupMessages, SignIn signIn,
                          Integer coins, Collection collection, List<String> unavailable, String updatedAt, Bot bot) {
    }

    public MiniappProfileService() {
        this(DatabaseManager::getConnection, () -> Config.getInstance().getQqAppId(), MiniappProfileService::currentBot);
    }

    public MiniappProfileService(Connections connections, Supplier<String> appId) {
        this(connections, appId, () -> new Bot("机器人", null));
    }

    public MiniappProfileService(Connections connections, Supplier<String> appId, Supplier<Bot> bot) {
        this.connections = connections;
        this.appId = appId;
        this.bot = bot;
    }

    public Profile load(MiniappSessions.Identity identity) {
        String userId = identity.userId();
        List<String> unavailable = new ArrayList<>();
        Account account = null;
        Messages privateMessages = null;
        Messages groupMessages = null;
        SignIn signIn = null;
        Integer coins = null;
        Collection collection = null;
        String username = null;
        try (Connection connection = connections.open()) {
            account = read(connection, "account", "SELECT uuid, username, create_time, minecraft_uuid, qq_user_uin FROM unified_account WHERE qq_user_open_id = ? LIMIT 1",
                    userId, rows -> rows.next() ? new Account(rows.getString("uuid"), clean(rows.getString("username")),
                            rows.getString("create_time"), clean(rows.getString("minecraft_uuid")), clean(rows.getString("qq_user_uin"))) : null, unavailable);
            if (account != null) username = account.username();
            if (username == null) {
                // The same recorded-name sources used by the bot's user management page.
                username = read(connection, "username", """
                        SELECT username FROM (
                          (SELECT username, created_at FROM official_c2c_record
                           WHERE union_openId = ? AND sender_is_bot = FALSE AND source <> 'BOT_SEND'
                             AND username IS NOT NULL AND TRIM(username) <> '' ORDER BY created_at DESC LIMIT 1)
                          UNION ALL
                          (SELECT username, created_at FROM official_group_record
                           WHERE union_openId = ? AND sender_is_bot = FALSE AND event_type <> 'BOT_SEND'
                             AND username IS NOT NULL AND TRIM(username) <> '' ORDER BY created_at DESC LIMIT 1)
                        ) names ORDER BY created_at DESC LIMIT 1
                        """, userId, rows -> rows.next() ? clean(rows.getString("username")) : null, unavailable);
            }
            privateMessages = read(connection, "privateMessages", """
                    SELECT COUNT(*) AS total, MIN(created_at) AS first_at, MAX(created_at) AS last_at
                    FROM official_c2c_record WHERE union_openId = ? AND sender_is_bot = FALSE AND source <> 'BOT_SEND'
                    """, userId, MiniappProfileService::messages, unavailable);
            groupMessages = read(connection, "groupMessages", """
                    SELECT COUNT(*) AS total, MIN(created_at) AS first_at, MAX(created_at) AS last_at
                    FROM official_group_record WHERE union_openId = ? AND sender_is_bot = FALSE AND event_type <> 'BOT_SEND'
                    """, userId, MiniappProfileService::messages, unavailable);
            signIn = read(connection, "signIn", "SELECT total_count, last_check_in_date FROM check_in_total WHERE user_open_id = ?",
                    userId, rows -> rows.next() ? new SignIn(rows.getInt("total_count"), rows.getString("last_check_in_date")) : null, unavailable);
            coins = read(connection, "coins", "SELECT coins FROM user_loots WHERE user_id = ?",
                    userId, rows -> rows.next() ? rows.getInt("coins") : null, unavailable);
            collection = read(connection, "collection", "SELECT COALESCE(SUM(`count`), 0) AS total, COUNT(*) AS kinds FROM user_loot_items WHERE user_id = ?",
                    userId, rows -> rows.next() ? new Collection(rows.getLong("total"), rows.getLong("kinds")) : null, unavailable);
        } catch (SQLException failure) {
            log.warn("Miniapp 档案数据库暂不可用", failure);
            unavailable.add("database");
        }
        // Do not invent a username from an OpenID or create a unified account just to show a page.
        return new Profile(userId, username, avatarUrl(appId.get(), userId), account, privateMessages, groupMessages,
                signIn, coins, collection, List.copyOf(unavailable), Instant.now().toString(), bot.get());
    }

    private static Bot currentBot() {
        String name = clean(QQBot.BOT_NAME);
        String avatar = clean(QQBot.BOT_AVATAR_URL);
        Config config = Config.getInstance();
        if (avatar == null && clean(config.getOfficialOpenId()) != null) {
            avatar = avatarUrl(config.getQqAppId(), config.getOfficialOpenId());
        }
        return new Bot(name == null ? "机器人" : name, avatar);
    }

    private static <T> T read(Connection connection, String section, String sql, String userId,
                              Read<T> reader, List<String> unavailable) {
        try (var statement = connection.prepareStatement(sql)) {
            // SQL templates are fixed; every parameter is the session identity, including the name UNION.
            int parameters = (int) sql.chars().filter(c -> c == '?').count();
            for (int i = 1; i <= parameters; i++) statement.setString(i, userId);
            statement.setQueryTimeout(5);
            try (ResultSet rows = statement.executeQuery()) {
                return reader.map(rows);
            }
        } catch (SQLException failure) {
            unavailable.add(section);
            log.warn("Miniapp 档案分区 {} 读取失败", section, failure);
            return null;
        }
    }

    private static Messages messages(ResultSet rows) throws SQLException {
        return rows.next() ? new Messages(rows.getLong("total"), rows.getString("first_at"), rows.getString("last_at")) : null;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String avatarUrl(String appId, String userId) {
        if (clean(appId) == null || "null".equalsIgnoreCase(appId)) return null;
        return "https://thirdqq.qlogo.cn/qqapp/" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                + "/" + URLEncoder.encode(userId, StandardCharsets.UTF_8) + "/100";
    }
}
