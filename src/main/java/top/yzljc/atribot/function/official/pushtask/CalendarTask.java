package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.auth.official.FullMessageAuth;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;

/**
 * @Author YZ_Ljc_
 * @ClassName CalendarTask
 * @Created_at 2026/06/14
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.pushtask
 */
public final class CalendarTask extends PushTask {

    public CalendarTask() {
        super("daily_calendar", "日历推送");
    }

    @Override
    public Markdown getDescription(String groupOpenId) {
        return TC.md("**📅 日历推送**\n\n" +
                "每日零点推送今日的公历和农历日期，以及距离下一个节气或假日的时长\n\n" +
                getStatus(groupOpenId));
    }

    @Override
    public Markdown enable(String groupOpenId, String operatorOpenId) {
        if (!OfficialGroups.isAllowedFullMessages(groupOpenId)) {
            return FullMessageAuth.n();
        }

        OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), true, operatorOpenId);
        return TC.md("✅ 已启用**" + this.getDisplayName() + "**");
    }

    @Override
    public Markdown disable(String groupOpenId, String operatorOpenId) {
        OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), false, operatorOpenId);
        return TC.md("❌ 已关闭**" + this.getDisplayName() + "**");
    }
}