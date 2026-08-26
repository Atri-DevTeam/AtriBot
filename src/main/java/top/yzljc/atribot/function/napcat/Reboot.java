package top.yzljc.atribot.function.napcat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.taskscheduler.TaskPlan;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;

import java.time.LocalTime;

public class Reboot implements CommandExecutor, ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(Reboot.class);
    private static final String debugGroupId = Config.getInstance().getNapcatDebugGroupUin();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof ConsoleCommandSender console) {
            console.sendMessage("[!] 正在重启AtriMeow服务端...");
            processReboot(console.getUserId(), Config.getInstance().getNapcatDebugGroupUin());
            return true;
        }

        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (!nc.hasPermission()) {
            nc.sendMessage("你没有权限执行此命令");
            return true;
        }
        processReboot(nc.getUserId(), nc.getGroupId());
        return true;
    }

    public static void processReboot(String userId, String groupId) {
        log.info("收到管理员 {} 的终止指令", userId);
        GroupMessage.chatMessage(groupId, "收到管理员 " + userId + " 的终止指令，正在终止进程...");

        new Thread(() -> {
            try {
                Thread.sleep(1000);
                log.info("调用系统退出方法终止当前进程：System.exit(0)");
                System.exit(0);
            } catch (Exception e) {
                log.warn("终止进程时发生异常: {}", e.getMessage());
                GroupMessage.chatMessage(groupId, "终止失败: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.daily).setTime(LocalTime.of(5, 20, 0));
    }

    @Override
    public void run() {
        processReboot("Scheduler", debugGroupId);
    }
}
