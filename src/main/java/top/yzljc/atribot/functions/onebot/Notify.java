package top.yzljc.atribot.functions.onebot;

import top.yzljc.atribot.chat.onebot.impl.MessageSegment;
import top.yzljc.atribot.chat.onebot.GroupInformation;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.GroupMessageEvent;
import top.yzljc.atribot.event.impl.PrivateMessageEvent;
import top.yzljc.atribot.utils.Alert;
import top.yzljc.atribot.utils.FormatTools;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName Notify
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions
 */
public class Notify implements Listener {
    @EventHandler
    public void onMention(GroupMessageEvent event) {
        if (event.getRawMessage().contains("[CQ:at,qq=" + event.getSelfId() + "]") && event.getUserId() != event.getSelfId()) {
            String groupName = GroupInformation.getGroupName(event.getGroupId());
            String userName = event.getSender().nickname();
            String time = FormatTools.formatTimestamp(event.getTime());
            LinkedList<MessageSegment> toSend = new LinkedList<>();
            toSend.add(GroupMessage.createTextNode(event.getMessage(), String.valueOf(event.getUserId()), userName));
            Alert.notify(" 收到来自群 " + groupName + " 中" + userName + "提醒消息，内容如下：");
            ThreadManager.schedule(() -> GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "群聊消息提醒", "点击从查看", "时间：" + time , "群：" + groupName, "用户：" + userName), 1, TimeUnit.SECONDS);
        }
    }

    @EventHandler
    public void onPrivateChat(PrivateMessageEvent event) {
        if (event.getUserId() == event.getSelfId()) return;
        String userName = event.getSender().nickname();
        String time = FormatTools.formatTimestamp(event.getTime());
        LinkedList<MessageSegment> toSend = new LinkedList<>();
        toSend.add(GroupMessage.createTextNode(event.getMessage(), String.valueOf(event.getUserId()), userName));
        Alert.notify(" 收到来自" + userName + "私聊提醒消息，内容如下：");
        ThreadManager.schedule(() -> GroupMessage.forwardMessage(Config.getInstance().getNapcatDebugGroupUin(), toSend, "私聊消息提醒", "点击从查看", "时间：" + time , "用户：" + userName), 1, TimeUnit.SECONDS);
    }
}