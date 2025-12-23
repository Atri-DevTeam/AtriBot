package top.yzljc.utiltools.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.utiltools.RecordGroupMessage;

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
            conn.setConnectTimeout(5000); // 合理设置超时时间
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
     * 启动每日统计定时推送（每晚0:00:05自动发统计）
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
        // 首次延时 = 距下一次凌晨0:00:05的秒数
        long initDelay = nextRunDelay();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                autoReportAllGroups(sendMsgFunc);
            } catch (Exception e) {
                System.err.println("MessageStats: 定时任务异常 " + e.getMessage());
            }
        }, initDelay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    // 计算距离下一个0点0分5秒的秒数
    private static long nextRunDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next =
                now.toLocalDate().plusDays(1).atTime(0, 0, 5);
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
                msgContent = rawMsg.substring(13).trim(); // "/statsoverall".length() == 13
            } else {
                msgContent = rawMsg.substring(6).trim();  // "/stats".length() == 6
            }
            qqAt = extractAtUser(msgContent);
        } else {
            return; // 不是统计指令直接略过
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
     * @param groupId 群号
     * @param whichDay 统计哪天（当日请用LocalDate.now()，总统计忽略）
     * @param overall 是否查总数
     * @param filterUserId 只查此人（null则全员）
     * @return 统计文本
     */
    public static String buildGroupStatsMsg(long groupId, LocalDate whichDay, boolean overall, Long filterUserId) {
        Map<Long, Integer> statMap = statGroupSpeak(groupId, whichDay, overall, filterUserId);
        if (statMap == null || statMap.isEmpty()) {
            if (filterUserId != null) return "[统计] 该成员暂无发言记录。";
            else return "[统计] 暂无可统计的发言记录。";
        }
        if (filterUserId != null) {
            int count = statMap.getOrDefault(filterUserId, 0);
            return String.format("[统计]%d：%s发言%d次",
                    filterUserId,
                    overall ? "历史共" : "今日",
                    count);
        }

        // 排序(发言多的在前)
        List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(statMap.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        sb.append(overall ? "[历史发言总统计]\n" : "[今日发言统计]\n");
        int i = 1;
        for (Map.Entry<Long, Integer> entry : sorted) {
            sb.append(i++).append(". ")
                    .append("QQ号:").append(entry.getKey()).append("：")
                    .append(entry.getValue()).append("次")
                    .append("\n");
        }
        return sb.toString();
    }

    // 已不用@，直接输出QQ号
    // private static String atUserStr(long qq) {
    //     return "[CQ:at,qq=" + qq + "]";
    // }

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
            // 当天零点和明天零点
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