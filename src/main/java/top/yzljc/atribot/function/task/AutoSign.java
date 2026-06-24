package top.yzljc.atribot.function.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

import java.util.Set;

public class AutoSign implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutoSign.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "auto_sign")) return true;
        if (!sender.hasPermission()) {
            sender.sendMessage("你没有权限执行此命令");
            return true;
        }
        processAutoSign();
        sender.sendMessage("已开始全局自动打卡");
        return true;
    }

    private static void signAllGroups() {
        try {
            Set<String> groupIds = GroupInformation.fetchAllGroupIds();

            if (groupIds.isEmpty()) {
                log.warn("未获取到任何群号，自动打卡跳过");
                return;
            }

            for (String groupId : groupIds) {

                if (!GroupConfigManager.isFeatureEnabled(groupId, "auto_sign")) {
                    continue;
                }

                sendGroupSign(groupId);
                log.info("群 {} 打卡成功", groupId);
            }
        } catch (Exception e) {
            log.warn("自动打卡异常", e);
        }
    }

    private static void sendGroupSign(String groupId) {
        PostRequest.sendSimplePost(RequestType.SEND_SIGN, "group_id", groupId);
    }

    @Schedule(time = "00:00:00", type = ScheduleType.DAILY)
    public static void processAutoSign() {
        ThreadManager.execute(AutoSign::signAllGroups);
    }
}
