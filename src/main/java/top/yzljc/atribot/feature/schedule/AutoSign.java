package top.yzljc.atribot.feature.schedule;

import top.yzljc.atribot.service.request.RequestType;
import top.yzljc.atribot.service.request.PostRequest;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.groups.GroupConfigManager;
import top.yzljc.atribot.service.ThreadManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.onebot.GroupInformation;

import java.util.Set;

public class AutoSign implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(AutoSign.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        processAutoSign();
        sender.reply("已开始全局自动打卡", false);
        return true;
    }

    private static void signAllGroups() {
        try {
            Set<Long> groupIds = GroupInformation.fetchAllGroupIds();

            if (groupIds.isEmpty()) {
                log.warn("未获取到任何群号，自动打卡跳过");
                return;
            }

            for (Long groupId : groupIds) {

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

    private static void sendGroupSign(long groupId) {
        PostRequest.sendSimplePost(RequestType.SEND_SIGN, "group_id", groupId);
    }

    @Schedule(time = "00:00:01", type = ScheduleType.DAILY)
    public static void processAutoSign() {
        ThreadManager.execute(AutoSign::signAllGroups);
    }
}
