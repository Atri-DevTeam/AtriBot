package top.yzljc.atribot.function.official.pushtask;

import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;

/**
 * @Author YZ_Ljc_
 * @ClassName MemerAddWelcomeTask
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.pushtask
 */
public final class MemerAddWelcomeTask extends PushTask {
    public MemerAddWelcomeTask() {
        super("member_add_welcome", "新成员入群欢迎语");
    }

    @Override
    public Markdown getDescription(String groupOpenId) {
        return TC.md("**新成员入群欢迎语**\n\n当有新成员加入群聊时，发送一条欢迎消息~\n\n" + getStatus(groupOpenId) + "\n\n" + "不需要主动消息权限");
    }

    @Override
    public Markdown enable(String groupOpenId, String operatorOpenId) {
        OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), true, operatorOpenId);
        return TC.md("✅ 已启用**" + this.getDisplayName() + "**");
    }

    @Override
    public Markdown disable(String groupOpenId, String operatorOpenId) {
        OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), false, operatorOpenId);
        return TC.md("❌ 已关闭**" + this.getDisplayName() + "**");
    }
}