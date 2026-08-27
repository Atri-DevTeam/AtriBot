package top.yzljc.atribot.function.tasks.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftVersionCheckTask
 * @Created_at 2026/06/14
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.pushtask
 */
public final class MinecraftNewsCheckTask extends PushTask {

    public MinecraftNewsCheckTask() {
        super("mc_news", "MC新闻与版本更新动态", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md(
                """
                        **Minecraft新闻动态**
                        
                        在启用此功能前，请先阅读以下使用条例：
                        
                        1. 本功能仅提供Minecraft相关的新闻动态，数据来源于官网，整点更新一次。
                        
                        2. 为了第一时间拿到内容，检索内容存在两个渠道，因此可能会出现内容相似的推送。
                        
                        3. 内容总结如有不合适的地方，请使用/feedback向开发者反馈，我们会第一时间进行调整。
                        
                        4. 请确保你已打开机器人在本群的**主动消息推送**权限，否则将无法收到新闻动态推送。
                        
                        %s
                        
                        点击下方按钮开启本功能则表示您已阅读并知晓上述内容
                        """.formatted(getStatus(platform, platformIdentifyId))
        );
    }
}