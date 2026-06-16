package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.auth.official.FullMessageAuth;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftVersionCheckTask
 * @Created_at 2026/06/14
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.pushtask
 */
public final class MinecraftNewsCheckTask extends PushTask {

    public MinecraftNewsCheckTask() {
        super("mc_news", "MC新闻与版本更新动态");
    }

    @Override
    public Markdown getDescription(String groupOpenId) {
        return TC.md(
                """
                        **Minecraft新闻动态**
                        
                        在启用此功能前，请先阅读以下使用条例：
                        
                        1. 本功能仅提供Minecraft相关的新闻动态，数据来源于官网，整点更新一次。
                        
                        2. 为了第一时间拿到内容，检索内容存在两个渠道，因此可能会出现内容相似的推送。
                        
                        3. 所有内容均为AI总结，如有不合适的内容，请使用/feedback向开发者反馈，我们会第一时间进行调整。
                        
                        4. 请确保你已打开机器人在本群的**主动消息推送**权限，否则将无法收到新闻动态推送。
                        
                        %s
                        
                        点击下方按钮开启本功能则表示您已阅读并知晓上述内容，如后续需要关闭可再次使用此指令关闭功能
                        """.formatted(getStatus(groupOpenId))
        );
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