package top.yzljc.qqbot.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.message.MessageRecorder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebDashboardAPI {

    private static final Logger log = LoggerFactory.getLogger(WebDashboardAPI.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String NAPCAT_ADDR = Config.getInstance().getHttpUrl();

    // 全局持有 server 实例，防止被 GC
    private static HttpServer server;
    // 专门用于 Web 请求的线程池，空闲60秒自动回收线程
    private static ExecutorService webExecutor;

    public static synchronized void start(int port) {
        // 防止重复启��
        if (server != null) return;

        try {
            // 1. 创建 HTTP 服务
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // 2. 关键优化：使用 CachedThreadPool
            // 特性：有请求时创建线程，没请求时线程数可以是 0，完全不占 CPU
            webExecutor = Executors.newCachedThreadPool();
            server.setExecutor(webExecutor);

            log.info("[WebDashboard] 服务已启动，端口：{}", port);

            // ================== 路由定义 ==================

            // 1. 获取群列表
            server.createContext("/api/groups", (exchange) -> {
                handleRequest(exchange, () -> {
                    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        return;
                    }
                    List<Map<String, Object>> result = new ArrayList<>();
                    List<String> features = GroupConfigManager.getFeatureList();
                    Map<Long, String> groupNames = fetchAllGroupNames();

                    for (Map.Entry<Long, String> entry : groupNames.entrySet()) {
                        long gid = entry.getKey();
                        Map<String, Object> gMap = new HashMap<>();
                        gMap.put("groupId", gid);
                        gMap.put("groupName", entry.getValue());
                        Map<String, Boolean> config = new HashMap<>();
                        for (String f : features) {
                            config.put(f, GroupConfigManager.isFeatureEnabled(gid, f));
                        }
                        gMap.put("config", config);
                        result.add(gMap);
                    }
                    sendResponse(exchange, result);
                });
            });

            // 2. 分页获取聊天记录
            server.createContext("/api/messages", (exchange) -> {
                handleRequest(exchange, () -> {
                    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) return;

                    Map<String, String> params = parseQuery(exchange.getRequestURI().getQuery());
                    if (!params.containsKey("groupId")) {
                        sendResponse(exchange, Collections.emptyList());
                        return;
                    }

                    long gid = Long.parseLong(params.get("groupId"));
                    int page = Integer.parseInt(params.getOrDefault("page", "1"));
                    int pageSize = 50;
                    int offset = (page - 1) * pageSize;

                    List<Map<String, Object>> msgs = new ArrayList<>();
                    // 动态获取连接，用完即关，不占用数据库连接池
                    String tableName = MessageRecorder.getDynamicTableName(gid);
                    try (Connection conn = MessageRecorder.getDataSource().getConnection()) {
                        if (tableExists(conn, tableName)) {
                            String sql = "SELECT message_id, user_id, msg_time, raw_message FROM " + tableName + " ORDER BY msg_time DESC LIMIT ? OFFSET ?";
                            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                                ps.setInt(1, pageSize);
                                ps.setInt(2, offset);
                                try (ResultSet rs = ps.executeQuery()) {
                                    while (rs.next()) {
                                        Map<String, Object> m = new HashMap<>();
                                        m.put("id", rs.getLong("message_id"));
                                        m.put("userId", rs.getLong("user_id"));
                                        m.put("time", rs.getLong("msg_time"));
                                        m.put("message", rs.getString("raw_message"));
                                        msgs.add(m);
                                    }
                                }
                            }
                        }
                    }
                    sendResponse(exchange, msgs);
                });
            });

            // 3. 昵称查询
            server.createContext("/api/nickname", (exchange) -> {
                handleRequest(exchange, () -> {
                    String query = exchange.getRequestURI().getQuery();
                    if (query == null || !query.contains("=")) {
                        sendResponse(exchange, Collections.singletonMap("nickname", "未知"));
                        return;
                    }
                    String userId = query.split("=")[1];
                    String nick = fetchNickname(userId);
                    sendResponse(exchange, Collections.singletonMap("nickname", nick));
                });
            });

            // 4. 撤回接口 (带时间范围)
            server.createContext("/api/withdraw", (exchange) -> {
                handleRequest(exchange, () -> {
                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        JsonNode body = mapper.readTree(exchange.getRequestBody());
                        List<Long> idsToWithdraw = new ArrayList<>();

                        if (body.has("messageIds") && body.get("messageIds").isArray()) {
                            for (JsonNode id : body.get("messageIds")) {
                                idsToWithdraw.add(id.asLong());
                            }
                        } else if (body.has("groupId")) {
                            long gid = body.get("groupId").asLong();
                            int limit = body.has("limit") ? body.get("limit").asInt(10) : 10;
                            long startTime = body.has("startTime") ? body.get("startTime").asLong(0) : 0;
                            long endTime = body.has("endTime") ? body.get("endTime").asLong(0) : 0;

                            Long targetUid = null;
                            if (body.has("userId")) {
                                String uStr = body.get("userId").asText();
                                if (!uStr.isEmpty() && !"0".equals(uStr)) targetUid = Long.parseLong(uStr);
                            }
                            idsToWithdraw = fetchMessageIds(gid, targetUid, limit, startTime, endTime);
                        }

                        int successCount = 0;
                        for (Long mid : idsToWithdraw) {
                            if (sendDeleteMessage(mid)) successCount++;
                        }
                        sendResponse(exchange, Collections.singletonMap("count", successCount));
                    }
                });
            });

            // 5. 开关
            server.createContext("/api/toggle", (exchange) -> {
                handleRequest(exchange, () -> {
                    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        JsonNode params = mapper.readTree(exchange.getRequestBody());
                        long gid = params.get("groupId").asLong();
                        String feature = params.get("feature").asText();
                        GroupConfigManager.toggleFeature(gid, feature);
                        sendResponse(exchange, Collections.singletonMap("status", "success"));
                    }
                });
            });

            // 启动服务
            server.start();

        } catch (IOException e) {
            e.printStackTrace();
            log.error("[WebDashboard] 启动失败，端口可能被占用：{}", port);
        }
    }

    // --- 统一的异常处理包装器，防止单个请求报错导致假死 ---
    private interface RequestHandler { void handle() throws Exception; }
    private static void handleRequest(HttpExchange exchange, RequestHandler handler) {
        try {
            setCorsHeaders(exchange); // 每次请求必设 CORS
            // 针对 OPTIONS 请求直接返回 200/204
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            handler.handle();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                // 尝试返回 JSON 格式的错误
                String errJson = "{\"error\":\"Internal Server Error\",\"msg\":\"" + e.getMessage() + "\"}";
                byte[] bytes = errJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(500, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
            } catch (IOException ex) {
                // 如果连报错都发不出去（比如连接断了），就只能记录日志了
                ex.printStackTrace();
            }
        }
    }

    // --- 数据库逻辑 ---
    private static List<Long> fetchMessageIds(long groupId, Long userId, int limit, long startTime, long endTime) {
        List<Long> list = new ArrayList<>();
        String tableName = MessageRecorder.getDynamicTableName(groupId);

        StringBuilder sql = new StringBuilder("SELECT message_id FROM ").append(tableName).append(" WHERE group_id = ?");

        if (startTime > 0 && endTime > 0) {
            sql.append(" AND msg_time >= ? AND msg_time <= ?");
        }
        if (userId != null) {
            sql.append(" AND user_id = ?");
        }

        if (startTime > 0 && endTime > 0) {
            sql.append(" ORDER BY id DESC LIMIT 2000");
        } else {
            sql.append(" ORDER BY id DESC LIMIT ?");
        }

        try (Connection conn = MessageRecorder.getDataSource().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int idx = 1;
            ps.setLong(idx++, groupId);

            if (startTime > 0 && endTime > 0) {
                ps.setLong(idx++, startTime);
                ps.setLong(idx++, endTime);
            }
            if (userId != null) {
                ps.setLong(idx++, userId);
            }
            if (startTime <= 0 || endTime <= 0) {
                ps.setInt(idx++, limit);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getLong("message_id"));
            }
        } catch (Exception e) {
            log.error("查库失败：{}", e.getMessage());
        }
        return list;
    }

    private static boolean sendDeleteMessage(long messageId) {
        try {
            URL url = new URI(NAPCAT_ADDR + "/delete_msg").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(4000);
            String jsonBody = "{\"message_id\":\"" + messageId + "\"}";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            return conn.getResponseCode() == 200;
        } catch (Exception e) { return false; }
    }

    private static String fetchNickname(String userId) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(NAPCAT_ADDR + "/get_stranger_info").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(("{\"user_id\":" + userId + "}").getBytes());
            JsonNode root = mapper.readTree(conn.getInputStream());
            return root.path("data").path("nick").asText("未知");
        } catch (Exception e) { return "未知"; }
    }

    private static Map<Long, String> fetchAllGroupNames() {
        Map<Long, String> map = new HashMap<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(NAPCAT_ADDR + "/get_group_list").toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write("{}".getBytes());
            JsonNode root = mapper.readTree(conn.getInputStream());
            if (root.has("data")) for (JsonNode node : root.get("data")) map.put(node.get("group_id").asLong(), node.get("group_name").asText());
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    private static boolean tableExists(Connection conn, String tableName) throws Exception {
        DatabaseMetaData dbm = conn.getMetaData();
        try (ResultSet tables = dbm.getTables(null, null, tableName, null)) { return tables.next(); }
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendResponse(HttpExchange exchange, Object data) throws IOException {
        byte[] resp = mapper.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, resp.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> res = new HashMap<>();
        if (query == null) return res;
        for (String p : query.split("&")) { String[] kv = p.split("="); if (kv.length > 1) res.put(kv[0], kv[1]); }
        return res;
    }
}
