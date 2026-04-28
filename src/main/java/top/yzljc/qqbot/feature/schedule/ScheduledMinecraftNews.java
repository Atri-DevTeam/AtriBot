package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.service.clock.Schedule;
import top.yzljc.qqbot.service.clock.ScheduleType;
import top.yzljc.qqbot.functions.minecraftnews.MinecraftNews;
import top.yzljc.qqbot.service.thread.ThreadManager;

public final class ScheduledMinecraftNews {

    @Schedule(time = "00:00", type = ScheduleType.HOURLY)
    public static void run() {
        ThreadManager.execute(() -> MinecraftNews.checkNews(false));
    }
}
