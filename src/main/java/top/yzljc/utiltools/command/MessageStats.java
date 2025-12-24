package top.yzljc.utiltools.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.utiltools.RecordGroupMessage;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 群发言统计工具
 * - 支持统计当日和总发言次数
 * - 支持自动汇报、全员统计、单人统计
 */
public class MessageStats {

    // CQ码@用户匹配 (只取第一个@的人)
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+)]");
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";
    private static final String NICKNAME_API = "http://106.14.23.232:8848/get_stranger_info";
    // 昵称缓存（避免频繁请求，简单策略10分钟有效）
    private static final Map<Long, CachedNickname> nicknameCache = new ConcurrentHashMap<>();
    private static final long NICKNAME_CACHE_EXPIRE = 60 * 1000L;
    // 是否已启动过定时器
    private static volatile boolean scheduled = false;

    /**
     * 独立的发群消息方法，模仿 App.java 实现
     */
    public static void sendMsgToGroup(long groupId, String msg) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("group_id", groupId);
            req.put("message", msg);
            ObjectMapper objectMapper = new ObjectMapper();
            HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] json = objectMapper.writeValueAsBytes(req);
            conn.getOutputStream().write(json);
            conn.getInputStream().close();
        } catch (Exception e) {
            System.err.println("[MessageStats] 发群消息失败: " + e.getMessage());
        }
    }

    /**
     * 启动每日统计定时推送（每晚23:59:45自动发统计）
     * sendMsgFunc: (groupId, msg) -> 群发方法
     */
    public static void startDailyReportScheduler(BiConsumer<Long, String> sendMsgFunc) {
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
                autoReportAllGroups(sendMsgFunc);
            } catch (Exception e) {
                System.err.println("MessageStats: 定时任务异常 " + e.getMessage());
            }
        }, initDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    // 计算距离下一个23:59:45的秒数
    private static long nextRunDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(23, 59, 45);
        if (!now.isBefore(next)) {
            next = now.toLocalDate().plusDays(1).atTime(23, 59, 45);
        }
        return Duration.between(now, next).getSeconds();
    }

    /**
     * 查询并发送所有统计（自动推送时用，仅有记录的群）
     * @param sendMsgFunc   发送消息方法：sendMsgFunc.accept(groupId, msg);
     */
    public static void autoReportAllGroups(BiConsumer<Long, String> sendMsgFunc) {
        Set<Long> groups = findAllGroupsWithRecords();
        for (long groupId : groups) {
            String msg = buildGroupStatsMsg(groupId, LocalDate.now(), false, null);
            if (msg != null && !msg.isEmpty()) {
                sendMsgFunc.accept(groupId, msg);
            }
        }
    }

    /**
     * 全部有消息记录的群号（按所有分表名自动提取/可根据RecordGroupMessage已有方法调整）
     */
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
     * @param jsonInput 全量消息JSON
     * @param sendMsgFunc 群发方法
     */
    public static void processCommand(JsonNode jsonInput, BiConsumer<Long, String> sendMsgFunc) {
        if (jsonInput == null || !"group".equals(jsonInput.path("message_type").asText())) return;
        long groupId = jsonInput.path("group_id").asLong();
        String rawMsg = jsonInput.path("raw_message").asText().trim();
        String msgContent = rawMsg;
        boolean overall = false;
        Long qqAt = null;

        // 检查指令类型
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
            // 全部成员
            replyMsg = buildGroupStatsMsg(groupId, now, overall, null);
        } else {
            // 指定成员
            replyMsg = buildGroupStatsMsg(groupId, now, overall, qqAt);
        }
        if (replyMsg != null && !replyMsg.isEmpty()) {
            sendMsgFunc.accept(groupId, replyMsg);
        }
    }

    /**
     * 提取@用户的QQ号（只取第一个）
     */
    private static Long extractAtUser(String msg) {
        Matcher m = AT_PATTERN.matcher(msg);
        if (m.find()) {
            try {
                return Long.valueOf(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 统计并组装群或用户统计结果消息
     */
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

        // 排序(发言多的在前)
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(statMap.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        sb.append(overall ? "[历史发言总统计]\n" : "[今日发言统计]\n");
        int i = 1;

        long nowTime = System.currentTimeMillis();
        // 清理过期缓存
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

    /**
     * 获取用户QQ昵称，带有缓存和惰性自动清理
     */
    private static String fetchNickname(Long userId) {
        try {
            long now = System.currentTimeMillis();
            // 惰性清理缓存（仅本userId项，如有大量访问再扩展全表清理）
            CachedNickname cached = nicknameCache.get(userId);
            if (cached != null && (now - cached.time) < NICKNAME_CACHE_EXPIRE) {
                return cached.nick;
            }
            nicknameCache.remove(userId);

            // 请求接口
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

    /**
     * 从分表统计发言
     * @param groupId 群号
     * @param whichDay 日期（需要当天0点-隔天0点）
     * @param overall 是否查全部
     * @param filterUserId 只查此人
     * @return userId->次数
     */
    public static Map<Long, Integer> statGroupSpeak(long groupId, LocalDate whichDay, boolean overall, Long filterUserId) {
        Map<Long, Integer> result = new HashMap<>();
        String tableName = RecordGroupMessage.getDynamicTableName(groupId);

        String base = "SELECT user_id, COUNT(*) as cnt FROM " + tableName + " WHERE group_id=?";
        List<Object> params = new ArrayList<>();
        params.add(groupId);

        if (!overall) {
            LocalDateTime dayStart = whichDay.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long tsBegin = dayStart.toEpochSecond(ZoneOffset.ofHours(8)); // +8区
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