package top.yzljc.atribot.function.task;

import top.yzljc.atribot.function.napcat.like.LikeUser;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

public final class ScheduledLikeUser {

    @Schedule(time = "00:03:00", type = ScheduleType.DAILY)
    public static void run() {
        LikeUser.likeAllinList();
    }
}
