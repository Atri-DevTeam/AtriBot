package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import top.yzljc.utiltools.command.AnnounceGroup;
import top.yzljc.utiltools.img.ManosabaDate;

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

import static top.yzljc.utiltools.LikeUser.sendLike;

public class SendLike {
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";
    private static final String ADMIN_FILE = "adminuser.json";
    private static final String SERVER_SECRET_FILE = "server-secret.json";
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static Map<String, List<String>> adminRules = new HashMap<>();
    private static Map<String, String> serverSecretMap = new HashMap<>();

    // ==== 新增：允许触发MC新闻手动检查的管理员QQ列表 ====
    private static final List<String> ALLOW_USERS = Arrays.asList(
            "3199590352"// 其他管理员QQ
    );

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
            loadAdminConfig();

            // ==== 启动 Minecraft 新闻定时任务 ====
            MinecraftNews.startScheduler();

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
                            System.err.println("[ERROR] 处理消息异常: " + e.getMessage());
                            e.printStackTrace();
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
        String postType = json.path("post_type").asText("");
        if ("request".equals(postType)) {
            AutoAccept.handle(json);
            return;
        }
        if (!"message".equals(postType)) {
            return;
        }

        ElectricCheck.processElectric(json);
        AutoSign.processAutoSign(json);
        AutoRepeat.processGroupMessage(json);
        ManosabaDate.processManodate(json);
        HypixelNews.processTestForHyp(json);
        AnnounceGroup.processAcCommand(json);
        AnnoyUser.processMessage(json);

        String messageType = json.path("message_type").asText();
        if (!"group".equals(messageType)) {
            return;
        }

        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();
        String rawMessage = json.path("raw_message").asText();

        if (rawMessage != null) {
            String msgLower = rawMessage.trim().toLowerCase();

            if ("testformc".equals(msgLower)) {
                if (ALLOW_USERS.contains(String.valueOf(userId))) {
                    sendGroupMessage(groupId, "正在手动检查 Minecraft 最新咨询...");
                    Executors.newSingleThreadExecutor().submit(() -> {
                        MinecraftNews.checkNews(true);
                    });
                } else {
                }
                return;
            }

            String[] keywords = {"赞我", "zanwo", "likeme"};
            for (String kw : keywords) {
                if (msgLower.equalsIgnoreCase(kw)) {
                    sendLike(userId, groupId);
                    return;
                }
            }
        }

        CheckBilibili.process(json);

        // 处理 /rc 指令
        if (rawMessage != null && rawMessage.trim().startsWith("/rc")) {
            System.out.printf("[CMD] 收到指令: %s (User:%d Group:%d)\n", rawMessage, userId, groupId);

            String key = userId + "/" + groupId;

            if (String.valueOf(userId).equals("3199590352")) {
                String[] parts = rawMessage.trim().split("\\s+", 3);
                if (parts.length < 3) {
                    sendGroupMessage(groupId, "格式错误: /rc <ServerID> <Command>");
                    return;
                }
                String targetServerId = parts[1];
                String command = parts[2];
                String secretKey = serverSecretMap.get(targetServerId);

                if (secretKey != null) {
                    executeRcCommand(targetServerId, command, new AuthInfo(targetServerId, secretKey), groupId);
                } else {
                    sendGroupMessage(groupId, "[!] 未找到目标服务器的密钥: " + targetServerId);
                }
                return;
            }

            if (adminRules.containsKey(key)) {
                List<String> userServers = adminRules.get(key);
                String[] parts = rawMessage.trim().split("\\s+", 3);
                if (parts.length < 3) {
                    sendGroupMessage(groupId, "格式错误: /rc <ServerID> <Command>");
                    return;
                }
                String targetServerId = parts[1];
                String command = parts[2];

                AuthInfo matchedInfo = null;
                for (String sid : userServers) {
                    if (sid.equals(targetServerId)) {
                        String secret = serverSecretMap.get(targetServerId);
                        if (secret != null) {
                            matchedInfo = new AuthInfo(sid, secret);
                        }
                        break;
                    }
                }

                if (matchedInfo != null) {
                    executeRcCommand(targetServerId, command, matchedInfo, groupId);
                } else {
                    System.out.println("[AUTH] 鉴权失败: 用户 " + userId + " 无权控制 " + targetServerId);
                    sendGroupMessage(groupId, "[!] 权限不足: 您在当前群未绑定服务器 " + targetServerId);
                }

            } else {
                System.out.println("[AUTH] 鉴权拒绝: " + key);
                sendGroupMessage(groupId, "You don't have permission to do that!");
            }
        }
    }

    private static String cleanLog(String log) {
        if (log == null) return "";
        return log.replaceAll("\\x1B\\[[;\\d]*m", "");
    }

    private static void executeRcCommand(String targetServerId, String command, AuthInfo info, long groupId) {
        // ... (保持不变) ...
        Executors.newSingleThreadExecutor().submit(() -> {
            boolean success = App.sendCommand(targetServerId, command, info.secretKey);

            if (!success) {
                sendGroupMessage(groupId, "[X] 目标服务器未连接或鉴权失败");
                return;
            }

            System.out.println("============================================================");
            System.out.printf("[SUCCESS] Socket 发送 -> Server: %s | Cmd: %s\n", targetServerId, command);
            System.out.println("============================================================");

            CompletableFuture<String> future = new CompletableFuture<>();
            pendingCommandResponses.put(targetServerId, future);

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

            String replyMsg = String.format("[√] 指令已送达\n目标: %s\n内容: %s\n----------------\n控制台返回:\n%s",
                    targetServerId, command, cleanLogContent);
            sendGroupMessage(groupId, replyMsg);
        });
    }

    public static void sendGroupMessage(long groupId, String content) {
        // ... (保持不变) ...
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
        // ... (保持不变) ...
        try {
            Path adminPath = Paths.get(ADMIN_FILE);
            Map<String, List<String>> newRules = new HashMap<>();

            if (Files.exists(adminPath)) {
                JsonNode rootNode = jsonMapper.readTree(adminPath.toFile());

                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        String user = node.path("user").asText();
                        String group = node.path("group").asText();
                        String sId = node.path("server-id").asText();

                        if (!user.isEmpty() && !group.isEmpty() && !sId.isEmpty()) {
                            String key = user + "/" + group;
                            newRules.computeIfAbsent(key, k -> new ArrayList<>()).add(sId);
                        }
                    }
                }
            }
            adminRules = newRules;

            Path secretPath = Paths.get(SERVER_SECRET_FILE);
            Map<String, String> secretMap = new HashMap<>();

            if (Files.exists(secretPath)) {
                JsonNode secNode = jsonMapper.readTree(secretPath.toFile());
                if (secNode.isArray()) {
                    for (JsonNode node : secNode) {
                        String sid = node.path("server-id").asText();
                        String secret = node.path("secret-key").asText();
                        if (!sid.isEmpty() && !secret.isEmpty()) {
                            secretMap.put(sid, secret);
                        }
                    }
                }
            }
            serverSecretMap = secretMap;
        } catch (IOException e) {
            System.out.println("[WARN] 读取权限配置文件失败: " + e.getMessage());
        }
    }
}