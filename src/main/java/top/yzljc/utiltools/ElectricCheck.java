package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.*;

public class ElectricCheck {
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String QUERY_URL = "https://di.tjufe.edu.cn:8088/CardApp2021/ElecSearch.php?ec=903004&xq=1";
    private static final String NAPCAT_GROUP_API = "http://106.14.23.232:8848/send_group_msg";
    private static final long[] ALLOWED_GROUPS = {1065552660L, 818804507L, 413478250L, 1041561558L};
    private static final long[] ALLOWED_USERS = {3199590352L};
    private static final String[] KEYWORDS = {"电表", "dianbiao", "db"};
    private static final long[] BROADCAST_GROUPS = {1065552660L}; //, 413478250L
    private static final String ELECTRIC_MONITOR_JSON = "electricmonitor.json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // 断电计数器
    private static final ConcurrentHashMap<Long, Integer> cdCountMap = new ConcurrentHashMap<>();
    private static final String CD_KEYWORD = "cd";
    private static final String TEST_CHECK_KEYWORD = "testforcheck";

    public static void startScheduler() {
        long delay = computeInitialDelay();
        scheduler.scheduleAtFixedRate(ElectricCheck::scheduledQueryAndRecord, delay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
        System.out.println("[ElectricCheck] 定时任务已启动，每天23:30自动查询记录并播报电量，可用testforcheck测试。下次任务延迟(ms): " + delay);
    }

    // 计算距离下次23:30的毫秒数
    private static long computeInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.with(LocalTime.of(23, 30));
        if (!now.isBefore(next)) { // 已过今日23:30，则是明日
            next = next.plusDays(1);
        }
        return java.time.Duration.between(now, next).toMillis();
    }

    // 手动or定时执行记录与播报
    private static void scheduledQueryAndRecord() {
        try {
            ElectricData data = fetchElectricData();
            if (data != null) {
                LocalDate today = LocalDate.now();
                saveMonitorData(today, data);
                compareAndBroadcast(today, data, true);
            }
        } catch (Exception e) {
            System.err.println("[ElectricCheck] scheduledQueryAndRecord异常: " + e.getMessage());
        }
    }

    // 测试指令（仅allowed_users），模拟记录与比较
    private static void testQueryAndRecord(long groupId, long userId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                ElectricData data = fetchElectricData();
                if (data != null) {
                    LocalDate today = LocalDate.now();
                    saveMonitorData(today, data);
                    String msg = compareAndBroadcast(today, data, false); // 不播报，仅返回内容
                    sendGroupMsg(groupId, "[testforcheck]" + msg);
                }
            } catch (Exception e) {
                sendGroupMsg(groupId, "[testforcheck] 查询/记录异常: " + e.getMessage());
            }
        });
    }

    // 记录今日用电信息
    private static synchronized void saveMonitorData(LocalDate date, ElectricData data) {
        try {
            File f = new File(ELECTRIC_MONITOR_JSON);
            ObjectNode root;
            if (f.exists()) {
                try (FileInputStream in = new FileInputStream(f)) {
                    root = (ObjectNode) jsonMapper.readTree(in);
                }
            } else {
                root = jsonMapper.createObjectNode();
            }
            ObjectNode entry = jsonMapper.createObjectNode();
            entry.put("all", data.allUsed);
            entry.put("rsmd", data.rsmd);
            entry.put("rsfd", data.rsfd);
            root.set(date.format(DATE_FORMATTER), entry);
            try (FileOutputStream out = new FileOutputStream(f, false)) {
                jsonMapper.writerWithDefaultPrettyPrinter().writeValue(out, root);
            }
            System.out.println("[ElectricCheck] 用电数据已记录: " + date + " -> " + data);
        } catch (Exception e) {
            System.err.println("[ElectricCheck] 记录electricmonitor.json失败: " + e.getMessage());
        }
    }

    /**
     * 对比今日和昨日的用电量，并播报
     * 今日用电: todayUsed = todayTotal - yesterdayTotal
     * 昨日用电: yestUsed = yesterdayTotal - beforeYesterdayTotal
     * “与昨日相比增加/减少”: todayUsed - yestUsed
     */
    private static String compareAndBroadcast(LocalDate today, ElectricData todayData, boolean needBroadcast) {
        try {
            File f = new File(ELECTRIC_MONITOR_JSON);
            if (!f.exists())
                return "无历史用电记录，今日数据已保存。";

            String todayStr = today.format(DATE_FORMATTER);
            String yestStr = today.minusDays(1).format(DATE_FORMATTER);
            String byestStr = today.minusDays(2).format(DATE_FORMATTER);

            JsonNode root;
            try (FileInputStream in = new FileInputStream(f)) {
                root = jsonMapper.readTree(in);
            }

            JsonNode todayNode = root.get(todayStr);
            JsonNode yestNode = root.get(yestStr);
            JsonNode byestNode = root.get(byestStr);

            if (yestNode == null)
                return "【电表播报】没有昨日数据，今日总电表：" + todayData.allUsed
                        + "度，当前剩余免费/付费电量：" + todayData.rsmd + "度/" + todayData.rsfd + "度。";

            double todayTotal = todayData.allUsed;
            double yestTotal = yestNode.path("all").asDouble();
            double todayUsed = todayTotal - yestTotal;

            String msg;
            if (byestNode != null && byestNode.has("all")) {
                double byestTotal = byestNode.path("all").asDouble();
                double yestUsed = yestTotal - byestTotal;
                double inc = todayUsed - yestUsed;

                String incStr = String.format("%.2f", Math.abs(inc));
                String incWord = inc > 0 ? "增加" : (inc < 0 ? "减少" : "无变化");

                msg = String.format(
                        "【电表播报】当前已统一断电，今日用电%.2f度，与昨日相比%s%s度，当前剩余免费电量/付费电量：%s/%s度。",
                        todayUsed, incWord, incStr, todayData.rsmd, todayData.rsfd
                );
            } else {
                msg = String.format(
                        "【电表播报】当前已统一断电，今日用电%.2f度，没有前天数据无法计算增减，当前剩余免费电量/付费电量：%s/%s度。",
                        todayUsed, todayData.rsmd, todayData.rsfd
                );
            }
            if (needBroadcast) {
                for (long gid : BROADCAST_GROUPS) {
                    sendGroupMsg(gid, msg);
                }
            }
            System.out.println("[ElectricCheck] 今日用电播报: " + msg.replace("\n", "|"));
            return msg;
        } catch (Exception e) {
            System.err.println("[ElectricCheck] 比较与播报异常: " + e.getMessage());
            return "[播报异常] " + e.getMessage();
        }
    }

    // 拉取接口电表数据
    private static ElectricData fetchElectricData() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(QUERY_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            conn.getInputStream().close();

            JsonNode respJson = null;
            try {
                respJson = jsonMapper.readTree(respStr);
            } catch (Exception ignored) {}
            if (respJson != null) {
                double rljd = safeDouble(respJson.path("rljd"));
                double rsmd = safeDouble(respJson.path("rsmd"));
                double rsfd = safeDouble(respJson.path("rsfd"));
                return new ElectricData(rljd, rsmd, rsfd);
            }
        } catch (Exception ex) {
            System.err.println("[ElectricCheck] fetchElectricData异常: " + ex.getMessage());
        }
        return null;
    }

    private static double safeDouble(JsonNode n) {
        try { return n.asDouble(); } catch (Exception e) { return 0.0; }
    }

    public static void processElectric(JsonNode json) {
        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();
        String rawMessage = json.path("raw_message").asText().trim().toLowerCase();

        if (isAllowedUser(userId) && CD_KEYWORD.equalsIgnoreCase(rawMessage)) {
            int cur = cdCountMap.getOrDefault(userId, 0) + 1;
            if (cur >= 5) {
                sendGroupMsg(groupId, "【断电提醒】累计检测到5次断电，需要人为恢复！计数器已重置");
                cdCountMap.put(userId, 0);
            } else {
                sendGroupMsg(groupId, "【断电提醒】当前累计断电次数：" + cur + " 次（达到5次后需人为恢复）");
                cdCountMap.put(userId, cur);
            }
            return;
        }

        if (isAllowedUser(userId) && TEST_CHECK_KEYWORD.equals(rawMessage)) {
            testQueryAndRecord(groupId, userId);
            return;
        }

        if (!isAllowedGroup(groupId) || !containsKeyword(rawMessage)) return;

        Executors.newSingleThreadExecutor().submit(() -> {
            String feedback;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(QUERY_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.getInputStream().close();

                JsonNode respJson = null;
                try { respJson = jsonMapper.readTree(respStr); } catch (Exception ignored) {}
                if (respJson != null) {
                    String rec = respJson.path("rec").asText();
                    String rsmd = respJson.path("rsmd").asText();
                    String rsfd = respJson.path("rsfd").asText();
                    String rljd = respJson.path("rljd").asText();
                    String rtzd = respJson.path("rtzd").asText();
                    String rgzzt = respJson.path("rgzzt").asText();

                    String status = decodeUnicode(rgzzt);

                    String cdTimesMsg;
                    int curCd = cdCountMap.getOrDefault(userId, 0);
                    cdTimesMsg = "\n当前累计断电次数：" + curCd + " 次";

                    feedback = String.format("[电表信息]\n电表号：%s\n剩余免费电量：%s 度\n剩余收费电量：%s 度\n累计电量：%s 度\n透支电量：%s 度\n当前工作状态：%s%s",
                            rec, rsmd, rsfd, rljd, rtzd, status, cdTimesMsg);
                    System.out.println("[ElectricCheck] 电表数据发送 => " + feedback.replace("\n", " | "));
                } else {
                    feedback = "[电表查询失败] 后台接口返回格式异常或无法解析。";
                    System.err.println("[ElectricCheck] 返回内容无法解析: " + respStr);
                }
            } catch (Exception ex) {
                feedback = "[电表查询失败] 网络异常或远端接口错误。";
                System.err.println("[ElectricCheck] 查询异常: " + ex.getMessage());
            }
            sendGroupMsg(groupId, feedback);
        });
    }

    private static boolean isAllowedGroup(long groupId) {
        for (long g : ALLOWED_GROUPS)
            if (g == groupId) return true;
        return false;
    }

    private static boolean containsKeyword(String msg) {
        for (String kw : KEYWORDS)
            if (msg.equalsIgnoreCase(kw)) return true;
        return false;
    }

    private static boolean isAllowedUser(long userId) {
        for (long u : ALLOWED_USERS)
            if (u == userId) return true;
        return false;
    }

    private static String decodeUnicode(String unicodeStr) {
        StringBuilder out = new StringBuilder();
        int len = unicodeStr.length();
        for (int i = 0; i < len;) {
            char c = unicodeStr.charAt(i++);
            if (c == '\\' && i < len && unicodeStr.charAt(i) == 'u' && i + 4 < len) {
                String hex = unicodeStr.substring(i + 1, i + 5);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                } catch (Exception e) {
                    out.append("\\u").append(hex);
                }
                i += 5;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static void sendGroupMsg(long groupId, String text) {
        try {
            var textNode = Collections.singletonMap("type", "text");
            var textData = Collections.singletonMap("text", text);
            var node = new java.util.HashMap<String, Object>(textNode);
            node.put("data", textData);
            var payloadMap = new java.util.HashMap<String, Object>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", Collections.singletonList(node));
            String payload = jsonMapper.writeValueAsString(payloadMap);

            HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_GROUP_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            conn.getInputStream().close();
            System.out.println("[ElectricCheck] 群反馈已发送 => groupId: " + groupId + " 内容: " + text.replace("\n", " | "));
        } catch (Exception e) {
            System.err.println("[ElectricCheck] 群消息发送失败: " + e.getMessage());
        }
    }
    
    private static class ElectricData {
        double allUsed;
        double rsmd, rsfd;
        public ElectricData(double allUsed, double rsmd, double rsfd) {
            this.allUsed = allUsed;
            this.rsmd = rsmd;
            this.rsfd = rsfd;
        }
        @Override
        public String toString() {
            return "累计:" + allUsed + " 剩余免费:" + rsmd + " 剩余付费:" + rsfd;
        }
    }
}