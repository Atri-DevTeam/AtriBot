package top.yzljc.qqbot.messages;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

public class RecordGroupMessage {
    static Settings settings = Config.getInstance();
    private static final String HOST = settings.getMysqlHost();
    private static final int PORT = settings.getMysqlPort();
    private static final String DATABASE = settings.getMysqlDatabase();
    private static final String DB_USER = settings.getMysqlUsername();
    private static final String DB_PASSWORD = settings.getMysqlPassword();

    private static final String DB_URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private static final String BASE_TABLE = "qq_group_message_record";

    private static HikariDataSource dataSource;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<Long> groups = settings.getMessageSpyGroups();

    static {
        try {
            initDataSource();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[INFO] 初始化数据库连接失败！");
        }
    }

    private static void initDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        dataSource = new HikariDataSource(config);
    }

    private static String getTableNameForGroup(long groupId) {
        return BASE_TABLE + "_" + groupId;
    }

    // 分表自动建表
    private static void initTableForGroup(long groupId) throws SQLException {
        String tableName = getTableNameForGroup(groupId);
        String sql = "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "message_id BIGINT NOT NULL COMMENT '群消息唯一编号', " +
                "group_id BIGINT NOT NULL COMMENT '群号', " +
                "user_id BIGINT NOT NULL COMMENT '发送者QQ号', " +
                "msg_time BIGINT NOT NULL COMMENT '发送时间戳', " +
                "raw_message TEXT COMMENT '原始消息内容', " +
                "record_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间', " +
                "UNIQUE KEY `idx_msg_id` (`message_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    public static void processRecord(JsonNode jsonInput) {
        if (jsonInput == null) return;
        try {
            String messageType = jsonInput.path("message_type").asText();
            if (!"group".equals(messageType)) return;

            long userId = jsonInput.path("user_id").asLong();
            long time = jsonInput.path("time").asLong();
            long messageId = jsonInput.path("message_id").asLong();
            long groupId = jsonInput.path("group_id").asLong();
            String rawMessage = jsonInput.path("raw_message").asText("");
            if (!groups.contains(groupId)) return;

            saveToDatabase(userId, time, messageId, groupId, rawMessage);
        } catch (Exception e) {
            System.err.println("[INFO] 解析消息或入库时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void processRecord(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            processRecord(node);
        } catch (Exception e) {
            System.err.println("[INFO] JSON字符串解析失败");
        }
    }

    // 每次插入，自动按群号分表并保证表有
    private static void saveToDatabase(long userId, long time, long messageId, long groupId, String rawMessage) {
        String tableName = getTableNameForGroup(groupId);

        try {
            initTableForGroup(groupId); // 保证表存在
        } catch (SQLException e) {
            System.err.println("[INFO] 自动建分表失败：" + e.getMessage());
            return;
        }

        String insertSql = "INSERT INTO `" + tableName + "` (user_id, msg_time, message_id, group_id, raw_message) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, time);
            pstmt.setLong(3, messageId);
            pstmt.setLong(4, groupId);
            pstmt.setString(5, rawMessage);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                // Duplicate entry
            } else {
                e.printStackTrace();
            }
        }
    }
    // 静态获取分表名字
    public static String getDynamicTableName(long groupId) {
        return "qq_group_message_record_" + groupId;
    }
    // 静态暴露数据源
    public static HikariDataSource getDataSource() {
        return dataSource;
    }
}