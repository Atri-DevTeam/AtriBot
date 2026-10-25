package top.yzljc.atribot.function.utils.napcat;

import com.zaxxer.hikari.HikariDataSource;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.napcat.UserInformation;
import top.yzljc.atribot.configuration.LoadIllegalWords;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.NapcatCommandSender;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchRelevant implements CommandExecutor {
    private static final Pattern QUOTE_PATTERN = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)(?:,.*?)?]");
    private static final Pattern REPLY_PATTERN = Pattern.compile("\\[CQ:reply,id=(\\d+)(?:,.*?)?]");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[CQ:image,.*?]");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_RESULTS = 200;
    private static final int MAX_FORWARD_NODES = 100;
    private static final int MAX_DISPLAY_RESULTS = MAX_FORWARD_NODES - 2;
    private static final int WITHDRAW_SECONDS = 100;
    private static final int MAX_MSG_LENGTH = 1000;

    private static final Map<String, CachedNickname> nicknameCache = new ConcurrentHashMap<>();
    private static final long NICKNAME_CACHE_EXPIRE = 60 * 1000L;

    private record CachedNickname(String nick, long time) {
    }

    private record SearchRecord(String userId, long time, String content) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (args.length == 0) {
            return false;
        }

        String fullArgs = String.join(" ", args);

        Matcher matcher = QUOTE_PATTERN.matcher(fullArgs);
        if (!matcher.find()) {
            nc.sendMessage("搜索格式错误。关键词必须使用双引号包裹，例如：/search \"关键词\"");
            return true;
        }

        String keyword = matcher.group(1);

        if (LoadIllegalWords.containsSensitiveWord(keyword) || isSqlKeywords(keyword)) {
            nc.sendMessage("搜索关键词不符合检索规则，拒绝执行!");
            return true;
        }

        String paramsStr = fullArgs.replace("\"" + keyword + "\"", "").trim();

        String targetUserId = null;
        String mode = "a";

        if (!paramsStr.isEmpty()) {
            String[] paramArgs = paramsStr.split("\\s+");

            for (int i = 0; i < paramArgs.length; i++) {
                String currentArg = paramArgs[i];

                if ("-u".equals(currentArg) && i + 1 < paramArgs.length) {
                    try {
                        targetUserId = paramArgs[i + 1];
                        i++;
                    } catch (NumberFormatException ignored) {}
                }
                else if ("-m".equals(currentArg) && i + 1 < paramArgs.length) {
                    String m = paramArgs[i + 1].toLowerCase();
                    if ("p".equals(m) || "a".equals(m)) {
                        mode = m;
                        i++;
                    }
                }
            }
        }

        searchInDatabase(nc.getGroupId(), keyword, targetUserId, mode);

        return true;
    }

    private static void searchInDatabase(String groupId, String keyword, String targetUserId, String mode) {
        HikariDataSource dataSource = DatabaseManager.getDataSource();
        if (dataSource == null) {
            GroupMessage.chatMessage(groupId, "数据库未初始化，无法搜索。");
            return;
        }

        String tableName = GroupContentRecord.getDynamicTableName(groupId);
        StringBuilder conditions = new StringBuilder(" WHERE ");
        if (targetUserId != null) conditions.append("user_id = ? AND ");
        conditions.append("p".equals(mode) ? "raw_message = ?" : "raw_message LIKE ?");
        String sql = "SELECT user_id, msg_time, raw_message FROM `" + tableName + "`" + conditions
                + " ORDER BY msg_time DESC, id DESC LIMIT ?";
        String countSql = "SELECT COUNT(*) FROM `" + tableName + "`" + conditions;

        try {
            List<SearchRecord> records = new ArrayList<>();
            long totalCount = 0;
            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    int idx = bindSearch(pstmt, keyword, targetUserId, mode);
                    pstmt.setInt(idx, MAX_RESULTS);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (records.size() < MAX_DISPLAY_RESULTS && rs.next()) {
                            String rawMsg = rs.getString("raw_message");
                            if (isMessyMessage(rawMsg) || LoadIllegalWords.containsSensitiveWord(rawMsg)) continue;
                            records.add(new SearchRecord(rs.getString("user_id"), rs.getLong("msg_time"), rawMsg));
                        }
                    }
                }
                try (PreparedStatement pstmt = conn.prepareStatement(countSql)) {
                    bindSearch(pstmt, keyword, targetUserId, mode);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) totalCount = rs.getLong(1);
                    }
                }
            }

            List<String> results = new ArrayList<>();
            for (var record : records) {
                String nick = fetchNickname(record.userId());
                String userDisplay = nick != null ? nick : record.userId();
                String time = DATE_FORMAT.format(Instant.ofEpochSecond(record.time()));
                results.add("[" + time + "] " + userDisplay + ": " + cleanMessageContent(record.content()));
            }
            String targetDisplay = null;
            if (targetUserId != null) {
                String nick = fetchNickname(targetUserId);
                targetDisplay = nick != null ? nick : targetUserId;
            }
            var nodes = buildForwardMessages(keyword, targetDisplay, mode, results, totalCount).stream()
                    .map(GroupMessage::createTextNode).toList();
            String messageId = GroupMessage.forwardMessage(groupId, nodes, "聊天记录搜索结果",
                    "显示 " + results.size() + " 条记录", "关键词：" + keyword);
            if (messageId != null) {
                ThreadManager.schedule(() -> GroupMessage.recallMessage(messageId), WITHDRAW_SECONDS, TimeUnit.SECONDS);
            } else {
                GroupMessage.chatMessage(groupId, "搜索结果发送失败，请稍后重试");
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146) GroupMessage.chatMessage(groupId, "当前群聊暂无消息记录表。");
            else GroupMessage.chatMessage(groupId, "数据库错误：" + e.getMessage());
        } catch (Exception e) {
            GroupMessage.chatMessage(groupId, "搜索异常");
        }
    }

    private static int bindSearch(PreparedStatement statement, String keyword, String targetUserId, String mode) throws SQLException {
        int index = 1;
        if (targetUserId != null) statement.setLong(index++, Long.parseLong(targetUserId));
        statement.setString(index++, "p".equals(mode) ? keyword : "%" + keyword + "%");
        return index;
    }

    static List<String> buildForwardMessages(String keyword, String targetDisplay, String mode,
                                             List<String> results, long totalCount) {
        var messages = new ArrayList<String>();
        String header = "🔍 搜索结果 (模式:" + ("p".equals(mode) ? "精确" : "模糊") + "): \"" + keyword + "\"";
        if (targetDisplay != null) header += "\n👤 限定用户: " + targetDisplay;
        messages.add(header);
        int displayed = Math.min(results.size(), MAX_DISPLAY_RESULTS);
        if (displayed == 0) messages.add("未找到可展示的记录");
        else messages.addAll(results.subList(0, displayed));
        long hidden = Math.max(0, totalCount - displayed);
        String footer = hidden > 0 ? "...还有 " + hidden + " 条记录未展示\n\n" : "";
        footer += "⚠️ 本消息将于" + WITHDRAW_SECONDS + "秒后自动撤回";
        messages.add(footer);
        return List.copyOf(messages);
    }

    private static boolean isSqlKeywords(String keyword) {
        String k = keyword.toUpperCase();
        return k.contains("SELECT") || k.contains("DELETE") || k.contains("UPDATE") || k.contains("INSERT") || k.trim().isEmpty();
    }

    private static boolean isMessyMessage(String msg) {
        if (msg == null) return true;
        String t = msg.trim();
        return t.length() > MAX_MSG_LENGTH || t.startsWith("[CQ:json") || t.startsWith("[CQ:xml") || t.startsWith("[CQ:file") || t.startsWith("[CQ:card");
    }

    private static String cleanMessageContent(String msg) {
        if (msg == null) return "";
        msg = REPLY_PATTERN.matcher(msg).replaceAll("[回复]");
        msg = IMAGE_PATTERN.matcher(msg).replaceAll("[图片]");
        Matcher m = AT_PATTERN.matcher(msg);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String qqStr = m.group(1);
            String replacement = "@" + qqStr;
            try {
                CachedNickname cached = nicknameCache.get(qqStr);
                if (cached != null && (System.currentTimeMillis() - cached.time < NICKNAME_CACHE_EXPIRE)) replacement = "@" + cached.nick;
            } catch (Exception ignored) {}
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    private static String fetchNickname(String userId) {
        try {
            long now = System.currentTimeMillis();
            CachedNickname cached = nicknameCache.get(userId);
            if (cached != null && (now - cached.time) < NICKNAME_CACHE_EXPIRE) return cached.nick;

            String nick = UserInformation.getUserName(userId);

            if (nick != null && !nick.isEmpty()) {
                nicknameCache.put(userId, new CachedNickname(nick, now));
                return nick;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
