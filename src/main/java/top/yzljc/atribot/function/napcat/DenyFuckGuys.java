package top.yzljc.atribot.function.napcat;

import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupRequestEvent;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;

/**
 * @Author YZ_Ljc_
 * @ClassName DenyFuckGuys
 * @Created_at 2026/04/03
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.functions
 */
public class DenyFuckGuys implements Listener {
    @EventHandler
    public void onGroupRequest(NapcatGroupRequestEvent event) {
        if (event.getComment().contains("进群交流，请同意，谢谢") || event.getComment().contains("请同意") ||
                event.getComment().contains("通过谢谢") || event.getComment().contains("请通过") || event.getComment().contains("通过下") || event.getComment().contains("通过")){
            event.reject("疑似人机验证信息，如误判请更换重试");
            BotRuntimeData.callAntiBotAction();
        }
    }
}