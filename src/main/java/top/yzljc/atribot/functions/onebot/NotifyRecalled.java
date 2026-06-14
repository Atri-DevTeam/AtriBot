package top.yzljc.atribot.functions.onebot;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import top.yzljc.atribot.utils.tools.FT;
import top.yzljc.atribot.utils.tools.StructRawMessage;
import top.yzljc.atribot.chat.onebot.GroupInformation;
import top.yzljc.atribot.chat.onebot.UserInformation;
import top.yzljc.atribot.chat.onebot.impl.MessageSegment;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.PrivateMessageEvent;
import top.yzljc.atribot.event.impl.RecallMessageEvent;
import top.yzljc.atribot.event.impl.RecallType;
import top.yzljc.atribot.utils.FormatTools;

import java.util.LinkedList;
import java.util.List;
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
        String time = FormatTools.formatTimestamp(event.getTime());
        String userName = UserInformation.getUserName(event.getUserId());
        if (recalledMessage != null) {
            List<MessageSegment> toSend = new LinkedList<>();
            toSend.add(GroupMessage.createTextNode("[私聊][" + time + "][" + userName + "]撤回了一条消息: ", String.valueOf(event.getSelfId()), "AtriBot"));
            toSend.add(GroupMessage.createTextNode(recalledMessage, String.valueOf(event.getUserId()), userName));
            GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "消息撤回监听", "点击查看", "时间：" + time, "用户：" + userName);
//            Logger.debug("检测到撤回消息，用户: {}, 消息 ID: {}, 内容: {}", userName, event.getMessageId(), recalledMessage);
        }
    }

    @EventHandler
    public void onGroupMessageRecall(RecallMessageEvent event) {
        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(event.getGroupId())) return;
        if (event.getUserId() == event.getSelfId() || Config.getInstance().getNapcatRecallIgnoredUsers().contains(event.getUserId())) return;
        if (event.getType() != RecallType.GROUP) return;
        String time = FormatTools.formatTimestamp(event.getTime());
        String userName = UserInformation.getUserName(event.getUserId());
        String groupName = GroupInformation.getGroupName(event.getGroupId());
        String foundMessage = FT.unescape(GroupContentRecord.searchMessage(event.getGroupId(), event.getMessageId()));
        if (foundMessage != null) {
            List<MessageSegment> toSend = new LinkedList<>();
            if (event.getUserId() != event.getOperatorId()) {
                toSend.add(GroupMessage.createTextNode("[群聊][" + time + "][" + groupName +  "][" + userName + "]的一条消息被群管理员" + UserInformation.getUserName(event.getOperatorId()) + "撤回: ", String.valueOf(event.getSelfId()), "AtriBot"));
                toSend.add(GroupMessage.createTextNode(StructRawMessage.parse(foundMessage), String.valueOf(event.getUserId()), userName));
                GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "消息撤回监听 [管理撤回]", "点击查看", "时间：" + time, "用户：" + userName, "群：" + groupName);
            } else {
                toSend.add(GroupMessage.createTextNode("[群聊][" + time + "][" + groupName +  "][" + userName + "]撤回了一条消息: ", String.valueOf(event.getSelfId()), "AtriBot"));
                toSend.add(GroupMessage.createTextNode(StructRawMessage.parse(foundMessage), String.valueOf(event.getUserId()), userName));
                GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "消息撤回监听", "点击查看", "时间：" + time, "用户：" + userName, "群：" + groupName);
            }
//            Logger.debug("检测到撤回消息，群: {}, 用户: {}, 消息 ID: {}, 内容: {}", groupName, userName, event.getMessageId(), foundMessage);
//            Logger.debug(toSend.toString());
        }
    }
}