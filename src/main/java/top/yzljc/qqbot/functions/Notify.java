package top.yzljc.qqbot.functions;

import top.yzljc.qqbot.service.userinfo.GetGroupInfo;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.event.impl.PrivateMessageEvent;

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
            GroupMessage.atUser(3199590352L, Config.getInstance().getDebugGroupId(), " 收到来自群 " + groupName + " 中" + userName + "提醒消息，内容如下：");
            ThreadManager.schedule(() -> GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), event.getMessage()), 1, TimeUnit.SECONDS);
        }
    }

    @EventHandler
    public void onPrivateChat(PrivateMessageEvent event) {
        if (event.getUserId() == event.getSelfId()) return;
        String userName = event.getSender().nickname();
        GroupMessage.atUser(3199590352L, Config.getInstance().getDebugGroupId(), " 收到来自" + userName + "私聊提醒消息，内容如下：");
        ThreadManager.schedule(() -> GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), event.getMessage()), 1, TimeUnit.SECONDS);
    }
}