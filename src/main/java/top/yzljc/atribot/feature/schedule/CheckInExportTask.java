package top.yzljc.atribot.feature.schedule;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.repo.SignRepository;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

/**
 * @Author YZ_Ljc_
 * @ClassName CheckInExportTask
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.feature.schedule
 */
@Slf4j
public class CheckInExportTask {

    @Schedule(time = "23:55:00", type = ScheduleType.DAILY)
    public static void exportDailyCheckIn() {
        log.info("开始执行每日打卡数据导出...");
        SignRepository.exportAndClearDaily();
    }
}
