package top.yzljc.atribot.utils.tools;

import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RM implements CommandExecutor {
    private static final Map<String, String> latestGroupMessageMap = new ConcurrentHashMap<>();
    private static final String DEBUG_GROUP = Config.getInstance().getNapcatDebugGroupUin();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission()) {
            sender.sendMessage("你没有权限执行此命令");
            return true;
        }
        if (args.length == 0) {
            recallLastMsg();
        } else {
            recallMsg(args[0]);
        }
        return true;
    }

    public static void recordLastMsg(String groupId, String messageId) {
        latestGroupMessageMap.put(groupId, messageId);
    }

    public static void recallLastMsg() {
        ThreadManager.execute(() -> latestGroupMessageMap.forEach((groupId, messageId) -> GroupMessage.recallMessage(messageId)));
        GroupMessage.chatMessage(DEBUG_GROUP, "已撤回所有记录的群最后一条消息");
    }

    public static void recallMsg(String messageId) {
        GroupMessage.recallMessage(messageId);
    }
}
