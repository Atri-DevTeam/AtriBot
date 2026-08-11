package top.yzljc.atribot.function.task;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.ConsoleCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.service.taskscheduler.DefaultTaskSchedule;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class RefreshGroupProfilesTask implements ScheduledTask, CommandExecutor {
    public static final RefreshGroupProfilesTask INSTANCE = new RefreshGroupProfilesTask();

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final TaskSchedule SCHEDULE = new DefaultTaskSchedule(ScheduleMode.daily, LocalTime.of(1, 30));
    private static final long REFRESH_INTERVAL_MILLIS = 1_200;

    @Override
    public TaskSchedule schedule() {
        return SCHEDULE;
    }

    @Override
    public void run() {
        refreshAllGroups("schedule", null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender) && !(sender instanceof ConsoleCommandSender)) {
            return true;
        }

        if (!sender.hasPermission()) {
            sender.sendMessage("[!] 你没有权限执行此命令！");
            return true;
        }

        if (!running.compareAndSet(false, true)) {
            sender.sendMessage("[!] 群资料刷新任务已在进行中");
            return true;
        }

        sender.sendMessage("[!] 已开始刷新全部群资料");
        try {
            startRefreshAlreadyLocked("command:" + sender.getUserId(), sender);
        } catch (RuntimeException e) {
            running.set(false);
            log.error("提交群资料刷新任务失败", e);
            sender.sendMessage("群资料刷新任务启动失败");
        }
        return true;
    }

    private static void refreshAllGroups(String trigger, CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            if (sender != null) {
                sender.sendMessage("[!] 群资料刷新任务已在进行中");
            }
            return;
        }
        startRefreshAlreadyLocked(trigger, sender);
    }

    private static void startRefreshAlreadyLocked(String trigger, CommandSender sender) {
        List<OfficialGroups.GroupData> groups = OfficialGroups.listGroups().stream()
                .sorted(Comparator.comparing(OfficialGroups.GroupData::groupOpenId, Comparator.nullsLast(String::compareTo)))
                .toList();
        RefreshStats stats = new RefreshStats(groups.size());
        log.debug("[!] 开始刷新QQ官机全局群资料: trigger={}, total={}, interval={}ms", trigger, stats.total, REFRESH_INTERVAL_MILLIS);
        scheduleNext(trigger, sender, groups, stats, 0, 0);
    }

    private static void scheduleNext(String trigger, CommandSender sender, List<OfficialGroups.GroupData> groups,
                                     RefreshStats stats, int index, long delayMillis) {
        try {
            ThreadManager.schedule(() -> refreshOne(trigger, sender, groups, stats, index), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            running.set(false);
            log.error("[!] 调度群资料刷新任务失败: trigger={}, index={}", trigger, index, e);
            if (sender != null) {
                sender.sendMessage("[!] 群资料刷新任务调度失败");
            }
        }
    }

    private static void refreshOne(String trigger, CommandSender sender, List<OfficialGroups.GroupData> groups,
                                   RefreshStats stats, int index) {
        if (index >= groups.size()) {
            finishRefresh(sender, stats);
            return;
        }

        OfficialGroups.GroupData group = groups.get(index);
        String groupOpenId = group.groupOpenId();
        if (groupOpenId == null || groupOpenId.isBlank()) {
            stats.failed++;
            scheduleNext(trigger, sender, groups, stats, index + 1, REFRESH_INTERVAL_MILLIS);
            return;
        }

        try {
            QQBot.GroupProfileResult result = QQBot.fetchGroupProfileDetailed(groupOpenId, true);
            if (result.profile() != null) {
                if (!OfficialGroups.saveGroupProfile(result.profile())) {
                    stats.failed++;
                    log.warn("[!] 刷新群资料失败，保存数据库失败，群ID为 {}", groupOpenId);
                } else {
                    stats.success++;
                }
            } else if (result.isDefunctGroup()) {
                // 接口返回作废群的 err_code，直接删除群记录
                if (OfficialGroups.removeGroup(groupOpenId)) {
                    stats.removed++;
                    log.info("[!] 群资料接口返回错误码{}，群数据无效，已删除群 {} 的相关记录", result.errCode(), groupOpenId);
                } else {
                    stats.failed++;
                    log.warn("[!] 群资料接口返回错误码{}，群数据无效，但未删除群 {} 的相关记录，可能是由于该群在黑名单内", result.errCode(), groupOpenId);
                }
            } else {
                stats.failed++;
            }
        } catch (Exception e) {
            stats.failed++;
            log.error("[!] 刷新群资料异常，群ID为 {}", groupOpenId, e);
        }

        scheduleNext(trigger, sender, groups, stats, index + 1, REFRESH_INTERVAL_MILLIS);
    }

    private static void finishRefresh(CommandSender sender, RefreshStats stats) {
        running.set(false);

        String result = "[!] 全部群资料刷新完成: 总数 %d, 成功 %d, 失败 %d, 删除(无效群)=%d".formatted(stats.total, stats.success, stats.failed, stats.removed);
        if (stats.failed > 0) {
            log.warn(result);
        } else {
            log.debug(result);
        }
        if (sender != null) {
            sender.sendMessage(result);
        }
    }

    private static final class RefreshStats {
        private final int total;
        private int success;
        private int failed;
        private int removed;

        private RefreshStats(int total) {
            this.total = total;
        }
    }
}
