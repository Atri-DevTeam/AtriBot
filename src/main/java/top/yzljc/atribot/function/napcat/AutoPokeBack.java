package top.yzljc.atribot.function.napcat;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatPokedEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.Objects;

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
    public void onReceivePoke(NapcatPokedEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "send_poke")) return;
        if (Objects.equals(event.getUserId(), event.getSelfId())) {
            return;
        }
        if (Objects.equals(event.getTargetId(), event.getSelfId())) {
            event.pokeBack();
            log.info("收到来自 {} 的戳一戳，已自动回戳", event.getUserId());
        }
    }
}
