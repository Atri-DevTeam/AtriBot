package top.yzljc.qqbot.feature.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.findinfo.GetGroupList;

import java.util.Set;

public class WakeUp {
    private static final Logger log = LoggerFactory.getLogger(WakeUp.class);
    static Settings settings = Config.getInstance();
    private static final String WAKEUP_IMG_PATH = settings.getWakeupImgLink();
    private static final Set<Long> GROUPS = GetGroupList.fetchAllGroupIds();

    public static void sendImgToGroup() {
        for (long groupId : GROUPS){
            if (GroupConfigManager.isFeatureEnabled(groupId, "wakeup_send")){
                MessageSender.sendGroupMessage(groupId,"早上好孩子们",WAKEUP_IMG_PATH,false);
            }
            log.info("已向群 {} 发送早安起床图片推送", groupId);
        }
    }

    public static void debugSendImgToGroup(long groupId){
        MessageSender.sendGroupMessage(groupId,"早上好孩子们",WAKEUP_IMG_PATH,false);
        log.info("已定向于 {} 发送早安起床图片推送", groupId);
    }
}
