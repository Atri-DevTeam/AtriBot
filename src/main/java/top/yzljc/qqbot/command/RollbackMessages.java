package top.yzljc.qqbot.command;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.message.MessageRecorder;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RollbackMessages implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(RollbackMessages.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("权限不足", false);
            return true;
        }
        
        int limit = 10;
        Long targetUserId = null;
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if ("-n".equalsIgnoreCase(arg) && i + 1 < args.length) {
                try {
                    limit = Integer.parseInt(args[i + 1]);
                    i++;
                } catch (NumberFormatException ignored) {
                    sender.reply("参数错误：-n 后面必须是数字", false);
                    return true;
                }
            }
            else if ("-u".equalsIgnoreCase(arg) && i + 1 < args.length) {
                try {
                    targetUserId = Long.parseLong(args[i + 1]);
                    i++;
                } catch (NumberFormatException ignored) {
                    sender.reply("参数错误：-u 后面必须是QQ号", false);
                    return true;
                }
            }
        }
        
        int count = performRollback(sender.getGroupId(), targetUserId, limit);
        
        if (count > 0) {
            sender.reply("已尝试撤回 " + count + " 条消息", false);
        } else {
            sender.reply("未找到可撤回的消息", false);
        }

        return true;
    }

    private int performRollback(long groupId, Long targetUserId, int limit) {
        HikariDataSource dataSource = MessageRecorder.getDataSource();
        List<Long> msgIdList = fetchMessageIds(groupId, targetUserId, limit, dataSource);

        int successCount = 0;
        for (Long msgId : msgIdList) {
            if (sendDeleteMessage(msgId)) {
                successCount++;
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        return successCount;
    }

    private List<Long> fetchMessageIds(long groupId, Long userId, int limit, HikariDataSource dataSource) {
        List<Long> list = new ArrayList<>();
        String tableName = MessageRecorder.getDynamicTableName(groupId);

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT message_id FROM ").append(tableName)
                .append(" WHERE group_id = ?");

        if (userId != null) {
            sqlBuilder.append(" AND user_id = ?");
        }

        sqlBuilder.append(" ORDER BY id DESC LIMIT ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {

            int paramIndex = 1;
            pstmt.setLong(paramIndex++, groupId);

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

    private boolean sendDeleteMessage(Long messageId) {
        try {
            PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE, "message_id", messageId);
            return true;
        } catch (Exception e) {
            log.warn("发送撤回包失败，message_id = {}：{}", messageId, e.getMessage());
            return false;
        }
    }
}