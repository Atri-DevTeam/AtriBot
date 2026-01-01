package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zaxxer.hikari.HikariDataSource;
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

public class SearchRelevant {

    // 正则用于初步提取：/search "内容" 后面的参数
    private static final Pattern QUOTE_PATTERN = Pattern.compile("/search\\s+\"([^\"]+)\"(.*)");

    // 正则用于匹配 CQ 码
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)(?:,.*?)?]");
    private static final Pattern REPLY_PATTERN = Pattern.compile("\\[CQ:reply,id=(\\d+)(?:,.*?)?]");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[CQ:image,.*?]");

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_RESULTS = 200;
    private static final int MAX_MSG_LENGTH = 1000;

    // API 配置
    private static final String API_BASE = "http://106.14.23.232:8848";
    private static final String NICKNAME_API = API_BASE + "/get_stranger_info";
    private static final String SEND_MSG_API = API_BASE + "/send_group_msg";
    private static final String DELETE_MSG_API = API_BASE + "/delete_msg";

    // 昵称获取相关配置
    private static final Map<Long, CachedNickname> nicknameCache = new ConcurrentHashMap<>();
    private static final long NICKNAME_CACHE_EXPIRE = 60 * 1000L;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 定时任务调度器，用于处理延时撤回
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 内部类用于缓存昵称
    private static class CachedNickname {
        final String nick;
        final long time;

        CachedNickname(String n, long t) {
            this.nick = n;
            this.time = t;
        }
    }

    public static void processCommand(JsonNode json) {
        // 1. 基本校验：必须是群消息
        if (!json.has("message_type") || !"group".equals(json.path("message_type").asText())) {
            return;
        }

        String rawMessage = json.path("raw_message").asText();
        if (rawMessage == null || !rawMessage.startsWith("/search ")) {
            return;
        }

        long groupId = json.path("group_id").asLong();

        // 2. 解析命令
        Matcher matcher = QUOTE_PATTERN.matcher(rawMessage);
        if (!matcher.find()) {
            MessageSender.sendGroupMessage(groupId, "搜索格式错误。正确用法：/search \"关键词\" [-u QQ号] [-m p/a]");
            return;
        }

        String keyword = matcher.group(1); // 引号内的文本
        String paramsStr = matcher.group(2); // 后面的参数字符串

        // 【新增】如果搜索关键词本身就包含违规词，直接拒绝搜索
        if (SensitiveWordFilter.containsSensitiveWord(keyword) || keyword.equals("\t") || keyword.equals("\n") || keyword.equals("SELECT") || keyword.equals("DELETE") || keyword.equals("UPDATE") || keyword.equals("INSERT") || keyword.equals("\n\t") || keyword.equals("\t\n")) {
            MessageSender.sendGroupMessage(groupId, "搜索关键词不符合检索规则，拒绝执行!");
            return;
        }

        Long targetUserId = null;
        String mode = "a"; // 默认模糊匹配 (ambiguous)

        // 解析后续参数
        if (paramsStr != null && !paramsStr.trim().isEmpty()) {
            String[] args = paramsStr.trim().split("\\s+");
            for (int i = 0; i < args.length; i++) {
                if ("-u".equals(args[i]) && i + 1 < args.length) {
                    try {
                        targetUserId = Long.parseLong(args[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        // 忽略错误的QQ号格式
                    }
                } else if ("-m".equals(args[i]) && i + 1 < args.length) {
                    String m = args[i + 1].toLowerCase();
                    if ("p".equals(m) || "a".equals(m)) {
                        mode = m;
                        i++;
                    }
                }
            }
        }

        // 3. 执行搜索并发送
        searchInDatabase(groupId, keyword, targetUserId, mode);
    }

    private static void searchInDatabase(long groupId, String keyword, Long targetUserId, String mode) {
        HikariDataSource dataSource = RecordGroupMessage.getDataSource();
        if (dataSource == null) {
            MessageSender.sendGroupMessage(groupId, "数据库未初始化，无法搜索。");
            return;
        }

        String tableName = RecordGroupMessage.getDynamicTableName(groupId);
        StringBuilder sqlBuilder = new StringBuilder();

        sqlBuilder.append("SELECT user_id, msg_time, raw_message FROM `")
                .append(tableName)
                .append("` WHERE 1=1 ");

        if (targetUserId != null) {
            sqlBuilder.append("AND user_id = ? ");
        }

        if ("p".equals(mode)) {
            sqlBuilder.append("AND raw_message = ? ");
        } else {
            sqlBuilder.append("AND raw_message LIKE ? ");
        }

        sqlBuilder.append("ORDER BY msg_time DESC LIMIT ?");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlBuilder.toString())) {

            int paramIndex = 1;

            if (targetUserId != null) {
                pstmt.setLong(paramIndex++, targetUserId);
            }

            if ("p".equals(mode)) {
                pstmt.setString(paramIndex++, keyword);
            } else {
                pstmt.setString(paramIndex++, "%" + keyword + "%");
            }

            pstmt.setInt(paramIndex, MAX_RESULTS);

            try (ResultSet rs = pstmt.executeQuery()) {
                List<String> results = new ArrayList<>();
                // 清理过期缓存
                long nowTime = System.currentTimeMillis();
                nicknameCache.entrySet().removeIf(entry -> nowTime - entry.getValue().time > NICKNAME_CACHE_EXPIRE);

                while (rs.next()) {
                    String rawMsg = rs.getString("raw_message");

                    // 1. 基础过滤：过滤掉完全不需要显示的脏消息
                    if (isMessyMessage(rawMsg)) {
                        continue;
                    }

                    // 2. 【新增】核心过滤：对接 SensitiveWordFilter 进行违规词过滤
                    // 如果消息内容包含 filter.yml 中的词汇，直接跳过不显示
                    if (SensitiveWordFilter.containsSensitiveWord(rawMsg)) {
                        continue;
                    }

                    // 3. 消息清洗：清洗消息内容，转换 CQ 码为可读文本
                    String displayMsg = cleanMessageContent(rawMsg);

                    long userId = rs.getLong("user_id");
                    long timeSec = rs.getLong("msg_time");

                    // 获取发送者昵称
                    String nick = fetchNickname(userId);
                    String userDisplay = (nick != null && !nick.isEmpty()) ? nick : String.valueOf(userId);
                    String timeStr = DATE_FORMAT.format(new Date(timeSec * 1000L));

                    results.add(String.format("[%s] %s: %s", timeStr, userDisplay, displayMsg));
                }

                // 4. 构建回复内容
                StringBuilder reply = new StringBuilder();
                reply.append("🔍 搜索结果 (模式:").append("p".equals(mode) ? "精确" : "模糊").append("): \"").append(keyword).append("\"\n");
                if (targetUserId != null) {
                    String targetNick = fetchNickname(targetUserId);
                    reply.append("👤 限定用户: ").append(targetNick != null ? targetNick : targetUserId).append("\n");
                }
                reply.append("----------------\n");

                if (results.isEmpty()) {
                    reply.append("未找到符合条件的记录（或含有违规内容已被隐藏）");
                } else {
                    for (String line : results) {
                        reply.append(line).append("\n");
                    }
                    // 添加自动撤回提示
                    reply.append("\n⚠️ 本消息将于1分钟后自动撤回");
                }

                // 使用自动撤回逻辑发送
                sendAndScheduleWithdraw(groupId, reply.toString().trim());
            }

        } catch (SQLException e) {
            if (e.getErrorCode() == 1146) {
                MessageSender.sendGroupMessage(groupId, "当前群聊暂无消息记录表，无法搜索。");
            } else {
                e.printStackTrace();
                MessageSender.sendGroupMessage(groupId, "搜索时发生数据库错误：" + e.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
            MessageSender.sendGroupMessage(groupId, "搜索功能发生未知异常。");
        }
    }

    /**
     * 发送消息并安排在1分钟后自动撤回
     */
    private static void sendAndScheduleWithdraw(long groupId, String message) {
        try {
            // 构造发送请求的 JSON
            ObjectNode root = objectMapper.createObjectNode();
            root.put("group_id", groupId);
            root.put("message", message);
            String jsonBody = objectMapper.writeValueAsString(root);

            // 发送 HTTP 请求
            HttpURLConnection conn = (HttpURLConnection) new URL(SEND_MSG_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 读取响应获取 message_id
            StringBuilder resp = new StringBuilder();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[256];
                int len;
                while ((len = in.read(buf)) != -1) {
                    resp.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                }
            }

            // 解析 message_id
            JsonNode respNode = objectMapper.readTree(resp.toString());
            if (respNode.has("data") && respNode.get("data").has("message_id")) {
                long messageId = respNode.get("data").get("message_id").asLong();

                // 安排撤回任务：60秒后执行
                scheduler.schedule(() -> withdrawMessage(messageId), 60, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            System.err.println("[INFO] 自动撤回发送失败，转为普通发送。Error: " + e.getMessage());
            MessageSender.sendGroupMessage(groupId, message);
        }
    }

    /**
     * 执行撤回操作
     */
    private static void withdrawMessage(long messageId) {
        try {
            String jsonBody = String.format("{\"message_id\":%d}", messageId);
            HttpURLConnection conn = (HttpURLConnection) new URL(DELETE_MSG_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                System.err.println("[INFO] 撤回消息 " + messageId + " 失败，HTTP Code: " + code);
            }
            conn.disconnect();
        } catch (Exception e) {
            System.err.println("[INFO] 撤回消息 " + messageId + " 异常: " + e.getMessage());
        }
    }

    /**
     * 判断是否为"乱七八糟"的非文本类消息，直接丢弃
     */
    private static boolean isMessyMessage(String msg) {
        if (msg == null) return true;
        String trimmed = msg.trim();

        if (trimmed.length() > MAX_MSG_LENGTH) return true;
        if (trimmed.startsWith("[CQ:json")) return true;
        if (trimmed.startsWith("[CQ:xml")) return true;
        if (trimmed.startsWith("[CQ:file")) return true;
        if (trimmed.startsWith("[CQ:image")) return true;
        if (trimmed.startsWith("[CQ:card")) return true;

        return false;
    }

    /**
     * 清洗消息内容，转换 CQ 码为可读文本
     */
    private static String cleanMessageContent(String msg) {
        if (msg == null) return "";
        msg = REPLY_PATTERN.matcher(msg).replaceAll("[回复]");
        msg = IMAGE_PATTERN.matcher(msg).replaceAll("[图片]");

        Matcher m = AT_PATTERN.matcher(msg);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String qqStr = m.group(1);
            String replacement = "@" + qqStr;
            try {
                long qq = Long.parseLong(qqStr);
                CachedNickname cached = nicknameCache.get(qq);
                if (cached != null && (System.currentTimeMillis() - cached.time < NICKNAME_CACHE_EXPIRE)) {
                    replacement = "@" + cached.nick;
                }
            } catch (Exception ignored) {}
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        msg = sb.toString();

        return msg.trim();
    }

    /**
     * 获取昵称
     */
    private static String fetchNickname(Long userId) {
        try {
            long now = System.currentTimeMillis();
            CachedNickname cached = nicknameCache.get(userId);
            if (cached != null && (now - cached.time) < NICKNAME_CACHE_EXPIRE) {
                return cached.nick;
            }
            if (cached != null) {
                nicknameCache.remove(userId);
            }

            String body = String.format("{\"user_id\":\"%d\"}", userId);
            HttpURLConnection conn = (HttpURLConnection) new URL(NICKNAME_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            StringBuilder resp = new StringBuilder();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[256];
                int len;
                while ((len = in.read(buf)) != -1) {
                    resp.append(new String(buf, 0, len, StandardCharsets.UTF_8));
                }
            }

            JsonNode node = objectMapper.readTree(resp.toString());
            String nick = null;
            if (node.has("data") && node.get("data").has("nick")) {
                nick = node.get("data").get("nick").asText();
            }

            if (nick != null && !nick.isEmpty()) {
                nicknameCache.put(userId, new CachedNickname(nick, now));
            }
            return nick;
        } catch (Exception e) {
            return null;
        }
    }
}