package top.yzljc.qqbot.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.Getter;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.webui.WebUIController;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.service.userinfo.GetUserInfo;

public class GroupContentRecord implements Listener {

    private static final Logger log = LoggerFactory.getLogger(GroupContentRecord.class);
    
    static Settings settings = Config.getInstance();
    private static final String HOST = settings.getMysqlHost();
    private static final int PORT = settings.getMysqlPort();
    private static final String DATABASE = settings.getMysqlDatabase();
    private static final String DB_USER = settings.getMysqlUsername();
    private static final String DB_PASSWORD = settings.getMysqlPassword();

    private static final String DB_URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";
    private static final String BASE_TABLE = "qq_group_message_record";

    @Getter
    private static HikariDataSource dataSource;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<Long> groups = settings.getMessageSpyGroups();

    static {
        try {
            initDataSource();
        } catch (Exception e) {
            log.error("初始化数据库连接失败: {}", e.getMessage());
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

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        long groupId = event.getGroupId();
        if (!Config.getInstance().getMessageSpyGroups().contains(groupId)) {
            return;
        }
        long time = event.getTime();
        long userId = event.getUserId();
        long messageId = event.getMessageId();
        String rawMessage = event.getRawMessage();

        try{
            saveToDatabase(userId, time, messageId, groupId, rawMessage);
        } catch (Exception e) {
            top.yzljc.qqbot.utils.Logger.error("消息入库失败: {}", e.getMessage());
        }
    }

    @Deprecated
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
            log.error("解析消息或入库时发生错误：{}", e.getMessage());
        }
    }

    // 这是一个备用的方法，暂时用不到，但是别删
    @Deprecated(forRemoval = true)
    public static void processRecord(String jsonString) {
        try {
            JsonNode node = objectMapper.readTree(jsonString);
            processRecord(node);
        } catch (Exception e) {
            log.error("JSON 字符串解析失败");
        }
    }

    // 每次插入，自动按群号分表并保证表有
    private static void saveToDatabase(long userId, long time, long messageId, long groupId, String rawMessage) {
        String tableName = getTableNameForGroup(groupId);

        try {
            initTableForGroup(groupId); // 保证表存在
        } catch (SQLException e) {
            log.error("自动建分表失败: {}", e.getMessage());
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
            log.warn("消息入库失败: {}", e.getMessage());
        }
    }

    public static String getDynamicTableName(long groupId) {
        return "qq_group_message_record_" + groupId;
    }

    public static String searchMessage(long groupId, long messageId) {
        String tableName = getTableNameForGroup(groupId);
        String sql = "SELECT raw_message FROM `" + tableName + "` WHERE message_id = ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, messageId);
            try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("raw_message");
                }
            }
        } catch (SQLException e) {
            log.error("查询原始消息失败: {}", e.getMessage());
        }
        return null;
    }

    public static LinkedList<WebUIController.GroupMessageDTO> fetchMessages(long groupId, int page) {
        LinkedList<WebUIController.GroupMessageDTO> result = new LinkedList<>();
        if (dataSource == null) {
            log.warn("数据源未初始化，无法查询消息");
            return result;
        }

        String tableName = getTableNameForGroup(groupId);
        int offset = (page - 1) * 25;
        String sql = "SELECT user_id, message_id, raw_message, msg_time FROM `" + tableName + "` ORDER BY id DESC LIMIT 25 OFFSET ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    WebUIController.GroupMessageDTO data = new WebUIController.GroupMessageDTO();
                    data.setUserId(rs.getLong("user_id"));
                    data.setMessageId(rs.getLong("message_id"));
                    data.setContent(rs.getString("raw_message"));
                    data.setTime(rs.getLong("msg_time"));
                    data.setUserName(GetUserInfo.getUserName(data.getUserId()));
                    data.setAdmin(GetUserInfo.isGroupAdmin(groupId, data.getUserId()));
                    result.add(data);
                }
            }
        } catch (SQLException e) {
            log.error("分页查询消息失败: {}", e.getMessage());
        }
        return result;
    }
}
