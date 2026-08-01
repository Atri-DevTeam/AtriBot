package top.yzljc.atribot.function.task;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.function.official.loot.LootService;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

/**
 * @Author YZ_Ljc_
 * @ClassName LootFreeDrawCleanupTask
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.task
 */
@Slf4j
public class LootFreeDrawCleanupTask {

    @Schedule(time = "23:55:00", type = ScheduleType.DAILY)
    public static void clearDailyFreeDrawRecord() {
        log.info("开始清理每日免费抽卡记录...");
        LootService.clearDailyFreeDrawRecord();
    }
}