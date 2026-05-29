package top.yzljc.atribot.utils.tools;

import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RM implements CommandExecutor {
    private static final Map<Long, Long> latestGroupMessageMap = new ConcurrentHashMap<>();
    private static final long DEBUG_GROUP = Config.getInstance().getDebugGroupId();

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
