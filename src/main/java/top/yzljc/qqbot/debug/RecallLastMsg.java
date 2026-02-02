package top.yzljc.qqbot.debug;

import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RecallLastMsg {
    private static final Map<Long, Long> latestGroupMessageMap = new ConcurrentHashMap<>();
    static Settings settings = Config.getInstance();
    private static final long DEBUG_GROUP = settings.getDebugGroupId();

    public static void recordLastMsg(long groupId, long messageId) {
        latestGroupMessageMap.put(groupId, messageId);
    }

    public static void recallLastMsg(){
        latestGroupMessageMap.forEach((_, messageId) -> PostRequest.sendSimplePost(RequestType.RECALL_MESSAGE,"message_id", messageId));
        MessageSender.sendGroupMessage(DEBUG_GROUP,"已撤回所有记录的群最后一条消息");
    }
}
