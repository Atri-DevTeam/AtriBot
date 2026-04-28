package top.yzljc.qqbot.service.tools;

import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.service.request.PostRequest;
import top.yzljc.qqbot.service.request.RequestType;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RM implements CommandExecutor {
    private static final Map<Long, Long> latestGroupMessageMap = new ConcurrentHashMap<>();
    static Settings settings = Config.getInstance();
    private static final long DEBUG_GROUP = settings.getDebugGroupId();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        recallLastMsg();
        return true;
    }

    public static void recordLastMsg(long groupId, long messageId) {
        latestGroupMessageMap.put(groupId, messageId);
    }

    public static void recallLastMsg() {
        ThreadManager.execute(() -> latestGroupMessageMap.forEach((_, messageId) -> GroupMessage.recallMessage(messageId)));
        GroupMessage.chatMessage(DEBUG_GROUP, "已撤回所有记录的群最后一条消息");
    }

    public static void recallMsg(long messageId) {
        GroupMessage.recallMessage(messageId);
    }
}
