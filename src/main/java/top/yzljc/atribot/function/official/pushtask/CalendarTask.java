package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName CalendarTask
 * @Created_at 2026/06/14
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.pushtask
 */
public final class CalendarTask extends PushTask {

    public CalendarTask() {
        super("daily_calendar", "日历推送", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**📅 日历推送**\n\n" +
                "每日零点推送今日的公历和农历日期，以及距离下一个节气或假日的时长\n\n" +
                getStatus(platform, platformIdentifyId));
    }
}