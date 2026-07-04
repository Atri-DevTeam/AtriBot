package top.yzljc.atribot.service.taskscheduler;

public interface ScheduledTask extends Runnable {
    TaskSchedule schedule();
}
