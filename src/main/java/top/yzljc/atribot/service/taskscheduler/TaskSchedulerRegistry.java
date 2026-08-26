package top.yzljc.atribot.service.taskscheduler;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.function.tasks.RefreshGroupProfilesTask;

import java.util.List;
import java.util.Objects;

public final class TaskSchedulerRegistry {
    private static final List<ScheduledTask> TASKS = List.of(
            Atri.getInstance().getCalendarTask(),
            Atri.getInstance().getCheckMojira(),
            Atri.getInstance().getCardLike(),
            Atri.getInstance().getReboot(),
            Atri.getInstance().getMinecraftVersionCheck(),
            Atri.getInstance().getMinecraftNews(),
            Atri.getInstance().getHypixelAnnouncements(),
            Atri.getInstance().getSkyblockPackCheck(),
            Atri.getInstance().getHypixelAlphaForums(),
            RefreshGroupProfilesTask.INSTANCE
    );

    private TaskSchedulerRegistry() {
    }

    public static void registerAll(TaskScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        TASKS.forEach(scheduler::schedule);
    }
}
