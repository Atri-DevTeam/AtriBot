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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
                    return; // 点赞后直接返回，不做后续处理
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

        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> redisPayload = new HashMap<>();
            redisPayload.put("serverId", targetServerId);
            redisPayload.put("command", command);
            redisPayload.put("secret", info.secretKey);
            redisPayload.put("issuer", String.valueOf(userId));

            String jsonPayload = jsonMapper.writeValueAsString(redisPayload);
            jedis.publish(REDIS_CHANNEL, jsonPayload);

            System.out.println("============================================================");
            System.out.printf("[SUCCESS] Redis推送 -> Server: %s | Cmd: %s\n", targetServerId, command);
            System.out.println("============================================================");

            String replyMsg = String.format("[√] 指令已送达\n目标: %s\n内容: %s", targetServerId, command);
            sendGroupMessage(groupId, replyMsg);
        } catch (Exception e) {
            System.err.println("Redis error: " + e.getMessage());
            sendGroupMessage(groupId, "[X] Redis 连接失败");
        }
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