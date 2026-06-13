package top.yzljc.atribot.feature.schedule;

import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;
import top.yzljc.atribot.functions.overall.minecraftnews.MinecraftNews;

public final class ScheduledMinecraftNews {

    @Schedule(time = "00:00", type = ScheduleType.HOURLY)
    public static void run() {
        MinecraftNews.checkNews(false);
    }
}
