package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.auth.official.FullMessageAuth;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelNewsTask
 * @Created_at 2026/06/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
public class HypixelNewsTask extends PushTask {
    public HypixelNewsTask() {
        super("hyp_news", "Hypixel公告推送");
    }

    @Override
    public Markdown getDescription(String groupOpenId) {
        return TC.md("Hypixel Announcements 论坛公告推送，每小时检查一次并完成公告推送");
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