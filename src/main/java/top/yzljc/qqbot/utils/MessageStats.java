package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.qqbot.botkits.seizeinfo.GetUserName;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botkits.message.SensitiveWordFilter;
import top.yzljc.qqbot.botkits.message.MessageRecorder;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageStats {

    private static final Logger log = LoggerFactory.getLogger(MessageStats.class);
    // 这玩意是处理@别人的，省的弄个一坨出来
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)]");

    static Settings settings = Config.getInstance();
    private static final String API_BASE = settings.getHttpUrl();
    private static final String NICKNAME_API = API_BASE + "/get_stranger_info";
    private static final String SEND_MSG_API = API_BASE + "/send_group_msg";
    private static final String DELETE_MSG_API = API_BASE + "/delete_msg";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Map<Long, CachedNickname> nicknameCache = new ConcurrentHashMap<>();
    private static final long NICKNAME_CACHE_EXPIRE = 60 * 1000L;
    private static volatile boolean scheduled = false;
    private static final ScheduledExecutorService withdrawScheduler = Executors.newSingleThreadScheduledExecutor();

    public static void startDailyReportScheduler() {
        if (scheduled) return;
        scheduled = true;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MsgStats-Scheduler");
            t.setDaemon(true);
            return t;
        });
        long initDelay = nextRunDelay();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                autoReportAllGroups();
            } catch (Exception e) {
                log.error("定时任务异常 {}", e.getMessage());
            }
        }, initDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    private static long nextRunDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(23, 59, 45);
        if (!now.isBefore(next)) {
            next = now.toLocalDate().plusDays(1).atTime(23, 59, 45);
        }
        return Duration.between(now, next).getSeconds();
    }

    public static void autoReportAllGroups() {
        Set<Long> groups = findAllGroupsWithRecords();
        for (long groupId : groups) {
            String msg = buildGroupStatsMsg(groupId, LocalDate.now(), false, null);
            if (msg != null && !msg.isEmpty()) {
                sendAndScheduleWithdraw(groupId, msg);
            }
        }
    }

    public static Set<Long> findAllGroupsWithRecords() {
        Set<Long> groupIds = new HashSet<>();
        try (Connection conn = MessageRecorder.getDataSource().getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, "qq_group_message_record_%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (table.startsWith("qq_group_message_record_")) {
                    String suffix = table.substring("qq_group_message_record_".length());
                    try {
                        groupIds.add(Long.valueOf(suffix));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.error("取所有群号分表异常 {}", e.getMessage());
        }
        return groupIds;
    }

    public static void processCommand(JsonNode jsonInput) {
        if (jsonInput == null || !"group".equals(jsonInput.path("message_type").asText())) return;
        long groupId = jsonInput.path("group_id").asLong();
        String rawMsg = jsonInput.path("raw_message").asText().trim();
        String msgContent = rawMsg;
        boolean overall = false;
        LocalDate targetDate = LocalDate.now();
        Long qqAt = null;

        // 【修复】增加长度判断，防止 substring 越界
        if (rawMsg.startsWith("/stats")) {
            if (rawMsg.startsWith("/statsoverall")) {
                overall = true;
                // 如果长度正好等于指令长度，说明后面没参数，给空字符串即可
                msgContent = rawMsg.length() > 13 ? rawMsg.substring(13).trim() : "";
            }
            else if (rawMsg.startsWith("/statsyesterday")) {
                targetDate = LocalDate.now().minusDays(1);
                msgContent = rawMsg.length() > 15 ? rawMsg.substring(15).trim() : "";
            }
            else if (rawMsg.startsWith("/statsy")) {
                targetDate = LocalDate.now().minusDays(1);
                msgContent = rawMsg.length() > 7 ? rawMsg.substring(7).trim() : "";
            }
            else {
                // 对应普通的 /stats
                msgContent = rawMsg.length() > 6 ? rawMsg.substring(6).trim() : "";
            }
            qqAt = extractAtUser(msgContent);
        } else {
            return;
        }

        String replyMsg;
        if (qqAt == null) {
            replyMsg = buildGroupStatsMsg(groupId, targetDate, overall, null);
        } else {
            replyMsg = buildGroupStatsMsg(groupId, targetDate, overall, qqAt);
        }

        if (replyMsg != null && !replyMsg.isEmpty()) {
            sendAndScheduleWithdraw(groupId, replyMsg);
        }
    }

    private static Long extractAtUser(String msg) {
        if (msg == null || msg.isEmpty()) return null;
        Matcher m = AT_PATTERN.matcher(msg);
        if (m.find()) {
            try {
                return Long.valueOf(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static String buildGroupStatsMsg(long groupId, LocalDate whichDay, boolean overall, Long filterUserId) {
        Map<Long, Integer> statMap = statGroupSpeak(groupId, whichDay, overall, filterUserId);
        if (statMap == null || statMap.isEmpty()) {
            if (filterUserId != null) return "[统计] 该成员暂无发言记录";
            else return "[统计] 暂无可统计的发言记录";
        }

        String timePrefix;
        if (overall) {
            timePrefix = "历史共";
        } else if (whichDay.equals(LocalDate.now())) {
            timePrefix = "今日";
        } else {
            timePrefix = whichDay.toString() + " ";
        }

        // 单人统计
        if (filterUserId != null) {
            int count = statMap.getOrDefault(filterUserId, 0);
            String nick = fetchNickname(filterUserId);

            if (SensitiveWordFilter.containsSensitiveWord(nick)) {
                nick = null; // 触发后文的 fallback 显示QQ号
            }

            return String.format("[统计]%s：%s发言%d次\n⚠️(1分钟后自动撤回)",
                    nick == null ? "QQ:" + filterUserId : nick,
                    timePrefix,
                    count);
        }

        // 排行榜统计
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(statMap.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();

        if (overall) {
            sb.append("[历史发言总统计]\n");
        } else if (whichDay.equals(LocalDate.now())) {
            sb.append("[今日发言统计]\n");
        } else if (whichDay.equals(LocalDate.now().minusDays(1))) {
            sb.append("[昨日发言统计]\n");
        } else {
            sb.append("[").append(whichDay.toString()).append(" 发言统计]\n");
        }

        int i = 1;

        long nowTime = System.currentTimeMillis();
        nicknameCache.entrySet().removeIf(entry -> nowTime - entry.getValue().time > NICKNAME_CACHE_EXPIRE);

        for (Map.Entry<Long, Integer> entry : sorted) {
            Long userId = entry.getKey();
            String nick = fetchNickname(userId);

            if (nick != null && SensitiveWordFilter.containsSensitiveWord(nick)) {
                nick = null; // 强制置空，触发下方的 "QQ号:" 逻辑
            }

            sb.append(i++).append(". ")
                    .append(nick == null ? "QQ:" + userId : nick)
                    .append("：")
                    .append(entry.getValue()).append("次")
                    .append("\n");
        }

        sb.append("\n⚠️ 本统计消息将于1分钟后自动撤回");

        if (whichDay.equals(LocalDate.now())){
            sb.append("\n使用/statsyesterday 或 /statsy 可查询昨日发言统计");
        }

        return sb.toString();
    }

    private static void sendAndScheduleWithdraw(long groupId, String message) {
        try {
            // 构造发送请求的 JSON
            ObjectNode root = objectMapper.createObjectNode();
            root.put("group_id", groupId);
            root.put("message", message);
            String jsonBody = objectMapper.writeValueAsString(root);

            // 发送 HTTP 请求
            HttpURLConnection conn = (HttpURLConnection) new URI(SEND_MSG_API).toURL().openConnection();
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

                withdrawScheduler.schedule(() -> withdrawMessage(messageId), 60, TimeUnit.SECONDS);
            }

        } catch (Exception e) {
            // 发送失败或解析失败，尝试回退到普通发送
            log.warn("自动撤回发送流程异常: {}", e.getMessage());
            MessageSender.sendGroupMessage(groupId, message);
        }
    }

    private static void withdrawMessage(long messageId) {
        try {
            String jsonBody = String.format("{\"message_id\":%d}", messageId);
            HttpURLConnection conn = (HttpURLConnection) new URI(DELETE_MSG_API).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            log.error("MessageStats: 撤回消息 {} 失败: {}", messageId, e.getMessage());
        }
    }

    private static String fetchNickname(Long userId) {
        try {
            long now = System.currentTimeMillis();
            CachedNickname cached = nicknameCache.get(userId);
            if (cached != null && (now - cached.time) < NICKNAME_CACHE_EXPIRE) {
                return cached.nick;
            }
            nicknameCache.remove(userId);

            String nick = GetUserName.getUserName(userId);

            if (nick == null) return null;

            nicknameCache.put(userId, new CachedNickname(nick, now));
            return nick;
        } catch (Exception e) {
            return null;
        }
    }

    private static class CachedNickname {
        final String nick;
        final long time;
        CachedNickname(String n, long t) {
            this.nick = n;
            this.time = t;
        }
    }

    public static Map<Long, Integer> statGroupSpeak(long groupId, LocalDate whichDay, boolean overall, Long filterUserId) {
        Map<Long, Integer> result = new HashMap<>();
        String tableName = MessageRecorder.getDynamicTableName(groupId);

        String base = "SELECT user_id, COUNT(*) as cnt FROM " + tableName + " WHERE group_id=?";
        List<Object> params = new ArrayList<>();
        params.add(groupId);

        if (!overall) {
            LocalDateTime dayStart = whichDay.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long tsBegin = dayStart.toEpochSecond(ZoneOffset.ofHours(8));
            long tsEnd = dayEnd.toEpochSecond(ZoneOffset.ofHours(8));
            base += " AND msg_time>=? AND msg_time<?";
            params.add(tsBegin);
            params.add(tsEnd);
        }
        if (filterUserId != null) {
            base += " AND user_id=?";
            params.add(filterUserId);
        }
        base += " GROUP BY user_id";

        try (Connection conn = MessageRecorder.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(base)) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else if (p instanceof Long) ps.setLong(i + 1, (Long) p);
                else ps.setObject(i + 1, p);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getLong("user_id"), rs.getInt("cnt"));
                }
            }
        } catch (Exception e) {
            log.error("MessageStats: 统计失败 {}", e.getMessage());
        }
        return result;
    }
}