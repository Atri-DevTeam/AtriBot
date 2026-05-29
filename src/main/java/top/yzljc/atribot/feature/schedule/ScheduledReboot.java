package top.yzljc.atribot.feature.schedule;

import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;
import top.yzljc.atribot.feature.Reboot;

public final class ScheduledReboot {

    @Schedule(time = "05:20:00", type = ScheduleType.DAILY)
    public static void run() {
        Reboot.processReboot();
    }
}
