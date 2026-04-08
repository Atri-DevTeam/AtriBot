package top.yzljc.qqbot.functions;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.service.tools.FT;
import top.yzljc.qqbot.service.tools.StructRawMessage;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;
import top.yzljc.qqbot.service.userinfo.GetUserInfo;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.PrivateMessageEvent;
import top.yzljc.qqbot.event.impl.RecallMessageEvent;
import top.yzljc.qqbot.event.impl.RecallType;
import top.yzljc.qqbot.utils.FormatTools;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName NotifyRecalled
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions
 */
public class NotifyRecalled implements Listener {

    private final Cache<Long, LinkedList<MessageSegment>> privateMessageCache = CacheBuilder.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();

    @EventHandler
    public void onPrivateMessage(PrivateMessageEvent event) {
        if (event.getUserId() == event.getSelfId()) return;
        privateMessageCache.put(event.getMessageId(), event.getMessage());
    }

    @EventHandler
    public void onPrivateMessageRecall(RecallMessageEvent event) {
        if (event.getUserId() == event.getSelfId()) return;
        if (event.getType() != RecallType.PRIVATE) return;
        LinkedList<MessageSegment> recalledMessage = privateMessageCache.getIfPresent(event.getMessageId());
        String time = "[" + FormatTools.formatTimestamp(event.getTime()) + "] ";
        String userName = "[" + GetUserInfo.getUserName(event.getUserId()) + "]";
        if (recalledMessage != null) {
            GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(),  "[私聊]" + time + userName + "撤回了一条消息: ");
            ThreadManager.schedule(() -> GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), recalledMessage), 1, TimeUnit.SECONDS);
        }
    }

    @EventHandler
    public void onGroupMessageRecall(RecallMessageEvent event) {
        if (!Config.getInstance().getMessageSpyGroups().contains(event.getGroupId())) return;
        if (event.getUserId() == event.getSelfId() || Config.getInstance().getIgnoredUsers().contains(event.getUserId())) return;
        if (event.getType() != RecallType.GROUP) return;
        String time = "[" + FormatTools.formatTimestamp(event.getTime()) + "] ";
        String userName = "[" + GetUserInfo.getUserName(event.getUserId()) + "]";
        String groupName = "[" + GetGroupInfo.getGroupName(event.getGroupId()) + "]";
        String foundMessage = FT.unescape(GroupContentRecord.searchMessage(event.getGroupId(), event.getMessageId()));
        if (foundMessage != null) {
            LinkedList<MessageSegment> s = StructRawMessage.parse(foundMessage);
            GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), "[群聊]" + time + groupName + userName + "撤回了一条消息: ");
            ThreadManager.schedule(() -> GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), s), 1, TimeUnit.SECONDS);
            // Logger.debug("检测到撤回消息，群: {}, 用户: {}, 消息 ID: {}, 内容: {}", groupName, userName, event.getMessageId(), s);
        }
    }
}