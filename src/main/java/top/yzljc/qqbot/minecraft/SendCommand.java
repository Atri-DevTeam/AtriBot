package top.yzljc.qqbot.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.App;
import top.yzljc.qqbot.messages.MessageSender;
import top.yzljc.qqbot.socket.SocketManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class SendCommand {
    private static final String ADMIN_FILE = "adminuser.json";
    private static final String SERVER_SECRET_FILE = "server-secret.json";
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // 权限与密钥配置
    private static Map<String, List<String>> adminRules = new HashMap<>();
    private static Map<String, String> serverSecretMap = new HashMap<>();

    // 用于等待 Socket 服务器响应的 Future (App.java 需要引用这个)
    public static final ConcurrentHashMap<String, CompletableFuture<String>> pendingCommandResponses = new ConcurrentHashMap<>();

    // 内部鉴权对象
    private static class AuthInfo {
        String serverId;
        String secretKey;

        AuthInfo(String serverId, String secretKey) {
            this.serverId = serverId;
            this.secretKey = secretKey;
        }
    }

    /**
     * 加载权限和密钥配置 (需定时调用)
     */
    public static void loadAdminConfig() {
        try {
            // 1. 加载管理员权限规则
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

            // 2. 加载服务器密钥
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

    /**
     * 处理 /rc 指令
     */
    public static void handle(long userId, long groupId, String rawMessage) {
        System.out.printf("[CMD] 收到指令: %s (User:%d Group:%d)\n", rawMessage, userId, groupId);

        String key = userId + "/" + groupId;

        // 超级管理员逻辑 (hardcoded)
        if (String.valueOf(userId).equals("3199590352")) {
            String[] parts = rawMessage.trim().split("\\s+", 3);
            if (parts.length < 3) {
                MessageSender.sendGroupMessage(groupId, "格式错误: /rc <ServerID> <Command>");
                return;
            }
            String targetServerId = parts[1];
            String command = parts[2];
            String secretKey = serverSecretMap.get(targetServerId);

            if (secretKey != null) {
                executeRcCommand(targetServerId, command, new AuthInfo(targetServerId, secretKey), groupId);
            } else {
                MessageSender.sendGroupMessage(groupId, "[!] 未找到目标服务器的密钥: " + targetServerId);
            }
            return;
        }

        // 普通配置管理员逻辑
        if (adminRules.containsKey(key)) {
            List<String> userServers = adminRules.get(key);
            String[] parts = rawMessage.trim().split("\\s+", 3);
            if (parts.length < 3) {
                MessageSender.sendGroupMessage(groupId, "格式错误: /rc <ServerID> <Command>");
                return;
            }
            String targetServerId = parts[1];
            String command = parts[2];

            // 检查该用户是否有权控制该服务器
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
                MessageSender.sendGroupMessage(groupId, "[!] 权限不足: 您在当前群未绑定服务器 " + targetServerId);
            }

        } else {
            System.out.println("[AUTH] 鉴权拒绝: " + key);
            MessageSender.sendGroupMessage(groupId, "You don't have permission to do that!");
        }
    }

    private static void executeRcCommand(String targetServerId, String command, AuthInfo info, long groupId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            // 调用 App 发送 Socket 指令
            boolean success = SocketManager.sendCommand(targetServerId, command, info.secretKey);

            if (!success) {
                MessageSender.sendGroupMessage(groupId, "[X] 目标服务器未连接或鉴权失败");
                return;
            }

            System.out.println("============================================================");
            System.out.printf("[SUCCESS] Socket 发送 -> Server: %s | Cmd: %s\n", targetServerId, command);
            System.out.println("============================================================");

            CompletableFuture<String> future = new CompletableFuture<>();
            pendingCommandResponses.put(targetServerId, future);

            String consoleLog;
            try {
                // 等待 App.java 收到 Socket 回复并填充 future
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
            MessageSender.sendGroupMessage(groupId, replyMsg);
        });
    }

    private static String cleanLog(String log) {
        if (log == null) return "";
        return log.replaceAll("\\x1B\\[[;\\d]*m", "");
    }
}