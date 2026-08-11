package top.yzljc.atribot.function.napcat;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.platform.Identifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RollbackMessages implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(RollbackMessages.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (!nc.hasPermission()) {
            nc.sendMessage(Identifier.NO_PERMISSION);
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
                    nc.sendMessage("参数错误：-n 后面必须是数字");
                    return true;
                }
            } else if ("-u".equalsIgnoreCase(arg) && i + 1 < args.length) {
                try {
                    targetUserId = Long.parseLong(args[i + 1]);
                    i++;
                } catch (NumberFormatException ignored) {
                    nc.sendMessage("参数错误：-u 后面必须是QQ号");
                    return true;
                }
            }
        }

        int count = performRollback(nc.getGroupId(), targetUserId, limit);

        if (count > 0) {
            nc.sendMessage("已尝试撤回 " + count + " 条消息");
        } else {
            nc.sendMessage("未找到可撤回的消息");
        }

        return true;
    }

    private int performRollback(String groupIdStr, Long targetUserId, int limit) {
        long groupId = Long.parseLong(groupIdStr);
        HikariDataSource dataSource = DatabaseManager.getDataSource();
        List<Long> msgIdList = fetchMessageIds(groupId, targetUserId, limit, dataSource);

        int successCount = 0;
        for (Long msgId : msgIdList) {
            GroupMessage.recallMessage(String.valueOf(msgId));
            successCount++;
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        return successCount;
    }

    private List<Long> fetchMessageIds(long groupId, Long userId, int limit, HikariDataSource dataSource) {
        List<Long> list = new ArrayList<>();
        String tableName = GroupContentRecord.getDynamicTableName(String.valueOf(groupId));

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
}
