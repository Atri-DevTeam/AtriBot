package top.yzljc.atribot.function.task;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.function.napcat.classtable.ProcessClassTable;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;

public class TufeClassAlert implements CommandExecutor {
    private static final int MAX_SESSION = 12;
    private static final int LOOKAHEAD_DAYS = 7;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "tufe_class_alert")) return true;
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        int startSessionToday = getCurrentSession(now);
        if (startSessionToday == 0) {
            startSessionToday = findNextSessionToday(now);
        }

        for (int dayOffset = 0; dayOffset <= LOOKAHEAD_DAYS; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);
            int startSession = (dayOffset == 0) ? Math.max(1, startSessionToday) : 1;

            for (int session = startSession; session <= MAX_SESSION; session++) {
                int result = ProcessClassTable.getClassTableJson(session, nc.getGroupId(), date);
                if (result == ProcessClassTable.RESULT_FOUND) {
                    return true;
                }
                if (result == ProcessClassTable.RESULT_REQUEST_FAILED) {
                    nc.sendMessage("课表接口暂时不可用，请稍后再试。");
                    return true;
                }
            }
        }

        nc.sendMessage("近期未查询到课程安排。");
        return true;
    }

    @Schedule(time = "07:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "09:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "12:45:05", type = ScheduleType.DAILY)
    @Schedule(time = "14:45:05", type = ScheduleType.DAILY)
    @Schedule(time = "17:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "19:25:05", type = ScheduleType.DAILY)
    private static void processNotify() {
        int session = getCurrentSession(LocalTime.now().plusMinutes(35));
        if (session > 0) {
            ProcessClassTable.getClassTableJson(session);
        }
    }

    public static int getCurrentSession(LocalTime time) {
        if (!time.isBefore(LocalTime.of(8, 0, 0)) && time.isBefore(LocalTime.of(8, 45, 0))) {
            return 1;
        }
        if (!time.isBefore(LocalTime.of(8, 45, 0)) && time.isBefore(LocalTime.of(9, 30, 0))) {
            return 2;
        }
        if (!time.isBefore(LocalTime.of(10, 0, 0)) && time.isBefore(LocalTime.of(10, 45, 0))) {
            return 3;
        }
        if (!time.isBefore(LocalTime.of(10, 45, 0)) && time.isBefore(LocalTime.of(11, 30, 0))) {
            return 4;
        }
        if (!time.isBefore(LocalTime.of(13, 20, 0)) && time.isBefore(LocalTime.of(14, 5, 0))) {
            return 5;
        }
        if (!time.isBefore(LocalTime.of(14, 5, 0)) && time.isBefore(LocalTime.of(14, 50, 0))) {
            return 6;
        }
        if (!time.isBefore(LocalTime.of(15, 20, 0)) && time.isBefore(LocalTime.of(16, 5, 0))) {
            return 7;
        }
        if (!time.isBefore(LocalTime.of(16, 5, 0)) && time.isBefore(LocalTime.of(16, 50, 0))) {
            return 8;
        }
        if (!time.isBefore(LocalTime.of(18, 0, 0)) && time.isBefore(LocalTime.of(18, 45, 0))) {
            return 9;
        }
        if (!time.isBefore(LocalTime.of(18, 45, 0)) && time.isBefore(LocalTime.of(19, 30, 0))) {
            return 10;
        }
        if (!time.isBefore(LocalTime.of(19, 50, 0)) && time.isBefore(LocalTime.of(20, 35, 0))) {
            return 11;
        }
        if (!time.isBefore(LocalTime.of(20, 35, 0)) && time.isBefore(LocalTime.of(21, 20, 0))) {
            return 12;
        }
        return 0;
    }

    private static int findNextSessionToday(LocalTime now) {
        for (int i = 1; i <= (24 * 60); i++) {
            int session = getCurrentSession(now.plusMinutes(i));
            if (session > 0) return session;
            if (now.plusMinutes(i).isBefore(now)) break;
        }
        return 0;
    }
}
