package top.yzljc.atribot.functions.official.permission;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;

/**
 * @Author YZ_Ljc_
 * @ClassName VerifyFullMessageGroup
 * @Created_at 2026/06/13
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.permission
 */
public class VerifyFullMessageGroup {

    public static Markdown m() {
        return TC.md(
                "**需要全量消息权限**\n\n" +
                        "完成授权后无需@亚托莉喵即可处理指令，同时亚托莉喵可以通过主动推送提供更加便捷的功能\n\n" +
                        "格式：" + Markdown.enterCommand("/全量消息 ", "/全量消息 群号")
        );
    }
}