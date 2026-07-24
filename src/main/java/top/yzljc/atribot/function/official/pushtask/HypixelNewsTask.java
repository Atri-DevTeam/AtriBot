package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelNewsTask
 * @Created_at 2026/06/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
public final class HypixelNewsTask extends PushTask {
    public HypixelNewsTask() {
        super("hyp_news", "Hypixel公告推送", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**Hypixel公告推送**\n\nHypixel Announcements 论坛公告推送，每小时检查一次并完成公告推送\n\n" + getStatus(platform, platformIdentifyId));
    }
}