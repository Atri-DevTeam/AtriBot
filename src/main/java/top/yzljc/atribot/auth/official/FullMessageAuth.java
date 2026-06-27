package top.yzljc.atribot.auth.official;

import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.configuration.Config;

/**
 * @Author YZ_Ljc_
 * @ClassName VerifyFullMessageGroup
 * @Created_at 2026/06/13
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.permission
 */
public class FullMessageAuth {

    public static Markdown n() {
        return TC.md(
                "**需要全局消息权限**\n\n" +
                        "完成授权后无需@亚托莉喵即可处理指令，同时" + Config.getInstance().getOfficialUsername() + "可以自动提供一些更加便捷的功能\n\n" +
                        "使用 " + Markdown.enterCommand("/全量消息", "/全量消息") + " 查看详细"
        );
    }

    public static Markdown a() {
        return TC.md(
                "**需要主动消息权限**\n\n" +
                        "完成授权后" + Config.getInstance().getOfficialUsername() + "才能发送推送内容\n\n" +
                        "使用 " + Markdown.enterCommand("/全量消息", "/全量消息") + " 查看详细"
        );
    }
}