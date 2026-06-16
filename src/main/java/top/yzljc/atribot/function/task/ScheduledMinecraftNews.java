package top.yzljc.atribot.function.task;

import top.yzljc.atribot.function.general.MinecraftNews;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

public final class ScheduledMinecraftNews {

    @Schedule(time = "00:00", type = ScheduleType.HOURLY)
    public static void run() {
        MinecraftNews.checkNews(false);
    }
}
