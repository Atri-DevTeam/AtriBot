package top.yzljc.qqbot.feature.minecraft.specificserver;

import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;
import top.yzljc.qqbot.socket.SocketManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class HypixelBanTest {
    private static final Logger log = LoggerFactory.getLogger(HypixelBanTest.class);

    public static class AuthInfo {
        public String serverId;
        public String secretKey;
        public AuthInfo(String serverId, String secretKey) {
            this.serverId = serverId;
            this.secretKey = secretKey;
        }
    }

    public static void handleUnbanMeCommand(long groupId, String rawTrimmed, String hbtSecret) {
        String[] parts = rawTrimmed.split("\\s+");
        if (parts.length < 2) {
            MessageSender.sendGroupMessage(groupId, "用法: /unbanme <ID>");
            return;
        }
        String targetId = parts[1];

        if (hbtSecret != null) {
            executeUnbanMeLogic(
                    targetId,
                    new AuthInfo("hbt", hbtSecret),
                    groupId
            );
        } else {
            MessageSender.sendGroupMessage(groupId, "[!] 未找到hbt服务器的密钥配置，无法执行解封");
        }
    }

    public static void executeUnbanMeLogic(String targetId, AuthInfo info, long groupId) {
        ThreadManager.execute(() -> {
            SocketManager.sendCommand(info.serverId, "unban " + targetId, info.secretKey);
            String secondCmd = "pardon " + targetId;
            boolean success = SocketManager.sendCommand(info.serverId, secondCmd, info.secretKey);

            if (!success) {
                MessageSender.sendGroupMessage(groupId, "[X] hbt 服务器未连接或鉴权失败");
                return;
            }

            ConcurrentHashMap<String, CompletableFuture<String>> pendingCommandResponses =
                    ServerRcon.getPendingCommandResponses();

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

            String cleanLogContent = ServerRcon.cleanLog(consoleLog);
            String replyMsg = String.format("[√] 自助解封申请已提交至服务器\n目标ID: %s\n----------------\n控制台返回:\n%s",
                    targetId, cleanLogContent);
            MessageSender.sendGroupMessage(groupId, replyMsg);
        });
    }
}