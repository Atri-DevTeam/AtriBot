package top.yzljc.atribot.function.task;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.NapcatCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;

import java.time.LocalTime;

/**
 * @Author YZ_Ljc_
 * @ClassName ManosabaDate
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.task
 */
public class ManosabaDate implements CommandExecutor, ScheduledTask {
    public static final ManosabaDate INSTANCE = new ManosabaDate();

    private static final Logger log = LoggerFactory.getLogger(ManosabaDate.class);
    private static final String GROUP_ID = String.valueOf(Config.getInstance().getManosabaGroupId());

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (nc.getGroupId().equals(GROUP_ID)) {
            var data = PreImageGenerate.dump(OpenApi.get("bot.manosaba-date"), Map.of(), nc.getPlatform());
            if (data.isError() || data.url() == null) {
                String errMsg = data.errorMessage();
                nc.sendMessage("开发天数图片获取失败: " + errMsg);
                return true;
            }
            nc.sendMessage(ImageComponent.imageOf(data.url()));
        } else {
            nc.sendMessage("此指令无法在该群聊调用！");
        }
        return true;
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.daily, LocalTime.of(0, 0, 10));
    }

    @Override
    public void run() {
        var data = PreImageGenerate.dump(OpenApi.get("bot.manosaba-date"), Map.of(), Platform.NAPCAT_GROUP);
        if (data.isError() || data.url() == null) {
            String errMsg = data.errorMessage();
            log.error("ManosabaDate 定时任务失败: {}", errMsg);
            return;
        }
        GroupMessage.chatMessage(Config.getInstance().getManosabaGroupId(), ImageComponent.imageOf(data.url()));
    }
}
