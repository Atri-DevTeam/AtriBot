package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 抽卡系统数据库访问层：用户金粒余额与已获得的物品卡列表
 *
 * @Author YZ_Ljc_
 * @ClassName LootRepository
 * @Created_at 2026/07/31
 * @Project AtriBot
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class LootRepository {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `user_loots` (" +
                "  `user_id` VARCHAR(255) NOT NULL," +
                "  `loots` LONGTEXT," +
                "  `coins` INT DEFAULT 0," +
                "  PRIMARY KEY (`user_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("抽卡数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化抽卡数据库表失败", e);
        }
        initLootItemCounts();
        rebuildLootItemCounts();
    }

    private static void initLootItemCounts() {
        String sql = "CREATE TABLE IF NOT EXISTS `user_loot_items` (" +
                "  `user_id` VARCHAR(255) NOT NULL," +
                "  `item_id` VARCHAR(255) NOT NULL," +
                "  `display_name` VARCHAR(255) NOT NULL DEFAULT ''," +
                "  `count` INT NOT NULL DEFAULT 1," +
                "  `first_receive_timestamp` BIGINT DEFAULT 0," +
                "  `last_receive_timestamp` BIGINT DEFAULT 0," +
                "  `way` VARCHAR(255) NOT NULL DEFAULT '未知'," +
                "  PRIMARY KEY (`user_id`, `item_id`)," +
                "  INDEX `idx_user_loot_items_user` (`user_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("抽卡物品计数表初始化完成");
        } catch (Exception e) {
            log.error("初始化抽卡物品计数表失败", e);
        }
    }

    private static void rebuildLootItemCounts() {
        record LootsSnapshot(String userId, String json) {
        }

        List<LootsSnapshot> snapshots = new ArrayList<>();
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("SELECT `user_id`, `loots` FROM `user_loots`");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                snapshots.add(new LootsSnapshot(rs.getString("user_id"), rs.getString("loots")));
            }
        } catch (Exception e) {
            log.error("读取抽卡旧数据失败，无法重建物品计数表", e);
            return;
        }

        try (var con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try (var clear = con.prepareStatement("DELETE FROM `user_loot_items`")) {
                clear.executeUpdate();
            }

            String insertSql = "INSERT INTO `user_loot_items` " +
                    "(`user_id`, `item_id`, `display_name`, `count`, `first_receive_timestamp`, `last_receive_timestamp`, `way`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
            String updateSql = "UPDATE `user_loots` SET `loots` = ? WHERE `user_id` = ?";
            try (var insert = con.prepareStatement(insertSql);
                 var update = con.prepareStatement(updateSql)) {
                for (LootsSnapshot snapshot : snapshots) {
                    List<LootRecord> loots = parseLoots(snapshot.json());
                    String normalized = writeLoots(loots);
                    update.setString(1, normalized);
                    update.setString(2, snapshot.userId());
                    update.addBatch();

                    for (LootRecord loot : loots) {
                        insert.setString(1, snapshot.userId());
                        insert.setString(2, loot.itemId());
                        insert.setString(3, loot.displayName());
                        insert.setInt(4, loot.count());
                        insert.setLong(5, loot.receiveTimestamp());
                        insert.setLong(6, loot.receiveTimestamp());
                        insert.setString(7, loot.way());
                        insert.addBatch();
                    }
                }
                update.executeBatch();
                insert.executeBatch();
            }
            con.commit();
            log.info("抽卡物品计数表重建完成，共处理 {} 名用户", snapshots.size());
        } catch (Exception e) {
            log.error("重建抽卡物品计数表失败", e);
        }
    }

    // ==================== 金粒 ====================

    public static int getCoins(String userId) {
        String sql = "SELECT `coins` FROM `user_loots` WHERE `user_id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("coins");
                }
            }
        } catch (Exception e) {
            log.error("查询金粒余额失败: userId={}", userId, e);
        }
        return 0;
    }

    public static boolean addCoins(String userId, int amount) {
        if (amount <= 0) return false;
        String sql = "INSERT INTO `user_loots` (`user_id`, `loots`, `coins`) VALUES (?, '[]', ?) " +
                "ON DUPLICATE KEY UPDATE `coins` = `coins` + ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, amount);
            ps.setInt(3, amount);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("增加金粒失败: userId={}, amount={}", userId, amount, e);
            return false;
        }
    }

    public static boolean removeCoins(String userId, int amount) {
        if (amount <= 0) return false;
        try (var con = DatabaseManager.getConnection()) {
            int balance;
            String querySql = "SELECT `coins` FROM `user_loots` WHERE `user_id` = ?";
            try (var ps = con.prepareStatement(querySql)) {
                ps.setString(1, userId);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    balance = rs.getInt("coins");
                }
            }
            if (balance < amount) return false;

            String updateSql = "UPDATE `user_loots` SET `coins` = `coins` - ? WHERE `user_id` = ?";
            try (var ps = con.prepareStatement(updateSql)) {
                ps.setInt(1, amount);
                ps.setString(2, userId);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            log.error("扣除金粒失败: userId={}, amount={}", userId, amount, e);
            return false;
        }
    }

    /**
     * 管理端直接设置金粒余额（不存在则创建记录）
     */
    public static boolean setCoins(String userId, int amount) {
        if (amount < 0) return false;
        String sql = "INSERT INTO `user_loots` (`user_id`, `loots`, `coins`) VALUES (?, '[]', ?) " +
                "ON DUPLICATE KEY UPDATE `coins` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2, amount);
            ps.setInt(3, amount);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("设置金粒余额失败: userId={}, amount={}", userId, amount, e);
            return false;
        }
    }

    // ==================== 物品卡 ====================

    public static List<LootRecord> getLoots(String userId) {
        String sql = "SELECT `loots` FROM `user_loots` WHERE `user_id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return parseLoots(rs.getString("loots"));
                }
            }
        } catch (Exception e) {
            log.error("查询物品卡列表失败: userId={}", userId, e);
        }
        return new ArrayList<>();
    }

    public static LootRecord appendLoot(String userId, String itemId, String displayName, String way) {
        String safeItemId = itemId == null || itemId.isBlank() ? UUID.randomUUID().toString() : itemId;
        String safeDisplayName = displayName == null ? "" : displayName;
        String safeWay = way == null || way.isBlank() ? "未知" : way;
        long now = Instant.now().getEpochSecond();
        LootRecord record = new LootRecord(safeItemId, safeDisplayName, now, safeWay, 1);

        try (var con = DatabaseManager.getConnection()) {
            List<LootRecord> current;
            String querySql = "SELECT `loots` FROM `user_loots` WHERE `user_id` = ? FOR UPDATE";
            try (var ps = con.prepareStatement(querySql)) {
                ps.setString(1, userId);
                try (var rs = ps.executeQuery()) {
                    current = rs.next() ? parseLoots(rs.getString("loots")) : new ArrayList<>();
                }
            }
            boolean found = false;
            for (int i = 0; i < current.size(); i++) {
                LootRecord existing = current.get(i);
                if (existing.itemId().equals(safeItemId)) {
                    long firstReceiveTimestamp = existing.receiveTimestamp() > 0 ? existing.receiveTimestamp() : now;
                    record = new LootRecord(safeItemId, safeDisplayName, firstReceiveTimestamp, existing.way(), existing.count() + 1);
                    current.set(i, record);
                    found = true;
                    break;
                }
            }
            if (!found) {
                current.add(record);
            }
            String json = writeLoots(current);

            String upsertSql = "INSERT INTO `user_loots` (`user_id`, `loots`, `coins`) VALUES (?, ?, 0) " +
                    "ON DUPLICATE KEY UPDATE `loots` = ?";
            try (var ps = con.prepareStatement(upsertSql)) {
                ps.setString(1, userId);
                ps.setString(2, json);
                ps.setString(3, json);
                ps.executeUpdate();
            }

            String upsertItemSql = "INSERT INTO `user_loot_items` " +
                    "(`user_id`, `item_id`, `display_name`, `count`, `first_receive_timestamp`, `last_receive_timestamp`, `way`) " +
                    "VALUES (?, ?, ?, 1, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE `display_name` = VALUES(`display_name`), `count` = `count` + 1, " +
                    "`last_receive_timestamp` = VALUES(`last_receive_timestamp`), `way` = VALUES(`way`)";
            try (var ps = con.prepareStatement(upsertItemSql)) {
                ps.setString(1, userId);
                ps.setString(2, safeItemId);
                ps.setString(3, safeDisplayName);
                ps.setLong(4, now);
                ps.setLong(5, now);
                ps.setString(6, safeWay);
                ps.executeUpdate();
            }
            return record;
        } catch (Exception e) {
            log.error("追加物品卡失败: userId={}, itemId={}", userId, itemId, e);
            return null;
        }
    }

    public static boolean adminRemoveLoot(String userId, String itemId) {
        try (var con = DatabaseManager.getConnection()) {
            List<LootRecord> current;
            String querySql = "SELECT `loots` FROM `user_loots` WHERE `user_id` = ? FOR UPDATE";
            try (var ps = con.prepareStatement(querySql)) {
                ps.setString(1, userId);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    current = parseLoots(rs.getString("loots"));
                }
            }
            boolean removed = false;
            boolean removeWholeItem = false;
            for (int i = 0; i < current.size(); i++) {
                LootRecord existing = current.get(i);
                if (existing.itemId().equals(itemId)) {
                    if (existing.count() > 1) {
                        current.set(i, new LootRecord(existing.itemId(), existing.displayName(), existing.receiveTimestamp(), existing.way(), existing.count() - 1));
                    } else {
                        current.remove(i);
                        removeWholeItem = true;
                    }
                    removed = true;
                    break;
                }
            }
            if (!removed) return false;

            String updateSql = "UPDATE `user_loots` SET `loots` = ? WHERE `user_id` = ?";
            try (var ps = con.prepareStatement(updateSql)) {
                ps.setString(1, writeLoots(current));
                ps.setString(2, userId);
                ps.executeUpdate();
            }

            String updateItemSql = removeWholeItem
                    ? "DELETE FROM `user_loot_items` WHERE `user_id` = ? AND `item_id` = ?"
                    : "UPDATE `user_loot_items` SET `count` = `count` - 1 WHERE `user_id` = ? AND `item_id` = ? AND `count` > 1";
            try (var ps = con.prepareStatement(updateItemSql)) {
                ps.setString(1, userId);
                ps.setString(2, itemId);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            log.error("撤销物品卡失败: userId={}, itemId={}", userId, itemId, e);
            return false;
        }
    }

    // ==================== 管理端查询 ====================

    public static List<CoinLeaderboardEntry> getCoinLeaderboard(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safePageSize;

        List<CoinLeaderboardEntry> result = new ArrayList<>();
        String sql = "SELECT `user_id`, `coins` FROM `user_loots` ORDER BY `coins` DESC LIMIT ? OFFSET ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setInt(1, safePageSize);
            ps.setInt(2, offset);
            try (var rs = ps.executeQuery()) {
                int rank = offset + 1;
                while (rs.next()) {
                    result.add(new CoinLeaderboardEntry(rs.getString("user_id"), rs.getInt("coins"), rank));
                    rank++;
                }
            }
        } catch (Exception e) {
            log.error("查询金粒排行榜失败", e);
        }
        return result;
    }

    public static int countUsers() {
        String sql = "SELECT COUNT(*) FROM `user_loots`";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("查询抽卡用户总数失败", e);
        }
        return 0;
    }

    /**
     * 分页查询抽卡用户，可选按 user_id 模糊搜索，按金粒降序排列
     */
    public static List<UserLootsSummary> listUsers(String search, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safePageSize;
        boolean hasSearch = search != null && !search.isBlank();

        List<UserLootsSummary> result = new ArrayList<>();
        String sql = "SELECT `user_id`, `loots`, `coins` FROM `user_loots`"
                + (hasSearch ? " WHERE `user_id` LIKE ?" : "")
                + " ORDER BY `coins` DESC LIMIT ? OFFSET ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            int idx = 1;
            if (hasSearch) {
                ps.setString(idx++, "%" + search + "%");
            }
            ps.setInt(idx++, safePageSize);
            ps.setInt(idx, offset);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    List<LootRecord> loots = parseLoots(rs.getString("loots"));
                    result.add(new UserLootsSummary(rs.getString("user_id"), rs.getInt("coins"), loots));
                }
            }
        } catch (Exception e) {
            log.error("查询抽卡用户列表失败", e);
        }
        return result;
    }

    public static int countUsersMatching(String search) {
        boolean hasSearch = search != null && !search.isBlank();
        String sql = hasSearch
                ? "SELECT COUNT(*) FROM `user_loots` WHERE `user_id` LIKE ?"
                : "SELECT COUNT(*) FROM `user_loots`";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            if (hasSearch) {
                ps.setString(1, "%" + search + "%");
            }
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计抽卡用户数量失败", e);
        }
        return 0;
    }

    public static UserLootsSummary getUserSummary(String userId) {
        String sql = "SELECT `loots`, `coins` FROM `user_loots` WHERE `user_id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    List<LootRecord> loots = parseLoots(rs.getString("loots"));
                    return new UserLootsSummary(userId, rs.getInt("coins"), loots);
                }
            }
        } catch (Exception e) {
            log.error("查询用户抽卡概况失败: userId={}", userId, e);
        }
        return new UserLootsSummary(userId, 0, new ArrayList<>());
    }

    // ==================== 内部工具 ====================

    private static List<LootRecord> parseLoots(String json) {
        Map<String, LootRecord> merged = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) {
                return new ArrayList<>();
            }
            for (JsonNode node : root) {
                String itemId = node.hasNonNull("item_id") ? node.get("item_id").asText() : UUID.randomUUID().toString();
                String displayName = node.hasNonNull("display_name") ? node.get("display_name").asText() : "未知物品";
                long receiveTimestamp = node.hasNonNull("receive_timestamp") ? node.get("receive_timestamp").asLong() : 0L;
                String way = node.hasNonNull("way") ? node.get("way").asText() : "未知";
                int count = node.hasNonNull("count") ? Math.max(1, node.get("count").asInt(1)) : 1;
                LootRecord existing = merged.get(itemId);
                if (existing == null) {
                    merged.put(itemId, new LootRecord(itemId, displayName, receiveTimestamp, way, count));
                } else {
                    long firstReceiveTimestamp = existing.receiveTimestamp() > 0 ? existing.receiveTimestamp() : receiveTimestamp;
                    merged.put(itemId, new LootRecord(itemId, displayName, firstReceiveTimestamp, existing.way(), existing.count() + count));
                }
            }
        } catch (Exception e) {
            log.error("解析物品卡 JSON 失败: {}", json, e);
        }
        return new ArrayList<>(merged.values());
    }

    private static String writeLoots(List<LootRecord> records) {
        ArrayNode array = mapper.createArrayNode();
        for (LootRecord record : records) {
            ObjectNode node = array.addObject();
            node.put("item_id", record.itemId());
            node.put("display_name", record.displayName());
            node.put("receive_timestamp", record.receiveTimestamp());
            node.put("way", record.way());
            node.put("count", record.count());
        }
        return array.toString();
    }

    public record LootRecord(String itemId, String displayName, long receiveTimestamp, String way, int count) {
        public LootRecord {
            count = Math.max(1, count);
        }
    }

    public record CoinLeaderboardEntry(String userId, int coins, int rank) {
    }

    public record UserLootsSummary(String userId, int coins, List<LootRecord> loots) {
        public int totalLootCount() {
            return loots.stream().mapToInt(LootRecord::count).sum();
        }
    }
}
