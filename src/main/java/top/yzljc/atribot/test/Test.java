package top.yzljc.atribot.test;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.GroupChat;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.EmailMessageEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.ai.AiProvider;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName Test
 * @Created_at 2026/06/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
@Slf4j
public class Test implements CommandExecutor, Listener {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;
//        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
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

//        String test = "你好";
//        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
//            String result = Atri.getInstance().getAiService().ask(AiProvider.OTHER, test);
//            sender.sendMessage(result);
//        });
//        Markdown md = TC.md(
//                "## 打卡成功\n\n" +
//                        "> 你已累计打卡**" + 1 + "**次！\n" +
//                        "> 今天已有**" + 2 + "**人参与了打卡！\n" +
//                        "> " + Markdown.img(ResourcesProperties.GOLD_IMG, 16, 16) + "+ " + 3 + "金粒" + "\n\n" +
//                        "文本123zzzzzzzzz"
//        );
//        Object buttons = TC.keyboard(List.of(
//                List.of(new Button("c1", "我也要打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ));
//        sender.sendMessage(md, buttons);
//        var result = Atri.getInstance().getChatService().getUserInfo(sender.getUserId(), sender.getGroupId());
//        sender.sendMessage(result);
        Markdown md = TC.md("✨ **" + Config.getInstance().getOfficialUsername() + "帮助菜单**\n\n" +
                "> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n" +
                Markdown.img(ResourcesProperties.GRASS_BLOCK_IMG, 16, 16) + Markdown.enterCommand("/mc",  "MC功能 ") + " | " + Markdown.enterCommand("/打卡", "\uD83D\uDCCC每日打卡") + "\n\n" +
                Markdown.enterCommand("/games", "\uD83C\uDF40小游戏  ") + " | " + Markdown.enterCommand("/hitokoto", "\uD83D\uDCAB随机一言") + "\n\n" +
                Markdown.enterCommand("/mojang", "\uD83D\uDEE0\uFE0FMC状态") + " | " + Markdown.enterCommand("/hypstatus", "\uD83D\uDCA4Hyp状态") + "\n\n" +
                Markdown.enterCommand("/newyear", "⏳新年倒数") + " | " + Markdown.enterCommand("/today", "\uD83D\uDCC5今日日历") + "\n\n" +
                "> " + Markdown.enterCommand("/rsp", "✊一场酣畅淋漓的石头剪刀布") + "\n" +
                "> " + Markdown.img(ResourcesProperties.MINECRAFT_CAPE_EXAMPLE, 16, 16) + Markdown.enterCommand("/mc capes", "Minecraft披风实况")  + "\n" +
                "> " + Markdown.img(ResourcesProperties.HYPIXEL_HEADER_IMG, 16, 16) + Markdown.enterCommand("/bantracker", "Hypixel BanTracker") + "\n" +
                "> " + Markdown.enterCommand("/cl ", "\uD83C\uDF81领取Hypixel每日签到奖励"));

        List<List<Button>> buttons = List.of(
                List.of(
                        new Button("s1", "问题反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("s2", "详细帮助", "/help -m", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                ),
                List.of(
                        new Button("s3", "贡献名单", "/贡献名单", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("s4", "推送任务", "/推送任务", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                ),
                List.of(
                        new Button("s5", "场景信息", "/whoami", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("s6", "全量消息", "/全量消息", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                ),
                List.of(
                        new Button("l1", "社区交流", "https://qm.qq.com/q/UXrrpLsICG", true, ButtonStyle.BLUE, ButtonType.LINK),
                        new Button("l2", "邀我进群", "https://qun.qq.com/qunpro/robot/qunshare?robot_uin=3889798968&robot_appid=102808581&sceneData=Y2m6DyvYd2SX5MyQfppq4axrIRdiOobQ6HDJim4ofdHS4e7PXVyNveeS8neRVhk4WdeLSBVcwJqpoXQTamuFFFC", true, ButtonStyle.BLUE, ButtonType.LINK)
                )
        );
        Object keyboard = TC.keyboard(buttons);
        sender.sendMessage(md, keyboard);
        return true;
    }

//    @EventHandler
//    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
//        if (event.getMessage().getContent().trim().equals("/test")) {
//            GroupChat.refMessage(event.getGroupId(), event.getMessage().getRefIdx(), "你好");
//        }
//    }

    @EventHandler
    public void onEmailReceived(EmailMessageEvent event) {
        log.info("收到邮件");
        log.info("发件人: {}", event.getAuthors().toString());
        log.info("主题: {}", event.getSubject());
        log.info("内容: {}", event.getContentSummary());
    }
}