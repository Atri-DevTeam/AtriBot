package top.yzljc.atribot.function.task;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.function.impl.drawitem.LootService;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;

import java.time.LocalTime;

/**
 * @Author YZ_Ljc_
 * @ClassName LootFreeDrawCleanupTask
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.task
 */
@Slf4j
public class LootFreeDrawCleanupTask implements ScheduledTask {
    public static final LootFreeDrawCleanupTask INSTANCE = new LootFreeDrawCleanupTask();

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.daily, LocalTime.of(23, 55));
    }

    @Override
    public void run() {
        log.info("开始清理每日免费抽卡记录...");
        LootService.clearDailyFreeDrawRecord();
    }
}