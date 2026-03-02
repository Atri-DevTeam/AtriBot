package top.yzljc.qqbot.feature.minecraft.specificserver;

import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;
import top.yzljc.qqbot.socket.SocketManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class YunTea {

    public static void handleWhiteListCommand(long userId, long groupId, String rawMessage) {
        String key = userId + "/" + groupId;
        List<String> userServers = ServerRcon.getAdminRules().get(key);

        if (!String.valueOf(userId).equals("3199590352")){
            if (userServers == null || !userServers.contains("yt")) {
                MessageSender.sendGroupMessage(groupId, "[!] 权限不足：您的账号未与 YunTEA 服务器管理组绑定，无法使用白名单指令");
                return;
            }
        }

        String[] parts = rawMessage.trim().split("\\s+");
        if (parts.length < 2) {
            MessageSender.sendGroupMessage(groupId, "用法: /wl add|remove <用户名> 或 /wl list");
            return;
        }
        String action = parts[1];
        String ytSecret = ServerRcon.getServerSecretMap().get("yt");
        if (ytSecret == null) {
            MessageSender.sendGroupMessage(groupId, "[!] 未找到YunTEA服务器的密钥配置，无法操作白名单");
            return;
        }
        ServerRcon.AuthInfo ytInfo = new ServerRcon.AuthInfo("yt", ytSecret);

        String ytCmd;
        switch (action) {
            case "add":
                if (parts.length < 3) {
                    MessageSender.sendGroupMessage(groupId, "用法: /wl add <用户名>");
                    return;
                }
                ytCmd = String.format("owhitelist add name %s", parts[2]);
                break;
            case "remove":
                if (parts.length < 3) {
                    MessageSender.sendGroupMessage(groupId, "用法: /wl remove <用户名>");
                    return;
                }
                ytCmd = String.format("owhitelist remove name %s", parts[2]);
                break;
            case "list":
                ytCmd = "owhitelist list name";
                break;
            default:
                MessageSender.sendGroupMessage(groupId, "未知子命令，仅支持 add、remove、list");
                return;
        }

        executeWhiteListCommand("yt", ytCmd, ytInfo, groupId);
    }

    private static void executeWhiteListCommand(String targetServerId, String command, ServerRcon.AuthInfo info, long groupId) {
        ThreadManager.execute(() -> {
            boolean success = SocketManager.sendCommand(targetServerId, command, info.secretKey);

            if (!success) {
                MessageSender.sendGroupMessage(groupId, "[X] YunTEA 服务器未连接或鉴权失败");
                return;
            }

            CompletableFuture<String> future = new CompletableFuture<>();
            ServerRcon.getPendingCommandResponses().put(targetServerId, future);

            String consoleLog;
            try {
                consoleLog = future.get(4500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                consoleLog = "(超时未收到控制台反馈)";
            } catch (Exception e) {
                consoleLog = "(获取反馈异常: " + e.getMessage() + ")";
            } finally {
                ServerRcon.getPendingCommandResponses().remove(targetServerId);
            }
            String cleanLogContent = ServerRcon.cleanLog(consoleLog);

            String replyMsg = String.format(
                    "[√] 白名单指令已送达 YunTEA 登陆服\n内容: %s\n----------------\n控制台返回:\n%s",
                    command, cleanLogContent);
            MessageSender.sendGroupMessage(groupId, replyMsg);
        });
    }

    public static void handleWhiteListSelfCheck(long groupId, String rawMessage) {
        String trimmed = rawMessage.trim();

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) {
            MessageSender.sendGroupMessage(groupId, "用法：白名单 <你的游戏名>（有空格，无书名号）");
            return;
        }
        String playerName = parts[1];
        String ytSecret = ServerRcon.getServerSecretMap().get("yt");
        if (ytSecret == null) {
            MessageSender.sendGroupMessage(groupId, "[!] 未找到YunTEA服务器的密钥配置，无法查询白名单");
            return;
        }
        ServerRcon.AuthInfo ytInfo = new ServerRcon.AuthInfo("yt", ytSecret);

        String ytCmd = String.format("owhitelist check name %s", playerName);

        ThreadManager.execute(() -> {
            boolean success = SocketManager.sendCommand("yt", ytCmd, ytInfo.secretKey);
            if (!success) {
                MessageSender.sendGroupMessage(groupId, "[X] YunTEA 服务器未连接或鉴权失败");
                return;
            }

            CompletableFuture<String> future = new CompletableFuture<>();
            ServerRcon.getPendingCommandResponses().put("yt", future);

            String consoleLog;
            try {
                consoleLog = future.get(10000, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                consoleLog = "(超时未收到控制台反馈)";
            } catch (Exception e) {
                consoleLog = "(获取反馈异常: " + e.getMessage() + ")";
            } finally {
                ServerRcon.getPendingCommandResponses().remove("yt");
            }

            boolean foundTrue = consoleLog != null && consoleLog.contains("[yz-ljc-bot-key_true]");

            if (foundTrue) {
                MessageSender.sendGroupMessage(groupId,
                        String.format("玩家 %s 的白名单已通过审核！", playerName));
            } else {
                MessageSender.sendGroupMessage(groupId,
                        String.format("没有查询到玩家 %s 的白名单信息，可能是玩家尚未提交问卷或审核尚未通过！", playerName));
                MessageSender.sendGroupMessage(858661536L,
                        String.format("玩家群组中，%s查询了自己的白名单信息但是无结果，请检查是否有新的调查问卷需要审核！", playerName));
            }
        });
    }
}