package top.yzljc.atribot.service.taskscheduler;

import java.time.LocalTime;

public interface TaskSchedule {
    TaskSchedule setMode(ScheduleMode mode);

    TaskSchedule setTime(LocalTime time);

    default TaskSchedule setTime() {
        return setTime(LocalTime.MIDNIGHT);
    }

    ScheduleMode getMode();

    LocalTime getTime();
}
