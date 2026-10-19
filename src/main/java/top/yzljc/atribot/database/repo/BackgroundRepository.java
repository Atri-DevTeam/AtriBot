package top.yzljc.atribot.database.repo;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.databind.ObjectMapper;

import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.function.background.BackgroundSettings;

/**
 * @Author YZ_Ljc_
 * @ClassName BackgroundRepository
 * @Created_at 2026/10/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class BackgroundRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VALUE = "JSON_EXTRACT(user_settings, '$.background')";
    private static final String STATUS = "JSON_UNQUOTE(JSON_EXTRACT(user_settings, '$.background.submission.status'))";

    private BackgroundRepository() {
    }

    public record Entry(String userId, BackgroundSettings settings) {
    }

    public static BackgroundSettings get(String userId) throws Exception {
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("SELECT " + VALUE + " FROM bot_userdata WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? parse(rs.getString(1)) : BackgroundSettings.EMPTY;
            }
        }
    }

    /**
     * 锁定用户行后仅更新 background JSON 节点，不覆盖其他个人设置。
     *
     * @param userId 官机用户 OpenID
     * @param update 不执行远程请求的状态变更
     * @return 提交后的背景设置
     * @throws Exception 数据库或状态校验失败，事务回滚
     */
    public static BackgroundSettings update(String userId, UnaryOperator<BackgroundSettings> update) throws Exception {
        try (var con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                BotUserDataRepository.ensureUser(con, userId);
                BackgroundSettings current;
                try (var ps = con.prepareStatement("SELECT " + VALUE + " FROM bot_userdata WHERE user_id = ? FOR UPDATE")) {
                    ps.setString(1, userId);
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("用户记录不存在");
                        current = parse(rs.getString(1));
                    }
                }
                BackgroundSettings next = update.apply(current);
                try (var ps = con.prepareStatement("UPDATE bot_userdata SET user_settings = JSON_SET(CASE WHEN JSON_TYPE(user_settings) = 'OBJECT' THEN user_settings ELSE JSON_OBJECT() END, '$.background', CAST(? AS JSON)) WHERE user_id = ?")) {
                    ps.setString(1, JSON.writeValueAsString(next));
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
                con.commit();
                return next;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public static List<Entry> list(String status, int page, int size) throws Exception {
        String where = STATUS + (status == null ? " IS NOT NULL" : " = ?");
        String sql = "SELECT user_id, " + VALUE + " FROM bot_userdata WHERE " + where
                + " ORDER BY JSON_EXTRACT(user_settings, '$.background.submission.createdAt') DESC, user_id LIMIT ? OFFSET ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            int index = 1;
            if (status != null) ps.setString(index++, status);
            ps.setInt(index++, size);
            ps.setLong(index, (long) (page - 1) * size);
            List<Entry> result = new ArrayList<>();
            try (var rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Entry(rs.getString(1), parse(rs.getString(2))));
            }
            return result;
        }
    }

    public static Map<String, Long> counts() throws Exception {
        Map<String, Long> counts = new LinkedHashMap<>(Map.of("pending", 0L, "reviewed", 0L, "denied", 0L, "all", 0L));
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("SELECT " + STATUS + ", COUNT(*) FROM bot_userdata WHERE " + STATUS + " IS NOT NULL GROUP BY " + STATUS);
             var rs = ps.executeQuery()) {
            long total = 0;
            while (rs.next()) {
                counts.put(rs.getString(1).toLowerCase(java.util.Locale.ROOT), rs.getLong(2));
                total += rs.getLong(2);
            }
            counts.put("all", total);
        }
        return counts;
    }

    static BackgroundSettings parse(String json) throws Exception {
        return json == null || "null".equals(json) ? BackgroundSettings.EMPTY : JSON.readValue(json, BackgroundSettings.class);
    }
}
