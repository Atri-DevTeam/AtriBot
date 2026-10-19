package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.function.skyblockprice.SkyblockPriceAlert;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @Author YZ_Ljc_
 * @ClassName SkyblockPriceAlertRepository
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class SkyblockPriceAlertRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SETTING = "skyblock_price_alerts";

    private SkyblockPriceAlertRepository() {
    }

    public static List<SkyblockPriceAlert> load(String userId) throws SQLException {
        try (var con = DatabaseManager.getConnection()) {
            return load(con, userId);
        }
    }

    static List<SkyblockPriceAlert> load(Connection con, String userId) throws SQLException {
        try (var ps = con.prepareStatement("SELECT user_settings FROM bot_userdata WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (var rows = ps.executeQuery()) {
                return rows.next() ? decode(BotUserDataRepository.parseObject(rows.getString(1)).path(SETTING).toString()) : new ArrayList<>();
            }
        }
    }

    /**
     * 在好友记录及用户数据行锁内修改提醒，网络请求必须在此事务之外执行
     *
     * @param userId 会话确认的官机用户标识
     * @param change 修改提醒列表的回调
     * @param <T>    回调结果类型
     * @return 回调结果
     * @throws SQLException 存取失败或好友记录已移除
     */
    public static <T> T update(String userId, Function<List<SkyblockPriceAlert>, T> change) throws SQLException {
        try (var con = DatabaseManager.getConnection()) {
            return update(con, userId, change);
        }
    }

    static <T> T update(Connection con, String userId, Function<List<SkyblockPriceAlert>, T> change) throws SQLException {
        return transact(con, userId, (connection, rules) -> change.apply(rules));
    }

    public static void setEnabled(String userId, boolean enabled) throws SQLException {
        try (var con = DatabaseManager.getConnection()) {
            transact(con, userId, (connection, rules) -> {
                ObjectNode functions;
                try (var ps = connection.prepareStatement("SELECT functions FROM bot_userdata WHERE user_id = ?")) {
                    ps.setString(1, userId);
                    try (var rows = ps.executeQuery()) {
                        if (!rows.next()) throw new SQLException("用户业务数据不存在");
                        functions = BotUserDataRepository.parseObject(rows.getString(1));
                    }
                }
                var config = JSON.createObjectNode().put("enabled", enabled).put("operator", userId)
                        .put("time", LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                functions.set("skyblock_price_alert", config);
                try (var ps = connection.prepareStatement("UPDATE bot_userdata SET functions = ? WHERE user_id = ?")) {
                    ps.setString(1, functions.toString());
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
                return null;
            });
        }
    }

    private static <T> T transact(Connection con, String userId, Change<T> change) throws SQLException {
        if (!con.getAutoCommit()) throw new SQLException("价格提醒修改需要独立事务");
        con.setAutoCommit(false);
        try {
            try (var ps = con.prepareStatement("SELECT user_openId FROM official_users WHERE user_openId = ? FOR UPDATE")) {
                ps.setString(1, userId);
                try (var rows = ps.executeQuery()) {
                    if (!rows.next()) throw new SQLException("私聊好友记录不存在");
                }
            }
            BotUserDataRepository.ensureUser(con, userId);
            List<SkyblockPriceAlert> alerts;
            ObjectNode settings;
            try (var ps = con.prepareStatement("SELECT user_settings FROM bot_userdata WHERE user_id = ? FOR UPDATE")) {
                ps.setString(1, userId);
                try (var rows = ps.executeQuery()) {
                    if (!rows.next()) throw new SQLException("用户业务数据不存在");
                    settings = BotUserDataRepository.parseObject(rows.getString(1));
                    alerts = decode(settings.path(SETTING).toString());
                }
            }
            T result = change.apply(con, alerts);
            settings.set(SETTING, JSON.valueToTree(alerts));
            try (var ps = con.prepareStatement("UPDATE bot_userdata SET user_settings = ? WHERE user_id = ?")) {
                ps.setString(1, settings.toString());
                ps.setString(2, userId);
                ps.executeUpdate();
            }
            con.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                con.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }

    private static List<SkyblockPriceAlert> decode(String text) throws SQLException {
        if (text == null || text.isBlank() || "null".equals(text)) return new ArrayList<>();
        try {
            if (!(JSON.readTree(text) instanceof ArrayNode array) || array.size() > 20)
                throw new SQLException("价格提醒配置格式无效");
            var result = new ArrayList<SkyblockPriceAlert>();
            for (var node : array) result.add(JSON.treeToValue(node, SkyblockPriceAlert.class));
            return result;
        } catch (java.io.IOException e) {
            throw new SQLException("价格提醒配置解析失败", e);
        }
    }

    @FunctionalInterface
    private interface Change<T> {
        T apply(Connection connection, List<SkyblockPriceAlert> rules) throws SQLException;
    }
}
