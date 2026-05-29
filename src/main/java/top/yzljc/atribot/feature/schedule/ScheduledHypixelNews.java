package top.yzljc.atribot.feature.schedule;

import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;
import top.yzljc.atribot.feature.news.HypixelNews;

public final class ScheduledHypixelNews {

    @Schedule(time = "00:00", type = ScheduleType.HOURLY)
    public static void run() {
        HypixelNews.checkNews(false);
    }
}
