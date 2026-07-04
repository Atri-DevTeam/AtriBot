package top.yzljc.atribot.test;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.GroupChat;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.ai.AiProvider;

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
//        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
//        String url = ResourcesProperties.A_SILENT_MIRROR_MP3;
//        GroupChat.replyMessage(sender.getGroupId(), sender.getMessageId(), 3, url);
//        String url = ResourcesProperties.WELCOME_IMG;
//        Markdown md = TC.md(
//                "欢迎新人喵~\n\n" +
//                        Markdown.img(url, 1238 ,564)
//        );
//        Object buttons = TC.keyboard(
//                List.of(
//                        List.of(new Button("c1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                                new Button("c2", "功能", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                                new Button("c3", "提建议", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
//                )
//        );
//        sender.sendMessage(md, buttons);
//        for (int i = 0; i < 25; i++) {
//            GroupChat.sendMessage("38884BB0281B0641BBFCAE0BD12832CA", String.valueOf(i));
//        }
//        Markdown md = TC.md(Markdown.atAll());
//        sender.sendMessage(md);

        String test = "你好";
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
            String result = Atri.getInstance().getAiService().ask(AiProvider.OTHER, test);
            sender.sendMessage(result);
        });

        return true;
    }

    @EventHandler
    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
        if (event.getMessage().getContent().trim().equals("/test")) {
            GroupChat.refMessage(event.getGroupId(), event.getMessage().getRefIdx(), "你好");
        }
    }
}