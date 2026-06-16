package top.yzljc.atribot.utils.debug;

import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialBotDebug
 * @Created_at 2026/05/28
 * @Project AtriBot
 * @Package top.yzljc.atribot.debug
 */
public class OfficialPacket implements Listener {

    public static final AtomicBoolean isOfficialDebugEnabled = new AtomicBoolean(false);

    @EventHandler
    public void onGroupAtMessage(OfficialGroupMessageCreateEvent event) {
        if (!event.getGroupId().equals(Config.getInstance().getDebugGroupOpenId())) return;
        if (event.getMessage().getContent().trim().contains("!debug -o")) {
            if (isOfficialDebugEnabled.compareAndSet(false, true)) {
                GroupChat.sendMessage(Config.getInstance().getDebugGroupOpenId(), "已启用官机调试模式，已监听全局官机事件");
            } else {
                isOfficialDebugEnabled.set(false);
                GroupChat.sendMessage(Config.getInstance().getDebugGroupOpenId(), "已禁用官机调试模式！");
            }
        }
    }
}