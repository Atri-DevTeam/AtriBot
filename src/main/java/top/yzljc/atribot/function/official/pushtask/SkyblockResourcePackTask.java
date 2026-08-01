package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName SkyblockResourcePackTask
 * @Created_at 2026/07/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
public class SkyblockResourcePackTask extends PushTask {
    public SkyblockResourcePackTask() {
        super("skyblock_resource_pack", "Skyblock 资源包版本更新", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**Skyblock资源包更新订阅**\n\n检查Hypixel Skyblock所用官方资源材质是否更新，在有新的变动时完成一次推送，检查周期为每小时一次\n\n" + getStatus(platform, platformIdentifyId));
    }
}