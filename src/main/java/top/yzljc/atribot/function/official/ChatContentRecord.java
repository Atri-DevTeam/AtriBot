package top.yzljc.atribot.function.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.MessageBody;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.EventPriority;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.webui.impl.SseBroadcaster;

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
    private static final int REFERENCE_SCAN_LIMIT = 500;

    public static void init() {
        String groupSql = "CREATE TABLE IF NOT EXISTS `" + GROUP_TABLE + "` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT," +
                "  `group_openId` VARCHAR(256) NOT NULL," +
                "  `union_openId` VARCHAR(256) NULL," +
                "  `username` VARCHAR(256) NULL," +
                "  `content` MEDIUMTEXT NULL," +
                "  `message_openId` VARCHAR(256) NULL," +
                "  `sender_is_bot` BOOLEAN NOT NULL DEFAULT FALSE," +
                "  `member_role` VARCHAR(32) NULL," +
                "  `event_type` VARCHAR(64) NOT NULL," +
                "  `message_type` INT NULL," +
                "  `event_timestamp` VARCHAR(64) NULL," +
                "  `attachments` MEDIUMTEXT NULL," +
                "  `mentions` MEDIUMTEXT NULL," +
                "  `message_reference` MEDIUMTEXT NULL," +
                "  `ref_idx` VARCHAR(256) NULL," +
                "  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_group_message_openId` (`message_openId`)," +
                "  KEY `idx_group_openId` (`group_openId`)," +
                "  KEY `idx_group_union_openId` (`union_openId`)," +
                "  KEY `idx_group_ref_idx` (`ref_idx`)," +
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
            ensureColumn(GROUP_TABLE, "member_role", "VARCHAR(32) NULL AFTER `sender_is_bot`");
            ensureColumn(GROUP_TABLE, "ref_idx", "VARCHAR(256) NULL AFTER `message_reference`");
            ensureIndex(GROUP_TABLE, "idx_group_union_openId", "`union_openId`");
            ensureIndex(GROUP_TABLE, "idx_group_ref_idx", "`ref_idx`");
            dropColumnIfExists(C2C_TABLE, "user_openId");
            log.info("官方机器人消息记录表初始化完成");
        } catch (SQLException e) {
            log.error("初始化官方机器人消息记录表失败: {}", e.getMessage(), e);
        }
    }

    private static void ensureC2CUserInCache(String userOpenId) {
        if (!OfficialUsers.isCached(userOpenId)) {
            OfficialUsers.registerUser(userOpenId);
            log.info("自动注册漏掉的C2C用户: {}", userOpenId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOfficialC2CMessage(OfficialC2CMessageCreateEvent event) {
        User user = event.getUser();
        String uid = user.getUserId();
        if (uid != null) ensureC2CUserInCache(uid);
        recordC2CMessage(
                null,
                uid,
                user.getUsername(),
                event.getMessage().getContent(),
                event.getMessage().getMessageId(),
                user.isBot(),
                "C2C_MESSAGE_CREATE",
                0,
                event.getTimestamp()
        );
    }

    private static void ensureGroupInCache(String groupOpenId) {
        if (OfficialGroups.getData(groupOpenId).timestamp() == -1) {
            OfficialGroups.registerGroup(groupOpenId, null, String.valueOf(System.currentTimeMillis() / 1000));
            log.info("自动注册漏掉的群: {}", groupOpenId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOfficialGroupAtMessage(OfficialGroupAtMessageCreateEvent event) {
        ensureGroupInCache(event.getGroupId());
        User user = event.getUser();
        recordGroupMessage(
                event.getGroupId(),
                user.getUserId(),
                user.getUsername(),
                event.getMessage().getContent(),
                event.getMessage().getMessageId(),
                user.isBot(),
                user.getRole().name(),
                "GROUP_AT_MESSAGE_CREATE",
                0,
                event.getTimestamp(),
                toJson(event.getMessage().getAttachments()),
                null,
                toJson(event.getMessage().getReference()),
                event.getMessage().getRefIdx()
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOfficialGroupMessage(OfficialGroupMessageCreateEvent event) {
        ensureGroupInCache(event.getGroupId());
        User user = event.getUser();
        recordGroupMessage(
                event.getGroupId(),
                user.getUserId(),
                user.getUsername(),
                event.getMessage().getContent(),
                event.getMessage().getMessageId(),
                user.isBot(),
                user.getRole().name(),
                "GROUP_MESSAGE_CREATE",
                event.getMessage().getType(),
                event.getTimestamp(),
                toJson(event.getMessage().getAttachments()),
                toJson(event.getMessage().getMentionedUsers()),
                toJson(event.getMessage().getReference()),
                event.getMessage().getRefIdx()
        );
    }

    public static void patchRefDisplayData(String messageOpenId, String refAuthor, String refContent, String refAttachments) {
        patchRefDisplayData(messageOpenId, refAuthor, refContent, refAttachments, null);
    }

    public static void patchRefDisplayData(String messageOpenId, String refAuthor, String refContent, String refAttachments, String refMsgIdx) {
        if (refAuthor == null && refContent == null && isBlank(refAttachments) && isBlank(refMsgIdx)) return;
        try {
            var refObj = objectMapper.createObjectNode();
            var authorNode = refObj.putObject("author");
            authorNode.put("username", refAuthor != null ? refAuthor : "Unknown");
            refObj.put("content", refContent != null ? refContent : "");
            if (!isBlank(refMsgIdx)) {
                refObj.put("msg_idx", refMsgIdx);
            }
            if (refAttachments != null && !refAttachments.isBlank()) {
                try { refObj.set("attachments", objectMapper.readTree(refAttachments)); } catch (Exception ignored) {}
            }
            String messageReference = objectMapper.writeValueAsString(List.of(refObj));
            String sql = "UPDATE `" + GROUP_TABLE + "` SET message_reference = ? WHERE message_openId = ?";
            try (var conn = DatabaseManager.getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, messageReference);
                stmt.setString(2, messageOpenId);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            log.error("更新引用消息展示数据失败, messageOpenId={}", messageOpenId, e);
        }
    }

    public static void recordSentGroupMessage(String groupOpenId, MessageBody request, String messageOpenId) {
        recordSentGroupMessage(groupOpenId, request, messageOpenId, null, null, null);
    }

    public static void recordSentGroupMessage(String groupOpenId, MessageBody request, String messageOpenId,
                                               String refAuthor, String refContent, String refAttachments) {
        String messageReference = null;
        // 如果前端传了引用消息的展示数据，直接拼成数组格式
        if (refAuthor != null || refContent != null) {
            try {
                var refObj = objectMapper.createObjectNode();
                var authorNode = refObj.putObject("author");
                authorNode.put("username", refAuthor != null ? refAuthor : "Unknown");
                refObj.put("content", refContent != null ? refContent : "");
                String refMsgIdx = extractReferenceMessageId(request);
                if (!isBlank(refMsgIdx)) {
                    refObj.put("msg_idx", refMsgIdx);
                }
                if (refAttachments != null && !refAttachments.isBlank()) {
                    try {
                        refObj.set("attachments", objectMapper.readTree(refAttachments));
                    } catch (Exception ignored) {}
                }
                messageReference = objectMapper.writeValueAsString(List.of(refObj));
            } catch (Exception e) {
                log.error("构建引用消息展示数据失败", e);
            }
        }
        recordGroupMessage(
                groupOpenId,
                BOT_UNION_OPEN_ID,
                BOT_USERNAME,
                extractContent(request),
                messageOpenId,
                true,
                null,
                "BOT_SEND",
                request.getMsgType(),
                nowLocalTime(),
                null,
                null,
                messageReference,
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
        String dataSql = "SELECT id, group_openId, union_openId, username, content, message_openId, " +
                "sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, mentions, message_reference, ref_idx, created_at " +
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
                records.add(toGroupMessageRecord(rs));
            }
        } catch (SQLException e) {
            log.error("查询官方群消息失败, groupOpenId={}, page={}, pageSize={}: {}", groupOpenId, safePage, safePageSize, e.getMessage(), e);
        }

        return new MessagePage<>(safePage, safePageSize, total, records);
    }

    public static GroupMessageLocation locateGroupMessageByRefIdx(String groupOpenId, String msgIdx, int pageSize, long excludeId) {
        if (isBlank(groupOpenId) || isBlank(msgIdx)) {
            return null;
        }

        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        String excludeClause = excludeId > 0 ? " AND id <> ?" : "";
        String targetSql = "SELECT id, group_openId, union_openId, username, content, message_openId, " +
                "sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, mentions, message_reference, ref_idx, created_at " +
                "FROM `" + GROUP_TABLE + "` WHERE group_openId = ? AND ref_idx = ?" + excludeClause + " ORDER BY id DESC LIMIT 1";
        String newerCountSql = "SELECT COUNT(*) FROM `" + GROUP_TABLE + "` WHERE group_openId = ? AND id > ?";

        try (var conn = DatabaseManager.getConnection();
             var targetStmt = conn.prepareStatement(targetSql);
             var countStmt = conn.prepareStatement(newerCountSql)) {
            targetStmt.setString(1, groupOpenId);
            targetStmt.setString(2, msgIdx);
            if (excludeId > 0) {
                targetStmt.setLong(3, excludeId);
            }

            var rs = targetStmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            GroupMessageRecord record = toGroupMessageRecord(rs);
            countStmt.setString(1, groupOpenId);
            countStmt.setLong(2, record.id());
            var countRs = countStmt.executeQuery();
            long newerCount = 0;
            if (countRs.next()) {
                newerCount = countRs.getLong(1);
            }
            int page = (int) (newerCount / safePageSize) + 1;
            return new GroupMessageLocation(page, safePageSize, record);
        } catch (SQLException e) {
            log.error("定位引用消息失败, groupOpenId={}, msgIdx={}: {}", groupOpenId, msgIdx, e.getMessage(), e);
            return null;
        }
    }

    public static GroupMessageLocation locateGroupMessageByReference(String groupOpenId, String msgIdx,
                                                                      String refAuthor, String refContent,
                                                                      String refAttachments, int pageSize,
                                                                      long excludeId) {
        GroupMessageLocation byMsgIdx = locateGroupMessageByRefIdx(groupOpenId, msgIdx, pageSize, excludeId);
        if (byMsgIdx != null) {
            return byMsgIdx;
        }
        return scanGroupMessageByReference(groupOpenId, refAuthor, refContent, refAttachments, pageSize, excludeId);
    }

    private static GroupMessageLocation scanGroupMessageByReference(String groupOpenId, String refAuthor,
                                                                    String refContent, String refAttachments,
                                                                    int pageSize, long excludeId) {
        if (isBlank(groupOpenId) || (isBlank(refContent) && isBlank(refAttachments))) {
            return null;
        }

        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        String idClause = excludeId > 0 ? " AND id < ?" : "";
        String scanSql = "SELECT id, group_openId, union_openId, username, content, message_openId, " +
                "sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, mentions, message_reference, ref_idx, created_at " +
                "FROM `" + GROUP_TABLE + "` WHERE group_openId = ?" + idClause + " ORDER BY id DESC LIMIT ?";
        String newerCountSql = "SELECT COUNT(*) FROM `" + GROUP_TABLE + "` WHERE group_openId = ? AND id > ?";

        String targetText = normalizeReferenceText(refContent);
        List<AttachmentFingerprint> targetAttachments = parseAttachmentFingerprints(refAttachments);
        String targetAuthor = emptyToNull(refAuthor);

        try (var conn = DatabaseManager.getConnection();
             var scanStmt = conn.prepareStatement(scanSql);
             var countStmt = conn.prepareStatement(newerCountSql)) {
            int idx = 1;
            scanStmt.setString(idx++, groupOpenId);
            if (excludeId > 0) {
                scanStmt.setLong(idx++, excludeId);
            }
            scanStmt.setInt(idx, REFERENCE_SCAN_LIMIT);

            var rs = scanStmt.executeQuery();
            while (rs.next()) {
                GroupMessageRecord record = toGroupMessageRecord(rs);
                if (!matchesReference(record, targetAuthor, targetText, targetAttachments)) {
                    continue;
                }

                countStmt.setString(1, groupOpenId);
                countStmt.setLong(2, record.id());
                var countRs = countStmt.executeQuery();
                long newerCount = 0;
                if (countRs.next()) {
                    newerCount = countRs.getLong(1);
                }
                int page = (int) (newerCount / safePageSize) + 1;
                return new GroupMessageLocation(page, safePageSize, record);
            }
        } catch (SQLException e) {
            log.error("扫描定位引用消息失败, groupOpenId={}, excludeId={}: {}", groupOpenId, excludeId, e.getMessage(), e);
        }
        return null;
    }

    private static boolean matchesReference(GroupMessageRecord record, String refAuthor, String refText,
                                            List<AttachmentFingerprint> refAttachments) {
        if (!isBlank(refAuthor) && !refAuthor.equals(record.username())) {
            return false;
        }

        if (!isBlank(refText)) {
            String content = normalizeReferenceText(record.content());
            if (refText.equals(content)) {
                return true;
            }
            if (refText.length() >= 6 && !isBlank(content)
                    && (content.contains(refText) || refText.contains(content))) {
                return true;
            }
        }

        if (!refAttachments.isEmpty()) {
            List<AttachmentFingerprint> attachments = parseAttachmentFingerprints(record.attachments());
            for (AttachmentFingerprint refAttachment : refAttachments) {
                for (AttachmentFingerprint attachment : attachments) {
                    if (refAttachment.matches(attachment)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static String normalizeReferenceText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("<faceType=\\d+,faceId=\"[^\"]*\",ext=\"[^\"]*\">", "[表情]")
                .replaceAll("<qqbot-at-user id=\"([A-F0-9]+)\"\\s*/>", "@$1")
                .replaceAll("<qqbot-cmd-input[^>]*show=\"([^\"]*)\"[^>]*/>", "$1")
                .replaceAll("(<@[A-F0-9]+>)\\s+\\1", "$1")
                .trim();
    }

    private static List<AttachmentFingerprint> parseAttachmentFingerprints(String raw) {
        if (isBlank(raw)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isArray()) {
                return List.of();
            }
            List<AttachmentFingerprint> result = new ArrayList<>();
            for (JsonNode item : node) {
                String url = item.path("url").asText(null);
                String voiceWavUrl = item.path("voice_wav_url").asText(null);
                String filename = item.path("filename").asText(null);
                if (!isBlank(url) || !isBlank(voiceWavUrl) || !isBlank(filename)) {
                    result.add(new AttachmentFingerprint(url, voiceWavUrl, filename));
                }
            }
            return result;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String extractReferenceMessageId(MessageBody request) {
        if (request == null || request.getMessageReference() == null) {
            return null;
        }
        try {
            JsonNode refNode = objectMapper.valueToTree(request.getMessageReference());
            JsonNode midNode = refNode.get("message_id");
            if (midNode != null && midNode.isTextual()) {
                return midNode.asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static GroupMessageRecord toGroupMessageRecord(java.sql.ResultSet rs) throws SQLException {
        return new GroupMessageRecord(
                rs.getLong("id"),
                rs.getString("group_openId"),
                rs.getString("union_openId"),
                rs.getString("username"),
                rs.getString("content"),
                rs.getString("message_openId"),
                rs.getBoolean("sender_is_bot"),
                rs.getString("member_role"),
                rs.getString("event_type"),
                (Integer) rs.getObject("message_type"),
                rs.getString("event_timestamp"),
                rs.getString("attachments"),
                rs.getString("mentions"),
                rs.getString("message_reference"),
                rs.getString("ref_idx"),
                rs.getString("created_at")
        );
    }

    private static void recordGroupMessage(String groupOpenId, String unionOpenId, String username, String content,
                                           String messageOpenId, boolean senderIsBot, String memberRole,
                                           String source, Integer messageType, String eventTimestamp,
                                           String attachments, String mentions, String messageReference, String refIdx) {
        if (isBlank(groupOpenId)) {
            log.warn("跳过官方群消息记录：groupOpenId 为空, eventType={}, messageOpenId={}", source, messageOpenId);
            return;
        }

        String sql = "INSERT INTO `" + GROUP_TABLE + "` " +
                "(group_openId, union_openId, username, content, message_openId, sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, mentions, message_reference, ref_idx) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "content = VALUES(content), " +
                "username = VALUES(username), " +
                "sender_is_bot = VALUES(sender_is_bot), " +
                "member_role = COALESCE(VALUES(member_role), member_role), " +
                "event_type = VALUES(event_type), " +
                "message_type = COALESCE(VALUES(message_type), message_type), " +
                "event_timestamp = COALESCE(VALUES(event_timestamp), event_timestamp), " +
                "attachments = COALESCE(VALUES(attachments), attachments), " +
                "mentions = COALESCE(VALUES(mentions), mentions), " +
                "message_reference = COALESCE(VALUES(message_reference), message_reference), " +
                "ref_idx = COALESCE(VALUES(ref_idx), ref_idx)";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupOpenId);
            stmt.setString(2, emptyToNull(unionOpenId));
            stmt.setString(3, emptyToNull(username));
            stmt.setString(4, content);
            stmt.setString(5, emptyToNull(messageOpenId));
            stmt.setBoolean(6, senderIsBot);
            stmt.setString(7, emptyToNull(memberRole));
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
            stmt.setString(14, emptyToNull(refIdx));
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

    private static void ensureIndex(String tableName, String indexName, String columns) {
        String sql = "ALTER TABLE `" + tableName + "` ADD INDEX `" + indexName + "` (" + columns + ")";
        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1061) {
                return;
            }
            log.error("为表 {} 补充索引 {} 失败: {}", tableName, indexName, e.getMessage(), e);
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
                                     String content, String messageOpenId,
                                     boolean senderIsBot, String memberRole,
                                     String eventType,
                                     Integer messageType, String eventTimestamp,
                                     String attachments, String mentions, String messageReference,
                                     String refIdx, String createdAt) {
    }

    public record GroupMessageLocation(int page, int pageSize, GroupMessageRecord record) {
    }

    private record AttachmentFingerprint(String url, String voiceWavUrl, String filename) {
        boolean matches(AttachmentFingerprint other) {
            return other != null
                    && ((!isBlank(url) && url.equals(other.url))
                    || (!isBlank(voiceWavUrl) && voiceWavUrl.equals(other.voiceWavUrl))
                    || (!isBlank(filename) && filename.equals(other.filename)));
        }
    }

    public record C2CMessageRecord(long id, String unionOpenId, String username,
                                   String content, String messageOpenId,
                                   boolean senderIsBot, Integer messageType,
                                   String eventTimestamp, String createdAt) {
    }

    /**
     * 跨所有群聚合查询，用于 WebUI 用户列表页面。
     */
    public static MessagePage<AllGroupUserRecord> fetchAllGroupMessages(int page, int pageSize, String search) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePage - 1) * safePageSize;

        boolean hasSearch = search != null && !search.isBlank();
        String likePattern = hasSearch ? "%" + search.trim() + "%" : null;

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM `")
                .append(GROUP_TABLE)
                .append("` WHERE sender_is_bot = FALSE");
        StringBuilder dataSql = new StringBuilder("SELECT union_openId, username, group_openId, content, member_role, ")
                .append("message_type, attachments, mentions, event_timestamp, created_at FROM `")
                .append(GROUP_TABLE)
                .append("` WHERE sender_is_bot = FALSE");

        if (hasSearch) {
            String searchClause = " AND (username LIKE ? OR union_openId LIKE ? OR group_openId LIKE ? OR content LIKE ?)";
            countSql.append(searchClause);
            dataSql.append(searchClause);
        }

        dataSql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");

        long total = 0;
        List<AllGroupUserRecord> records = new ArrayList<>();

        try (var conn = DatabaseManager.getConnection();
             var countStmt = conn.prepareStatement(countSql.toString());
             var dataStmt = conn.prepareStatement(dataSql.toString())) {

            int countIdx = 1;
            if (hasSearch) {
                countStmt.setString(countIdx++, likePattern);
                countStmt.setString(countIdx++, likePattern);
                countStmt.setString(countIdx++, likePattern);
                countStmt.setString(countIdx++, likePattern);
            }
            var countRs = countStmt.executeQuery();
            if (countRs.next()) total = countRs.getLong(1);

            int dataIdx = 1;
            if (hasSearch) {
                dataStmt.setString(dataIdx++, likePattern);
                dataStmt.setString(dataIdx++, likePattern);
                dataStmt.setString(dataIdx++, likePattern);
                dataStmt.setString(dataIdx++, likePattern);
            }
            dataStmt.setInt(dataIdx++, safePageSize);
            dataStmt.setInt(dataIdx, offset);
            var rs = dataStmt.executeQuery();
            while (rs.next()) {
                String unionOpenId = rs.getString("union_openId");
                String userRole = "-";
                if (unionOpenId != null && !unionOpenId.isBlank()) {
                    var userData = OfficialUsers.getData(unionOpenId);
                    if (userData != null) userRole = userData.role().name();
                }
                records.add(new AllGroupUserRecord(
                        unionOpenId,
                        rs.getString("username"),
                        rs.getString("group_openId"),
                        rs.getString("content"),
                        rs.getString("member_role"),
                        userRole,
                        (Integer) rs.getObject("message_type"),
                        rs.getString("attachments"),
                        rs.getString("mentions"),
                        rs.getString("event_timestamp"),
                        rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            log.error("聚合查询所有群消息失败, page={}, pageSize={}: {}", safePage, safePageSize, e.getMessage(), e);
        }

        return new MessagePage<>(safePage, safePageSize, total, records);
    }

    public record AllGroupUserRecord(String unionOpenId, String username, String groupOpenId,
                                     String content, String memberRole, String userRole,
                                     Integer messageType, String attachments, String mentions,
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
