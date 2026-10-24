package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.function.games.fishing.FishingResult;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName DailyFishingRepository
 * @Created_at 2026/10/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class DailyFishingRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final UserGameDataRepository.Connections connections;

    public record Settlement(FishingResult result, boolean created) {
    }

    public DailyFishingRepository() {
        this(DatabaseManager::getConnection);
    }

    public DailyFishingRepository(UserGameDataRepository.Connections connections) {
        this.connections = connections;
    }

    public void init() throws SQLException {
        try (var con = connections.open(); var ps = con.createStatement()) {
            ps.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS daily_fishing (
                      user_id VARCHAR(255) NOT NULL,
                      fishing_date DATE NOT NULL,
                      result LONGTEXT NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (user_id, fishing_date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    /**
     * @param userId QQ 官方用户 OpenID，群聊与私聊共用
     * @param date   钓鱼日期
     * @return 指定日期存在已结算的钓鱼记录时返回 {@code true}
     * @throws SQLException 查询钓鱼记录失败
     */
    public boolean hasFished(String userId, LocalDate date) throws SQLException {
        try (var con = connections.open();
             var ps = con.prepareStatement("SELECT 1 FROM daily_fishing WHERE user_id = ? AND fishing_date = ?")) {
            ps.setString(1, userId);
            ps.setDate(2, Date.valueOf(date));
            try (var rows = ps.executeQuery()) {
                return rows.next();
            }
        }
    }

    /**
     * 渔获、金粒、来源日志及累计统计在同一事务中提交。
     *
     * @param userId QQ 官方用户 OpenID，群聊与私聊共用
     * @param date   钓鱼日期
     * @param roll   首次钓鱼时生成完整结果；事务重试时可能再次调用，不得包含外部副作用
     * @return 已保存的渔获及本次是否首次结算
     * @throws SQLException 读取或结算失败
     */
    public Settlement catchOnce(String userId, LocalDate date, Supplier<FishingResult> roll) throws SQLException {
        if (userId == null || userId.isBlank() || userId.length() > 255 || date == null || roll == null)
            throw new IllegalArgumentException("Invalid fishing request");
        for (int attempt = 0; ; attempt++) {
            try {
                return transaction(userId, date, roll);
            } catch (SQLException e) {
                if (attempt >= 2 || !("40001".equals(e.getSQLState()) || e.getErrorCode() == 1213 || e.getErrorCode() == 1205))
                    throw e;
            }
        }
    }

    private Settlement transaction(String userId, LocalDate date, Supplier<FishingResult> roll) throws SQLException {
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                ObjectNode root = UserGameDataRepository.lockData(con, userId);
                try (var ps = con.prepareStatement("SELECT result FROM daily_fishing WHERE user_id = ? AND fishing_date = ? FOR UPDATE")) {
                    ps.setString(1, userId);
                    ps.setDate(2, Date.valueOf(date));
                    try (var rows = ps.executeQuery()) {
                        if (rows.next()) {
                            FishingResult saved = JSON.readValue(rows.getString(1), FishingResult.class);
                            con.commit();
                            return new Settlement(saved, false);
                        }
                    }
                }
                FishingResult result = roll.get();
                if (!date.toString().equals(result.date())) throw new IllegalArgumentException("Fishing date mismatch");
                try (var ps = con.prepareStatement("INSERT INTO daily_fishing (user_id, fishing_date, result) VALUES (?, ?, ?)")) {
                    ps.setString(1, userId);
                    ps.setDate(2, Date.valueOf(date));
                    ps.setString(3, JSON.writeValueAsString(result));
                    ps.executeUpdate();
                }
                if (result.totalGold() > 0) {
                    LootRepository.addCoins(con, userId, result.totalGold());
                    CoinGainLogRepository.recordCoinGain(con, userId, "daily_fishing", result.totalGold());
                }
                ObjectNode fishing = object(root, "fishing");
                add(fishing, "plays", 1);
                add(fishing, result.category(), 1);
                add(fishing, "gold", result.totalGold());
                add(fishing, "enchanted", result.enchantments().isEmpty() ? 0 : 1);
                for (var item : result.items()) add(object(fishing, "items"), item.id(), item.count());
                add(object(fishing, "biomes"), result.biomeId(), 1);
                UserGameDataRepository.saveData(con, userId, root);
                try (var ps = con.prepareStatement("INSERT INTO user_game_stat_events (game, event_id, user_id) VALUES ('fishing', ?, ?)")) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
                con.commit();
                return new Settlement(result, true);
            } catch (SQLException | IOException | RuntimeException e) {
                try {
                    con.rollback();
                } catch (SQLException rollback) {
                    e.addSuppressed(rollback);
                }
                if (e instanceof IOException) throw new SQLException("Invalid stored fishing result", e);
                if (e instanceof SQLException sql) throw sql;
                throw (RuntimeException) e;
            }
        }
    }

    private static ObjectNode object(ObjectNode parent, String key) throws SQLException {
        var value = parent.get(key);
        if (value == null) return parent.putObject(key);
        if (value instanceof ObjectNode object) return object;
        throw new SQLException("Invalid fishing statistics: " + key);
    }

    private static void add(ObjectNode node, String key, long amount) throws SQLException {
        var old = node.get(key);
        if (old != null && (!old.isIntegralNumber() || !old.canConvertToLong() || old.longValue() < 0))
            throw new SQLException("Invalid fishing counter: " + key);
        node.put(key, Math.addExact(old == null ? 0 : old.longValue(), amount));
    }
}
