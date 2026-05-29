package top.yzljc.atribot.feature.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.chat.onebot.impl.MessageUtils;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.config.groups.GroupConfigManager;
import top.yzljc.atribot.chat.onebot.GroupInformation;

import java.util.Set;

@Deprecated(since = "2026-05-17")
public class WakeUp implements CommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(WakeUp.class);
    private static final String WAKEUP_IMG_PATH = Config.getInstance().getWakeupImgLink();
    private static final Set<Long> GROUPS = GroupInformation.fetchAllGroupIds();

    @Override
    public boolean onCommand(CommandSender sender, Command command,String label,String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        if (sender.isDebug()) {
            sendImgToGroup();
        }else{
            debugSendImgToGroup(sender.groupId());
        }
        return true;
    }

    public static void sendImgToGroup() {
        ThreadManager.execute(() -> {
            for (long groupId : GROUPS){
                if (GroupConfigManager.isFeatureEnabled(groupId, "wakeup_send")){
                    GroupMessage.chatMessage(groupId, "早上好孩子们", WAKEUP_IMG_PATH, MessageUtils.ImageType.URL);
                }
                log.info("已向群 {} 发送早安起床图片推送", groupId);
            }
        });
    }

    public static void debugSendImgToGroup(long groupId){
        GroupMessage.chatMessage(groupId, "早上好孩子们", WAKEUP_IMG_PATH, MessageUtils.ImageType.URL);
        log.info("已定向于 {} 发送早安起床图片推送", groupId);
    }
}
