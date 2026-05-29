package top.yzljc.atribot.feature.schedule;

import top.yzljc.atribot.functions.official.minecraft.VersionCheckImpl;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

/**
 * @Author YZ_Ljc_
 * @ClassName ScheduledCheckMcVersion
 * @Created_at 2026/06/04
 * @Project AtriBot
 * @Package top.yzljc.atribot.feature.schedule
 */
public class ScheduledCheckMcVersion {

    @Schedule(time = "00:00", type = ScheduleType.HALF_HOURLY)
    public static void run() {
        VersionCheckImpl.checkVersion();
    }
}