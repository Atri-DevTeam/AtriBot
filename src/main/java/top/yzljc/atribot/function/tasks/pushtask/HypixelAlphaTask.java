package top.yzljc.atribot.function.tasks.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelAlphaTask
 * @Created_at 2026/07/27
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
public class HypixelAlphaTask extends PushTask {

    public HypixelAlphaTask() {
        super("hyp_alpha_news", "Hypixel Alpha公告推送", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**Hypixel Alpha公告推送**\n\nHypixel Alpha 子论坛公告内容，主要内容为测试服Skyblock相关的更新内容，由于订阅数据源问题，部分管理员的更新内容可能被遗漏\n\n" + getStatus(platform, platformIdentifyId));
    }
}