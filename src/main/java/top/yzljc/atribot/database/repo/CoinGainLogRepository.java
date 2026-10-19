package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.time.Instant;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 金粒增加来源记录表数据库访问层
 *
 * <p>记录用户每次金粒增加的来源（way），用于来源维度的快速统计与上限控制，
 * 例如「用户有 N 次拉群给金粒的机会，用完即止」的场景：</p>
 * <pre>
 * if (CoinGainLogRepository.countCoinGains(userId, "group_invite") &gt;= 5) {
 *     // 拉群机会已用完，不再发放
 * } else {
 *     LootRepository.addCoins(userId, coins);
 *     CoinGainLogRepository.recordCoinGain(userId, "group_invite", coins);
 * }
 * </pre>
 *
 * @Author YZ_Ljc_
 * @ClassName CoinGainLogRepository
 * @Created_at 2026/08/03
 * @Project AtriBot
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class CoinGainLogRepository {

    private static final String DEFAULT_WAY = "未知";

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `user_coin_gain_logs` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT," +
                "  `user_id` VARCHAR(255) NOT NULL," +
                "  `timestamp` BIGINT NOT NULL," +
                "  `way` VARCHAR(255) NOT NULL," +
                "  `amount` INT NOT NULL," +
                "  PRIMARY KEY (`id`)," +
                "  INDEX `idx_coin_gain_user` (`user_id`)," +
                "  INDEX `idx_coin_gain_way` (`way`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("金粒来源记录表初始化完成");
        } catch (Exception e) {
            log.error("初始化金粒来源记录表失败", e);
        }
    }

    /**
     * 记录一次金粒增加来源
     *
     * @param userId 用户 ID
     * @param way    来源标识，例如 {@code "group_invite"}（拉群）、{@code "sign_in"}（打卡）
     * @param amount 增加的金粒数量，必须大于 0
     * @return 是否记录成功
     */
    public static boolean recordCoinGain(String userId, String way, int amount) {
        if (amount <= 0) return false;
        try (var con = DatabaseManager.getConnection()) {
            recordCoinGain(con, userId, way, amount);
            return true;
        } catch (Exception e) {
            log.error("记录金粒来源失败: userId={}, way={}, amount={}", userId, way, amount, e);
            return false;
        }
    }

    /**
     * @param con    调用方事务连接，本方法不提交事务
     * @param userId 用户标识
     * @param way    金粒来源
     * @param amount 增加数量，必须大于零
     * @throws SQLException 日志写入失败
     */
    static void recordCoinGain(Connection con, String userId, String way, int amount) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("金粒数量必须大于零");
        String sql = "INSERT INTO `user_coin_gain_logs` (`user_id`, `timestamp`, `way`, `amount`) VALUES (?, ?, ?, ?)";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setLong(2, Instant.now().getEpochSecond());
            ps.setString(3, way == null || way.isBlank() ? DEFAULT_WAY : way);
            ps.setInt(4, amount);
            ps.executeUpdate();
        }
    }

    /**
     * 统计某用户通过某来源获得金粒的次数，可用于来源发放次数上限判断
     *
     * @return 次数，查询异常或参数非法时返回 0
     */
    public static int countCoinGains(String userId, String way) {
        if (way == null || way.isBlank()) return 0;
        String sql = "SELECT COUNT(*) FROM `user_coin_gain_logs` WHERE `user_id` = ? AND `way` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, way);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计金粒来源次数失败: userId={}, way={}", userId, way, e);
        }
        return 0;
    }

    /**
     * 统计某用户通过某来源累计获得的金粒数量
     *
     * @return 累计金粒数，查询异常或参数非法时返回 0
     */
    public static int sumCoinGains(String userId, String way) {
        if (way == null || way.isBlank()) return 0;
        String sql = "SELECT COALESCE(SUM(`amount`), 0) FROM `user_coin_gain_logs` WHERE `user_id` = ? AND `way` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, way);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计金粒来源总额失败: userId={}, way={}", userId, way, e);
        }
        return 0;
    }
}
