package top.yzljc.atribot.function.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atricore.chat.napcat.GroupInformation;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.NapcatCommandSender;
import top.yzljc.atricore.platform.napcat.PostRequest;
import top.yzljc.atricore.platform.napcat.RequestType;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;

import java.time.LocalTime;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName AutoSign
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.task
 */
public class AutoSign implements CommandExecutor, ScheduledTask {
    public static final AutoSign INSTANCE = new AutoSign();

    private static final Logger log = LoggerFactory.getLogger(AutoSign.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "auto_sign")) return true;
        if (!nc.hasPermission()) {
            nc.sendMessage("你没有权限执行此命令");
            return true;
        }
        ThreadManager.execute(this);
        nc.sendMessage("已开始全局自动打卡");
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

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.daily, LocalTime.MIDNIGHT);
    }

    @Override
    public void run() {
        signAllGroups();
    }
}
