package top.yzljc.atribot.function.task;

import top.yzljc.atribot.function.napcat.Reboot;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

public final class ScheduledReboot {

    @Schedule(time = "05:20:00", type = ScheduleType.DAILY)
    public static void run() {
        Reboot.processReboot();
    }
}
