package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.botservice.clock.Schedule;
import top.yzljc.qqbot.botservice.clock.ScheduleType;
import top.yzljc.qqbot.feature.news.HypixelNews;

public final class ScheduledHypixelNews {

    @Schedule(time = "00:00", type = ScheduleType.HOURLY)
    public static void run() {
        HypixelNews.checkNews(false);
    }
}
