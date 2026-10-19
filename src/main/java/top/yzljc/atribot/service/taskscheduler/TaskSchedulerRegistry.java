package top.yzljc.atribot.service.taskscheduler;

import top.yzljc.atribot.function.tasks.pushtask.NewYearCountdownTask;
import top.yzljc.atricore.service.taskscheduler.*;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.function.task.AutoSign;
import top.yzljc.atribot.function.task.CheckInExportTask;
import top.yzljc.atribot.function.task.LootFreeDrawCleanupTask;
import top.yzljc.atribot.function.task.ManosabaDate;
import top.yzljc.atribot.function.task.MessageStats;
import top.yzljc.atribot.function.task.QqBotDocsMonitor;
import top.yzljc.atribot.function.tasks.RefreshGroupProfilesTask;
import top.yzljc.atribot.function.tasks.pushtask.HypixelBanWaveAlertTask;
import top.yzljc.atribot.function.tasks.pushtask.SkyblockPriceAlertTask;

import java.util.List;
import java.util.Objects;

public final class TaskSchedulerRegistry {
    private static final List<ScheduledTask> TASKS = List.of(
            AutoSign.INSTANCE,
            Atri.getInstance().getCalendarTask(),
            Atri.getInstance().getCheckMojira(),
            Atri.getInstance().getCardLike(),
            Atri.getInstance().getReboot(),
            Atri.getInstance().getMinecraftVersionCheck(),
            Atri.getInstance().getMinecraftNews(),
            Atri.getInstance().getHypixelAnnouncements(),
            Atri.getInstance().getHypixelSkyblockAnnouncements(),
            HypixelBanWaveAlertTask.TASK_INSTANCE,
            Atri.getInstance().getSkyblockPackCheck(),
            Atri.getInstance().getHypixelAlphaForums(),
            RefreshGroupProfilesTask.INSTANCE,
            QqBotDocsMonitor.INSTANCE,
            CheckInExportTask.INSTANCE,
            LootFreeDrawCleanupTask.INSTANCE,
            ManosabaDate.INSTANCE,
            MessageStats.INSTANCE,
            NewYearCountdownTask.INSTANCE,
            SkyblockPriceAlertTask.INSTANCE
    );

    private TaskSchedulerRegistry() {
    }

    public static void registerAll(TaskScheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        TASKS.forEach(scheduler::schedule);
    }
}
