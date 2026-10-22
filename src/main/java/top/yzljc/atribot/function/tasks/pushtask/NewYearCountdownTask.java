package top.yzljc.atribot.function.tasks.pushtask;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.utils.tools.Alert;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName NewYearCountdownTask
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks.pushtask
 */
public final class NewYearCountdownTask extends PushTask implements ScheduledTask {

    public static final NewYearCountdownTask INSTANCE = new NewYearCountdownTask();

    private static final String FUNCTION_ID = "new_year_countdown";

    public NewYearCountdownTask() {
        super(FUNCTION_ID, "新年倒计时", true);
    }

    public static double getThawProgress() {
        return getThawProgress(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
    }

    static double getThawProgress(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        int lunarYear = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar().getYear();
        LocalDate start = LocalDate.parse(Lunar.fromYmd(lunarYear, 1, 1).getSolar().toYmd());
        LocalDate end = LocalDate.parse(Lunar.fromYmd(lunarYear + 1, 1, 1).getSolar().toYmd());
        long elapsedMinutes = ChronoUnit.MINUTES.between(start.atStartOfDay(), dateTime);
        long totalMinutes = ChronoUnit.MINUTES.between(start.atStartOfDay(), end.atStartOfDay());
        return elapsedMinutes * 100.0 / totalMinutes;
    }

    @Override
    public void run() {
        var i = PreImageGenerate.dump(OpenApi.get("bot.happy-new-year"), Map.of());

        if (i.isError()) {
            Alert.notify("新年倒计时图片生成失败，错误信息：" + i.errorMessage());
            return;
        }

        var md = TC.md(Markdown.img(i.url(), i.width(), i.height()) + "\n\n> 当前解冻进度：" + String.format("%.2f", getThawProgress()) + "%");

        PushTask.push(FUNCTION_ID, md.setKeyboard(getButtons(true)));
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**\uD83E\uDDE8 新年倒计时**\n\n每日0时推送新年倒计时图片，查看距离春节的剩余时间，您可以使用 `/newyear` 查看预期效果。\n\n"
                + getStatus(platform, platformIdentifyId));
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.daily).setTime(LocalTime.of(0, 1, 0));
    }

    public static Keyboard getButtons(final boolean isSubscribed) {
        return new Keyboard(List.of(
                List.of(
                        new Button("\uD83D\uDCDD 每日打卡", "/sign", ButtonStyle.BLUE),
                        new Button(isSubscribed ? "关闭推送" : "开启推送", "/tasks " + (isSubscribed ? "disable" : "enable") + " " + FUNCTION_ID, ButtonStyle.BLUE_WITH_BACKGROUND)
                )
        ));
    }
}
