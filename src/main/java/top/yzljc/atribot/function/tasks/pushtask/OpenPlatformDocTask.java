package top.yzljc.atribot.function.tasks.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName OpenPlatformDocTask
 * @Created_at 2026/09/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks.pushtask
 */
public class OpenPlatformDocTask extends PushTask {

    public OpenPlatformDocTask() {
        super("open_platform_doc_check", "开放平台文档更新订阅", true, true, false);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**开放平台机器人文档更新订阅**\n\n订阅最新的开发文档更新动态~\n\n" + getStatus(platform, platformIdentifyId));
    }
}