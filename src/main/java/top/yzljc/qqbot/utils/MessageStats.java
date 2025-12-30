package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.messages.RecordGroupMessage;
import top.yzljc.qqbot.messages.MessageSender;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 群发言统计工具
 */
public class MessageStats {

    // CQ码@用户匹配
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)]");
    private static final String NICKNAME_API = "http://106.14.23.232:8848/get_stranger_info";

    // 昵称缓存
    private static final Map<Long, CachedNickname> nicknameCache = new ConcurrentHashMap<>();
    private static final long NICKNAME_CACHE_EXPIRE = 60 * 1000L;
    // 是否已启动过定时器
    private static volatile boolean scheduled = false;

    /**
     * 启动每日统计定时推送（每晚23:59:45自动发统计）
     * 不需要传回调了，直接内部调用 MessageSender
     */
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
                System.err.println("MessageStats: 定时任务异常 " + e.getMessage());
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

    /**
     * 查询并发送所有统计
     */
    public static void autoReportAllGroups() {
        Set<Long> groups = findAllGroupsWithRecords();
        for (long groupId : groups) {
            String msg = buildGroupStatsMsg(groupId, LocalDate.now(), false, null);
            if (msg != null && !msg.isEmpty()) {
                // 直接调用 MessageSender
                MessageSender.sendGroupMessage(groupId, msg);
            }
        }
    }

    public static Set<Long> findAllGroupsWithRecords() {
        Set<Long> groupIds = new HashSet<>();
        try (Connection conn = RecordGroupMessage.getDataSource().getConnection();
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
            System.err.println("MessageStats: 取所有群号分表异常 " + e.getMessage());
        }
        return groupIds;
    }

    /**
     * 查询指令入口
     */
    public static void processCommand(JsonNode jsonInput) {
        if (jsonInput == null || !"group".equals(jsonInput.path("message_type").asText())) return;
        long groupId = jsonInput.path("group_id").asLong();
        String rawMsg = jsonInput.path("raw_message").asText().trim();
        String msgContent = rawMsg;
        boolean overall = false;
        Long qqAt = null;

        if (rawMsg.startsWith("/stats")) {
            if (rawMsg.startsWith("/statsoverall")) {
                overall = true;
                msgContent = rawMsg.substring(13).trim();
            } else {
                msgContent = rawMsg.substring(6).trim();
            }
            qqAt = extractAtUser(msgContent);
        } else {
            return;
        }

        LocalDate now = LocalDate.now();
        String replyMsg;
        if (qqAt == null) {
            replyMsg = buildGroupStatsMsg(groupId, now, overall, null);
        } else {
            replyMsg = buildGroupStatsMsg(groupId, now, overall, qqAt);
        }

        if (replyMsg != null && !replyMsg.isEmpty()) {
            // 直接调用 MessageSender
            MessageSender.sendGroupMessage(groupId, replyMsg);
        }
    }

    private static Long extractAtUser(String msg) {
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
            if (filterUserId != null) return "[统计] 该成员暂无发言记录。";
            else return "[统计] 暂无可统计的发言记录。";
        }
        if (filterUserId != null) {
            int count = statMap.getOrDefault(filterUserId, 0);
            String nick = fetchNickname(filterUserId);
            return String.format("[统计]%s：%s发言%d次",
                    nick == null ? filterUserId : nick,
                    overall ? "历史共" : "今日",
                    count);
        }

        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(statMap.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        sb.append(overall ? "[历史发言总统计]\n" : "[今日发言统计]\n");
        int i = 1;

        long nowTime = System.currentTimeMillis();
        nicknameCache.entrySet().removeIf(entry -> nowTime - entry.getValue().time > NICKNAME_CACHE_EXPIRE);

        for (Map.Entry<Long, Integer> entry : sorted) {
            Long userId = entry.getKey();
            String nick = fetchNickname(userId);
            sb.append(i++).append(". ")
                    .append(nick == null ? "QQ号:" + userId : nick)
                    .append("：")
                    .append(entry.getValue()).append("次")
                    .append("\n");
        }
        return sb.toString();
    }

    private static String fetchNickname(Long userId) {
        try {
            long now = System.currentTimeMillis();
            CachedNickname cached = nicknameCache.get(userId);
            if (cached != null && (now - cached.time) < NICKNAME_CACHE_EXPIRE) {
                return cached.nick;
            }
            nicknameCache.remove(userId);

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
            ObjectMapper om = new ObjectMapper();
            JsonNode node = om.readTree(resp.toString());
            String nick = null;
            if (node.has("data") && node.get("data").has("nick")) {
                nick = node.get("data").get("nick").asText();
            }
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
        String tableName = RecordGroupMessage.getDynamicTableName(groupId);

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

        try (Connection conn = RecordGroupMessage.getDataSource().getConnection();
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
            System.err.println("MessageStats: 统计失败 " + e.getMessage());
        }
        return result;
    }
}