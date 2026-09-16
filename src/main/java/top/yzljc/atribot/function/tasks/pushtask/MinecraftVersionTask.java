package top.yzljc.atribot.function.tasks.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftVersionTask
 * @Created_at 2026/09/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks.pushtask
 */
public class MinecraftVersionTask extends PushTask {

    public MinecraftVersionTask() {
        super("mc_ver", "Minecraft 版本更新推送", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {

        return TC.md("**Minecraft版本更新推送**\n\n> 版本号: 26.2\n> 发布时间: 2026-06-16 19:37:53\n\n在MC发布新的正式版/预览版时，提供如上格式的版本更新推送提醒\n\n" + getStatus(platform, platformIdentifyId));
    }
}