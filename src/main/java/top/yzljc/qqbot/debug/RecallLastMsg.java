package top.yzljc.qqbot.debug;

import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecallLastMsg implements CommandExecutor {
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
        ThreadManager.execute(() -> latestGroupMessageMap.forEach((_, messageId) -> PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE, "message_id", messageId)));
        MessageSender.sendGroupMessage(DEBUG_GROUP, "已撤回所有记录的群最后一条消息");
    }
}
