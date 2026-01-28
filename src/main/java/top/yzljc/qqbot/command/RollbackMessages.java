package top.yzljc.qqbot.command;

import com.zaxxer.hikari.HikariDataSource;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.message.MessageRecorder;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollbackMessages {

    private static final Logger log = LoggerFactory.getLogger(RollbackMessages.class);
    private static final Pattern ROLLBACK_PATTERN =
        Pattern.compile("^/rollback(?:\\s+-n\\s+(\\d+))?(?:\\s+-u\\s+(\\d+))?\\s*$");

    public static void processRollBack(long groupId, String rawMsg) {
        handleRollbackCommand(groupId, rawMsg);
    }

    /**
     * 处理指令消息（假设主程序已判断是用户3199590352发送指令，此入口只需处理撤回逻辑）
     * @param groupId 当前群号
     * @param commandText 用户消息内容
     * @param dataSource 数据源（用 RecordGroupMessage 提供的连接池）
     * @return 撤回条数
     */
    public static int rollback(String groupId, String commandText, HikariDataSource dataSource) {
        Matcher matcher = ROLLBACK_PATTERN.matcher(commandText.trim());
        if (!matcher.matches()) {
            return 0;
        }
        String nStr = matcher.group(1);
        String uStr = matcher.group(2);

        int limit = 10;
        if (nStr != null && !nStr.isEmpty()) {
            try {
                limit = Integer.parseInt(nStr);
                if (limit <= 0) limit = 10;
            } catch (Exception ignored) {}
        }
        Long userId = null;
        if (uStr != null && !uStr.isEmpty()) {
            try {
                userId = Long.parseLong(uStr);
            } catch (Exception ignored) {}
        }

        List<Long> msgIdList = fetchMessageIds(groupId, userId, limit, dataSource);

        for (Long msgId : msgIdList) {
            sendDeleteMessage(msgId);
        }
        return msgIdList.size();
    }

    private static List<Long> fetchMessageIds(String groupId, Long userId, int limit, HikariDataSource dataSource) {
        List<Long> list = new ArrayList<>();
        String tableName = MessageRecorder.getDynamicTableName(Long.parseLong(groupId));
        String sql =
                "SELECT message_id FROM " + tableName +
                        " WHERE group_id = ?" +
                        (userId != null ? " AND user_id = ?" : "") +
                        " ORDER BY id DESC LIMIT ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, Long.parseLong(groupId));
            int paramIndex = 2;
            if (userId != null) {
                pstmt.setLong(paramIndex++, userId);
            }
            pstmt.setInt(paramIndex, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getLong("message_id"));
                }
            }
        } catch (Exception e) {
            log.error("查询数据库失败：{}", e.getMessage());
        }
        return list;
    }

    private static void sendDeleteMessage(Long messageId) {
        try {
            PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE,"message_id",messageId);
        } catch (Exception e) {
            log.warn("发送撤回包失败，message_id = {}：{}", messageId, e.getMessage());
        }
    }

    /**
     * @param groupId    群号
     * @param message    消息内容
     */
    public static void handleRollbackCommand(long groupId, String message) {
        int count = rollback(Long.toString(groupId), message, MessageRecorder.getDataSource());
        log.info("已批量撤回消息数 = {}", count);
        MessageSender.sendGroupMessage(groupId, "已批量撤回消息 " + count + " 条");
    }
}
