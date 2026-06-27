package top.yzljc.atribot.function.napcat;

import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.event.events.NapcatPrivateMessageEvent;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName Notify
 * @Created_at 2026/04/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public class Notify implements Listener {
    @EventHandler
    public void onMention(NapcatGroupMessageEvent event) {
        String rawMessage = event.getMessage().getContent();
        if (rawMessage.contains("[CQ:at,qq=" + Config.getInstance().getNapcatBotUin()) && !event.getUser().isBot()) {
            String groupName = GroupInformation.getGroupName(event.getGroupId());
            String userName = event.getUser().getUsername();
            String time = FormatTools.formatTimestamp(Long.parseLong(event.getTimestamp()));
            LinkedList<MessageSegment> toSend = new LinkedList<>();
            toSend.add(GroupMessage.createTextNode(event.getMessage().getSegments(), event.getUser().getUserId(), userName));
            Alert.notify(" 收到来自群 " + groupName + " 中" + userName + "提醒消息，内容如下：");
            ThreadManager.schedule(() -> GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "群聊消息提醒", "点击从查看", "时间：" + time , "群：" + groupName, "用户：" + userName), 1, TimeUnit.SECONDS);
        }
    }

    @EventHandler
    public void onPrivateChat(NapcatPrivateMessageEvent event) {
        if (event.getUser().isBot()) return;
        String userName = event.getUser().getUsername();
        String time = FormatTools.formatTimestamp(Long.parseLong(event.getTimestamp()));
        LinkedList<MessageSegment> toSend = new LinkedList<>();
        toSend.add(GroupMessage.createTextNode(event.getMessage().getSegments(), event.getUser().getUserId(), userName));
//        Alert.notify(" 收到来自" + userName + "私聊提醒消息，内容如下：");
        ThreadManager.schedule(() -> GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "私聊消息提醒", "点击从查看", "时间：" + time , "用户：" + userName), 1, TimeUnit.SECONDS);
    }
}
