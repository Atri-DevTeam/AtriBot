package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

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
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // 【核心修改 1】 Value 改为 List，支持一对多
    private static Map<String, List<AuthInfo>> adminRules = new HashMap<>();

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

        String messageType = json.path("message_type").asText();
        if (!"group".equals(messageType)) {
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

        // 处理 /rc 指令
        if (rawMessage != null && rawMessage.trim().startsWith("/rc")) {
            System.out.printf("[CMD] 收到指令: %s (User:%d Group:%d)\n", rawMessage, userId, groupId);

            String key = userId + "/" + groupId;

            // 先检查该用户在当前群是否有任何权限配置
            if (adminRules.containsKey(key)) {
                List<AuthInfo> userServers = adminRules.get(key);

                // 解析指令，获取目标 ServerID
                String[] parts = rawMessage.trim().split("\\s+", 3);
                if (parts.length < 3) {
                    sendGroupMessage(groupId, "格式错误: /rc <ServerID> <Command>");
                    return;
                }
                String targetServerId = parts[1];
                String command = parts[2];

                // 【核心修改 2】 遍历列表，寻找匹配的 ServerID
                AuthInfo matchedInfo = null;
                for (AuthInfo info : userServers) {
                    if (info.serverId.equals(targetServerId)) {
                        matchedInfo = info;
                        break;
                    }
                }

                if (matchedInfo != null) {
                    // 找到了对应权限，执行指令
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
        Executors.newSingleThreadExecutor().submit(() -> {
            // 传递密钥给 App.sendCommand
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

    // 【核心修改 3】加载逻辑适配一对多
    private static void loadAdminConfig() {
        try {
            Path path = Paths.get(ADMIN_FILE);
            if (Files.exists(path)) {
                JsonNode rootNode = jsonMapper.readTree(path.toFile());

                Map<String, List<AuthInfo>> newRules = new HashMap<>();

                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        String user = node.path("user").asText();
                        String group = node.path("group").asText();
                        String sId = node.path("server-id").asText();
                        String secret = node.path("secret-key").asText();

                        if (!user.isEmpty() && !group.isEmpty()) {
                            String key = user + "/" + group;
                            // 如果 Key 不存在，初始化一个新的 List
                            newRules.computeIfAbsent(key, k -> new ArrayList<>())
                                    .add(new AuthInfo(sId, secret));
                        }
                    }
                }
                adminRules = newRules;
            }
        } catch (IOException e) {
            System.out.println("[WARN] 读取权限配置文件失败: " + e.getMessage());
        }
    }
}