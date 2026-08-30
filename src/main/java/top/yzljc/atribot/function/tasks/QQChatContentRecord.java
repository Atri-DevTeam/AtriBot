package top.yzljc.atribot.function.tasks;

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
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.webui.SseBroadcaster;
import top.yzljc.atribot.webui.repo.ChatStatsSnapshotRepo;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class QQChatContentRecord implements Listener {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String GROUP_TABLE = "official_group_record";
    private static final String C2C_TABLE = "official_c2c_record";
    private static final String BOT_UNION_OPEN_ID = Config.getInstance().getOfficialOpenId();
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
                "  `ark` MEDIUMTEXT NULL," +
                "  `mentions` MEDIUMTEXT NULL," +
                "  `message_reference` MEDIUMTEXT NULL," +
                "  `ref_idx` VARCHAR(256) NULL," +
                "  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_group_message_openId` (`message_openId`)," +
                "  KEY `idx_group_openId` (`group_openId`)," +
                "  KEY `idx_group_union_openId` (`union_openId`)," +
                "  KEY `idx_group_ref_idx` (`ref_idx`)," +
                "  KEY `idx_group_created_at` (`created_at`)," +
                "  KEY `idx_group_sender_created` (`sender_is_bot`, `created_at`)," +
                "  KEY `idx_group_open_sender_created` (`group_openId`, `sender_is_bot`, `created_at`)," +
                "  KEY `idx_group_union_sender_created` (`union_openId`, `sender_is_bot`, `created_at`)," +
                "  KEY `idx_group_event_created` (`event_type`, `created_at`)," +
                "  KEY `idx_group_open_event_created` (`group_openId`, `event_type`, `created_at`)," +
                "  KEY `idx_group_union_event_created` (`union_openId`, `event_type`, `created_at`)" +
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
                "  `attachments` MEDIUMTEXT NULL," +
                "  `ark` MEDIUMTEXT NULL," +
                "  `message_reference` MEDIUMTEXT NULL," +
                "  `ref_idx` VARCHAR(256) NULL," +
                "  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_c2c_message_openId` (`message_openId`)," +
                "  KEY `idx_c2c_union_openId` (`union_openId`)," +
                "  KEY `idx_c2c_ref_idx` (`ref_idx`)," +
                "  KEY `idx_c2c_created_at` (`created_at`)," +
                "  KEY `idx_c2c_sender_created` (`sender_is_bot`, `created_at`)," +
                "  KEY `idx_c2c_union_sender_created` (`union_openId`, `sender_is_bot`, `created_at`)," +
                "  KEY `idx_c2c_source_created` (`source`, `created_at`)," +
                "  KEY `idx_c2c_union_source_created` (`union_openId`, `source`, `created_at`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var conn = DatabaseManager.getConnection();
             var groupStmt = conn.prepareStatement(groupSql);
             var c2cStmt = conn.prepareStatement(c2cSql)) {
            groupStmt.execute();
            c2cStmt.execute();
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

    /*
     * 入站消息一律用 LOWEST 优先级落库：CommandManager 等业务监听器跑在 NORMAL，
     * 且 QQSenderImpl -> C2CChat/GroupChat 的发送是 future.get() 阻塞的，
     * 机器人回复会在业务监听器里先写进表拿到更小的自增 id。
     * WebUI 按 id 排序展示，这会让回复排在用户原消息前面，因此必须先于业务监听器记录。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
                event.getMessage().getType(),
                event.getTimestamp(),
                toJson(event.getMessage().getAttachments()),
                toJsonOrNull(event.getMessage().getArk()),
                toJson(event.getMessage().getReference()),
                event.getMessage().getRefIdx()
        );
    }

    private static void ensureGroupInCache(String groupOpenId) {
        if (!OfficialGroups.isCached(groupOpenId)) {
            OfficialGroups.registerGroup(groupOpenId, null, String.valueOf(System.currentTimeMillis() / 1000));
            log.info("自动注册漏掉的群: {}", groupOpenId);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
                event.getMessage().getType(),
                event.getTimestamp(),
                toJson(event.getMessage().getAttachments()),
                toJsonOrNull(event.getMessage().getArk()),
                null,
                toJson(event.getMessage().getReference()),
                event.getMessage().getRefIdx()
        );
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
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
                toJsonOrNull(event.getMessage().getArk()),
                toJson(event.getMessage().getMentionedUsers()),
                toJson(event.getMessage().getReference()),
                event.getMessage().getRefIdx()
        );
    }

    public static void patchRefDisplayData(String messageOpenId, String refAuthor, String refContent, String refAttachments, String refMsgIdx) {
        patchRefDisplayData(GROUP_TABLE, messageOpenId, refAuthor, refContent, refAttachments, refMsgIdx);
    }

    public static void patchC2CRefDisplayData(String messageOpenId, String refAuthor, String refContent,
                                              String refAttachments, String refMsgIdx) {
        patchRefDisplayData(C2C_TABLE, messageOpenId, refAuthor, refContent, refAttachments, refMsgIdx);
    }

    private static void patchRefDisplayData(String table, String messageOpenId, String refAuthor, String refContent,
                                            String refAttachments, String refMsgIdx) {
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
                try {
                    refObj.set("attachments", objectMapper.readTree(refAttachments));
                } catch (Exception ignored) {
                }
            }
            String messageReference = objectMapper.writeValueAsString(List.of(refObj));
            String sql = "UPDATE `" + table + "` SET message_reference = ? WHERE message_openId = ?";
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

    public static void recordSentGroupMessage(String groupOpenId, MessageBody request, String messageOpenId, String refIdx, String timestamp) {
        String messageReference = buildReferenceDisplayJson(true, extractReferenceMessageId(request));
        recordGroupMessage(
                groupOpenId,
                BOT_UNION_OPEN_ID,
                QQBot.BOT_NAME,
                extractContent(request),
                messageOpenId,
                true,
                null,
                "BOT_SEND",
                request.getMsgType(),
                timestamp,
                request.getRecordAttachments(),
                toJsonOrNull(request.getArk()),
                null,
                messageReference,
                refIdx
        );
    }

    /**
     * 前端传了引用消息的展示数据时拼成数组格式，供 WebUI 渲染引用块和定位来源
     */
    private static String buildReferenceDisplayJson(boolean group, String refIdx) {
        if (refIdx == null || isBlank(refIdx)) {
            return null;
        }
        ReferenceSource dbSource = findReferenceDisplaySource(group, refIdx);
        String refAuthor = null;
        String refContent = null;
        String refAttachments = null;
        if (dbSource != null) {
            refAuthor = dbSource.username();
            refContent = dbSource.content();
            refAttachments = dbSource.attachments();
        }
        try {
            var refObj = objectMapper.createObjectNode();
            var authorNode = refObj.putObject("author");
            authorNode.put("username", refAuthor != null ? refAuthor : "Unknown");
            refObj.put("content", refContent != null ? refContent : "");
            if (!isBlank(refIdx)) {
                refObj.put("msg_idx", refIdx);
            }
            if (refAttachments != null && !refAttachments.isBlank()) {
                try {
                    refObj.set("attachments", objectMapper.readTree(refAttachments));
                } catch (Exception ignored) {
                }
            }
            return objectMapper.writeValueAsString(List.of(refObj));
        } catch (Exception e) {
            log.error("构建引用消息展示数据失败", e);
            return null;
        }
    }

    private static ReferenceSource findReferenceDisplaySource(boolean group, String refMsgIdx) {
        String table = group ? GROUP_TABLE : C2C_TABLE;
        if (refMsgIdx == null || isBlank(refMsgIdx)) {
            return null;
        }
        String sql = "SELECT username, content, attachments, ref_idx FROM `" + table + "` " +
                "WHERE ref_idx = ? ORDER BY id DESC LIMIT 1";
        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, refMsgIdx);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return new ReferenceSource(
                        rs.getString("username"),
                        rs.getString("content"),
                        rs.getString("attachments"),
                        rs.getString("ref_idx")
                );
            }
        } catch (SQLException e) {
            log.error("按 ref_idx 查询引用展示数据失败, table={}, refIdx={}: {}", table, refMsgIdx, e.getMessage(), e);
        }
        return null;
    }

    public static void recordSentC2CMessage(String userOpenId, MessageBody request, String messageOpenId, String refIdx, String timestamp) {
        recordC2CMessage(
                null,
                userOpenId,
                QQBot.BOT_NAME,
                extractContent(request),
                messageOpenId,
                true,
                "BOT_SEND",
                request.getMsgType(),
                timestamp,
                request.getRecordAttachments(),
                toJsonOrNull(request.getArk()),
                buildReferenceDisplayJson(false, extractReferenceMessageId(request)),
                refIdx
        );
    }

    public static MessagePage<GroupMessageRecord> fetchGroupMessages(String groupOpenId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePage - 1) * safePageSize;

        String countSql = "SELECT COUNT(*) FROM `" + GROUP_TABLE + "` WHERE group_openId = ?";
        String dataSql = "SELECT id, group_openId, union_openId, username, content, message_openId, " +
                "sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, ark, mentions, message_reference, ref_idx, created_at " +
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
                records.add(toGroupMessageRecord(conn, rs));
            }
        } catch (SQLException e) {
            log.error("查询官方群消息失败, groupOpenId={}, page={}, pageSize={}: {}", groupOpenId, safePage, safePageSize, e.getMessage(), e);
        }

        return new MessagePage<>(safePage, safePageSize, total, records);
    }

    public static ClearResult clearGroupMessages(String groupOpenId, String mode, int count,
                                                 String startDate, String endDate) {
        return clearMessages(GROUP_TABLE, "group_openId", groupOpenId, mode, count, startDate, endDate);
    }

    public static ClearResult clearC2CMessages(String userOpenId, String mode, int count,
                                               String startDate, String endDate) {
        return clearMessages(C2C_TABLE, "union_openId", userOpenId, mode, count, startDate, endDate);
    }

    private static ClearResult clearMessages(String table, String scopeColumn, String scopeValue,
                                             String mode, int count, String startDate, String endDate) {
        if (isBlank(scopeValue)) throw new IllegalArgumentException("会话标识不能为空");
        String safeMode = isBlank(mode) ? "all" : mode;
        if (!("all".equals(safeMode) || "first".equals(safeMode) || "range".equals(safeMode))) {
            throw new IllegalArgumentException("清理模式无效");
        }
        if ("first".equals(safeMode) && (count <= 0 || count > 1_000_000)) {
            throw new IllegalArgumentException("清理条数必须在 1 到 1000000 之间");
        }

        String timeExpr = "COALESCE(STR_TO_DATE(REPLACE(SUBSTRING(event_timestamp, 1, 19), 'T', ' '), '%Y-%m-%d %H:%i:%s'), created_at)";
        StringBuilder sql = new StringBuilder("DELETE FROM `").append(table).append("` WHERE `")
                .append(scopeColumn).append("` = ?");
        List<Object> params = new ArrayList<>();
        params.add(scopeValue);
        if ("first".equals(safeMode)) {
            sql = new StringBuilder("DELETE FROM `").append(table).append("` WHERE id IN (SELECT id FROM (SELECT id FROM `")
                    .append(table).append("` WHERE `").append(scopeColumn).append("` = ? ORDER BY id ASC LIMIT ?) selected_ids)");
            params.add(count);
        } else if ("range".equals(safeMode)) {
            LocalDate start = parseDate(startDate, "开始日期");
            LocalDate end = parseDate(endDate, "结束日期");
            if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不能早于开始日期");
            sql.append(" AND ").append(timeExpr).append(" >= ? AND ").append(timeExpr).append(" < ?");
            params.add(start.atStartOfDay());
            params.add(end.plusDays(1).atStartOfDay());
        }

        ChatStatsSnapshotRepo.ensureInitialized();
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object value = params.get(i);
                if (value instanceof Integer integer) stmt.setInt(i + 1, integer);
                else if (value instanceof LocalDateTime dateTime) stmt.setString(i + 1, dateTime.format(LOCAL_TIME_FORMATTER));
                else stmt.setString(i + 1, String.valueOf(value));
            }
            long deleted = stmt.executeUpdate();
            return new ClearResult(deleted);
        } catch (SQLException e) {
            log.error("清理聊天记录失败, table={}, scope={}: {}", table, scopeValue, e.getMessage(), e);
            throw new IllegalStateException("清理聊天记录失败: " + e.getMessage(), e);
        }
    }

    private static LocalDate parseDate(String value, String label) {
        if (isBlank(value)) throw new IllegalArgumentException(label + "不能为空");
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + "格式无效，应为 yyyy-MM-dd");
        }
    }

    public static GroupMessageLocation locateGroupMessageByRefIdx(String groupOpenId, String msgIdx, int pageSize, long excludeId) {
        if (isBlank(groupOpenId) || isBlank(msgIdx)) {
            return null;
        }

        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        String excludeClause = excludeId > 0 ? " AND id <> ?" : "";
        String targetSql = "SELECT id, group_openId, union_openId, username, content, message_openId, " +
                "sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, ark, mentions, message_reference, ref_idx, created_at " +
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

            GroupMessageRecord record = toGroupMessageRecord(conn, rs);
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
                "sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, ark, mentions, message_reference, ref_idx, created_at " +
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
                GroupMessageRecord record = toGroupMessageRecord(conn, rs);
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

    private static GroupMessageRecord toGroupMessageRecord(java.sql.Connection conn, java.sql.ResultSet rs) throws SQLException {
        String groupOpenId = rs.getString("group_openId");
        return new GroupMessageRecord(
                rs.getLong("id"),
                groupOpenId,
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
                rs.getString("ark"),
                rs.getString("mentions"),
                enrichMessageReference(conn, groupOpenId, rs.getString("message_reference")),
                rs.getString("ref_idx"),
                rs.getString("created_at")
        );
    }

    private static String enrichMessageReference(java.sql.Connection conn, String groupOpenId, String rawReference) {
        return enrichMessageReference(conn, GROUP_TABLE, "group_openId", groupOpenId, rawReference);
    }

    /**
     * 原始引用数据里往往只有一个 msg_idx，缺发送者和正文，渲染出来是空引用块。
     * 这里回查同会话的历史记录补全展示字段，群聊和私聊只差表名和会话键列。
     */
    private static String enrichMessageReference(java.sql.Connection conn, String table, String keyColumn,
                                                 String keyValue, String rawReference) {
        if (isBlank(keyValue) || isBlank(rawReference)) {
            return rawReference;
        }
        try {
            JsonNode root = objectMapper.readTree(rawReference);
            JsonNode refNode = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            if (refNode == null || refNode.isMissingNode() || refNode.isNull()) {
                return rawReference;
            }
            if (isReferenceComplete(refNode)) {
                return rawReference;
            }

            ReferenceSource source = findReferenceSource(conn, table, keyColumn, keyValue, refNode);
            if (source == null) {
                return rawReference;
            }

            var enrichedRoot = root.deepCopy();
            JsonNode enrichedRefNode = enrichedRoot.isArray() && !enrichedRoot.isEmpty() ? enrichedRoot.get(0) : enrichedRoot;
            if (!(enrichedRefNode instanceof com.fasterxml.jackson.databind.node.ObjectNode refObj)) {
                return rawReference;
            }

            JsonNode authorJson = refObj.get("author");
            com.fasterxml.jackson.databind.node.ObjectNode authorNode;
            if (authorJson instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode) {
                authorNode = objectNode;
            } else {
                authorNode = objectMapper.createObjectNode();
                refObj.set("author", authorNode);
            }
            if (isBlank(findReferenceAuthor(refObj))) {
                authorNode.put("username", emptyToNull(source.username()));
            }
            if (isBlank(findReferenceContent(refObj))) {
                refObj.put("content", source.content() == null ? "" : source.content());
            }
            if (!hasReferenceAttachments(refObj) && !isBlank(source.attachments())) {
                try {
                    refObj.set("attachments", objectMapper.readTree(source.attachments()));
                } catch (Exception ignored) {
                }
            }
            if (isBlank(findRefIdxValue(refObj)) && !isBlank(source.refIdx())) {
                refObj.put("msg_idx", source.refIdx());
            }
            return objectMapper.writeValueAsString(enrichedRoot);
        } catch (Exception e) {
            log.debug("补全引用消息展示数据失败: {}", e.getMessage());
            return rawReference;
        }
    }

    private static ReferenceSource findReferenceSource(java.sql.Connection conn, String table, String keyColumn,
                                                       String keyValue, JsonNode refNode) throws SQLException {
        String refMsgIdx = findRefIdxValue(refNode);
        if (!isBlank(refMsgIdx)) {
            String sql = "SELECT username, content, attachments, ref_idx FROM `" + table + "` " +
                    "WHERE `" + keyColumn + "` = ? AND ref_idx = ? ORDER BY id DESC LIMIT 1";
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, keyValue);
                stmt.setString(2, refMsgIdx);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return new ReferenceSource(
                            rs.getString("username"),
                            rs.getString("content"),
                            rs.getString("attachments"),
                            rs.getString("ref_idx")
                    );
                }
            }
        }

        String targetAuthor = emptyToNull(findReferenceAuthor(refNode));
        String targetText = normalizeReferenceText(findReferenceContent(refNode));
        List<AttachmentFingerprint> targetAttachments = referenceAttachments(refNode);
        if (isBlank(targetText) && targetAttachments.isEmpty()) {
            return null;
        }

        String scanSql = "SELECT username, content, attachments, ref_idx FROM `" + table + "` " +
                "WHERE `" + keyColumn + "` = ? ORDER BY id DESC LIMIT ?";
        try (var stmt = conn.prepareStatement(scanSql)) {
            stmt.setString(1, keyValue);
            stmt.setInt(2, REFERENCE_SCAN_LIMIT);
            var rs = stmt.executeQuery();
            while (rs.next()) {
                String username = rs.getString("username");
                if (!isBlank(targetAuthor) && !targetAuthor.equals(username)) {
                    continue;
                }

                if (matchesReferenceSource(
                        rs.getString("content"),
                        rs.getString("attachments"),
                        targetText,
                        targetAttachments
                )) {
                    return new ReferenceSource(
                            username,
                            rs.getString("content"),
                            rs.getString("attachments"),
                            rs.getString("ref_idx")
                    );
                }
            }
        }
        return null;
    }

    private static boolean isReferenceComplete(JsonNode node) {
        return !isBlank(findReferenceAuthor(node))
                && (!isBlank(findReferenceContent(node)) || hasReferenceAttachments(node));
    }

    private static boolean matchesReferenceSource(String content, String attachments,
                                                  String refText, List<AttachmentFingerprint> refAttachments) {
        if (!isBlank(refText)) {
            String normalizedContent = normalizeReferenceText(content);
            if (refText.equals(normalizedContent)) {
                return true;
            }
            if (refText.length() >= 6 && !isBlank(normalizedContent)
                    && (normalizedContent.contains(refText) || refText.contains(normalizedContent))) {
                return true;
            }
        }

        if (!refAttachments.isEmpty()) {
            List<AttachmentFingerprint> sourceAttachments = parseAttachmentFingerprints(attachments);
            for (AttachmentFingerprint refAttachment : refAttachments) {
                for (AttachmentFingerprint sourceAttachment : sourceAttachments) {
                    if (refAttachment.matches(sourceAttachment)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static List<AttachmentFingerprint> referenceAttachments(JsonNode node) {
        JsonNode attachments = node.get("attachments");
        if (attachments == null || !attachments.isArray()) {
            return List.of();
        }
        return parseAttachmentFingerprints(attachments.toString());
    }

    private record ReferenceSource(String username, String content, String attachments, String refIdx) {
    }

    private static String findReferenceAuthor(JsonNode node) {
        return firstJsonText(
                node.at("/author/username"),
                node.at("/author/user_name"),
                node.at("/author/nickname"),
                node.at("/author/nick"),
                node.at("/author/name"),
                node.at("/sender/username"),
                node.at("/sender/user_name"),
                node.at("/sender/nickname"),
                node.at("/sender/nick"),
                node.at("/sender/name"),
                node.at("/user/username"),
                node.at("/user/user_name"),
                node.at("/user/nickname"),
                node.at("/user/nick"),
                node.at("/user/name"),
                node.get("username"),
                node.get("user_name"),
                node.get("nickname"),
                node.get("nick"),
                node.get("author_name"),
                node.get("sender_name")
        );
    }

    private static String findReferenceContent(JsonNode node) {
        return firstJsonText(
                node.get("content"),
                node.get("text"),
                node.get("message"),
                node.get("raw_message"),
                node.get("rawMessage")
        );
    }

    private static boolean hasReferenceAttachments(JsonNode node) {
        JsonNode attachments = node.get("attachments");
        return attachments != null && attachments.isArray() && !attachments.isEmpty();
    }

    private static String findRefIdxValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            String direct = firstJsonText(
                    node.get("msg_idx"),
                    node.get("msgIdx"),
                    node.get("ref_idx"),
                    node.get("refIdx"),
                    node.get("message_id"),
                    node.get("messageId"),
                    node.get("msg_id"),
                    node.get("msgId")
            );
            if (!isBlank(direct)) {
                return direct;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = findRefIdxValue(fields.next().getValue());
                if (!isBlank(found)) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String found = findRefIdxValue(item);
                if (!isBlank(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String firstJsonText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private static void recordGroupMessage(String groupOpenId, String unionOpenId, String username, String content,
                                           String messageOpenId, boolean senderIsBot, String memberRole,
                                           String source, Integer messageType, String eventTimestamp,
                                           String attachments, String ark, String mentions, String messageReference, String refIdx) {
        if (isBlank(groupOpenId)) {
            log.warn("跳过官方群消息记录：groupOpenId 为空, eventType={}, messageOpenId={}", source, messageOpenId);
            return;
        }
        ChatStatsSnapshotRepo.ensureInitialized();

        String sql = "INSERT INTO `" + GROUP_TABLE + "` " +
                "(group_openId, union_openId, username, content, message_openId, sender_is_bot, member_role, event_type, message_type, event_timestamp, attachments, ark, mentions, message_reference, ref_idx) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "content = VALUES(content), " +
                "username = VALUES(username), " +
                "sender_is_bot = VALUES(sender_is_bot), " +
                "member_role = COALESCE(VALUES(member_role), member_role), " +
                "event_type = VALUES(event_type), " +
                "message_type = COALESCE(VALUES(message_type), message_type), " +
                "event_timestamp = COALESCE(VALUES(event_timestamp), event_timestamp), " +
                "attachments = COALESCE(VALUES(attachments), attachments), " +
                "ark = COALESCE(VALUES(ark), ark), " +
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
            stmt.setString(12, emptyToNull(ark));
            stmt.setString(13, emptyToNull(mentions));
            stmt.setString(14, emptyToNull(messageReference));
             stmt.setString(15, emptyToNull(refIdx));
             int affected = stmt.executeUpdate();

             if (affected == 1) {
                 ChatStatsSnapshotRepo.recordGroupMessage(groupOpenId, unionOpenId, username, senderIsBot, eventTimestamp);
             }

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

//    private static void ensureColumn(String tableName, String columnName, String definition) {
//        String sql = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + definition;
//        try (var conn = DatabaseManager.getConnection();
//             var stmt = conn.prepareStatement(sql)) {
//            stmt.execute();
//        } catch (SQLException e) {
//            if (e.getErrorCode() == 1060 || "42S21".equals(e.getSQLState())) {
//                return;
//            }
//            log.error("为表 {} 补充字段 {} 失败: {}", tableName, columnName, e.getMessage(), e);
//        }
//    }
//
//    private static void ensureIndex(String tableName, String indexName, String columns) {
//        String sql = "ALTER TABLE `" + tableName + "` ADD INDEX `" + indexName + "` (" + columns + ")";
//        try (var conn = DatabaseManager.getConnection();
//             var stmt = conn.prepareStatement(sql)) {
//            stmt.execute();
//        } catch (SQLException e) {
//            if (e.getErrorCode() == 1061) {
//                return;
//            }
//            log.error("为表 {} 补充索引 {} 失败: {}", tableName, indexName, e.getMessage(), e);
//        }
//    }

    private static void recordC2CMessage(String userOpenId, String unionOpenId, String username, String content,
                                         String messageOpenId, boolean senderIsBot, String source,
                                         Integer messageType, String eventTimestamp, String attachments, String ark,
                                         String messageReference, String refIdx) {
        String conversationOpenId = firstNonBlank(unionOpenId, userOpenId);
        if (isBlank(conversationOpenId)) {
            log.warn("跳过官方 C2C 消息记录：userOpenId/unionOpenId 为空, eventType={}, messageOpenId={}", source, messageOpenId);
            return;
        }
        ChatStatsSnapshotRepo.ensureInitialized();

        String sql = "INSERT INTO `" + C2C_TABLE + "` " +
                "(union_openId, username, content, message_openId, sender_is_bot, source, message_type, event_timestamp, attachments, ark, message_reference, ref_idx) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE content = VALUES(content), username = COALESCE(VALUES(username), username), source = VALUES(source), " +
                "attachments = COALESCE(VALUES(attachments), attachments), " +
                "ark = COALESCE(VALUES(ark), ark), " +
                "message_reference = COALESCE(VALUES(message_reference), message_reference), " +
                "ref_idx = COALESCE(VALUES(ref_idx), ref_idx)";

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
            stmt.setString(9, emptyToNull(attachments));
            stmt.setString(10, emptyToNull(ark));
            stmt.setString(11, emptyToNull(messageReference));
             stmt.setString(12, emptyToNull(refIdx));
             int affected = stmt.executeUpdate();

             if (affected == 1) {
                 ChatStatsSnapshotRepo.recordC2CMessage(conversationOpenId, username, senderIsBot, source, eventTimestamp);
             }

            // SSE — C2C 刷新信号
            try {
                var payload = objectMapper.createObjectNode();
                payload.put("type", "c2c_refresh");
                payload.put("userOpenId", conversationOpenId);
                SseBroadcaster.broadcast(objectMapper.writeValueAsString(payload));
            } catch (Exception ignored) {
            }
        } catch (SQLException e) {
            log.error("记录官方 C2C 消息失败, userOpenId={}, unionOpenId={}, messageOpenId={}: {}",
                    userOpenId, unionOpenId, messageOpenId, e.getMessage(), e);
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
            if (!isBlank(request.getRecordAttachments())) {
                return "";
            }
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

    private static String toJsonOrNull(Object value) {
        if (value instanceof JsonNode node && (node.isNull() || node.isMissingNode())) {
            return null;
        }
        return value == null ? null : toJson(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

//    private static String nowLocalTime() {
//        return LocalDateTime.now().format(LOCAL_TIME_FORMATTER);
//    }

    private static String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record MessagePage<T>(int page, int pageSize, long total, List<T> records) {
    }

    public record ClearResult(long deleted) {
    }

    public record GroupMessageRecord(long id, String groupOpenId, String unionOpenId, String username,
                                     String content, String messageOpenId,
                                     boolean senderIsBot, String memberRole,
                                     String eventType,
                                     Integer messageType, String eventTimestamp,
                                     String attachments, String ark, String mentions, String messageReference,
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
                                   String eventTimestamp, String attachments, String ark,
                                   String messageReference, String refIdx, String createdAt) {
    }

    public record C2CMessageLocation(int page, int pageSize, C2CMessageRecord record) {
    }

    public static MessagePage<C2CMessageRecord> fetchC2CMessages(String userOpenId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        int offset = (safePage - 1) * safePageSize;

        String countSql = "SELECT COUNT(*) FROM `" + C2C_TABLE + "` WHERE union_openId = ?";
        String dataSql = "SELECT id, union_openId, username, content, message_openId, " +
                "sender_is_bot, message_type, event_timestamp, attachments, ark, message_reference, ref_idx, created_at " +
                "FROM `" + C2C_TABLE + "` WHERE union_openId = ? ORDER BY id DESC LIMIT ? OFFSET ?";

        long total = 0;
        List<C2CMessageRecord> records = new ArrayList<>();

        try (var conn = DatabaseManager.getConnection();
             var countStmt = conn.prepareStatement(countSql);
             var dataStmt = conn.prepareStatement(dataSql)) {
            String fallbackUsername = findLatestKnownUsername(conn, userOpenId);
            countStmt.setString(1, userOpenId);
            var countRs = countStmt.executeQuery();
            if (countRs.next()) total = countRs.getLong(1);

            dataStmt.setString(1, userOpenId);
            dataStmt.setInt(2, safePageSize);
            dataStmt.setInt(3, offset);
            var rs = dataStmt.executeQuery();
            while (rs.next()) {
                boolean senderIsBot = rs.getBoolean("sender_is_bot");
                String username = rs.getString("username");
                if (!senderIsBot && isBlank(username)) {
                    username = fallbackUsername;
                }
                records.add(toC2CMessageRecord(conn, rs, username, senderIsBot));
            }
        } catch (SQLException e) {
            log.error("查询C2C消息失败, userOpenId={}, page={}, pageSize={}: {}", userOpenId, safePage, safePageSize, e.getMessage(), e);
        }

        return new MessagePage<>(safePage, safePageSize, total, records);
    }

    private static C2CMessageRecord toC2CMessageRecord(java.sql.Connection conn, java.sql.ResultSet rs,
                                                       String username, boolean senderIsBot) throws SQLException {
        String unionOpenId = rs.getString("union_openId");
        return new C2CMessageRecord(
                rs.getLong("id"),
                unionOpenId,
                username,
                rs.getString("content"),
                rs.getString("message_openId"),
                senderIsBot,
                (Integer) rs.getObject("message_type"),
                rs.getString("event_timestamp"),
                rs.getString("attachments"),
                rs.getString("ark"),
                enrichMessageReference(conn, C2C_TABLE, "union_openId", unionOpenId, rs.getString("message_reference")),
                rs.getString("ref_idx"),
                rs.getString("created_at")
        );
    }

    /**
     * 私聊版的引用定位，与 {@link #locateGroupMessageByReference} 同构，只是换了表和会话键
     */
    public static C2CMessageLocation locateC2CMessageByReference(String userOpenId, String msgIdx,
                                                                 String refAuthor, String refContent,
                                                                 String refAttachments, int pageSize,
                                                                 long excludeId) {
        C2CMessageLocation byMsgIdx = locateC2CMessageByRefIdx(userOpenId, msgIdx, pageSize, excludeId);
        if (byMsgIdx != null) {
            return byMsgIdx;
        }
        return scanC2CMessageByReference(userOpenId, refAuthor, refContent, refAttachments, pageSize, excludeId);
    }

    public static C2CMessageLocation locateC2CMessageByRefIdx(String userOpenId, String msgIdx,
                                                              int pageSize, long excludeId) {
        if (isBlank(userOpenId) || isBlank(msgIdx)) {
            return null;
        }

        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        String excludeClause = excludeId > 0 ? " AND id <> ?" : "";
        String targetSql = "SELECT id, union_openId, username, content, message_openId, " +
                "sender_is_bot, message_type, event_timestamp, attachments, ark, message_reference, ref_idx, created_at " +
                "FROM `" + C2C_TABLE + "` WHERE union_openId = ? AND ref_idx = ?" + excludeClause + " ORDER BY id DESC LIMIT 1";
        String newerCountSql = "SELECT COUNT(*) FROM `" + C2C_TABLE + "` WHERE union_openId = ? AND id > ?";

        try (var conn = DatabaseManager.getConnection();
             var targetStmt = conn.prepareStatement(targetSql);
             var countStmt = conn.prepareStatement(newerCountSql)) {
            targetStmt.setString(1, userOpenId);
            targetStmt.setString(2, msgIdx);
            if (excludeId > 0) {
                targetStmt.setLong(3, excludeId);
            }

            var rs = targetStmt.executeQuery();
            if (!rs.next()) {
                return null;
            }

            C2CMessageRecord record = toC2CMessageRecord(conn, rs, rs.getString("username"), rs.getBoolean("sender_is_bot"));
            return new C2CMessageLocation(pageOf(countStmt, userOpenId, record.id(), safePageSize), safePageSize, record);
        } catch (SQLException e) {
            log.error("定位私聊引用消息失败, userOpenId={}, msgIdx={}: {}", userOpenId, msgIdx, e.getMessage(), e);
            return null;
        }
    }

    private static C2CMessageLocation scanC2CMessageByReference(String userOpenId, String refAuthor,
                                                                String refContent, String refAttachments,
                                                                int pageSize, long excludeId) {
        if (isBlank(userOpenId) || (isBlank(refContent) && isBlank(refAttachments))) {
            return null;
        }

        int safePageSize = Math.max(1, Math.min(pageSize, 200));
        String idClause = excludeId > 0 ? " AND id < ?" : "";
        String scanSql = "SELECT id, union_openId, username, content, message_openId, " +
                "sender_is_bot, message_type, event_timestamp, attachments, ark, message_reference, ref_idx, created_at " +
                "FROM `" + C2C_TABLE + "` WHERE union_openId = ?" + idClause + " ORDER BY id DESC LIMIT ?";
        String newerCountSql = "SELECT COUNT(*) FROM `" + C2C_TABLE + "` WHERE union_openId = ? AND id > ?";

        String targetText = normalizeReferenceText(refContent);
        List<AttachmentFingerprint> targetAttachments = parseAttachmentFingerprints(refAttachments);
        String targetAuthor = emptyToNull(refAuthor);

        try (var conn = DatabaseManager.getConnection();
             var scanStmt = conn.prepareStatement(scanSql);
             var countStmt = conn.prepareStatement(newerCountSql)) {
            int idx = 1;
            scanStmt.setString(idx++, userOpenId);
            if (excludeId > 0) {
                scanStmt.setLong(idx++, excludeId);
            }
            scanStmt.setInt(idx, REFERENCE_SCAN_LIMIT);

            var rs = scanStmt.executeQuery();
            while (rs.next()) {
                String username = rs.getString("username");
                if (!isBlank(targetAuthor) && !targetAuthor.equals(username)) {
                    continue;
                }
                if (!matchesReferenceSource(rs.getString("content"), rs.getString("attachments"),
                        targetText, targetAttachments)) {
                    continue;
                }

                C2CMessageRecord record = toC2CMessageRecord(conn, rs, username, rs.getBoolean("sender_is_bot"));
                return new C2CMessageLocation(pageOf(countStmt, userOpenId, record.id(), safePageSize), safePageSize, record);
            }
        } catch (SQLException e) {
            log.error("扫描定位私聊引用消息失败, userOpenId={}, excludeId={}: {}", userOpenId, excludeId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * 目标消息之后还有多少条更新的记录，决定它落在分页的第几页
     */
    private static int pageOf(java.sql.PreparedStatement newerCountStmt, String keyValue,
                              long recordId, int pageSize) throws SQLException {
        newerCountStmt.setString(1, keyValue);
        newerCountStmt.setLong(2, recordId);
        var countRs = newerCountStmt.executeQuery();
        long newerCount = countRs.next() ? countRs.getLong(1) : 0;
        return (int) (newerCount / pageSize) + 1;
    }

    /**
     * 群成员列表。
     *
     * <p>官方 API 不提供群成员名册，Bot 只能看见与它同群且<b>发过言</b>的人，
     * 所以这里的「成员」= 在本群留下过消息记录的 union_openId，
     * 与统计里的「群活跃人数」是同一批人，不等于真实群成员。
     * 用户名和身份取该用户最后一条消息上的值。
     */
    public static List<GroupMemberRecord> fetchGroupMembers(String groupOpenId) {
        List<GroupMemberRecord> result = new ArrayList<>();
        if (isBlank(groupOpenId)) {
            return result;
        }

        String sql = "SELECT r.union_openId, r.username, r.member_role, r.sender_is_bot, r.event_timestamp, r.created_at, t.msg_count " +
                "FROM `" + GROUP_TABLE + "` r " +
                "JOIN (SELECT union_openId, MAX(id) AS max_id, COUNT(*) AS msg_count FROM `" + GROUP_TABLE + "` " +
                "      WHERE group_openId = ? AND event_type <> ? AND union_openId IS NOT NULL " +
                "      GROUP BY union_openId) t " +
                "ON r.id = t.max_id";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupOpenId);
            stmt.setString(2, "BOT_SEND");
            var rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new GroupMemberRecord(
                        rs.getString("union_openId"),
                        rs.getString("username"),
                        rs.getString("member_role"),
                        rs.getBoolean("sender_is_bot"),
                        rs.getLong("msg_count"),
                        firstNonBlank(rs.getString("event_timestamp"), rs.getString("created_at"))
                ));
            }
        } catch (SQLException e) {
            log.error("查询群成员失败, groupOpenId={}: {}", groupOpenId, e.getMessage(), e);
        }

        // 群主 → 管理员 → 普通成员，同档内按最近发言时间倒序
        result.sort((a, b) -> {
            int rank = roleRank(a.memberRole()) - roleRank(b.memberRole());
            if (rank != 0) return rank;
            String ta = a.lastActiveAt() == null ? "" : a.lastActiveAt();
            String tb = b.lastActiveAt() == null ? "" : b.lastActiveAt();
            return tb.compareTo(ta);
        });
        return result;
    }

    private static int roleRank(String role) {
        if (role == null) return 2;
        return switch (role) {
            case "OWNER" -> 0;
            case "ADMIN" -> 1;
            default -> 2;
        };
    }

    public record GroupMemberRecord(String unionOpenId, String username, String memberRole,
                                    boolean senderIsBot, long messageCount, String lastActiveAt) {
    }

//    private static final int DEFAULT_CONVERSATION_LIMIT = 300;
//
//    /**
//     * 会话列表聚合查询：每个群 / 每个私聊用户各取最新一条消息记录，
//     * 供 WebUI「聊天」页左侧会话列表使用（含最后一条消息预览与时间）。
//     */
//    public static List<ConversationRecord> fetchConversations() {
//        return fetchConversations(DEFAULT_CONVERSATION_LIMIT);
//    }

    public record ConversationPage(List<ConversationRecord> items, boolean hasMore) {
    }

    /**
     * 分页拉取会话列表：群聊 + 私聊合并，按各自最后一条消息时间倒序。
     *
     * @param limit               本页条数
     * @param offset              跳过条数（只按「未被排除的会话」计数）
     * @param excludeGroupOpenIds 需排除的群会话（置顶会话由调用方单独前置，避免重复）
     * @param excludeC2COpenIds   需排除的私聊会话（同上）
     */
    public static ConversationPage fetchConversations(int limit, int offset,
                                                      Set<String> excludeGroupOpenIds, Set<String> excludeC2COpenIds) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        int safeOffset = Math.max(0, offset);
        // 每张表各自取「offset + limit + 1」条：+1 用来探测是否还有下一页，
        // 也保证合并排序后本页窗口一定能凑满（一个会话若不进本表前 N，全局必然也不进前 N）。
        int batch = Math.min(safeOffset + safeLimit + 1, 2000);

        String groupNotIn = buildExclusionClause("group_openId", "WHERE", excludeGroupOpenIds);
        String c2cNotIn = buildExclusionClause("union_openId", "AND", excludeC2COpenIds);

        String groupSql = "SELECT r.group_openId, r.username, r.content, r.sender_is_bot, r.message_type, " +
                "r.attachments, r.ark, r.event_timestamp, r.created_at " +
                "FROM `" + GROUP_TABLE + "` r " +
                "JOIN (SELECT group_openId, MAX(id) AS max_id FROM `" + GROUP_TABLE + "`" + groupNotIn + " GROUP BY group_openId) t " +
                "ON r.id = t.max_id " +
                "ORDER BY r.created_at DESC LIMIT ?";

        String c2cSql = "SELECT r.union_openId, r.username, r.content, r.sender_is_bot, r.message_type, " +
                "r.attachments, r.ark, r.event_timestamp, r.created_at " +
                "FROM `" + C2C_TABLE + "` r " +
                "JOIN (SELECT union_openId, MAX(id) AS max_id FROM `" + C2C_TABLE + "` " +
                "WHERE union_openId IS NOT NULL" + c2cNotIn + " GROUP BY union_openId) t " +
                "ON r.id = t.max_id " +
                "ORDER BY r.created_at DESC LIMIT ?";

        List<ConversationRecord> result = new ArrayList<>();
        try (var conn = DatabaseManager.getConnection();
             var groupStmt = conn.prepareStatement(groupSql);
             var c2cStmt = conn.prepareStatement(c2cSql)) {
            int idx = bindExclusionParams(groupStmt, 1, excludeGroupOpenIds);
            groupStmt.setInt(idx, batch);
            var groupRs = groupStmt.executeQuery();
            List<Object[]> groupRows = new ArrayList<>();
            while (groupRs.next()) {
                groupRows.add(new Object[]{
                        groupRs.getString("group_openId"),
                        groupRs.getString("username"),
                        groupRs.getString("content"),
                        groupRs.getBoolean("sender_is_bot"),
                        groupRs.getObject("message_type"),
                        groupRs.getString("attachments"),
                        groupRs.getString("ark"),
                        groupRs.getString("event_timestamp"),
                        groupRs.getString("created_at")
                });
            }
            for (Object[] row : groupRows) {
                result.add(buildGroupConversation(row));
            }

            idx = bindExclusionParams(c2cStmt, 1, excludeC2COpenIds);
            c2cStmt.setInt(idx, batch);
            var c2cRs = c2cStmt.executeQuery();
            List<Object[]> c2cRows = new ArrayList<>();
            Set<String> needsUsernameFallback = new java.util.HashSet<>();
            while (c2cRs.next()) {
                String unionOpenId = c2cRs.getString("union_openId");
                boolean senderIsBot = c2cRs.getBoolean("sender_is_bot");
                String senderName = c2cRs.getString("username");
                if (senderIsBot || isBlank(senderName)) {
                    needsUsernameFallback.add(unionOpenId);
                }
                c2cRows.add(new Object[]{
                        unionOpenId, senderName, c2cRs.getString("content"), senderIsBot,
                        c2cRs.getObject("message_type"), c2cRs.getString("attachments"),
                        c2cRs.getString("ark"), c2cRs.getString("event_timestamp"), c2cRs.getString("created_at")
                });
            }
            Map<String, String> fallbackNames = batchFindLatestKnownUsernames(conn, needsUsernameFallback);
            for (Object[] row : c2cRows) {
                String unionOpenId = (String) row[0];
                String senderName = (String) row[1];
                boolean senderIsBot = (boolean) row[3];
                String displayName = (!senderIsBot && !isBlank(senderName))
                        ? senderName
                        : fallbackNames.get(unionOpenId);
                result.add(buildC2cConversation(row, senderName, displayName));
            }
        } catch (SQLException e) {
            log.error("聚合查询会话列表失败: {}", e.getMessage(), e);
        }

        // created_at 是 "yyyy-MM-dd HH:mm:ss" 格式，字典序即时间序
        result.sort((a, b) -> {
            String ta = a.lastCreatedAt() == null ? "" : a.lastCreatedAt();
            String tb = b.lastCreatedAt() == null ? "" : b.lastCreatedAt();
            return tb.compareTo(ta);
        });

        boolean hasMore = result.size() > safeOffset + safeLimit;
        int from = Math.min(safeOffset, result.size());
        int to = Math.min(safeOffset + safeLimit, result.size());
        return new ConversationPage(new ArrayList<>(result.subList(from, to)), hasMore);
    }

    /**
     * 取单个会话的最新一条消息（置顶会话需要被前置展示时使用）。
     */
    public static ConversationRecord fetchConversation(String type, String openId) {
        if (isBlank(openId)) {
            return null;
        }
        try (var conn = DatabaseManager.getConnection()) {
            if ("group".equals(type)) {
                String sql = "SELECT r.group_openId, r.username, r.content, r.sender_is_bot, r.message_type, " +
                        "r.attachments, r.ark, r.event_timestamp, r.created_at " +
                        "FROM `" + GROUP_TABLE + "` r WHERE r.group_openId = ? ORDER BY r.id DESC LIMIT 1";
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, openId);
                    var rs = stmt.executeQuery();
                    if (rs.next()) {
                        return buildGroupConversation(new Object[]{
                                rs.getString("group_openId"), rs.getString("username"), rs.getString("content"),
                                rs.getBoolean("sender_is_bot"), rs.getObject("message_type"), rs.getString("attachments"),
                                rs.getString("ark"), rs.getString("event_timestamp"), rs.getString("created_at")
                        });
                    }
                }
            } else if ("c2c".equals(type)) {
                String sql = "SELECT r.union_openId, r.username, r.content, r.sender_is_bot, r.message_type, " +
                        "r.attachments, r.ark, r.event_timestamp, r.created_at " +
                        "FROM `" + C2C_TABLE + "` r WHERE r.union_openId = ? ORDER BY r.id DESC LIMIT 1";
                try (var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, openId);
                    var rs = stmt.executeQuery();
                    if (rs.next()) {
                        Object[] row = new Object[]{
                                rs.getString("union_openId"), rs.getString("username"), rs.getString("content"),
                                rs.getBoolean("sender_is_bot"), rs.getObject("message_type"), rs.getString("attachments"),
                                rs.getString("ark"), rs.getString("event_timestamp"), rs.getString("created_at")
                        };
                        String senderName = (String) row[1];
                        boolean senderIsBot = (boolean) row[3];
                        String displayName = (!senderIsBot && !isBlank(senderName))
                                ? senderName
                                : findLatestKnownUsername(conn, openId);
                        return buildC2cConversation(row, senderName, displayName);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("查询单个会话失败, type={}, openId={}: {}", type, openId, e.getMessage(), e);
        }
        return null;
    }

    private static String buildExclusionClause(String column, String connector, Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        return " " + connector + " " + column + " NOT IN ("
                + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")";
    }

    private static int bindExclusionParams(java.sql.PreparedStatement stmt, int startIdx, Set<String> ids) throws SQLException {
        int idx = startIdx;
        if (ids != null) {
            for (String id : ids) {
                stmt.setString(idx++, id);
            }
        }
        return idx;
    }

    private static ConversationRecord buildGroupConversation(Object[] row) {
        String groupOpenId = (String) row[0];
        String groupName = null;
        Long realGroupId = null;
        int groupMemberNum = 0;
        try {
            var groupData = OfficialGroups.getData(groupOpenId);
            groupName = groupData.groupName();
            realGroupId = groupData.realGroupId();
            groupMemberNum = groupData.groupMemberNum();
        } catch (Exception ignored) {
        }
        return new ConversationRecord(
                "group",
                groupOpenId,
                groupName,
                realGroupId,
                groupMemberNum,
                (String) row[2],
                (String) row[1],
                (boolean) row[3],
                (Integer) row[4],
                (String) row[5],
                !isBlank((String) row[6]),
                (String) row[7],
                (String) row[8]
        );
    }

    private static ConversationRecord buildC2cConversation(Object[] row, String senderName, String displayName) {
        return new ConversationRecord(
                "c2c",
                (String) row[0],
                displayName,
                null,
                null,
                (String) row[2],
                senderName,
                (boolean) row[3],
                (Integer) row[4],
                (String) row[5],
                !isBlank((String) row[6]),
                (String) row[7],
                (String) row[8]
        );
    }

    /**
     * 批量版 {@link #findLatestKnownUsername}：一次查询取回一批 unionOpenId 各自最近一次
     * 非空、非机器人发送的用户名，避免 fetchConversations 对每个私聊会话单独发一条子查询。
     */
    private static Map<String, String> batchFindLatestKnownUsernames(java.sql.Connection conn, java.util.Set<String> unionOpenIds) throws SQLException {
        Map<String, String> resultMap = new java.util.HashMap<>();
        if (unionOpenIds.isEmpty()) {
            return resultMap;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(unionOpenIds.size(), "?"));
        String sql = "SELECT union_openId, username FROM (" +
                "  SELECT union_openId, username, created_at, " +
                "         ROW_NUMBER() OVER (PARTITION BY union_openId ORDER BY created_at DESC) AS rn " +
                "  FROM (" +
                "    SELECT union_openId, username, created_at FROM `" + C2C_TABLE + "` " +
                "    WHERE union_openId IN (" + placeholders + ") AND sender_is_bot = FALSE " +
                "    AND username IS NOT NULL AND TRIM(username) <> '' " +
                "    UNION ALL " +
                "    SELECT union_openId, username, created_at FROM `" + GROUP_TABLE + "` " +
                "    WHERE union_openId IN (" + placeholders + ") AND sender_is_bot = FALSE " +
                "    AND username IS NOT NULL AND TRIM(username) <> '' " +
                "  ) combined" +
                ") ranked WHERE rn = 1";
        try (var stmt = conn.prepareStatement(sql)) {
            int idx = 1;
            for (String id : unionOpenIds) {
                stmt.setString(idx++, id);
            }
            for (String id : unionOpenIds) {
                stmt.setString(idx++, id);
            }
            var rs = stmt.executeQuery();
            while (rs.next()) {
                resultMap.put(rs.getString("union_openId"), rs.getString("username"));
            }
        }
        return resultMap;
    }

    public record ConversationRecord(String type, String openId, String name, Long realGroupId, Integer groupMemberNum,
                                     String lastContent, String lastSenderName, boolean lastSenderIsBot,
                                     Integer lastMessageType, String lastAttachments, boolean lastArk,
                                     String lastEventTimestamp, String lastCreatedAt) {
    }

    private static String findLatestKnownUsername(java.sql.Connection conn, String userOpenId) {
        if (isBlank(userOpenId)) {
            return null;
        }
        String sql = "SELECT username FROM (" +
                "SELECT username, created_at FROM `" + C2C_TABLE + "` " +
                "WHERE union_openId = ? AND sender_is_bot = FALSE AND username IS NOT NULL AND TRIM(username) <> '' " +
                "UNION ALL " +
                "SELECT username, created_at FROM `" + GROUP_TABLE + "` " +
                "WHERE union_openId = ? AND sender_is_bot = FALSE AND username IS NOT NULL AND TRIM(username) <> ''" +
                ") known_usernames ORDER BY created_at DESC LIMIT 1";
        try (var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userOpenId);
            stmt.setString(2, userOpenId);
            var rs = stmt.executeQuery();
            if (rs.next()) {
                return emptyToNull(rs.getString("username"));
            }
        } catch (SQLException e) {
            log.error("查询官方用户最近用户名失败, userOpenId={}: {}", userOpenId, e.getMessage(), e);
        }
        return null;
    }

    /**
     * WebUI 用户名单用：批量取回一批 unionOpenId 各自最近一次非空、非机器人发送的用户名
     * （群+私聊消息表各取最新，再按时间取最新）。查不到的 openId 不会进返回的 map。
     */
    public static Map<String, String> findLatestKnownUsernames(Set<String> unionOpenIds) {
        if (unionOpenIds == null || unionOpenIds.isEmpty()) {
            return Map.of();
        }
        try (var conn = DatabaseManager.getConnection()) {
            return batchFindLatestKnownUsernames(conn, unionOpenIds);
        } catch (SQLException e) {
            log.error("批量查询用户最近用户名失败: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * 单用户版，给权限弹窗等场景用。
     */
    public static String findLatestKnownUsername(String userOpenId) {
        if (isBlank(userOpenId)) {
            return null;
        }
        try (var conn = DatabaseManager.getConnection()) {
            return findLatestKnownUsername(conn, userOpenId);
        } catch (SQLException e) {
            log.error("查询用户最近用户名失败, userOpenId={}: {}", userOpenId, e.getMessage(), e);
            return null;
        }
    }
}
