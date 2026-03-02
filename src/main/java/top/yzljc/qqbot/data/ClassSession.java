package top.yzljc.qqbot.data;

import java.time.LocalTime;

public enum ClassSession {
    FIRST_SESSION("8:00", "8:45"),
    SECOND_SESSION("8:45", "9:30"),
    THIRD_SESSION("10:00", "10:45");

    private final LocalTime startTime;
    private final LocalTime endTime;

    ClassSession(String startTimeStr, String endTimeStr) {
        this.startTime = LocalTime.parse(startTimeStr);
        this.endTime = LocalTime.parse(endTimeStr);
    }

    // 获取起始时间 LocalTime
    public LocalTime getStartTime() {
        return startTime;
    }

    // 获取结束时间 LocalTime
    public LocalTime getEndTime() {
        return endTime;
    }
}