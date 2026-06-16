package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * group_whitelist 和 function_list 表的纯数据库访问层。
 * 缓存和业务逻辑保留在 GroupList 中。
 *
 * @Author YZ_Ljc_
 * @ClassName GroupRepository
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.repo
 */
@Slf4j
public class GroupRepository {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Table init ====================

    public static void initTables() {
        String sqlGroup = "CREATE TABLE IF NOT EXISTS `group_whitelist` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `op_member_openId` VARCHAR(256) NULL," +
                "  `timestamp` BIGINT NOT NULL," +
                "  `is_whitelist` BOOLEAN NOT NULL," +
                "  `is_blacklisted` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `is_allowed_active` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `real_group_id` BIGINT NULL," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String sqlFunc = "CREATE TABLE IF NOT EXISTS `function_list` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `functions` JSON NOT NULL," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(sqlGroup)) {
                ps.execute();
            }
            try (var ps = con.prepareStatement(sqlFunc)) {
                ps.execute();
            }
        } catch (Exception e) {
            log.error("初始化群相关数据库表失败", e);
        }
    }

    // ==================== CRUD ====================

    /**
     * 加载所有群数据行（用于启动时填充缓存）
     */
    public static List<GroupRow> loadAllGroups() {
        List<GroupRow> rows = new ArrayList<>();
        String sql = "SELECT group_openId, op_member_openId, timestamp, is_whitelist, is_blacklisted, is_allowed_active, real_group_id FROM group_whitelist";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {

            while (rs.next()) {
                rows.add(new GroupRow(
                        rs.getString("group_openId"),
                        rs.getString("op_member_openId"),
                        rs.getLong("timestamp"),
                        rs.getBoolean("is_whitelist"),
                        rs.getBoolean("is_blacklisted"),
                        rs.getBoolean("is_allowed_active"),
                        (Long) rs.getObject("real_group_id")
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
    public static boolean insertGroup(String groupOpenId, String opMemberOpenId, long timestamp) {
        String sql = "INSERT IGNORE INTO group_whitelist (group_openId, op_member_openId, timestamp, is_whitelist) VALUES (?, ?, ?, ?)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setLong(3, timestamp);
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
        String sql = "DELETE FROM group_whitelist WHERE group_openId = ?";

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
    public static boolean upsertWhitelist(String groupOpenId, String opMemberOpenId, long timestamp, boolean isWhitelist) {
        String sql = "INSERT INTO group_whitelist (group_openId, op_member_openId, timestamp, is_whitelist) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE timestamp = VALUES(timestamp), is_whitelist = VALUES(is_whitelist)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setLong(3, timestamp);
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
    public static boolean upsertRealGroupId(String groupOpenId, String opMemberOpenId, long timestamp, boolean isWhitelist, Long realGroupId) {
        String sql = "INSERT INTO group_whitelist (group_openId, op_member_openId, timestamp, is_whitelist, real_group_id) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE real_group_id = VALUES(real_group_id)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setLong(3, timestamp);
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
    public static boolean upsertAllowedFullMessage(String groupOpenId, String opMemberOpenId, long timestamp, boolean isWhitelist, boolean allowedActive) {
        String sql = "INSERT INTO group_whitelist (group_openId, op_member_openId, timestamp, is_whitelist, is_allowed_active) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE is_allowed_active = VALUES(is_allowed_active)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setLong(3, timestamp);
            ps.setBoolean(4, isWhitelist);
            ps.setBoolean(5, allowedActive);
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
    public static boolean upsertGroupBlacklisted(String groupOpenId, String opMemberOpenId, long timestamp, boolean isWhitelist, boolean isBlacklisted) {
        String sql = "INSERT INTO group_whitelist (group_openId, op_member_openId, timestamp, is_whitelist, is_blacklisted) " +
                "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE is_blacklisted = VALUES(is_blacklisted)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, groupOpenId);
            ps.setString(2, opMemberOpenId);
            ps.setLong(3, timestamp);
            ps.setBoolean(4, isWhitelist);
            ps.setBoolean(5, isBlacklisted);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("设置群黑名单失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== function_list CRUD ====================

    /**
     * 读取群的 function_list JSON 字符串，不存在返回 null
     */
    public static String getFunctionConfigJson(String groupOpenId) {
        String sql = "SELECT functions FROM function_list WHERE group_openId = ?";

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
     * 保存群的 function_list JSON 字符串
     */
    public static boolean saveFunctionConfigJson(String groupOpenId, String json) {
        String sql = "INSERT INTO function_list (group_openId, functions) VALUES (?, ?) " +
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
        String sql = "SELECT group_openId FROM function_list WHERE JSON_EXTRACT(functions, ?) = true";
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

    public record GroupRow(String groupOpenId, String opMemberOpenId, long timestamp,
                           boolean isWhitelist, boolean isBlacklisted,
                           boolean isAllowedActive, Long realGroupId) {
    }
}
