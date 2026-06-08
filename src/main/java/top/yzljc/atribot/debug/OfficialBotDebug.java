package top.yzljc.atribot.debug;

import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.functions.official.permission.C2CList;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialBotDebug
 * @Created_at 2026/05/28
 * @Project AtriBot
 * @Package top.yzljc.atribot.debug
 */
public class OfficialBotDebug implements Listener {

    public static final AtomicBoolean isOfficialDebugEnabled = new AtomicBoolean(false);

    @EventHandler
    public void onGroupAtMessage(OfficialGroupMessageCreateEvent event) {
        if (!event.isAtBotMessage()) return;
        if (!C2CList.isAdmin(event.getAuthor().getMemberOpenId())) return;
        if (event.getContent().trim().contains("!debug")) {
            if (isOfficialDebugEnabled.compareAndSet(false, true)) {
                event.sendMessage("已启用官机调试模式，已监听全局官机事件");
            } else {
                isOfficialDebugEnabled.set(false);
                event.sendMessage("已禁用官机调试模式！");
            }
        }
    }
}