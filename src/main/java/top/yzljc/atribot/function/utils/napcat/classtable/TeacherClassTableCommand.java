package top.yzljc.atribot.function.utils.napcat.classtable;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.Platform;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionException;

/**
 * 在官方 QQ 群聊和单聊中按教师姓名查询整周课表，并提供周次切换按钮。
 *
 * @Author YZ_Ljc_
 * @ClassName TeacherClassTableCommand
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
@Slf4j
public final class TeacherClassTableCommand implements CommandExecutor {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate FIRST_MONDAY = ProcessClassTable.SEMESTER_START.with(DayOfWeek.MONDAY);
    private static final int LAST_WEEK = (int) ChronoUnit.WEEKS.between(FIRST_MONDAY,
            ProcessClassTable.SEMESTER_END.with(DayOfWeek.MONDAY)) + 1;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)
                || (qq.getPlatform() != Platform.OFFICIAL_GROUP && qq.getPlatform() != Platform.OFFICIAL_C2C)) return true;

        if (!sender.hasPermission("atri.tufe")) return true;

        if (args.length == 0) {
            sendHelp(qq);
            return true;
        }
        int week = (int) ChronoUnit.WEEKS.between(FIRST_MONDAY,
                LocalDate.now(SCHOOL_ZONE).with(DayOfWeek.MONDAY)) + 1;
        int page = 1;
        int nameLength = args.length;
        if (args[nameLength - 1].matches("第[1-9][0-9]{0,2}页")) {
            String value = args[--nameLength];
            page = Integer.parseInt(value.substring(1, value.length() - 1));
        }
        if (nameLength > 0) {
            String last = args[nameLength - 1];
            if (List.of("上周", "本周", "下周").contains(last)) {
                week += "上周".equals(last) ? -1 : "下周".equals(last) ? 1 : 0;
                nameLength--;
            } else if (last.matches("[0-9]{1,3}")) {
                week = Integer.parseInt(last);
                nameLength--;
            } else if (last.matches("第[0-9]{1,3}周")) {
                week = Integer.parseInt(last.substring(1, last.length() - 1));
                nameLength--;
            }
        }
        if (nameLength == 0) {
            sendHelp(qq);
            return true;
        }
        String teacher = String.join(" ", Arrays.copyOf(args, nameLength)).strip();
        if (teacher.isEmpty() || teacher.length() > 40 || !teacher.matches("[\\p{L}\\p{M} ·•.'’\\-]+")) {
            sendHelp(qq);
            return true;
        }
        if (week < 1 || week > LAST_WEEK) {
            qq.sendMessage(TC.md("**教师课表**\n\n当前学期为 " + ProcessClassTable.SEMESTER
                    + "，可查询第 1 至 " + LAST_WEEK + " 周。"), TC.keyboard(List.of(List.of(
                    button("first", "第一周", teacher + " 1", true),
                    button("last", "最后一周", teacher + " " + LAST_WEEK, true)))));
            return true;
        }
        try {
            TeacherClassTableService.Snapshot snapshot = TeacherClassTableService.current();
            var loading = TeacherClassTableService.refreshIfNeeded();
            if (snapshot == null && loading.isDone()) snapshot = loading.join();
            if (snapshot == null) {
                var progress = TeacherClassTableService.progress();
                String progressText = progress.total() > 0
                        ? progress.completed() + "/" + progress.total() + " 个班级" : "正在读取班级目录";
                // 官机通过新的按钮指令查询进度，避免长时间同步后使用旧消息被动回复。
                qq.sendMessage(TC.md("**教师课表同步中**\n\n" + progressText
                        + "\n\n首次同步可能需要几分钟，稍后点击下方按钮查看进度或所选周次的结果。"),
                        TC.keyboard(List.of(List.of(button("progress", "查看进度 / 结果",
                                teacher + " " + week, true)))));
                return true;
            }
            LocalDate monday = FIRST_MONDAY.plusWeeks(week - 1L);
            List<String> pages = TeacherClassTableQuery.query(snapshot, teacher, monday, monday.plusDays(6));
            page = Math.min(page, pages.size());
            qq.sendMessage(TC.md(pages.get(page - 1)), keyboard(teacher, week, page, pages.size()));
        } catch (CompletionException failure) {
            log.warn("教师课表同步暂不可用: {}", failure.getCause() == null ? failure.getMessage() : failure.getCause().getMessage());
            qq.sendMessage(TC.md("**教师课表暂不可用**\n\n数据同步失败，请在几分钟后点击重试。"),
                    TC.keyboard(List.of(List.of(button("retry", "重试", teacher + " " + week, true)))));
        }
        return true;
    }

    private static void sendHelp(QQCommandSender qq) {
        qq.sendMessage(TC.md("**教师课表**\n\n输入教师全名，默认查询本周周一至周日的课程。"
                + "\n\n用法：" + commandPrefix() + "教师课表 姓名"
                + "\n\n指定周次直接加数字，例如：" + commandPrefix() + "教师课表 宋丽红 5"
                + "\n\n也可使用 本周、上周、下周，或通过按钮切换周次。"),
                TC.keyboard(List.of(List.of(button("name", "输入老师姓名", "", false)))));
    }

    private static Object keyboard(String teacher, int week, int page, int pages) {
        List<List<Button>> rows = new ArrayList<>();
        List<Button> weeks = new ArrayList<>();
        if (week > 1) weeks.add(button("previous_week", "上一周", teacher + " " + (week - 1), true));
        weeks.add(button("current_week", "回到本周", teacher + " 本周", true));
        if (week < LAST_WEEK) weeks.add(button("next_week", "下一周", teacher + " " + (week + 1), true));
        rows.add(weeks);
        if (pages > 1) {
            List<Button> pagination = new ArrayList<>();
            String query = teacher + " " + week + " 第";
            if (page > 1) pagination.add(button("previous_page", "上页课程", query + (page - 1) + "页", true));
            if (page < pages) pagination.add(button("next_page", "更多课程", query + (page + 1) + "页", true));
            rows.add(pagination);
        }
        rows.add(List.of(button("choose_week", "指定周次", teacher + " " + week, false),
                button("change_teacher", "换老师", "", false)));
        return TC.keyboard(rows);
    }

    private static Button button(String id, String label, String arguments, boolean enter) {
        return new Button("teacher_" + id, label, commandPrefix() + "教师课表 " + arguments,
                enter, ButtonStyle.BLUE, ButtonType.COMMAND);
    }

    private static String commandPrefix() {
        return Config.getInstance().getCommandPrefix();
    }
}
