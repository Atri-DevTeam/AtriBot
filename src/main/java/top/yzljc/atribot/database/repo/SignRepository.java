package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.io.File;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 打卡签到数据库访问层
 *
 * @Author YZ_Ljc_
 * @ClassName SignRepository
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.repo
 */
@Slf4j
public class SignRepository {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // ==================== Table init ====================

    public static void init() {
        String sqlTotal = "CREATE TABLE IF NOT EXISTS `check_in_total` (" +
                "  `user_open_id` VARCHAR(255) NOT NULL," +
                "  `total_count` INT DEFAULT 1," +
                "  `total_coins` INT DEFAULT 0," +
                "  `last_check_in_date` DATE NOT NULL," +
                "  PRIMARY KEY (`user_open_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String sqlDaily = "CREATE TABLE IF NOT EXISTS `check_in_daily` (" +
                "  `id` BIGINT AUTO_INCREMENT," +
                "  `user_open_id` VARCHAR(255) NOT NULL," +
                "  `check_in_time` BIGINT NOT NULL," +
                "  `check_in_date` DATE NOT NULL," +
                "  `coins` INT DEFAULT 0," +
                "  PRIMARY KEY (`id`)," +
                "  INDEX `idx_date` (`check_in_date`)," +
                "  UNIQUE KEY `uk_user_date` (`user_open_id`, `check_in_date`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(sqlTotal)) {
                ps.execute();
            }
            try (var ps = con.prepareStatement(sqlDaily)) {
                ps.execute();
            }
            // 迁移：为旧表补充 coins 列
            try (var ps = con.prepareStatement(
                    "ALTER TABLE `check_in_daily` ADD COLUMN `coins` INT DEFAULT 0")) {
                ps.execute();
            } catch (SQLException ignored) {
                // 列已存在，忽略
            }
            try (var ps = con.prepareStatement(
                    "ALTER TABLE `check_in_total` ADD COLUMN `total_coins` INT DEFAULT 0")) {
                ps.execute();
            } catch (SQLException ignored) {
                // 列已存在，忽略
            }
            log.info("打卡数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化打卡数据库表失败", e);
        }
    }

    // ==================== 打卡 ====================

    /**
     * 执行打卡，返回打卡结果；如果今日已打卡则返回 null
     */
    public static CheckInResult checkIn(String userOpenId) {
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();

        try (var con = DatabaseManager.getConnection()) {

            // 查询今日打卡人数，用于计算排名
            int rank;
            String countSql = "SELECT COUNT(*) FROM `check_in_daily` WHERE `check_in_date` = ?";
            try (var ps = con.prepareStatement(countSql)) {
                ps.setDate(1, Date.valueOf(today));
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    rank = rs.getInt(1) + 1;
                }
            }

            // 根据排名计算硬币奖励
            int coins = calculateCoins(rank);

            // 插入日表记录（唯一键约束防止重复打卡）
            String insertDaily = "INSERT INTO `check_in_daily` (`user_open_id`, `check_in_time`, `check_in_date`, `coins`) VALUES (?, ?, ?, ?)";
            try (var ps = con.prepareStatement(insertDaily)) {
                ps.setString(1, userOpenId);
                ps.setLong(2, now);
                ps.setDate(3, Date.valueOf(today));
                ps.setInt(4, coins);
                ps.executeUpdate();
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("Duplicate")) {
                    return null; // 今日已打卡
                }
                throw e;
            }

            // 更新总表（累计次数、累计硬币）
            String upsertTotal = "INSERT INTO `check_in_total` (`user_open_id`, `total_count`, `total_coins`, `last_check_in_date`) " +
                    "VALUES (?, 1, ?, ?) ON DUPLICATE KEY UPDATE `total_count` = `total_count` + 1, `last_check_in_date` = ?, `total_coins` = `total_coins` + ?";
            try (var ps = con.prepareStatement(upsertTotal)) {
                ps.setString(1, userOpenId);
                ps.setInt(2, coins);
                ps.setDate(3, Date.valueOf(today));
                ps.setDate(4, Date.valueOf(today));
                ps.setInt(5, coins);
                ps.executeUpdate();
            }

            // 获取累计打卡次数和累计硬币
            int totalCount;
            int totalCoins;
            String totalSql = "SELECT `total_count`, `total_coins` FROM `check_in_total` WHERE `user_open_id` = ?";
            try (var ps = con.prepareStatement(totalSql)) {
                ps.setString(1, userOpenId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    totalCount = rs.getInt("total_count");
                    totalCoins = rs.getInt("total_coins");
                }
            }

            return new CheckInResult(rank, totalCount, coins, totalCoins);

        } catch (Exception e) {
            log.error("打卡失败，userOpenId: {}", userOpenId, e);
            return null;
        }
    }

    /**
     * 根据打卡排名计算随机硬币奖励
     * 1-3名: 90~100, 4-10名: 70~89, 11-25名: 50~69, 26名以后: 20~49
     */
    private static int calculateCoins(int rank) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (rank <= 3) {
            return r.nextInt(90, 101);   // 90-100
        } else if (rank <= 10) {
            return r.nextInt(70, 90);    // 70-89
        } else if (rank <= 25) {
            return r.nextInt(50, 70);    // 50-69
        } else {
            return r.nextInt(20, 50);    // 20-49
        }
    }

    public static boolean hasCheckedInToday(String userOpenId) {
        LocalDate today = LocalDate.now();
        String sql = "SELECT 1 FROM `check_in_daily` WHERE `user_open_id` = ? AND `check_in_date` = ? LIMIT 1";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setDate(2, Date.valueOf(today));
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("查询今日打卡状态失败", e);
            return false;
        }
    }

    /**
     * 获取用户累计打卡次数
     */
    public static int getTotalCount(String userOpenId) {
        String sql = "SELECT `total_count` FROM `check_in_total` WHERE `user_open_id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_count");
                }
            }
        } catch (Exception e) {
            log.error("查询累计打卡次数失败", e);
        }
        return 0;
    }

    /**
     * 获取用户累计硬币
     */
    public static int getTotalCoins(String userOpenId) {
        String sql = "SELECT `total_coins` FROM `check_in_total` WHERE `user_open_id` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_coins");
                }
            }
        } catch (Exception e) {
            log.error("查询累计硬币失败", e);
        }
        return 0;
    }

    /**
     * 给用户加硬币（不存在则自动创建记录）
     */
    public static boolean addCoins(String userOpenId, int amount) {
        if (amount <= 0) return false;
        String sql = "INSERT INTO `check_in_total` (`user_open_id`, `total_count`, `total_coins`, `last_check_in_date`) " +
                "VALUES (?, 0, ?, ?) ON DUPLICATE KEY UPDATE `total_coins` = `total_coins` + ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setInt(2, amount);
            ps.setDate(3, Date.valueOf(LocalDate.now()));
            ps.setInt(4, amount);
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("加硬币失败，userOpenId: {}, amount: {}", userOpenId, amount, e);
            return false;
        }
    }

    /**
     * 给用户扣硬币（余额不足返回 false）
     */
    public static boolean removeCoins(String userOpenId, int amount) {
        if (amount <= 0) return false;
        try (var con = DatabaseManager.getConnection()) {
            // 先查余额
            int balance;
            String querySql = "SELECT `total_coins` FROM `check_in_total` WHERE `user_open_id` = ?";
            try (var ps = con.prepareStatement(querySql)) {
                ps.setString(1, userOpenId);
                try (var rs = ps.executeQuery()) {
                    if (!rs.next()) return false;       // 没有记录
                    balance = rs.getInt("total_coins");
                }
            }
            if (balance < amount) return false;          // 余额不足

            String updateSql = "UPDATE `check_in_total` SET `total_coins` = `total_coins` - ? WHERE `user_open_id` = ?";
            try (var ps = con.prepareStatement(updateSql)) {
                ps.setInt(1, amount);
                ps.setString(2, userOpenId);
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            log.error("扣硬币失败，userOpenId: {}, amount: {}", userOpenId, amount, e);
            return false;
        }
    }

    /**
     * 获取今日打卡人数
     */
    public static int getTodayCount() {
        LocalDate today = LocalDate.now();
        String sql = "SELECT COUNT(*) FROM `check_in_daily` WHERE `check_in_date` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(today));
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("查询今日打卡人数失败", e);
        }
        return 0;
    }

    public static void exportAndClearDaily() {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

        String querySql = "SELECT `user_open_id`, `check_in_time`, `coins` FROM `check_in_daily` WHERE `check_in_date` = ? ORDER BY `id`";

        ObjectNode root = mapper.createObjectNode();
        root.put("date", dateStr);
        root.put("export_time", System.currentTimeMillis());
        ArrayNode records = root.putArray("records");

        int totalCount = 0;

        try (var con = DatabaseManager.getConnection()) {
            try (var ps = con.prepareStatement(querySql)) {
                ps.setDate(1, Date.valueOf(today));
                try (var rs = ps.executeQuery()) {
                    int rank = 0;
                    while (rs.next()) {
                        rank++;
                        ObjectNode record = mapper.createObjectNode();
                        record.put("rank", rank);
                        record.put("user_open_id", rs.getString("user_open_id"));
                        record.put("check_in_time", rs.getLong("check_in_time"));
                        record.put("coins", rs.getInt("coins"));
                        records.add(record);
                    }
                    totalCount = rank;
                }
            }

            root.put("total_count", totalCount);

            File dir = new File("sign_data");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, dateStr + ".json");
            mapper.writeValue(file, root);
            log.info("打卡日表已导出到: {}（共 {} 条记录）", file.getAbsolutePath(), totalCount);

            // 无脑清空日表
            try (var ps = con.prepareStatement("TRUNCATE TABLE `check_in_daily`")) {
                ps.execute();
            }
            log.info("打卡日表已清空");

        } catch (Exception e) {
            log.error("导出打卡日表失败", e);
        }
    }

    public static boolean isInSettlementWindow() {
        LocalTime now = LocalTime.now();
        return now.getHour() == 23 && now.getMinute() >= 50;
    }

    public record CheckInResult(int rank, int totalCount, int coins, int totalCoins) {
    }
}
