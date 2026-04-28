package top.yzljc.qqbot.functions;

import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.event.impl.PrivateMessageEvent;
import top.yzljc.qqbot.utils.FormatTools;

import java.util.Arrays;
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
        if (event.getRawMessage().contains("[CQ:at,qq=970717559]") && event.getUserId() != event.getSelfId()) {
            String groupName = GetGroupInfo.getGroupName(event.getGroupId());
            String userName = event.getSender().nickname();
            String time = FormatTools.formatTimestamp(event.getTime());
            LinkedList<MessageSegment> toSend = new LinkedList<>();
            toSend.add(GroupMessage.createTextNode(event.getMessage(), String.valueOf(event.getUserId()), userName));
            GroupMessage.atUser(3199590352L, Config.getInstance().getDebugGroupId(), " 收到来自群 " + groupName + " 中" + userName + "提醒消息，内容如下：");
            ThreadManager.schedule(() -> GroupMessage.forwardMessage(Config.getInstance().getDebugGroupId(), toSend, "群聊消息提醒", "点击从查看", "时间：" + time , "群：" + groupName, "用户：" + userName), 1, TimeUnit.SECONDS);
        }
    }

    @EventHandler
    public void onPrivateChat(PrivateMessageEvent event) {
        if (event.getUserId() == event.getSelfId()) return;
        String userName = event.getSender().nickname();
        String time = FormatTools.formatTimestamp(event.getTime());
        LinkedList<MessageSegment> toSend = new LinkedList<>();
        toSend.add(GroupMessage.createTextNode(event.getMessage(), String.valueOf(event.getUserId()), userName));
        GroupMessage.atUser(3199590352L, Config.getInstance().getDebugGroupId(), " 收到来自" + userName + "私聊提醒消息，内容如下：");
        ThreadManager.schedule(() -> GroupMessage.forwardMessage(Config.getInstance().getDebugGroupId(), toSend, "私聊消息提醒", "点击从查看", "时间：" + time , "用户：" + userName), 1, TimeUnit.SECONDS);
    }
}