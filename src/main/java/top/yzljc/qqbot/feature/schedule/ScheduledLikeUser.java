package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.service.clock.Schedule;
import top.yzljc.qqbot.service.clock.ScheduleType;
import top.yzljc.qqbot.feature.LikeUser;

public final class ScheduledLikeUser {

    @Schedule(time = "00:03:00", type = ScheduleType.DAILY)
    public static void run() {
        LikeUser.likeAllinList();
    }
}
