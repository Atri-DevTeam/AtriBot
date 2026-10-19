package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName BotUserDataRepository
 * @Created_at 2026/10/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public final class BotUserDataRepository {
    private static final ObjectMapper JSON = new ObjectMapper();

    private BotUserDataRepository() {
    }

    public enum SettingWriteResult {SAVED, ALREADY_EXISTS, UID_IN_USE, FAILED}

    public static void init() {
        try (var con = DatabaseManager.getConnection()) {
            init(con);
        } catch (SQLException e) {
            throw new IllegalStateException("机器人用户数据表初始化或迁移失败", e);
        }
    }

    /**
     * 初始化用户业务数据表，迁入旧配置和游戏数据，校验成功后删除旧存储
     *
     * @param con 启动阶段的自动提交连接
     * @throws SQLException 初始化、数据校验或清理旧存储失败
     */
    public static void init(Connection con) throws SQLException {
        try (var statement = con.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bot_userdata (
                      user_id VARCHAR(256) COLLATE utf8mb4_bin NOT NULL,
                      user_settings JSON NULL,
                      game_data JSON NULL,
                      functions JSON NULL,
                      PRIMARY KEY (user_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
//        migrateLegacyData(con);
    }

//    static void migrateLegacyData(Connection con) throws SQLException {
//        var sources = new ArrayList<LegacySource>();
//        Set<String> official = columns(con, "official_users");
//        for (String field : List.of("user_settings", "game_data")) {
//            if (official.contains(field)) sources.add(new LegacySource("official_users", "user_openId", field, false));
//        }
//        if (columns(con, "user_game_data").contains("game_data"))
//            sources.add(new LegacySource("user_game_data", "user_id", "game_data", true));
//        if (columns(con, "c2c_function_list").contains("functions"))
//            sources.add(new LegacySource("c2c_function_list", "user_openId", "functions", true));
//        if (sources.isEmpty()) return;
//        if (!con.getAutoCommit()) throw new SQLException("用户业务数据迁移需要独立的自动提交连接");
//        con.setAutoCommit(false);
//        try {
//            for (var source : sources) migrateSource(con, source);
//            con.commit();
//        } catch (SQLException | RuntimeException e) {
//            try { con.rollback(); }
//            catch (SQLException rollback) { e.addSuppressed(rollback); }
//            throw e;
//        } finally {
//            con.setAutoCommit(true);
//        }
//        try (var statement = con.createStatement()) {
//            for (var source : sources) {
//                statement.executeUpdate(source.dropTable()
//                        ? "DROP TABLE " + source.table()
//                        : "ALTER TABLE " + source.table() + " DROP COLUMN " + source.field());
//            }
//        }
//        log.info("用户设置、游戏数据及私聊功能配置已统一迁入 bot_userdata");
//    }

//    private static void migrateSource(Connection con, LegacySource source) throws SQLException {
//        String after = null;
//        while (true) {
//            var batch = new ArrayList<LegacyRow>();
//            String sql = "SELECT " + source.id() + ", " + source.field() + " FROM " + source.table()
//                    + " WHERE " + source.field() + " IS NOT NULL" + (after == null ? "" : " AND " + source.id() + " > ?")
//                    + " ORDER BY " + source.id() + " LIMIT 100";
//            try (var ps = con.prepareStatement(sql)) {
//                if (after != null) ps.setString(1, after);
//                try (var rows = ps.executeQuery()) {
//                    while (rows.next()) batch.add(new LegacyRow(rows.getString(1), rows.getString(2)));
//                }
//            }
//            if (batch.isEmpty()) return;
//            for (var legacy : batch) {
//                ObjectNode value = parseObject(legacy.json());
//                ensureUser(con, legacy.userId());
//                try (var ps = con.prepareStatement("SELECT " + source.field() + " FROM bot_userdata WHERE user_id = ? FOR UPDATE")) {
//                    ps.setString(1, legacy.userId());
//                    try (var rows = ps.executeQuery()) {
//                        if (!rows.next()) throw new SQLException("用户业务数据行不存在");
//                        String existing = rows.getString(1);
//                        if (existing != null) {
//                            if (!parseObject(existing).equals(value))
//                                throw new SQLException("用户业务数据冲突，保留旧存储：" + source.table() + "." + source.field() + ", " + legacy.userId());
//                            continue;
//                        }
//                    }
//                }
//                try (var ps = con.prepareStatement("UPDATE bot_userdata SET " + source.field() + " = ? WHERE user_id = ?")) {
//                    ps.setString(1, value.toString());
//                    ps.setString(2, legacy.userId());
//                    ps.executeUpdate();
//                }
//            }
//            after = batch.getLast().userId();
//        }
//    }

    static Set<String> columns(Connection con, String table) throws SQLException {
        var result = new HashSet<String>();
        try (var rows = con.getMetaData().getColumns(con.getCatalog(), null, table, null)) {
            while (rows.next()) {
                if (table.equalsIgnoreCase(rows.getString("TABLE_NAME")))
                    result.add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    /**
     * 确保用户业务数据行存在，不修改其配置、游戏数据或统一账号权限
     *
     * @param con    调用方数据库连接
     * @param userId 用户标识
     * @throws SQLException 用户标识无效或记录创建失败
     */
    public static void ensureUser(Connection con, String userId) throws SQLException {
        if (userId == null || userId.isBlank() || userId.length() > 256) throw new SQLException("用户标识无效");
        try (var ps = con.prepareStatement("INSERT INTO bot_userdata (user_id) VALUES (?) ON DUPLICATE KEY UPDATE user_id = user_id")) {
            ps.setQueryTimeout(5);
            ps.setString(1, userId);
            ps.executeUpdate();
        }
    }

    static ObjectNode parseObject(String raw) throws SQLException {
        if (raw == null) return JSON.createObjectNode();
        try {
            if (JSON.readTree(raw) instanceof ObjectNode object) return object;
            throw new SQLException("用户业务数据必须为 JSON 对象");
        } catch (java.io.IOException e) {
            throw new SQLException("用户业务数据 JSON 无效", e);
        }
    }

    /**
     * 在行锁事务内修改单个偏好，B 站 UID 另用命名锁保证跨用户唯一
     *
     * @param userId       用户标识
     * @param setting      设置键
     * @param valueJson    设置值的 JSON 表示
     * @param onlyIfAbsent 是否仅在未设置时写入
     * @return 写入结果
     */
    public static SettingWriteResult setUserSetting(String userId, String setting, String valueJson, boolean onlyIfAbsent) {
        if (userId == null || userId.isBlank() || setting == null || setting.isBlank())
            return SettingWriteResult.FAILED;
        try (var con = DatabaseManager.getConnection()) {
            return setUserSetting(con, userId, setting, valueJson, onlyIfAbsent);
        } catch (Exception e) {
            log.error("保存用户 {} 的设置 {} 失败", userId, setting, e);
            return SettingWriteResult.FAILED;
        }
    }

    static SettingWriteResult setUserSetting(Connection con, String userId, String setting,
                                             String valueJson, boolean onlyIfAbsent) throws Exception {
        if (!"bv_uid".equals(setting)) return writeUserSetting(con, userId, setting, valueJson, onlyIfAbsent, null);
        var uid = JSON.readTree(valueJson);
        if (uid == null || !uid.isIntegralNumber() || !uid.canConvertToLong() || uid.longValue() <= 0)
            return SettingWriteResult.FAILED;
        String lockName = "atrimeow:bv_uid:" + uid.longValue();
        try (var ps = con.prepareStatement("SELECT GET_LOCK(?, 10)")) {
            ps.setString(1, lockName);
            try (var rows = ps.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1) throw new SQLException("获取 B站 UID 绑定锁失败");
            }
        }
        try {
            return writeUserSetting(con, userId, setting, valueJson, true, uid.longValue());
        } finally {
            try (var ps = con.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                ps.setString(1, lockName);
                try (var rows = ps.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != 1) throw new SQLException("释放 B站 UID 绑定锁失败");
                }
            } catch (Exception e) {
                try {
                    con.abort(Runnable::run);
                } catch (Exception abort) {
                    e.addSuppressed(abort);
                }
                throw e;
            }
        }
    }

    private static SettingWriteResult writeUserSetting(Connection con, String userId, String setting,
                                                       String valueJson, boolean onlyIfAbsent, Long uid) throws Exception {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            ensureUser(con, userId);
            ObjectNode settings;
            try (var ps = con.prepareStatement("SELECT user_settings FROM bot_userdata WHERE user_id = ? FOR UPDATE")) {
                ps.setString(1, userId);
                try (var rows = ps.executeQuery()) {
                    if (!rows.next()) throw new SQLException("用户业务数据行不存在");
                    settings = parseObject(rows.getString(1));
                }
            }
            if (onlyIfAbsent && settings.hasNonNull(setting)) {
                con.rollback();
                return SettingWriteResult.ALREADY_EXISTS;
            }
            if (uid != null) {
                try (var ps = con.prepareStatement("SELECT user_id FROM bot_userdata WHERE user_id <> ?"
                        + " AND CAST(JSON_UNQUOTE(JSON_EXTRACT(user_settings, '$.bv_uid')) AS DECIMAL(20, 0)) = ? LIMIT 1")) {
                    ps.setString(1, userId);
                    ps.setLong(2, uid);
                    try (var rows = ps.executeQuery()) {
                        if (rows.next()) {
                            con.rollback();
                            return SettingWriteResult.UID_IN_USE;
                        }
                    }
                }
            }
            settings.set(setting, JSON.readTree(valueJson));
            try (var ps = con.prepareStatement("UPDATE bot_userdata SET user_settings = ? WHERE user_id = ?")) {
                ps.setString(1, settings.toString());
                ps.setString(2, userId);
                ps.executeUpdate();
            }
            con.commit();
            return SettingWriteResult.SAVED;
        } catch (Exception e) {
            try {
                con.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    public static String getUserSettingsJson(String userId) {
        return readJson(userId, "user_settings");
    }

    public static String getFunctionConfigJson(String userId) {
        return readJson(userId, "functions");
    }

    private static String readJson(String userId, String field) {
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("SELECT " + field + " FROM bot_userdata WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (var rows = ps.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        } catch (SQLException e) {
            log.error("读取用户 {} 的 {} 失败", userId, field, e);
            return null;
        }
    }

    public static boolean saveFunctionConfigJson(String userId, String json) {
        try (var con = DatabaseManager.getConnection()) {
            saveFunctionConfigJson(con, userId, json);
            return true;
        } catch (SQLException e) {
            log.error("保存用户 {} 的私聊功能配置失败", userId, e);
            return false;
        }
    }

    static void saveFunctionConfigJson(Connection con, String userId, String json) throws SQLException {
        try (var ps = con.prepareStatement("INSERT INTO bot_userdata (user_id, functions) VALUES (?, ?)"
                + " ON DUPLICATE KEY UPDATE functions = VALUES(functions)")) {
            ps.setString(1, userId);
            ps.setString(2, parseObject(json).toString());
            ps.executeUpdate();
        }
    }

    public static List<String> queryEnabledUsers(String functionKey) {
        try (var con = DatabaseManager.getConnection()) {
            return queryEnabledUsers(con, functionKey);
        } catch (SQLException e) {
            log.error("查询功能 {} 的启用私聊用户列表失败", functionKey, e);
            return List.of();
        }
    }

    static List<String> queryEnabledUsers(Connection con, String functionKey) throws SQLException {
        var users = new ArrayList<String>();
        try (var ps = con.prepareStatement("SELECT user_id FROM bot_userdata WHERE JSON_EXTRACT(functions, ?) = true")) {
            ps.setString(1, "$." + functionKey + ".enabled");
            try (var rows = ps.executeQuery()) {
                while (rows.next()) users.add(rows.getString(1));
            }
        }
        return users;
    }
//
//    private record LegacySource(String table, String id, String field, boolean dropTable) {}
//    private record LegacyRow(String userId, String json) {}
}
