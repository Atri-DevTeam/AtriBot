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
        super("hyp_alpha_news", "Skyblock 更新动态", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**Hypixel Skyblock更新动态**\n\n主要包括以下内容：Hypixel主论坛Skyblock版块公告，Hypixel Alpha 子论坛公告内容（主要内容为测试服Skyblock相关的更新内容）和Skyblock材质包更新情况，检查周期为每小时一次\n\n" + getStatus(platform, platformIdentifyId));
    }
}