package top.yzljc.atribot.function.task;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.repo.SignRepository;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;

import java.time.LocalTime;

/**
 * @Author YZ_Ljc_
 * @ClassName CheckInExportTask
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.function.task
 */
@Slf4j
public class CheckInExportTask implements ScheduledTask {
    public static final CheckInExportTask INSTANCE = new CheckInExportTask();

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.daily, LocalTime.of(23, 55));
    }

    @Override
    public void run() {
        log.info("开始执行每日打卡数据导出...");
        SignRepository.exportAndClearDaily();
    }
}
