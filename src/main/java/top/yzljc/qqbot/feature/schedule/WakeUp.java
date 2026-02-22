package top.yzljc.qqbot.feature.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.userinfo.GetGroupInfo;

import java.util.Set;

public class WakeUp implements CommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(WakeUp.class);
    static Settings settings = Config.getInstance();
    private static final String WAKEUP_IMG_PATH = settings.getWakeupImgLink();
    private static final Set<Long> GROUPS = GetGroupInfo.fetchAllGroupIds();

    @Override
    public boolean onCommand(CommandSender sender, Command command,String label,String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        if (sender.isDebug()) {
            sendImgToGroup();
        }else{
            debugSendImgToGroup(sender.getGroupId());
        }
        return true;
    }

    public static void sendImgToGroup() {
        ThreadManager.execute(() -> {
            for (long groupId : GROUPS){
                if (GroupConfigManager.isFeatureEnabled(groupId, "wakeup_send")){
                    MessageSender.sendGroupMessage(groupId,"早上好孩子们",WAKEUP_IMG_PATH,false);
                }
                log.info("已向群 {} 发送早安起床图片推送", groupId);
            }
        });
    }

    public static void debugSendImgToGroup(long groupId){
        MessageSender.sendGroupMessage(groupId,"早上好孩子们",WAKEUP_IMG_PATH,false);
        log.info("已定向于 {} 发送早安起床图片推送", groupId);
    }
}
