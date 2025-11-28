package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import static top.yzljc.utiltools.Likeuser.sendLike;

public class SendLike {
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";
    private static final String ADMIN_FILE = "adminuser.txt";
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "ljcyyds0316@";
    private static final String REDIS_CHANNEL = "mc_rc_channel";
    private static JedisPool jedisPool;

    private static Map<String, AuthInfo> adminRules = new HashMap<>();

    // 【新增】用于存储等待指令回执的Future，Key=serverId
    public static final ConcurrentHashMap<String, CompletableFuture<String>> pendingCommandResponses = new ConcurrentHashMap<>();

    private static class AuthInfo {
        String serverId;
        String secretKey;

        AuthInfo(String serverId, String secretKey) {
            this.serverId = serverId;
            this.secretKey = secretKey;
        }
    }

    public static void start(int port) {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, 2000, REDIS_PASSWORD);

            loadAdminConfig();

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/", (HttpExchange exchange) -> {
                try {
                    InputStream is = exchange.getRequestBody();
                    byte[] bodyBytes = is.readAllBytes();
                    String body = new String(bodyBytes, StandardCharsets.UTF_8);

                    if (!body.isEmpty()) {
                        try {
                            JsonNode root = jsonMapper.readTree(body);
                            processMessage(root);
                        } catch (Exception e) {
                        }
                    }

                    String resp = "{\"status\":\"ok\"}";
                    exchange.sendResponseHeaders(200, resp.length());
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp.getBytes());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    exchange.sendResponseHeaders(500, 0);
                    exchange.close();
                }
            });

            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            System.out.println("[INFO] QQ指令监听服务已启动，端口: " + port);

            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(SendLike::loadAdminConfig, 60, 60, TimeUnit.SECONDS);

        } catch (IOException e) {
            System.err.println("[ERROR] 无法启动 SendLike 服务: " + e.getMessage());
        }
    }

    private static void processMessage(JsonNode json) {
        ElectricCheck.processElectric(json);
        String postType = json.path("post_type").asText();
        String messageType = json.path("message_type").asText();
        if (!"message".equals(postType) || !"group".equals(messageType)) {
            return;
        }

        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();
        String rawMessage = json.path("raw_message").asText();

        if (rawMessage != null) {
            String msgLower = rawMessage.trim().toLowerCase();
            String[] keywords = {"赞我", "zanwo", "likeme"};
            for (String kw : keywords) {
                if (msgLower.equalsIgnoreCase(kw)) {
                    sendLike(userId, groupId);
                    return;
                }
            }
        }

        if (rawMessage != null && rawMessage.trim().startsWith("/rc")) {
            System.out.printf("[CMD] 收到指令: %s (User:%d Group:%d)\n", rawMessage, userId, groupId);

            String key = userId + "/" + groupId;

            if (adminRules.containsKey(key)) {
                AuthInfo info = adminRules.get(key);
                handleRcCommand(rawMessage, groupId, userId, info);
            } else {
                System.out.println("[AUTH] 鉴权拒绝: " + key);
                sendGroupMessage(groupId, "You don't have permission to do that!");
            }
        }
    }
    private static String cleanLog(String log) {
        if (log == null) return "";
        // 正则表达式匹配 ANSI 转义序列
        return log.replaceAll("\\x1B\\[[;\\d]*m", "");
    }
    private static void handleRcCommand(String rawMessage, long groupId, long userId, AuthInfo info) {
        String[] parts = rawMessage.trim().split("\\s+", 3);
        if (parts.length < 3) {
            sendGroupMessage(groupId, "Invalid command.");
            return;
        }
        String targetServerId = parts[1];
        String command = parts[2];

        if (!targetServerId.equals(info.serverId)) {
            sendGroupMessage(groupId, "[!] 权限不足: 您只能控制编号为 " + info.serverId + " 的服务器。");
            return;
        }

        // 【修改】改为异步执行，包含等待回传逻辑
        Executors.newSingleThreadExecutor().submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Map<String, String> redisPayload = new HashMap<>();
                redisPayload.put("serverId", targetServerId);
                redisPayload.put("command", command);
                redisPayload.put("secret", info.secretKey);
                redisPayload.put("issuer", String.valueOf(userId));

                // 1. 注册 Future
                CompletableFuture<String> future = new CompletableFuture<>();
                pendingCommandResponses.put(targetServerId, future);

                // 2. 推送 Redis
                String jsonPayload = jsonMapper.writeValueAsString(redisPayload);
                jedis.publish(REDIS_CHANNEL, jsonPayload);

                System.out.println("============================================================");
                System.out.printf("[SUCCESS] Redis推送 -> Server: %s | Cmd: %s\n", targetServerId, command);
                System.out.println("============================================================");

                // 3. 等待日志回传 (超时设为 4500ms，配合插件端的 3000ms)
                String consoleLog;
                try {
                    consoleLog = future.get(4500, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    consoleLog = "(超时未收到控制台反馈)";
                } catch (Exception e) {
                    consoleLog = "(获取反馈异常: " + e.getMessage() + ")";
                } finally {
                    pendingCommandResponses.remove(targetServerId);
                }
                String cleanLogContent = cleanLog(consoleLog);
                // 4. 发送结果
                String replyMsg = String.format("[√] 指令已送达\n目标: %s\n内容: %s\n----------------\n控制台返回:\n%s",
                        targetServerId, command, cleanLogContent);
                sendGroupMessage(groupId, replyMsg);

            } catch (Exception e) {
                System.err.println("Redis error: " + e.getMessage());
                sendGroupMessage(groupId, "[X] Redis 连接失败");
            }
        });
    }

    private static void sendGroupMessage(long groupId, String content) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Map<String, Object> textData = new HashMap<>();
                textData.put("text", content);

                Map<String, Object> textNode = new HashMap<>();
                textNode.put("type", "text");
                textNode.put("data", textData);

                Map<String, Object> payloadMap = new HashMap<>();
                payloadMap.put("group_id", groupId);
                payloadMap.put("message", Collections.singletonList(textNode));

                String payload = jsonMapper.writeValueAsString(payloadMap);

                HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_API).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                conn.getInputStream().close();

                if (code != 200) {
                    System.err.println("[SENDER] 消息发送失败，HTTP Code: " + code);
                }
            } catch (Exception ex) {
                System.err.println("[SENDER] 推送异常: " + ex.getMessage());
            }
        });
    }
    private static void loadAdminConfig() {
        try {
            Path path = Paths.get(ADMIN_FILE);
            if (Files.exists(path)) {
                String content = Files.readString(path).trim();
                Map<String, AuthInfo> newRules = new HashMap<>();
                for (String pair : content.split("#")) {
                    if (pair.isBlank()) continue;
                    String[] parts = pair.trim().split("/");
                    if (parts.length == 4) {
                        String qq = parts[0];
                        String group = parts[1];
                        String sId = parts[2];
                        String secret = parts[3];
                        newRules.put(qq + "/" + group, new AuthInfo(sId, secret));
                    }
                }
                adminRules = newRules;
            }
        } catch (IOException e) {
            System.out.println("[WARN] 读取权限配置文件失败: " + e.getMessage());
        }
    }
}