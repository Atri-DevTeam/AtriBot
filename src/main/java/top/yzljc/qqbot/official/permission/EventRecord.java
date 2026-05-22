package top.yzljc.qqbot.official.permission;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.OfficialGroupAtMessageCreateEvent;
import top.yzljc.qqbot.event.impl.OfficialGroupDelEvent;
import top.yzljc.qqbot.event.impl.OfficialGroupJoinEvent;
import top.yzljc.qqbot.event.impl.OfficialPrivateChatEvent;
import top.yzljc.qqbot.utils.Alert;

/**
 * @Author YZ_Ljc_
 * @ClassName EventRecord
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.permission
 */
@Slf4j
public class EventRecord implements Listener {

    @EventHandler
    public void onGroupJoin(OfficialGroupJoinEvent event) {
        boolean result = GroupList.registerGroup(event.getGroupOpenId(), event.getOpMemberOpenId(), event.getTimestamp());
        if (!result) {
            log.error("Failed to register group: {}", event.getGroupOpenId());
            Alert.notify("Failed to register group: " + event.getGroupOpenId());
        } else {
            log.info("Registered group: {}", event.getGroupOpenId());
            Alert.notify(event.getOpMemberOpenId() + "将亚托莉喵添加到群聊" + event.getGroupOpenId() + "中");
        }
    }

    @EventHandler
    public void onGroupDel(OfficialGroupDelEvent event) {
        boolean result = GroupList.removeGroup(event.getGroupOpenId());
        if (!result) {
            log.error("Failed to remove group: {}", event.getGroupOpenId());
            Alert.notify("Failed to remove group: " + event.getGroupOpenId());
        } else {
            log.info("Removed group: {}", event.getGroupOpenId());
            Alert.notify(event.getOpMemberOpenId() + "将亚托莉喵移出群聊" + event.getGroupOpenId());
        }
    }

    @EventHandler
    public void onGroupMessage(OfficialGroupAtMessageCreateEvent event) {
        if (!event.getContent().trim().startsWith(Config.getInstance().getCommandPrefix())) {
            event.replyText("你好！我是亚托莉喵，感谢你在群里@我！由于官方限制，我暂时不能主动聊天哦，您可以通过 /help 查看所有可用指令，也可以通过 /feedback <内容> 向开发者提交反馈，感谢您的支持喵~");
        }
    }

    @EventHandler
    public void onPrivateMessage(OfficialPrivateChatEvent event) {
        if (!event.getContent().trim().startsWith(Config.getInstance().getCommandPrefix())) {
            event.replyText("你好！我是亚托莉喵，感谢你私聊我！由于官方限制，我暂时不能主动聊天哦，您可以通过 /help 查看所有可用指令，也可以通过 /feedback <内容> 向开发者提交反馈，感谢您的支持喵~");
        }
    }
}