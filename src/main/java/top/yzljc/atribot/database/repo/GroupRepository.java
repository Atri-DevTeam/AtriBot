package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Types;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * official_groups 表的数据库访问层
 * 缓存和业务逻辑保留在 OfficialGroups 中。
 *
 * @Author YZ_Ljc_
 * @ClassName GroupRepository
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.repo
 */
@Slf4j
public class GroupRepository {

    private static final String GROUP_TABLE = "official_groups";
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void initTables() {
        String sqlGroup = "CREATE TABLE IF NOT EXISTS `" + GROUP_TABLE + "` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `op_member_openId` VARCHAR(256) NULL," +
                "  `joined_at` VARCHAR(64) NULL," +
                "  `is_whitelist` BOOLEAN NOT NULL," +
                "  `is_blacklisted` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `allow_proactive_msg` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `real_group_id` BIGINT NULL," +
                "  `member_openid` VARCHAR(256) NULL," +
                "  `recv_msg_setting` VARCHAR(64) NULL," +
                "  `member_role` VARCHAR(32) NULL," +
                "  `group_name` VARCHAR(256) NULL," +
                "  `group_finger_memo` VARCHAR(512) NULL," +
                "  `group_class_text` VARCHAR(256) NULL," +
                "  `group_tags` JSON NULL," +
                "  `group_settings` JSON NULL," +
                "  `group_member_num` INT NOT NULL DEFAULT 0," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(sqlGroup)) {
                ps.execute();
            }
//            migrateGroupSettings(con);
        } catch (Exception e) {
            throw new IllegalStateException("初始化或迁移群相关数据库表失败", e);
        }

//        migrateCurrentGroupSchema();
//        migrateGroupTable("group_whitelist");
//        migrateGroupTable("group-whiteist");
    }

    /**
     * 加载所有群数据行（用于启动时填充缓存）
     */
    public static List<GroupRow> loadAllGroups() {
        List<GroupRow> rows = new ArrayList<>();
        String sql = "SELECT group_openId, op_member_openId, joined_at, is_whitelist, is_blacklisted, " +
                "allow_proactive_msg, real_group_id, member_openid, recv_msg_setting, member_role, " +
                "group_name, group_finger_memo, group_class_text, group_tags, group_member_num FROM `" + GROUP_TABLE + "`";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {

            while (rs.next()) {
                rows.add(new GroupRow(
                        rs.getString("group_openId"),
                        rs.getString("op_member_openId"),
                        rs.getString("joined_at"),
                        rs.getBoolean("is_whitelist"),
                        rs.getBoolean("is_blacklisted"),
                        rs.getBoolean("allow_proactive_msg"),
                        (Long) rs.getObject("real_group_id"),
                        rs.getString("member_openid"),
                        rs.getString("recv_msg_setting"),
                        rs.getString("member_role"),
                        rs.getString("group_name"),
                        rs.getString("group_finger_memo"),
                        rs.getString("group_class_text"),
                        rs.getString("group_tags"),
                        rs.getInt("group_member_num")
                ));
            }
        } catch (Exception e) {
            log.error("加载群数据失败", e);
        }
        return rows;
    }

    /**
     * 注册群（INSERT IGNORE，仅写入基本信息）
     */
    public static boolean insertGroup(String groupOpenId, String opMemberOpenId, String joinedAt) {
        String sql = "INSERT IGNORE INTO `" + GROUP_TABLE + "` (group_openId, op_member_openId, joined_at, is_whitelist) VALUES (?, ?, ?, ?)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setString(3, joinedAt);
            ps.setBoolean(4, false);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("注册群失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除群资料及其中的功能和欢迎配置
     */
    public static boolean deleteGroup(String groupOpenId) {
        String sql = "DELETE FROM `" + GROUP_TABLE + "` WHERE group_openId = ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("删除群 {} 的数据及功能配置失败", groupOpenId, e);
            return false;
        }
    }

    /**
     * 设置白名单状态
     */
    public static boolean upsertWhitelist(String groupOpenId, String opMemberOpenId, String joinedAt, boolean isWhitelist) {
        String sql = "INSERT INTO `" + GROUP_TABLE + "` (group_openId, op_member_openId, joined_at, is_whitelist) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE is_whitelist = VALUES(is_whitelist)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setString(3, joinedAt);
            ps.setBoolean(4, isWhitelist);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("设置群白名单失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置真实群号
     */
    public static boolean upsertRealGroupId(String groupOpenId, String opMemberOpenId, String joinedAt, boolean isWhitelist, Long realGroupId) {
        String sql = "INSERT INTO `" + GROUP_TABLE + "` (group_openId, op_member_openId, joined_at, is_whitelist, real_group_id) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE real_group_id = VALUES(real_group_id)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setString(3, joinedAt);
            ps.setBoolean(4, isWhitelist);
            if (realGroupId == null) ps.setNull(5, Types.BIGINT);
            else ps.setLong(5, realGroupId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("设置真实群号失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置主动推送状态
     */
    public static boolean upsertAllowProactiveMsg(String groupOpenId, String opMemberOpenId, String joinedAt, boolean isWhitelist, boolean allowProactiveMsg) {
        String sql = "INSERT INTO `" + GROUP_TABLE + "` (group_openId, op_member_openId, joined_at, is_whitelist, allow_proactive_msg) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE allow_proactive_msg = VALUES(allow_proactive_msg)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setString(3, joinedAt);
            ps.setBoolean(4, isWhitelist);
            ps.setBoolean(5, allowProactiveMsg);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("设置主动推送失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 设置黑名单状态
     */
    public static boolean upsertGroupBlacklisted(String groupOpenId, String opMemberOpenId, String joinedAt, boolean isWhitelist, boolean isBlacklisted) {
        String sql = "INSERT INTO `" + GROUP_TABLE + "` (group_openId, op_member_openId, joined_at, is_whitelist, is_blacklisted) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE is_blacklisted = VALUES(is_blacklisted)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setString(3, joinedAt);
            ps.setBoolean(4, isWhitelist);
            ps.setBoolean(5, isBlacklisted);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("设置群黑名单失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 写入官方 /bot_state 与 /info 组合出来的群资料。
     */
    public static boolean upsertGroupProfile(String groupOpenId, String opMemberOpenId, String joinedAt,
                                             boolean isWhitelist, boolean isBlacklisted, boolean allowProactiveMsg,
                                             Long realGroupId, String memberOpenid, String recvMsgSetting,
                                             String memberRole, String groupName, String groupFingerMemo,
                                             String groupClassText, String groupTagsJson, int groupMemberNum) {
        String sql = "INSERT INTO `" + GROUP_TABLE + "` (" +
                "group_openId, op_member_openId, joined_at, is_whitelist, is_blacklisted, allow_proactive_msg, " +
                "real_group_id, member_openid, recv_msg_setting, member_role, group_name, group_finger_memo, " +
                "group_class_text, group_tags, group_member_num) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "op_member_openId = COALESCE(VALUES(op_member_openId), op_member_openId), " +
                "joined_at = VALUES(joined_at), " +
                "allow_proactive_msg = VALUES(allow_proactive_msg), " +
                "member_openid = VALUES(member_openid), " +
                "recv_msg_setting = VALUES(recv_msg_setting), " +
                "member_role = VALUES(member_role), " +
                "group_name = VALUES(group_name), " +
                "group_finger_memo = VALUES(group_finger_memo), " +
                "group_class_text = VALUES(group_class_text), " +
                "group_tags = VALUES(group_tags), " +
                "group_member_num = VALUES(group_member_num)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setString(3, joinedAt);
            ps.setBoolean(4, isWhitelist);
            ps.setBoolean(5, isBlacklisted);
            ps.setBoolean(6, allowProactiveMsg);
            if (realGroupId == null) ps.setNull(7, Types.BIGINT);
            else ps.setLong(7, realGroupId);
            ps.setString(8, memberOpenid);
            ps.setString(9, recvMsgSetting);
            ps.setString(10, memberRole);
            ps.setString(11, groupName);
            ps.setString(12, groupFingerMemo);
            ps.setString(13, groupClassText);
            ps.setString(14, groupTagsJson == null || groupTagsJson.isBlank() ? "[]" : groupTagsJson);
            ps.setInt(15, groupMemberNum);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("保存群资料失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 读取 group_settings.functions，不存在返回 null
     */
    public static String getFunctionConfigJson(String groupOpenId) {
        try (var con = DatabaseManager.getConnection()) {
            return getFunctionConfigJson(con, groupOpenId);
        } catch (Exception e) {
            log.error("读取群 {} 的功能配置失败: {}", groupOpenId, e.getMessage());
        }
        return null;
    }

    /**
     * 保存 group_settings.functions，保留其他群设置
     */
    public static boolean saveFunctionConfigJson(String groupOpenId, String json) {
        try (var con = DatabaseManager.getConnection()) {
            saveFunctionConfigJson(con, groupOpenId, json);
            return true;
        } catch (Exception e) {
            log.error("保存群 {} 的功能配置失败: {}", groupOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 查询开启某功能的所有群 openId
     */
    public static List<String> queryEnabledGroups(String functionKey) {
        try (var con = DatabaseManager.getConnection()) {
            return queryEnabledGroups(con, functionKey);
        } catch (Exception e) {
            log.error("查询功能 {} 的启用群列表失败: {}", functionKey, e.getMessage());
            return List.of();
        }
    }

    static String getFunctionConfigJson(Connection con, String groupOpenId) throws SQLException {
        return getGroupSettingJson(con, groupOpenId, "functions");
    }

    static void saveFunctionConfigJson(Connection con, String groupOpenId, String json) throws SQLException {
        saveGroupSettingJson(con, groupOpenId, "functions", json);
    }

    static List<String> queryEnabledGroups(Connection con, String functionKey) throws SQLException {
        List<String> groups = new ArrayList<>();
        String sql = "SELECT group_openId FROM official_groups WHERE JSON_EXTRACT(group_settings, ?) = true";
        String jsonPath = "$.functions." + functionKey + ".enabled";

        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, jsonPath);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) groups.add(rs.getString("group_openId"));
            }
        }
        return groups;
    }

//    private static void migrateGroupTable(String legacyTable) {
//        if (!tableExists(legacyTable)) {
//            return;
//        }
//
//        String joinedAtColumn = columnExists(legacyTable, "joined_at") ? "joined_at" : "CAST(`timestamp` AS CHAR)";
//        String allowProactiveColumn = columnExists(legacyTable, "allow_proactive_msg") ? "allow_proactive_msg" : "is_allowed_active";
//        String sql = "INSERT IGNORE INTO `" + GROUP_TABLE + "` " +
//                "(group_openId, op_member_openId, joined_at, is_whitelist, is_blacklisted, allow_proactive_msg, real_group_id) " +
//                "SELECT group_openId, op_member_openId, " + joinedAtColumn + ", is_whitelist, is_blacklisted, " + allowProactiveColumn + ", real_group_id " +
//                "FROM `" + legacyTable + "`";
//
//        try (var con = DatabaseManager.getConnection();
//             var ps = con.prepareStatement(sql)) {
//            int count = ps.executeUpdate();
//            log.info("已从旧表 {} 迁移 {} 条群数据到 {}", legacyTable, count, GROUP_TABLE);
//        } catch (Exception e) {
//            log.warn("从旧群表 {} 迁移到 {} 失败: {}", legacyTable, GROUP_TABLE, e.getMessage());
//        }
//    }
//
//    private static void migrateCurrentGroupSchema() {
//        renameColumnIfNeeded(GROUP_TABLE, "timestamp", "joined_at", "VARCHAR(64) NULL");
//        ensureColumn(GROUP_TABLE, "joined_at", "VARCHAR(64) NULL AFTER `op_member_openId`");
//        renameColumnIfNeeded(GROUP_TABLE, "is_allowed_active", "allow_proactive_msg", "BOOLEAN NOT NULL DEFAULT FALSE");
//        ensureColumn(GROUP_TABLE, "allow_proactive_msg", "BOOLEAN NOT NULL DEFAULT FALSE AFTER `is_blacklisted`");
//        ensureColumn(GROUP_TABLE, "member_openid", "VARCHAR(256) NULL AFTER `real_group_id`");
//        ensureColumn(GROUP_TABLE, "recv_msg_setting", "VARCHAR(64) NULL AFTER `member_openid`");
//        ensureColumn(GROUP_TABLE, "member_role", "VARCHAR(32) NULL AFTER `recv_msg_setting`");
//        ensureColumn(GROUP_TABLE, "group_name", "VARCHAR(256) NULL AFTER `member_role`");
//        ensureColumn(GROUP_TABLE, "group_finger_memo", "VARCHAR(512) NULL AFTER `group_name`");
//        ensureColumn(GROUP_TABLE, "group_class_text", "VARCHAR(256) NULL AFTER `group_finger_memo`");
//        ensureColumn(GROUP_TABLE, "group_tags", "JSON NULL AFTER `group_class_text`");
//        ensureColumn(GROUP_TABLE, "group_member_num", "INT NOT NULL DEFAULT 0 AFTER `group_tags`");
//    }
//
//    private static void renameColumnIfNeeded(String tableName, String oldColumn, String newColumn, String definition) {
//        if (!columnExists(tableName, oldColumn)) {
//            return;
//        }
//        if (columnExists(tableName, newColumn)) {
//            String sql = "UPDATE `" + tableName + "` SET `" + newColumn + "` = `" + oldColumn + "` WHERE `" + newColumn + "` IS NULL";
//            executeSchemaUpdate(sql, "回填列 " + tableName + "." + newColumn + " 失败");
//            return;
//        }
//        String sql = "ALTER TABLE `" + tableName + "` CHANGE COLUMN `" + oldColumn + "` `" + newColumn + "` " + definition;
//        executeSchemaUpdate(sql, "重命名列 " + tableName + "." + oldColumn + " 失败");
//    }
//
//    private static void ensureColumn(String tableName, String columnName, String definition) {
//        if (columnExists(tableName, columnName)) {
//            return;
//        }
//        String sql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + definition;
//        executeSchemaUpdate(sql, "添加列 " + tableName + "." + columnName + " 失败");
//    }
//
//    private static boolean tableExists(String tableName) {
//        try (var con = DatabaseManager.getConnection();
//             var rs = con.getMetaData().getTables(null, null, tableName, null)) {
//            return rs.next();
//        } catch (Exception e) {
//            log.warn("检查数据表 {} 是否存在失败: {}", tableName, e.getMessage());
//            return false;
//        }
//    }
//
//    private static boolean columnExists(String tableName, String columnName) {
//        try (var con = DatabaseManager.getConnection();
//             var rs = con.getMetaData().getColumns(null, null, tableName, columnName)) {
//            return rs.next();
//        } catch (Exception e) {
//            log.warn("检查列 {}.{} 是否存在失败: {}", tableName, columnName, e.getMessage());
//            return false;
//        }
//    }
//
//    private static void executeSchemaUpdate(String sql, String errorMessage) {
//        try (var con = DatabaseManager.getConnection();
//             var ps = con.prepareStatement(sql)) {
//            ps.executeUpdate();
//        } catch (Exception e) {
//            log.warn("{}: {}", errorMessage, e.getMessage());
//        }
//    }
//
//    /**
//     * 启动阶段补充群设置列，分批迁移旧欢迎和功能配置；内容冲突时回滚，成功后删除旧表
//     *
//     * @param con 自动提交模式的初始化连接，迁移期间尚未接收群设置写入
//     * @throws SQLException 扩列、迁移或删除旧表失败
//     */
//    static void migrateGroupSettings(Connection con) throws SQLException {
//        if (!con.getAutoCommit()) throw new SQLException("群设置迁移需要独立的自动提交连接");
//        boolean hasSettings = false;
//        try (var columns = con.getMetaData().getColumns(con.getCatalog(), null, GROUP_TABLE, "group_settings")) {
//            while (columns.next()) {
//                if (GROUP_TABLE.equalsIgnoreCase(columns.getString("TABLE_NAME"))
//                        && "group_settings".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) hasSettings = true;
//            }
//        }
//        if (!hasSettings) {
//            try (var ps = con.createStatement()) {
//                ps.executeUpdate("ALTER TABLE official_groups ADD COLUMN group_settings JSON NULL");
//            }
//        }
//        List<LegacySource> sources = new ArrayList<>();
//        for (var source : List.of(new LegacySource("group_join_welcome", "config", "join_text"),
//                new LegacySource("group_function_list", "functions", "functions"))) {
//            try (var tables = con.getMetaData().getTables(con.getCatalog(), null, source.table(), null)) {
//                while (tables.next()) {
//                    if (source.table().equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
//                        sources.add(source);
//                        break;
//                    }
//                }
//            }
//        }
//        if (sources.isEmpty()) return;
//        long migrated = 0;
//        con.setAutoCommit(false);
//        try {
//            for (var source : sources) {
//                String after = null;
//                while (true) {
//                    List<LegacySetting> batch = new ArrayList<>();
//                    String sql = "SELECT group_openId, " + source.column() + " FROM " + source.table()
//                            + (after == null ? "" : " WHERE group_openId > ?") + " ORDER BY group_openId LIMIT 100";
//                    try (var ps = con.prepareStatement(sql)) {
//                        if (after != null) ps.setString(1, after);
//                        try (var rows = ps.executeQuery()) {
//                            while (rows.next()) batch.add(new LegacySetting(rows.getString(1), rows.getString(2)));
//                        }
//                    }
//                    if (batch.isEmpty()) break;
//                    for (var row : batch) {
//                        writeGroupSetting(con, row.groupId(), source.key(), parseObject(row.config()), true);
//                    }
//                    migrated += batch.size();
//                    after = batch.getLast().groupId();
//                }
//            }
//            con.commit();
//        } catch (SQLException | RuntimeException e) {
//            try { con.rollback(); }
//            catch (SQLException rollback) { e.addSuppressed(rollback); }
//            throw e;
//        } finally {
//            con.setAutoCommit(true);
//        }
//        try (var ps = con.createStatement()) {
//            for (var source : sources) ps.executeUpdate("DROP TABLE " + source.table());
//        }
//        log.info("群设置迁移完成，共 {} 条，已合并至 official_groups.group_settings", migrated);
//    }
//
//    private record LegacySource(String table, String column, String key) {}
//    private record LegacySetting(String groupId, String config) {}

    /**
     * 读取群入群欢迎个性化配置 JSON 字符串，不存在返回 null
     */
    public static String getJoinWelcomeConfigJson(String groupOpenId) {
        try {
            return getJoinWelcomeConfigJsonOrThrow(groupOpenId);
        } catch (SQLException e) {
            log.error("读取群 {} 的入群欢迎配置失败: {}", groupOpenId, e.getMessage());
            return null;
        }
    }

    public static String getJoinWelcomeConfigJsonOrThrow(String groupOpenId) throws SQLException {
        try (var con = DatabaseManager.getConnection()) {
            return getJoinWelcomeConfigJson(con, groupOpenId);
        }
    }

    static String getJoinWelcomeConfigJson(Connection con, String groupOpenId) throws SQLException {
        return getGroupSettingJson(con, groupOpenId, "join_text");
    }

    private static String getGroupSettingJson(Connection con, String groupOpenId, String key) throws SQLException {
        try (var ps = con.prepareStatement("SELECT group_settings FROM official_groups WHERE group_openId = ?")) {
            ps.setString(1, groupOpenId);
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) return null;
                JsonNode config = parseObject(rows.getString(1)).get(key);
                if (config == null || config.isNull()) return null;
                if (!config.isObject()) throw new SQLException("群设置字段 " + key + " 不是 JSON 对象");
                return config.toString();
            }
        }
    }

    /**
     * 保存群入群欢迎个性化配置 JSON 字符串
     */
    public static boolean saveJoinWelcomeConfigJson(String groupOpenId, String json) {
        try (var con = DatabaseManager.getConnection()) {
            saveJoinWelcomeConfigJson(con, groupOpenId, json);
            return true;
        } catch (Exception e) {
            log.error("保存群 {} 的入群欢迎配置失败: {}", groupOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 删除群入群欢迎个性化配置
     */
    public static boolean deleteJoinWelcomeConfig(String groupOpenId) {
        try (var con = DatabaseManager.getConnection()) {
            saveJoinWelcomeConfigJson(con, groupOpenId, null);
            return true;
        } catch (Exception e) {
            log.error("删除群 {} 的入群欢迎配置失败: {}", groupOpenId, e.getMessage());
            return false;
        }
    }

    /**
     * 在行锁保护下只更新指定设置，保留其他字段；null 表示清除，不创建缺失的群记录
     *
     * @param con 调用方的自动提交连接
     * @param groupOpenId 群 OpenID
     * @param key 群设置字段
     * @param json 配置对象的 JSON，清除时为 null
     * @throws SQLException 数据无效或事务失败
     */
    private static void saveGroupSettingJson(Connection con, String groupOpenId, String key, String json) throws SQLException {
        ObjectNode config = json == null ? null : parseObject(json);
        if (!con.getAutoCommit()) throw new SQLException("群设置保存需要独立的自动提交连接");
        con.setAutoCommit(false);
        try {
            writeGroupSetting(con, groupOpenId, key, config);
            con.commit();
        } catch (SQLException | RuntimeException e) {
            try { con.rollback(); }
            catch (SQLException rollback) { e.addSuppressed(rollback); }
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }

    static void saveJoinWelcomeConfigJson(Connection con, String groupOpenId, String json) throws SQLException {
        saveGroupSettingJson(con, groupOpenId, "join_text", json);
    }

    private static void writeGroupSetting(Connection con, String groupOpenId, String key, ObjectNode config) throws SQLException {
        if (config != null) {
            try (var ps = con.prepareStatement("INSERT INTO official_groups (group_openId, is_whitelist) VALUES (?, FALSE)"
                    + " ON DUPLICATE KEY UPDATE group_openId = group_openId")) {
                ps.setString(1, groupOpenId);
                ps.executeUpdate();
            }
        }
        ObjectNode settings;
        try (var ps = con.prepareStatement("SELECT group_settings FROM official_groups WHERE group_openId = ? FOR UPDATE")) {
            ps.setString(1, groupOpenId);
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) return;
                settings = parseObject(rows.getString(1));
            }
        }
        if (config == null) settings.remove(key);
        else settings.set(key, config);
        try (var ps = con.prepareStatement("UPDATE official_groups SET group_settings = ? WHERE group_openId = ?")) {
            ps.setString(1, settings.toString());
            ps.setString(2, groupOpenId);
            ps.executeUpdate();
        }
    }

    private static ObjectNode parseObject(String raw) throws SQLException {
        if (raw == null) return JSON.createObjectNode();
        try {
            if (JSON.readTree(raw) instanceof ObjectNode object) return object;
            throw new SQLException("群设置不是 JSON 对象，拒绝覆盖");
        } catch (IOException e) {
            throw new SQLException("群设置 JSON 无效，拒绝覆盖", e);
        }
    }

    public record GroupRow(String groupOpenId, String opMemberOpenId, String joinedAt,
                           boolean isWhitelist, boolean isBlacklisted,
                           boolean allowProactiveMsg, Long realGroupId,
                           String memberOpenid, String recvMsgSetting, String memberRole,
                           String groupName, String groupFingerMemo, String groupClassText,
                           String groupTagsJson, int groupMemberNum) {
    }
}
