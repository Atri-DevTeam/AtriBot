package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.botservice.clock.Schedule;
import top.yzljc.qqbot.botservice.clock.ScheduleType;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.data.ProcessClassTable;

import java.time.LocalDate;
import java.time.LocalTime;

public class TufeClassAlert implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.SATURDAY || LocalDate.now().getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            sender.reply("周末不支持查询，如有课程安排请等待自动播报", false);
            return true;
        }
        int time = getCurrentSession(LocalTime.now());
        if (time == 0) {
            int checkTargetSession;
            long i = 1;
            do {
                LocalTime tmp = LocalTime.now().plusMinutes(i);
                checkTargetSession = getCurrentSession(tmp);
                i++;
            } while (checkTargetSession == 0);
            time = checkTargetSession;
        }
        while (ProcessClassTable.getClassTableJson(time, sender.getGroupId()) == -1) {
            time++;
        }
        return true;
    }

    @Schedule(time = "07:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "09:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "12:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "14:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "17:25:05", type = ScheduleType.DAILY)
    @Schedule(time = "19:25:05", type = ScheduleType.DAILY)
    private static void processNotify() {
        ProcessClassTable.getClassTableJson(getCurrentSession(LocalTime.now().plusMinutes(35)));
    }

    public static int getCurrentSession(LocalTime time) {
        if (time.isAfter(LocalTime.of(8,0,0)) && time.isBefore(LocalTime.of(8,45,0))) {
            return 1;
        }
        if (time.isAfter(LocalTime.of(8,45,0)) && time.isBefore(LocalTime.of(9,30,0))) {
            return 2;
        }
        if (time.isAfter(LocalTime.of(10,0,0)) && time.isBefore(LocalTime.of(10,45,0))) {
            return 3;
        }
        if (time.isAfter(LocalTime.of(10,45,0)) && time.isBefore(LocalTime.of(11,30,0))) {
            return 4;
        }
        if (time.isAfter(LocalTime.of(13,20,0)) && time.isBefore(LocalTime.of(14,5,0))) {
            return 5;
        }
        if (time.isAfter(LocalTime.of(14,5,0)) && time.isBefore(LocalTime.of(14,50,0))) {
            return 6;
        }
        if (time.isAfter(LocalTime.of(15,20,0)) && time.isBefore(LocalTime.of(16,5,0))) {
            return 7;
        }
        if (time.isAfter(LocalTime.of(16,5,0)) && time.isBefore(LocalTime.of(16,50,0))) {
            return 8;
        }
        if (time.isAfter(LocalTime.of(18,0,0)) && time.isBefore(LocalTime.of(18,45,0))) {
            return 9;
        }
        if (time.isAfter(LocalTime.of(18,45,0)) && time.isBefore(LocalTime.of(19,30,0))) {
            return 10;
        }
        if (time.isAfter(LocalTime.of(19,50,0)) && time.isBefore(LocalTime.of(20,35,0))) {
            return 11;
        }
        if (time.isAfter(LocalTime.of(20,35,0)) && time.isBefore(LocalTime.of(21,20,0))) {
            return 12;
        }
        return 0;
    }
}
