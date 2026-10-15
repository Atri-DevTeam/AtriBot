package top.yzljc.atribot.webui.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @Author YZ_Ljc_
 * @ClassName ChatConversationRepo
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.repo
 */
@Slf4j
public final class ChatConversationRepo {
    public static final String TABLE = "webui_chat_conversation";

    private ChatConversationRepo() {}

    public static void init() {
        try (var conn = DatabaseManager.getConnection()) {
            initialize(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化聊天会话摘要失败", e);
        }
    }

    static void initialize(Connection conn) throws SQLException {
        try (var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                    + "type VARCHAR(8) NOT NULL, open_id VARCHAR(256) NOT NULL, last_id BIGINT NOT NULL DEFAULT 0, "
                    + "updated_at TIMESTAMP NULL, user_name VARCHAR(256) NULL, "
                    + "PRIMARY KEY(type, open_id), KEY idx_chat_updated(updated_at, type, open_id)) "
                    + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
        conn.setAutoCommit(false);
        try {
            lock(conn, "meta", "v1");
            try (var stmt = conn.prepareStatement("SELECT last_id FROM " + TABLE + " WHERE type='meta' AND open_id='v1'");
                 var rs = stmt.executeQuery()) {
                if (rs.next() && rs.getLong(1) == 1) {
                    conn.commit();
                    return;
                }
            }
            log.info("首次建立聊天会话摘要，正在索引已有消息");
            for (String type : new String[]{"group", "c2c"}) {
                String table = sourceTable(type);
                String column = scopeColumn(type);
                String sql = "INSERT INTO " + TABLE + " (type,open_id,last_id,updated_at,user_name) "
                        + "SELECT ?, r." + column + ",r.id,r.created_at,n.username FROM " + table + " r "
                        + "JOIN (SELECT " + column + " AS scope_id,MAX(id) AS latest_id,"
                        + "MAX(CASE WHEN sender_is_bot=FALSE AND username IS NOT NULL AND TRIM(username)<>'' THEN id END) AS name_id "
                        + "FROM " + table + " WHERE " + column + " IS NOT NULL GROUP BY " + column + ") latest ON r.id=latest.latest_id "
                        + "LEFT JOIN " + table + " n ON n.id=latest.name_id "
                        + "ON DUPLICATE KEY UPDATE last_id=VALUES(last_id),updated_at=VALUES(updated_at),user_name=VALUES(user_name)";
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, type);
                    stmt.executeUpdate();
                }
            }
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE " + TABLE + " SET last_id=1 WHERE type='meta' AND open_id='v1'");
            }
            conn.commit();
            log.info("聊天会话摘要初始化完成");
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * 在消息写入或清理前锁定会话摘要，调用方必须在同一事务内完成消息及摘要更新
     *
     * @param conn 当前事务连接
     * @param type 会话类型
     * @param openId 会话标识
     * @throws SQLException 锁定失败
     */
    public static void lock(Connection conn, String type, String openId) throws SQLException {
        try (var stmt = conn.prepareStatement("INSERT INTO " + TABLE + " (type,open_id) VALUES (?,?) "
                + "ON DUPLICATE KEY UPDATE open_id=VALUES(open_id)")) {
            stmt.setString(1, type);
            stmt.setString(2, openId);
            stmt.executeUpdate();
        }
    }

    public static void refresh(Connection conn, String type, String openId, String knownName) throws SQLException {
        String source = sourceTable(type);
        String column = scopeColumn(type);
        long id = 0;
        java.sql.Timestamp createdAt = null;
        try (var stmt = conn.prepareStatement("SELECT id,created_at FROM " + source + " WHERE " + column + "=? ORDER BY id DESC LIMIT 1")) {
            stmt.setString(1, openId);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    id = rs.getLong(1);
                    createdAt = rs.getTimestamp(2);
                }
            }
        }
        try (var stmt = conn.prepareStatement("UPDATE " + TABLE
                + " SET last_id=?,updated_at=?,user_name=COALESCE(?,user_name) WHERE type=? AND open_id=?")) {
            stmt.setLong(1, id);
            stmt.setTimestamp(2, createdAt);
            stmt.setString(3, knownName == null || knownName.isBlank() ? null : knownName);
            stmt.setString(4, type);
            stmt.setString(5, openId);
            stmt.executeUpdate();
        }
    }

    public static String sourceTable(String type) {
        return switch (type) {
            case "group" -> "official_group_record";
            case "c2c" -> "official_c2c_record";
            default -> throw new IllegalArgumentException("无效的会话类型");
        };
    }

    public static String scopeColumn(String type) {
        return "group".equals(type) ? "group_openId" : "union_openId";
    }
}
