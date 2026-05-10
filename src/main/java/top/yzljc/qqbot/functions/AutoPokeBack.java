package top.yzljc.qqbot.functions;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GetPokeEvent;

/**
 * @Author YZ_Ljc_
 * @ClassName SendPoke
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.feature
 */
@Slf4j
public class AutoPokeBack implements Listener {
    @EventHandler
    public void onReceivePoke(GetPokeEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "send_poke")) return;
        if (event.getUserId() == event.getSelfId()) {
            return;
        }
        if (event.getTargetId() == event.getSelfId()) {
            event.pokeBack();
            log.info("收到来自 {} 的戳一戳，已自动回戳", event.getUserId());
        }
    }

}
