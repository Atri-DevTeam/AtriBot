package top.yzljc.atribot.functions.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.GroupMessageType;
import top.yzljc.atribot.chat.official.MessageBody;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.event.Author;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.EventPriority;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.OfficialC2CMessageEvent;
import top.yzljc.atribot.event.impl.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.functions.official.permission.C2CList;
import top.yzljc.atribot.webui.official.SseBroadcaster;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChatContentRecord implements Listener {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GROUP_TABLE = "official_group_record";
    private static final String C2C_TABLE = "official_c2c_record";
    private static final String BOT_UNION_OPEN_ID = Config.getInstance().getOfficialOpenId();
    private static final String BOT_USERNAME = Config.getInstance().getOfficialUsername();
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void init() {
        String groupSql = "CREATE TABLE IF NOT EXISTS `" + GROUP_TABLE + "` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT," +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `union_openId` VARCHAR(256) NULL," +
                "  `username` VARCHAR(256) NULL," +
                "  `content` MEDIUMTEXT NULL," +
                "  `message_openId` VARCHAR(256) NULL," +
                "  `member_openId` VARCHAR(256) NULL," +
                "  `sender_is_bot` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `event_type` VARCHAR(64) NOT NULL," +
                "  `message_type` INT NULL," +
                "  `event_timestamp` VARCHAR(64) NULL," +
                "  `attachments` MEDIUMTEXT NULL," +
                "  `mentions` MEDIUMTEXT NULL," +
                "  `message_reference` MEDIUMTEXT NULL," +
                "  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_group_message_openId` (`message_openId`)," +
                "  KEY `idx_group_openId` (`group_openId`)," +
                "  KEY `idx_group_created_at` (`created_at`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        String c2cSql = "CREATE TABLE IF NOT EXISTS `" + C2C_TABLE + "` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT," +
                "  `union_openId` VARCHAR(256) NULL," +
                "  `username` VARCHAR(256) NULL," +
                "  `content` MEDIUMTEXT NULL," +
                "  `message_openId` VARCHAR(256) NULL," +
                "  `sender_is_bot` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `source` VARCHAR(64) NOT NULL," +
                "  `message_type` INT NULL," +
                "  `event_timestamp` VARCHAR(64) NULL," +
                "  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_c2c_message_openId` (`message_openId`)," +
                "  KEY `idx_c2c_union_openId` (`union_openId`)," +
                "  KEY `idx_c2c_created_at` (`created_at`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var conn = DatabaseManager.getConnection();
             var groupStmt = conn.prepareStatement(groupSql);
             var c2cStmt = conn.prepareStatement(c2cSql)) {
            groupStmt.execute();
            c2cStmt.execute();
            ensureColumn("official_group_record", "event_type", "VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN' AFTER `sender_is_bot`");
            dropColumnIfExists(C2C_TABLE, "user_openId");
            log.info("官方机器人消息记录表初始化完成");
        } catch (SQLException e) {
            log.error("初始化官方机器人消息记录表失败: {}", e.getMessage(), e);
        }
    }

    private static void ensureC2CUserInCache(String userOpenId) {
        if (!C2CList.isCached(userOpenId)) {
            C2CList.registerUser(userOpenId);
            log.info("自动注册漏掉的C2C用户: {}", userOpenId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOfficialC2CMessage(OfficialC2CMessageEvent event) {
        Author author = event.getAuthor();
        String uid = firstNonBlank(author.getUnionOpenId(), author.getMemberOpenId());
        if (uid != null) ensureC2CUserInCache(uid);
        recordC2CMessage(
                author.getMemberOpenId(),
                author.getUnionOpenId(),
                author.getUsername(),
                event.getContent(),
                event.getMsgId(),
                author.isBot(),
                "C2C_MESSAGE_CREATE",
                GroupMessageType.TEXT.getValue(),
                event.getTimestamp()
        );
    }

    private static void ensureGroupInCache(String groupOpenId) {
        if (GroupList.getData(groupOpenId).timestamp() == -1) {
            GroupList.registerGroup(groupOpenId, null, String.valueOf(System.currentTimeMillis() / 1000));
            log.info("自动注册漏掉的群: {}", groupOpenId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOfficialGroupAtMessage(OfficialGroupAtMessageCreateEvent event) {
        ensureGroupInCache(event.getGroupOpenId());
        Author author = event.getAuthor();
        recordGroupMessage(
                event.getGroupOpenId(),
                author.getUnionOpenId(),
                author.getUsername(),
                event.getContent(),
                event.getMsgId(),
                author.getMemberOpenId(),
                author.isBot(),
                "GROUP_AT_MESSAGE_CREATE",
                GroupMessageType.TEXT.getValue(),
                event.getTimestamp(),
                toJson(event.getAttachments()),
                null,
                toJson(event.getMessageReference())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOfficialGroupMessage(OfficialGroupMessageCreateEvent event) {
        ensureGroupInCache(event.getGroupOpenId());
        Author author = event.getAuthor();
        recordGroupMessage(
                event.getGroupOpenId(),
                author.getUnionOpenId(),
                author.getUsername(),
                event.getContent(),
                event.getMessageId(),
                author.getMemberOpenId(),
                author.isBot(),
                "GROUP_MESSAGE_CREATE",
                event.getMessageType(),
                event.getTimestamp(),
                toJson(event.getAttachments()),
                toJson(event.getMentions()),
                toJson(event.getMessageReference())
        );
    }

    public static void recordSentGroupMessage(String groupOpenId, MessageBody request, String messageOpenId) {
        recordGroupMessage(
                groupOpenId,
                BOT_UNION_OPEN_ID,
                BOT_USERNAME,
                extractContent(request),
                messageOpenId,
                null,
                true,
                "BOT_SEND",
                request.getMsgType(),
                nowLocalTime(),
                null,
                null,
                null
        );
    }

    public static void recordSentC2CMessage(String userOpenId, MessageBody request, String messageOpenId) {
        recordC2CMessage(
                null,
                userOpenId,
                BOT_USERNAME,
                extractContent(request),
                messageOpenId,
                true,
                "BOT_SEND",
                request.getMsgType(),
                nowLocalTime()
        );
    }

    public static MessagePage<GroupMessageRecord> fetchGroupMessages(String groupOpenId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePage - 1) * safePageSize;

        String countSql = "SELECT COUNT(*) FROM `" + GROUP_TABLE + "` WHERE group_openId = ?";
        String dataSql = "SELECT id, group_openId, union_openId, username, content, message_openId, member_openId, " +
                "sender_is_bot, event_type, message_type, event_timestamp, attachments, mentions, message_reference, created_at " +
                "FROM `" + GROUP_TABLE + "` WHERE group_openId = ? ORDER BY id DESC LIMIT ? OFFSET ?";

        long total = 0;
        List<GroupMessageRecord> records = new ArrayList<>();

        try (var conn = DatabaseManager.getConnection();
             var countStmt = conn.prepareStatement(countSql);
             var dataStmt = conn.prepareStatement(dataSql)) {
            countStmt.setString(1, groupOpenId);
            var countRs = countStmt.executeQuery();
            if (countRs.next()) {
                total = countRs.getLong(1);
            }

            dataStmt.setString(1, groupOpenId);
            dataStmt.setInt(2, safePageSize);
            dataStmt.setInt(3, offset);
            var rs = dataStmt.executeQuery();
            while (rs.next()) {
                records.add(new GroupMessageRecord(
                        rs.getLong("id"),
                        rs.getString("group_openId"),
                        rs.getString("union_openId"),
                        rs.getString("username"),
                        rs.getString("content"),
                        rs.getString("message_openId"),
                        rs.getString("member_openId"),
                        rs.getBoolean("sender_is_bot"),
                        rs.getString("event_type"),
                        (Integer) rs.getObject("message_type"),
                        rs.getString("event_timestamp"),
                        rs.getString("attachments"),
                        rs.getString("mentions"),
                        rs.getString("message_reference"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            log.error("查询官方群消息失败, groupOpenId={}, page={}, pageSize={}: {}", groupOpenId, safePage, safePageSize, e.getMessage(), e);
        }

        return new MessagePage<>(safePage, safePageSize, total, records);
    }

    private static void recordGroupMessage(String groupOpenId, String unionOpenId, String username, String content,
                                           String messageOpenId, String memberOpenId, boolean senderIsBot,
                                           String source, Integer messageType, String eventTimestamp,
                                           String attachments, String mentions, String messageReference) {
        if (isBlank(groupOpenId)) {
            log.warn("跳过官方群消息记录：groupOpenId 为空, eventType={}, messageOpenId={}", source, messageOpenId);
            return;
        }

        String sql = "INSERT INTO `" + GROUP_TABLE + "` " +
                "(group_openId, union_openId, username, content, message_openId, member_openId, sender_is_bot, event_type, message_type, event_timestamp, attachments, mentions, message_reference) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE content = VALUES(content), username = VALUES(username), event_type = VALUES(event_type)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupOpenId);
            stmt.setString(2, emptyToNull(unionOpenId));
            stmt.setString(3, emptyToNull(username));
            stmt.setString(4, content);
            stmt.setString(5, emptyToNull(messageOpenId));
            stmt.setString(6, emptyToNull(memberOpenId));
            stmt.setBoolean(7, senderIsBot);
            stmt.setString(8, source);
            if (messageType == null) {
                stmt.setNull(9, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(9, messageType);
            }
            stmt.setString(10, emptyToNull(eventTimestamp));
            stmt.setString(11, emptyToNull(attachments));
            stmt.setString(12, emptyToNull(mentions));
            stmt.setString(13, emptyToNull(messageReference));
            stmt.executeUpdate();

            // SSE 实时推送 — 只发刷新信号，前端自己拉数据
            try {
                var payload = objectMapper.createObjectNode();
                payload.put("type", "refresh");
                payload.put("groupOpenId", groupOpenId);
                SseBroadcaster.broadcast(objectMapper.writeValueAsString(payload));
            } catch (Exception ignored) {
                // SSE 发送失败不影响主流程
            }
        } catch (SQLException e) {
            log.error("记录官方群消息失败, groupOpenId={}, messageOpenId={}: {}", groupOpenId, messageOpenId, e.getMessage(), e);
        }
    }

    private static void ensureColumn(String tableName, String columnName, String definition) {
        String sql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + definition;
        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1060 || "42S21".equals(e.getSQLState())) {
                return;
            }
            log.error("为表 {} 补充字段 {} 失败: {}", tableName, columnName, e.getMessage(), e);
        }
    }

    private static void recordC2CMessage(String userOpenId, String unionOpenId, String username, String content,
                                         String messageOpenId, boolean senderIsBot, String source,
                                         Integer messageType, String eventTimestamp) {
        String conversationOpenId = firstNonBlank(unionOpenId, userOpenId);
        if (isBlank(conversationOpenId)) {
            log.warn("跳过官方 C2C 消息记录：userOpenId/unionOpenId 为空, eventType={}, messageOpenId={}", source, messageOpenId);
            return;
        }

        String sql = "INSERT INTO `" + C2C_TABLE + "` " +
                "(union_openId, username, content, message_openId, sender_is_bot, source, message_type, event_timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE content = VALUES(content), username = VALUES(username), source = VALUES(source)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, conversationOpenId);
            stmt.setString(2, emptyToNull(username));
            stmt.setString(3, content);
            stmt.setString(4, emptyToNull(messageOpenId));
            stmt.setBoolean(5, senderIsBot);
            stmt.setString(6, emptyToNull(source));
            if (messageType == null) {
                stmt.setNull(7, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(7, messageType);
            }
            stmt.setString(8, emptyToNull(eventTimestamp));
            stmt.executeUpdate();

            // SSE — C2C 刷新信号
            try {
                var payload = objectMapper.createObjectNode();
                payload.put("type", "c2c_refresh");
                payload.put("userOpenId", conversationOpenId);
                SseBroadcaster.broadcast(objectMapper.writeValueAsString(payload));
            } catch (Exception ignored) {}
        } catch (SQLException e) {
            log.error("记录官方 C2C 消息失败, userOpenId={}, unionOpenId={}, messageOpenId={}: {}",
                    userOpenId, unionOpenId, messageOpenId, e.getMessage(), e);
        }
    }

    private static void dropColumnIfExists(String tableName, String columnName) {
        String sql = "ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName + "`";
        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1091 || "42000".equals(e.getSQLState())) {
                return;
            }
            log.error("删除表 {} 字段 {} 失败: {}", tableName, columnName, e.getMessage(), e);
        }
    }

    private static String extractContent(MessageBody request) {
        if (!isBlank(request.getContent())) {
            return request.getContent();
        }
        if (request.getMarkdown() != null) {
            return extractMarkdownContent(request.getMarkdown());
        }
        if (request.getArk() != null) {
            return toJson(request.getArk());
        }
        if (request.getMedia() != null) {
            return "[media] " + toJson(request.getMedia());
        }
        if (request.getKeyboard() != null) {
            return "[keyboard] " + toJson(request.getKeyboard());
        }
        return "";
    }

    private static String extractMarkdownContent(Object markdown) {
        JsonNode node = objectMapper.valueToTree(markdown);
        JsonNode contentNode = node.get("content");
        if (contentNode != null && contentNode.isTextual()) {
            return contentNode.asText();
        }
        return node.toString();
    }

    private static String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String nowLocalTime() {
        return LocalDateTime.now().format(LOCAL_TIME_FORMATTER);
    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record MessagePage<T>(int page, int pageSize, long total, List<T> records) {
    }

    public record GroupMessageRecord(long id, String groupOpenId, String unionOpenId, String username,
                                     String content, String messageOpenId, String memberOpenId,
                                     boolean senderIsBot, String eventType,
                                     Integer messageType, String eventTimestamp,
                                     String attachments, String mentions, String messageReference, String createdAt) {
    }

    public record C2CMessageRecord(long id, String unionOpenId, String username,
                                   String content, String messageOpenId,
                                   boolean senderIsBot, Integer messageType,
                                   String eventTimestamp, String createdAt) {
    }

    public static MessagePage<C2CMessageRecord> fetchC2CMessages(String userOpenId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePage - 1) * safePageSize;

        String countSql = "SELECT COUNT(*) FROM `" + C2C_TABLE + "` WHERE union_openId = ?";
        String dataSql = "SELECT id, union_openId, username, content, message_openId, " +
                "sender_is_bot, message_type, event_timestamp, created_at " +
                "FROM `" + C2C_TABLE + "` WHERE union_openId = ? ORDER BY id DESC LIMIT ? OFFSET ?";

        long total = 0;
        List<C2CMessageRecord> records = new ArrayList<>();

        try (var conn = DatabaseManager.getConnection();
             var countStmt = conn.prepareStatement(countSql);
             var dataStmt = conn.prepareStatement(dataSql)) {
            countStmt.setString(1, userOpenId);
            var countRs = countStmt.executeQuery();
            if (countRs.next()) total = countRs.getLong(1);

            dataStmt.setString(1, userOpenId);
            dataStmt.setInt(2, safePageSize);
            dataStmt.setInt(3, offset);
            var rs = dataStmt.executeQuery();
            while (rs.next()) {
                records.add(new C2CMessageRecord(
                        rs.getLong("id"),
                        rs.getString("union_openId"),
                        rs.getString("username"),
                        rs.getString("content"),
                        rs.getString("message_openId"),
                        rs.getBoolean("sender_is_bot"),
                        (Integer) rs.getObject("message_type"),
                        rs.getString("event_timestamp"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            log.error("查询C2C消息失败, userOpenId={}, page={}, pageSize={}: {}", userOpenId, safePage, safePageSize, e.getMessage(), e);
        }

        return new MessagePage<>(safePage, safePageSize, total, records);
    }
}
