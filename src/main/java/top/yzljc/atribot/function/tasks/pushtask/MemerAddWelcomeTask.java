package top.yzljc.atribot.function.tasks.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName MemerAddWelcomeTask
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
public final class MemerAddWelcomeTask extends PushTask {
    public MemerAddWelcomeTask() {
        super("member_add_welcome", "新成员入群欢迎语", false, true, false);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**新成员入群欢迎语**\n\n当有新成员加入群聊时，发送一条欢迎消息~\n\n" + getStatus(platform, platformIdentifyId) + "\n\n" + "不需要主动消息权限");
    }
}