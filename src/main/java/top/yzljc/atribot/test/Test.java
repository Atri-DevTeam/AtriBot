package top.yzljc.atribot.test;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.platform.Platform;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName Test
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
public class Test implements CommandExecutor, Listener {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;
//        String url = ResourcesProperties.A_SILENT_MIRROR_MP3;
//        GroupChat.replyMessage(sender.getGroupId(), sender.getMessageId(), 3, url);
        String url = ResourcesProperties.WELCOME_IMG;
        Markdown md = TC.md(
                "欢迎新人喵~\n\n" +
                        Markdown.img(url, 1238 ,564)
        );
        Object buttons = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "功能", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "提建议", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                )
        );
        sender.sendMessage(md, buttons);
        return true;
    }
}