package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;
import top.yzljc.qqbot.messages.RecordGroupMessage;
import top.yzljc.qqbot.messages.SensitiveWordFilter;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
public class SearchRelevant {

    private static final Logger log = LoggerFactory.getLogger(SearchRelevant.class);

    private static final Pattern QUOTE_PATTERN = Pattern.compile("/search\\s+\"([^\"]+)\"(.*)");
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)(?:,.*?)?]");
    private static final Pattern REPLY_PATTERN = Pattern.compile("\\[CQ:reply,id=(\\d+)(?:,.*?)?]");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[CQ:image,.*?]");

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_RESULTS = 200;
    private static final int MAX_MSG_LENGTH = 1000;

    static Settings settings = Config.getInstance();
    private static final String API_BASE = settings.getHttpUrl();
    private static final String NICKNAME_API = API_BASE + "/get_stranger_info";
    private static final String SEND_MSG_API = API_BASE + "/send_group_msg";
    private static final String DELETE_MSG_API = API_BASE + "/delete_msg";

    private static final Map<Long, CachedNickname> nicknameCache = new ConcurrentHashMap<>();
    private static final long NICKNAME_CACHE_EXPIRE = 60 * 1000L;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static class CachedNickname {
        final String nick;
        final long time;
        CachedNickname(String n, long t) { this.nick = n; this.time = t; }
    }

    public static void processCommand(JsonNode json) {
        if (!json.has("message_type") || !"group".equals(json.path("message_type").asText())) return;
        String rawMessage = json.path("raw_message").asText();
        if (rawMessage == null || !rawMessage.startsWith("/search ")) return;

        long groupId = json.path("group_id").asLong();
        Matcher matcher = QUOTE_PATTERN.matcher(rawMessage);
        if (!matcher.find()) {
            MessageSender.sendGroupMessage(groupId, "搜索格式错误。正确用法：/search \"关键词\" [-u QQ号] [-m p/a]");
            return;
        }

        String keyword = matcher.group(1);
        String paramsStr = matcher.group(2);

        if (SensitiveWordFilter.containsSensitiveWord(keyword) || isSqlKeywords(keyword)) {
            MessageSender.sendGroupMessage(groupId, "搜索关键词不符合检索规则，拒绝执行!");
            return;
        }

        Long targetUserId = null;
        String mode = "a";

        if (paramsStr != null && !paramsStr.trim().isEmpty()) {
            String[] args = paramsStr.trim().split("\\s+");
            for (int i = 0; i < args.length; i++) {
                if ("-u".equals(args[i]) && i + 1 < args.length) {
                    try { targetUserId = Long.parseLong(args[i + 1]); i++; } catch (NumberFormatException ignored) {}
                } else if ("-m".equals(args[i]) && i + 1 < args.length) {
                    String m = args[i + 1].toLowerCase();
                    if ("p".equals(m) || "a".equals(m)) { mode = m; i++; }
                }
            }
        }
        searchInDatabase(groupId, keyword, targetUserId, mode);
    }

    private static void searchInDatabase(long groupId, String keyword, Long targetUserId, String mode) {
        HikariDataSource dataSource = RecordGroupMessage.getDataSource();
        if (dataSource == null) {
            MessageSender.sendGroupMessage(groupId, "数据库未初始化，无法搜索。");
            return;
        }

        String tableName = RecordGroupMessage.getDynamicTableName(groupId);
        long nowSeconds = System.currentTimeMillis() / 1000L;
        long sevenDaysAgoTs = nowSeconds - (7 * 24 * 60 * 60); // 7天前的时间戳

        // 1. 构建主查询（7天内）
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT user_id, msg_time, raw_message FROM `").append(tableName).append("` WHERE msg_time >= ? ");
        if (targetUserId != null) sqlBuilder.append("AND user_id = ? ");
        if ("p".equals(mode)) sqlBuilder.append("AND raw_message = ? ");
        else sqlBuilder.append("AND raw_message LIKE ? ");
        sqlBuilder.append("ORDER BY msg_time DESC LIMIT ?");

        // 2. 构建计数查询（超过7天的数据量）
        StringBuilder countSqlBuilder = new StringBuilder();
        countSqlBuilder.append("SELECT COUNT(*) FROM `").append(tableName).append("` WHERE msg_time < ? ");
        if (targetUserId != null) countSqlBuilder.append("AND user_id = ? ");
        if ("p".equals(mode)) countSqlBuilder.append("AND raw_message = ? ");
        else countSqlBuilder.append("AND raw_message LIKE ? ");

        try (Connection conn = dataSource.getConnection()) {
            List<String> results = new ArrayList<>();
            int olderCount = 0;

            // 执行主查询
            try (PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {
                int idx = 1;
                pstmt.setLong(idx++, sevenDaysAgoTs);
                if (targetUserId != null) pstmt.setLong(idx++, targetUserId);
                if ("p".equals(mode)) pstmt.setString(idx++, keyword);
                else pstmt.setString(idx++, "%" + keyword + "%");
                pstmt.setInt(idx, MAX_RESULTS);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String rawMsg = rs.getString("raw_message");
                        if (isMessyMessage(rawMsg) || SensitiveWordFilter.containsSensitiveWord(rawMsg)) continue;
                        String displayMsg = cleanMessageContent(rawMsg);
                        long userId = rs.getLong("user_id");
                        long timeSec = rs.getLong("msg_time");
                        String nick = fetchNickname(userId);
                        String userDisplay = (nick != null) ? nick : String.valueOf(userId);
                        String timeStr = DATE_FORMAT.format(new Date(timeSec * 1000L));
                        results.add(String.format("[%s] %s: %s", timeStr, userDisplay, displayMsg));
                    }
                }
            }

            // 执行计数查询（查出有多少条早于7天的）
            try (PreparedStatement pcstmt = conn.prepareStatement(countSqlBuilder.toString())) {
                int idx = 1;
                pcstmt.setLong(idx++, sevenDaysAgoTs);
                if (targetUserId != null) pcstmt.setLong(idx++, targetUserId);
                if ("p".equals(mode)) pcstmt.setString(idx++, keyword);
                else pcstmt.setString(idx++, "%" + keyword + "%");

                try (ResultSet rs = pcstmt.executeQuery()) {
                    if (rs.next()) olderCount = rs.getInt(1);
                }
            }

            // 3. 构建回复
            StringBuilder reply = new StringBuilder();
            reply.append("🔍 搜索结果 (7天内, 模式:").append("p".equals(mode) ? "精确" : "模糊").append("): \"").append(keyword).append("\"\n");
            if (targetUserId != null) {
                String targetNick = fetchNickname(targetUserId);
                reply.append("👤 限定用户: ").append(targetNick != null ? targetNick : targetUserId).append("\n");
            }
            reply.append("----------------\n");

            if (results.isEmpty()) {
                reply.append("近期(7天内)未找到符合条件的记录\n");
            } else {
                for (String line : results) reply.append(line).append("\n");
            }

            if (olderCount > 0) {
                reply.append("...还有 ").append(olderCount).append(" 条超过7天的记录已隐藏\n");
            }

            reply.append("\n⚠️ 本消息将于1分钟后自动撤回");
            sendAndScheduleWithdraw(groupId, reply.toString().trim());

        } catch (SQLException e) {
            if (e.getErrorCode() == 1146) MessageSender.sendGroupMessage(groupId, "当前群聊暂无消息记录表。");
            else MessageSender.sendGroupMessage(groupId, "数据库错误：" + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            MessageSender.sendGroupMessage(groupId, "搜索异常。");
        }
    }

    private static boolean isSqlKeywords(String keyword) {
        String k = keyword.toUpperCase();
        return k.contains("SELECT") || k.contains("DELETE") || k.contains("UPDATE") || k.contains("INSERT") || k.trim().isEmpty();
    }

    private static void sendAndScheduleWithdraw(long groupId, String message) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("group_id", groupId);
            root.put("message", message);
            String jsonBody = objectMapper.writeValueAsString(root);

            HttpURLConnection conn = (HttpURLConnection) new URI(SEND_MSG_API).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            StringBuilder resp = new StringBuilder();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[256];
                int len;
                while ((len = in.read(buf)) != -1) resp.append(new String(buf, 0, len, StandardCharsets.UTF_8));
            }

            JsonNode respNode = objectMapper.readTree(resp.toString());
            if (respNode.has("data") && respNode.get("data").has("message_id")) {
                long messageId = respNode.get("data").get("message_id").asLong();
                scheduler.schedule(() -> withdrawMessage(messageId), 60, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            MessageSender.sendGroupMessage(groupId, message);
        }
    }

    private static void withdrawMessage(long messageId) {
        try {
            String jsonBody = String.format("{\"message_id\":%d}", messageId);
            HttpURLConnection conn = (HttpURLConnection) new URI(DELETE_MSG_API).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception ignored) {}
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
                long qq = Long.parseLong(qqStr);
                CachedNickname cached = nicknameCache.get(qq);
                if (cached != null && (System.currentTimeMillis() - cached.time < NICKNAME_CACHE_EXPIRE)) replacement = "@" + cached.nick;
            } catch (Exception ignored) {}
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    private static String fetchNickname(Long userId) {
        try {
            long now = System.currentTimeMillis();
            CachedNickname cached = nicknameCache.get(userId);
            if (cached != null && (now - cached.time) < NICKNAME_CACHE_EXPIRE) return cached.nick;

            String body = String.format("{\"user_id\":\"%d\"}", userId);
            HttpURLConnection conn = (HttpURLConnection) new URI(NICKNAME_API).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            JsonNode node = objectMapper.readTree(conn.getInputStream());
            String nick = node.path("data").path("nick").asText(null);

            if (nick != null && !nick.isEmpty()) {
                nicknameCache.put(userId, new CachedNickname(nick, now));
                return nick;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
