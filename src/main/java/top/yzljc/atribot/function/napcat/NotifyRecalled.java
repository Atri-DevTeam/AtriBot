package top.yzljc.atribot.function.napcat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.UserInformation;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.chat.napcat.impl.StructRawMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatPrivateMessageEvent;
import top.yzljc.atribot.event.events.NapcatRecallMessageEvent;
import top.yzljc.atribot.event.impl.RecallType;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.utils.FormatTools;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName NotifyRecalled
 * @Created_at 2026/04/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public class NotifyRecalled implements Listener {

    private final Cache<String, Message> privateMessageCache = CacheBuilder.newBuilder()
            .expireAfterWrite(2, TimeUnit.MINUTES)
            .build();

    @EventHandler
    public void onPrivateMessage(NapcatPrivateMessageEvent event) {
        if (event.getUser().isBot()) return;
        privateMessageCache.put(event.getMessage().getMessageId(), event.getMessage());
    }

    @EventHandler
    public void onPrivateMessageRecall(NapcatRecallMessageEvent event) {
        if (event.getUserId().equals(event.getSelfId())) return;
        if (event.getType() != RecallType.PRIVATE) return;
        Message recalledMessage = privateMessageCache.getIfPresent(event.getMessageId());
        String time = FormatTools.formatTimestamp(Long.parseLong(event.getTime()));
        String userName = UserInformation.getUserName(event.getUserId());
        if (recalledMessage != null) {
            List<MessageSegment> toSend = new LinkedList<>();
            toSend.add(GroupMessage.createTextNode("[私聊][" + time + "][" + userName + "]撤回了一条消息: ", event.getSelfId(), "AtriBot"));
            toSend.add(GroupMessage.createTextNode(recalledMessage.getContent(), event.getUserId(), userName));
            GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "消息撤回监听", "点击查看", "时间：" + time, "用户：" + userName);
        }
    }

    @EventHandler
    public void onGroupMessageRecall(NapcatRecallMessageEvent event) {
        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(event.getGroupId())) return;
        if (event.getUserId().equals(event.getSelfId()) || Config.getInstance().getNapcatRecallIgnoredUsers().contains(event.getUserId())) return;
        if (event.getType() != RecallType.GROUP) return;
        String time = FormatTools.formatTimestamp(Long.parseLong(event.getTime()));
        String userName = UserInformation.getUserName(event.getUserId());
        String groupName = GroupInformation.getGroupName(event.getGroupId());
        String foundMessage = FormatTools.unescape(GroupContentRecord.searchMessage(Long.parseLong(event.getGroupId()), Long.parseLong(event.getMessageId())));
        if (foundMessage != null) {
            List<MessageSegment> toSend = new LinkedList<>();
            if (!event.getUserId().equals(event.getOperatorId())) {
                toSend.add(GroupMessage.createTextNode("[群聊][" + time + "][" + groupName + "][" + userName + "]的一条消息被群管理员" + UserInformation.getUserName(event.getOperatorId()) + "撤回: ", event.getSelfId(), "AtriBot"));
                toSend.add(GroupMessage.createTextNode(StructRawMessage.parse(foundMessage), event.getUserId(), userName));
                GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "消息撤回监听 [管理撤回]", "点击查看", "时间：" + time, "用户：" + userName, "群：" + groupName);
            } else {
                toSend.add(GroupMessage.createTextNode("[群聊][" + time + "][" + groupName + "][" + userName + "]撤回了一条消息: ", event.getSelfId(), "AtriBot"));
                toSend.add(GroupMessage.createTextNode(StructRawMessage.parse(foundMessage), event.getUserId(), userName));
                GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "消息撤回监听", "点击查看", "时间：" + time, "用户：" + userName, "群：" + groupName);
            }
        }
    }
}
