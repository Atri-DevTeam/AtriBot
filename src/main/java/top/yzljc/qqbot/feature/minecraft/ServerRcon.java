package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.feature.minecraft.specificserver.YunTea;
import top.yzljc.qqbot.socket.SocketManager;
import top.yzljc.qqbot.feature.minecraft.specificserver.HypixelBanTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerRcon {

    private static final Logger log = LoggerFactory.getLogger(ServerRcon.class);

    private static final String ADMIN_FILE = ConfigFile.RCON_USER.getFileName();
    private static final String SERVER_SECRET_FILE = ConfigFile.RCON_SERVER_SECRET.getFileName();
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static Map<String, List<String>> adminRules = new HashMap<>();
    private static Map<String, String> serverSecretMap = new HashMap<>();
    public static final ConcurrentHashMap<String, CompletableFuture<String>> pendingCommandResponses = new ConcurrentHashMap<>();

    public static class AuthInfo {
        public String serverId;
        public String secretKey;

        public AuthInfo(String serverId, String secretKey) {
            this.serverId = serverId;
            this.secretKey = secretKey;
        }
    }

    public static Map<String, List<String>> getAdminRules() {
        return adminRules;
    }
    public static Map<String, String> getServerSecretMap() {
        return serverSecretMap;
    }
    public static ConcurrentHashMap<String, CompletableFuture<String>> getPendingCommandResponses() {
        return pendingCommandResponses;
    }
    public static String cleanLog(String log) {
        if (log == null) return "";
        return log.replaceAll("\\x1B\\[[;\\d]*m", "");
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

    // server子包指令传入的核心逻辑，所有与服务器相关的内容都在这里分发
    public static void processMessage(JsonNode json) {
        if (!"group".equals(json.path("message_type").asText(""))) return;

        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText("");
        long userId = json.path("user_id").asLong();
        String rawTrimmed = rawMsg.trim();

        if (rawTrimmed.startsWith("/unbanme")) {
            HypixelBanTest.handleUnbanMeCommand(groupId, rawTrimmed, serverSecretMap.get("hbt"));
        }

        if (rawTrimmed.startsWith("/rc")) {
            handle(userId, groupId, rawTrimmed);
        }

        if (rawTrimmed.startsWith("/wl")){
            YunTea.handleWhiteListCommand(userId, groupId, rawTrimmed);
        }

        if (rawTrimmed.startsWith("白名单") && groupId == 715842297L){
            YunTea.handleWhiteListSelfCheck(groupId, rawTrimmed);
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
        ThreadManager.execute(() -> {
            boolean success = SocketManager.sendCommand(targetServerId, command, info.secretKey);

            if (!success) {
                MessageSender.sendGroupMessage(groupId, "[X] 目标服务器未连接或鉴权失败");
                return;
            }

            log.info("Minecraft服务器指令发送 -> 服务器: {} | 指令: {}", targetServerId, command);

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
}