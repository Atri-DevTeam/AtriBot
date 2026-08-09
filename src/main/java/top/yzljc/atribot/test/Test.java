package top.yzljc.atribot.test;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;

import top.yzljc.atribot.chat.official.GuildChannelChat;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.function.official.imagesource.ImageSourceClient;
import top.yzljc.atribot.function.official.loot.LootService;

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
        if (!sender.hasPermission()) {
            sender.sendMessage("你是谁？");
            return true;
        }
//        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;
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
//        Markdown md = TC.md("✨ **" + Config.getInstance().getOfficialUsername() + "帮助菜单**\n\n" +
//                "> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n" +
//                Markdown.img(ResourcesProperties.GRASS_BLOCK_IMG, 16, 16) + Markdown.enterCommand("/mc",  "MC功能 ") + " | " + Markdown.enterCommand("/打卡", "\uD83D\uDCCC每日打卡") + "\n\n" +
//                Markdown.enterCommand("/games", "\uD83C\uDF40小游戏  ") + " | " + Markdown.enterCommand("/hitokoto", "\uD83D\uDCAB随机一言") + "\n\n" +
//                Markdown.enterCommand("/mojang", "\uD83D\uDEE0\uFE0FMC状态") + " | " + Markdown.enterCommand("/hypstatus", "\uD83D\uDCA4Hyp状态") + "\n\n" +
//                Markdown.enterCommand("/newyear", "⏳新年倒数") + " | " + Markdown.enterCommand("/today", "\uD83D\uDCC5今日日历") + "\n\n" +
//                "> " + Markdown.enterCommand("/rsp", "✊一场酣畅淋漓的石头剪刀布") + "\n" +
//                "> " + Markdown.img(ResourcesProperties.MINECRAFT_CAPE_EXAMPLE, 16, 16) + Markdown.enterCommand("/mc capes", "Minecraft披风实况")  + "\n" +
//                "> " + Markdown.img(ResourcesProperties.HYPIXEL_HEADER_IMG, 16, 16) + Markdown.enterCommand("/bantracker", "Hypixel BanTracker") + "\n" +
//                "> " + Markdown.enterCommand("/cl ", "\uD83C\uDF81领取Hypixel每日签到奖励"));
//
//        List<List<Button>> buttons = List.of(
//                List.of(
//                        new Button("s1", "问题反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND),
//                        new Button("s2", "详细帮助", "/help -m", false, ButtonStyle.BLUE, ButtonType.COMMAND)
//                ),
//                List.of(
//                        new Button("s3", "贡献名单", "/贡献名单", false, ButtonStyle.BLUE, ButtonType.COMMAND),
//                        new Button("s4", "推送任务", "/推送任务", false, ButtonStyle.BLUE, ButtonType.COMMAND)
//                ),
//                List.of(
//                        new Button("s5", "场景信息", "/whoami", false, ButtonStyle.BLUE, ButtonType.COMMAND),
//                        new Button("s6", "全量消息", "/全量消息", false, ButtonStyle.BLUE, ButtonType.COMMAND)
//                ),
//                List.of(
//                        new Button("l1", "社区交流", "https://qm.qq.com/q/UXrrpLsICG", true, ButtonStyle.BLUE, ButtonType.LINK),
//                        new Button("l2", "邀我进群", "https://qun.qq.com/qunpro/robot/qunshare?robot_uin=3889798968&robot_appid=102808581&sceneData=Y2m6DyvYd2SX5MyQfppq4axrIRdiOobQ6HDJim4ofdHS4e7PXVyNveeS8neRVhk4WdeLSBVcwJqpoXQTamuFFFC", true, ButtonStyle.BLUE, ButtonType.LINK)
//                )
//        );
//        Object keyboard = TC.keyboard(buttons);
//        sender.sendMessage(md, keyboard);
//        var k = List.of(List.of(
//                new Button("t1", "1", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t2", "2", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t3", "3", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t4", "4", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t5", "5", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t6", "6", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t7", "7", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t8", "8", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND)
//                new Button("t9", "9", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                new Button("t10", "10", "call_back_data", true, ButtonStyle.BLUE, ButtonType.COMMAND)
//        ));
//        var d = ImageSourceClient.getRandomImage();
//        var md = TC.md(
//                Markdown.at(sender) + " 打卡成功\n\n" +
//                        ((d == null || d.url() == null) ? "" : Markdown.img(d.url(), d.w(), d.h()) + "\n\n") +
//                        "> 收集自网络，可联系删除 " + Markdown.enterCommand("/投稿 ", "我要投稿") + "\n" +
//                        "> 你已累计打卡**" + 20 + "**次！\n" +
//                        "> 今天已有**" + 11 + "**人参与了打卡！\n" +
//                        "> " + Markdown.img(ResourcesProperties.GOLD_IMG, 16, 16) + "+ " + 100 + "金粒   " + Markdown.enterCommand("/golds", "查看总数") + "\n\n"
//        );
//
//        Object buttons = TC.keyboard(List.of(
//                List.of(new Button("c1", "我也要打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ));
//        String streamMessageId = C2CChat.replyStreamDeltas(sender.getUserId(), sender.getMessageId(), List.of(
//                TC.md("正在生成回答..."),
//                TC.md("\n已完成标题部分"),
//                TC.md("\n这是最终内容")
//        ));
//        sender.sendMessage("消息ID: " + sender.getMessageId() + " 场景: " + sender.getPlatform());
//        if (args.length > 0 && args[0].equals("-g")) {
//            var t = LootService.drawFree(sender.getUserId());
//            sender.sendMessage(t.imageUrl(), ImageType.URL);
//            System.out.println("已为用户 " + sender.getUserId() + " 生成免费抽奖卡片");
//            return true;
//        }
//
//        String url = LootService.renderOverviewCard(sender.getUserId());
//        System.out.println(url);
//        sender.sendMessage(url, ImageType.URL);
//        sender.sendMessage("https://thirdqq.qlogo.cn/g?b=oidb&k=9ibwZcgtYsVOkxNVvIbaeSg&kti=adPQXgwBHsE&s=0&t=1775489118", ImageType.URL);
//        ImageSourceClient.migrateUnreviewedToDirs();
        return true;
    }

//    @EventHandler
//    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
//        if (event.getMessage().getContent().trim().equals("/test")) {
//            GroupChat.refMessage(event.getGroupId(), event.getMessage().getRefIdx(), "你好");
//        }
//    }
//
//    @EventHandler
//    public void onEmailReceived(EmailMessageEvent event) {
//        log.info("收到邮件");
//        log.info("发件人: {}", event.getAuthors().toString());
//        log.info("主题: {}", event.getSubject());
//        log.info("内容: {}", event.getContentSummary());
//    }
//    @EventHandler
//    public void onGroupAtMessage(OfficialGroupAtMessageCreateEvent event) {
//        if (event.getUser().isBot()) return;
//        if (event.getMessage().getContent().trim().contains("/helps")) {
//            event.sendMessage("指令帮助:\n  /helps - 显示帮助\n  /test - 测试指令\n  /ping - 查看机器人运行状态");
//        }
//    }
//
//    @EventHandler
//    public void onMessageC2C(OfficialC2CMessageCreateEvent event) {
//        if (event.getUser().isBot()) return;
//        if (event.getMessage().getContent().trim().equals("/helps")) {
//            event.sendMessage("指令帮助:\n  /helps - 显示帮助\n  /test - 测试指令\n  /ping - 查看机器人运行状态");
//        }
//    }
    @EventHandler
    public void onBotSendFuckingLikeMessage(NapcatGroupMessageEvent event) {
        if (!event.getGroupId().equals("818804507")) return;
        if (!event.getUser().getUserId().equals("3993660791")) return;
        if (event.getMessage().getContent().contains("点") && event.getMessage().getContent().contains("赞")) {
            Atri.getInstance().getScheduler().runTaskLaterAsynchronously(event::recall, 10000L);
        }
    }
//
//    @EventHandler
//    public void onGuildMessage(OfficialGuildAtMessageCreateEvent event) {
//        GuildChannelChat.replyMessage(event.getChannelId(), event.getMessage().getMessageId(), event.getMessage().getContent());
//    }

    @EventHandler
    public void onGroupJoinRequest(OfficialGroupJoinRequestEvent event) {
        log.info("问题: {}", event.getVerifyQuestion());
        log.info("答案: {}", event.getVerifyAnswer());
    }
}
