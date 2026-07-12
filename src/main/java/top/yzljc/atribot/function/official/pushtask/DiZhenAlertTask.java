package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;

/**
 * @Author YZ_Ljc_
 * @ClassName DiZhenAlertTask
 * @Created_at 2026/07/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
@Deprecated(since = "不合规，不写了")
public class DiZhenAlertTask extends PushTask {
    public DiZhenAlertTask() {
        super("earthquake_alert", "地震预警通知", true);
    }

    @Override
    public Markdown getDescription(String groupOpenId) {
        return TC.md("依据地震台网数据信息，发布地震预警通知，更新频率为每15分钟一次\n\n" + getStatus(groupOpenId));
    }
}