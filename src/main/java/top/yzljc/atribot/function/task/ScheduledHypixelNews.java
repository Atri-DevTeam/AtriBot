package top.yzljc.atribot.function.task;

import top.yzljc.atribot.function.napcat.HypixelNews;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

public final class ScheduledHypixelNews {

    @Schedule(time = "00:00", type = ScheduleType.HOURLY)
    public static void run() {
        HypixelNews.checkNews(false);
    }
}
