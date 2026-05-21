package top.yzljc.qqbot.official.permission;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.database.DatabaseManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupWhitelist
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.permission
 */
@Slf4j
public class GroupList {

    private static final Map<String, WhitelistData> cache = new ConcurrentHashMap<>();

    public static void init() {

        String sql = "CREATE TABLE IF NOT EXISTS `group_whitelist` (" +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `op_member_openId` VARCHAR(256) NOT NULL," +
                "  `timestamp` BIGINT NOT NULL," +
                "  `is_whitelist` BOOLEAN NOT NULL," +
                "  PRIMARY KEY (`group_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.execute();

        } catch (Exception e) {
            log.error("初始化群白名单数据库表失败: {}", e.getMessage());
        }

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(
                     "SELECT group_openId, op_member_openId, timestamp, is_whitelist FROM group_whitelist")) {

            var rs = stmt.executeQuery();

            while (rs.next()) {

                String groupOpenId = rs.getString("group_openId");
                String opMemberOpenId = rs.getString("op_member_openId");
                long timestamp = rs.getLong("timestamp");
                boolean isWhitelist = rs.getBoolean("is_whitelist");

                cache.put(groupOpenId, new WhitelistData(groupOpenId, opMemberOpenId, timestamp, isWhitelist));
            }

        } catch (Exception e) {
            log.error("加载群白名单缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 注册群
     */
    public static boolean registerGroup(String groupOpenId, String opMemberOpenId, String timestampStr) {

        if (cache.containsKey(groupOpenId)) {
            return true;
        }

        long timestamp;

        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (Exception e) {
            log.error("解析时间戳失败: {}", e.getMessage());
            return false;
        }

        String sql = "INSERT IGNORE INTO group_whitelist " +
                "(group_openId, op_member_openId, timestamp, is_whitelist) " +
                "VALUES (?, ?, ?, ?)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);
            stmt.setString(2, opMemberOpenId);
            stmt.setLong(3, timestamp);
            stmt.setBoolean(4, false);

            stmt.executeUpdate();

            cache.put(groupOpenId, new WhitelistData(groupOpenId, opMemberOpenId, timestamp, false));

            return true;

        } catch (Exception e) {
            log.error("注册群失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 删除群数据
     */
    public static boolean removeGroup(String groupOpenId) {

        String sql = "DELETE FROM group_whitelist WHERE group_openId = ?";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);

            stmt.executeUpdate();

            cache.remove(groupOpenId);

            return true;

        } catch (Exception e) {
            log.error("删除群数据失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取群数据
     */
    public static WhitelistData getData(String groupOpenId) {

        return cache.getOrDefault(
                groupOpenId, new WhitelistData(groupOpenId, null, -1, false)
        );
    }

    /**
     * 是否为白名单群
     */
    public static boolean isWhitelist(String groupOpenId) {

        return getData(groupOpenId).isWhitelist();
    }

    /**
     * 设置白名单状态
     */
    public static boolean setWhitelist(String groupOpenId, boolean isWhitelist) {

        WhitelistData oldData = getData(groupOpenId);

        long timestamp = System.currentTimeMillis() / 1000;

        String sql = "INSERT INTO group_whitelist " +
                "(group_openId, op_member_openId, timestamp, is_whitelist) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "timestamp = VALUES(timestamp), " +
                "is_whitelist = VALUES(is_whitelist)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, groupOpenId);
            stmt.setString(2, oldData.opMemberOpenId());
            stmt.setLong(3, timestamp);
            stmt.setBoolean(4, isWhitelist);

            stmt.executeUpdate();

            cache.put(groupOpenId, new WhitelistData(groupOpenId, oldData.opMemberOpenId(), timestamp, isWhitelist));

            return true;

        } catch (Exception e) {
            log.error("设置群白名单失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 开启白名单
     */
    public static boolean addWhitelist(String groupOpenId) {

        return setWhitelist(groupOpenId, true);
    }

    /**
     * 关闭白名单
     */
    public static boolean removeWhitelist(String groupOpenId) {

        return setWhitelist(groupOpenId, false);
    }

    public record WhitelistData(String groupOpenId, String opMemberOpenId, long timestamp, boolean isWhitelist) {
    }
}