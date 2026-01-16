package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.socket.SocketManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendCommand {

    private static final Logger log = LoggerFactory.getLogger(SendCommand.class);

    private static final String ADMIN_FILE = "adminuser.json";
    private static final String SERVER_SECRET_FILE = "server-secret.json";
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static Map<String, List<String>> adminRules = new HashMap<>();
    private static Map<String, String> serverSecretMap = new HashMap<>();
    public static final ConcurrentHashMap<String, CompletableFuture<String>> pendingCommandResponses = new ConcurrentHashMap<>();

    private static class AuthInfo {
        String serverId;
        String secretKey;

        AuthInfo(String serverId, String secretKey) {
            this.serverId = serverId;
            this.secretKey = secretKey;
        }
    }

    public static void loadAdminConfig() {
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
            log.warn("读取权限配置文件失败：{}", e.getMessage());
        }
    }

    public static void processMessage(JsonNode json) {
        if (!"group".equals(json.path("message_type").asText(""))) return;

        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText("");
        String rawTrimmed = rawMsg.trim();

        if (rawTrimmed.startsWith("/unbanme")) {
            handleUnbanMeCommand(groupId, rawTrimmed);
        }
    }

    private static void handleUnbanMeCommand(long groupId, String rawTrimmed) {
        String[] parts = rawTrimmed.split("\\s+");
        if (parts.length < 2) {
            MessageSender.sendGroupMessage(groupId, "用法: /unbanme <ID>");
            return;
        }
        String targetId = parts[1];

        String hbtSecret = serverSecretMap.get("hbt");

        if (hbtSecret != null) {
            executeUnbanMeLogic(targetId, new AuthInfo("hbt", hbtSecret), groupId);
        } else {
            MessageSender.sendGroupMessage(groupId, "[!] 未找到hbt服务器的密钥配置，无法执行解封。");
        }
    }

    public static void handle(long userId, long groupId, String rawMessage) {
        log.info("收到指令：{} (User: {}, Group: {}", rawMessage, userId, groupId);

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
                log.info("[AUTH] 鉴权失败：用户 {} 无权控制 {}", userId, targetServerId);
                MessageSender.sendGroupMessage(groupId, "[!] 权限不足: 您在当前群未绑定服务器 " + targetServerId);
            }

        } else {
            log.info("[AUTH] 鉴权拒绝：{}", key);
            MessageSender.sendGroupMessage(groupId, "You don't have permission to do that!");
        }
    }

    private static void executeRcCommand(String targetServerId, String command, AuthInfo info, long groupId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            boolean success = SocketManager.sendCommand(targetServerId, command, info.secretKey);

            if (!success) {
                MessageSender.sendGroupMessage(groupId, "[X] 目标服务器未连接或鉴权失败");
                return;
            }

            System.out.println("============================================================");
            log.info("[SUCCESS] Socket 发送 -> Server: {} | Cmd: {}", targetServerId, command);
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
            MessageSender.sendGroupMessage(groupId, replyMsg);
        });
    }

    /**
     * 专门为 /unbanme 准备的逻辑，向 hbt 服务器发送双重解封指令
     */
    private static void executeUnbanMeLogic(String targetId, AuthInfo info, long groupId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            // 1. 发送 unban 指令
            SocketManager.sendCommand(info.serverId, "unban " + targetId, info.secretKey);

            // 2. 发送 pardon 指令并准备接收反馈
            String secondCmd = "pardon " + targetId;
            boolean success = SocketManager.sendCommand(info.serverId, secondCmd, info.secretKey);

            if (!success) {
                MessageSender.sendGroupMessage(groupId, "[X] hbt 服务器未连接或鉴权失败");
                return;
            }

            CompletableFuture<String> future = new CompletableFuture<>();
            pendingCommandResponses.put(info.serverId, future);

            String consoleLog;
            try {
                // 等待反馈
                consoleLog = future.get(4500, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                consoleLog = "(解封指令已发送，但未收到控制台回执)";
            } finally {
                pendingCommandResponses.remove(info.serverId);
            }

            String cleanLogContent = cleanLog(consoleLog);
            String replyMsg = String.format("[√] 自助解封申请已提交至 hbt\n目标ID: %s\n----------------\n控制台返回:\n%s",
                    targetId, cleanLogContent);
            MessageSender.sendGroupMessage(groupId, replyMsg);
        });
    }

    private static String cleanLog(String log) {
        if (log == null) return "";
        return log.replaceAll("\\x1B\\[[;\\d]*m", "");
    }
}
