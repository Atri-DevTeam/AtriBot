package top.yzljc.atribot.feature.schedule;

import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;
import top.yzljc.atribot.feature.like.LikeUser;

public final class ScheduledLikeUser {

    @Schedule(time = "00:03:00", type = ScheduleType.DAILY)
    public static void run() {
        LikeUser.likeAllinList();
    }
}
