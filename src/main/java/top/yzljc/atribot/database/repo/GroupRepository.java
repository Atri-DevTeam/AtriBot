package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * official_groups 和 group_function_list 表的纯数据库访问层。
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
    private static final String GROUP_FUNCTION_TABLE = "group_function_list";
    private static final String GROUP_JOIN_WELCOME_TABLE = "group_join_welcome";

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
                "  `group_member_num` INT NOT NULL DEFAULT 0," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String sqlFunc = "CREATE TABLE IF NOT EXISTS `" + GROUP_FUNCTION_TABLE + "` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `functions` JSON NOT NULL," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String sqlJoinWelcome = "CREATE TABLE IF NOT EXISTS `" + GROUP_JOIN_WELCOME_TABLE + "` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `config` JSON NOT NULL," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(sqlGroup)) {
                ps.execute();
            }
            try (var ps = con.prepareStatement(sqlFunc)) {
                ps.execute();
            }
            try (var ps = con.prepareStatement(sqlJoinWelcome)) {
                ps.execute();
            }
        } catch (Exception e) {
            log.error("初始化群相关数据库表失败", e);
        }

//        migrateCurrentGroupSchema();
//        migrateGroupTable("group_whitelist");
//        migrateGroupTable("group-whiteist");
//        migrateFunctionTable("function_list");
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
     * 删除群数据
     */
    public static boolean deleteGroup(String groupOpenId) {
        String sql = "DELETE FROM `" + GROUP_TABLE + "` WHERE group_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("删除群数据失败: {}", e.getMessage());
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

    // ==================== group_function_list CRUD ====================

    /**
     * 读取群的 group_function_list JSON 字符串，不存在返回 null
     */
    public static String getFunctionConfigJson(String groupOpenId) {
        String sql = "SELECT functions FROM `" + GROUP_FUNCTION_TABLE + "` WHERE group_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("functions");
            }
        } catch (Exception e) {
            log.error("读取群 {} 的功能配置失败: {}", groupOpenId, e.getMessage());
        }
        return null;
    }

    /**
     * 保存群的 group_function_list JSON 字符串
     */
    public static boolean saveFunctionConfigJson(String groupOpenId, String json) {
        String sql = "INSERT INTO `" + GROUP_FUNCTION_TABLE + "` (group_openId, functions) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE functions = VALUES(functions)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, json);
            ps.executeUpdate();
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
        List<String> groups = new ArrayList<>();
        String sql = "SELECT group_openId FROM `" + GROUP_FUNCTION_TABLE + "` WHERE JSON_EXTRACT(functions, ?) = true";
        String jsonPath = "$." + functionKey + ".enabled";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, jsonPath);
            var rs = ps.executeQuery();
            while (rs.next()) {
                groups.add(rs.getString("group_openId"));
            }
        } catch (Exception e) {
            log.error("查询功能 {} 的启用群列表失败: {}", functionKey, e.getMessage());
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
//    private static void migrateFunctionTable(String legacyTable) {
//        if (!tableExists(legacyTable)) {
//            return;
//        }
//
//        String sql = "INSERT IGNORE INTO `" + GROUP_FUNCTION_TABLE + "` (group_openId, functions) " +
//                "SELECT group_openId, functions FROM `" + legacyTable + "`";
//
//        try (var con = DatabaseManager.getConnection();
//             var ps = con.prepareStatement(sql)) {
//            int count = ps.executeUpdate();
//            log.info("已从旧表 {} 迁移 {} 条群功能配置到 {}", legacyTable, count, GROUP_FUNCTION_TABLE);
//        } catch (Exception e) {
//            log.warn("从旧群功能表 {} 迁移到 {} 失败: {}", legacyTable, GROUP_FUNCTION_TABLE, e.getMessage());
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

    // ==================== group_join_welcome CRUD ====================

    /**
     * 读取群入群欢迎个性化配置 JSON 字符串，不存在返回 null
     */
    public static String getJoinWelcomeConfigJson(String groupOpenId) {
        String sql = "SELECT config FROM `" + GROUP_JOIN_WELCOME_TABLE + "` WHERE group_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            var rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("config");
            }
        } catch (Exception e) {
            log.error("读取群 {} 的入群欢迎配置失败: {}", groupOpenId, e.getMessage());
        }
        return null;
    }

    /**
     * 保存群入群欢迎个性化配置 JSON 字符串
     */
    public static boolean saveJoinWelcomeConfigJson(String groupOpenId, String json) {
        String sql = "INSERT INTO `" + GROUP_JOIN_WELCOME_TABLE + "` (group_openId, config) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE config = VALUES(config)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, json);
            ps.executeUpdate();
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
        String sql = "DELETE FROM `" + GROUP_JOIN_WELCOME_TABLE + "` WHERE group_openId = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("删除群 {} 的入群欢迎配置失败: {}", groupOpenId, e.getMessage());
            return false;
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
