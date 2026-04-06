package top.yzljc.qqbot.functions;

import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GetPokeEvent;
import top.yzljc.qqbot.utils.Logger;

/**
 * @Author YZ_Ljc_
 * @ClassName SendPoke
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.feature
 */
public class AutoPokeBack implements Listener {
    @EventHandler
    public void onReceivePoke(GetPokeEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "send_poke")) return;
        if (event.getUserId() == event.getSelfId()) {
            return;
        }
        if (event.getTargetId() == event.getSelfId()) {
            event.pokeBack();
            Logger.info("收到来自 {} 的戳一戳，已自动回戳", event.getUserId());
        }
    }

}
