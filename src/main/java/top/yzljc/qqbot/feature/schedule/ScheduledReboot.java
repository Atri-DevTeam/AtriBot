package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.service.clock.Schedule;
import top.yzljc.qqbot.service.clock.ScheduleType;
import top.yzljc.qqbot.command.impl.Reboot;

public final class ScheduledReboot {

    @Schedule(time = "05:20:00", type = ScheduleType.DAILY)
    public static void run() {
        Reboot.processReboot();
    }
}
