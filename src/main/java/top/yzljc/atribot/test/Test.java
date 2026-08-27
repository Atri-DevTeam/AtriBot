package top.yzljc.atribot.test;

import lombok.extern.slf4j.Slf4j;

import top.yzljc.atribot.chat.official.Ark23;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonSize;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.media.HexColor;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;
import top.yzljc.sakuraba_ema.guild.ChannelPosts;
import top.yzljc.sakuraba_ema.guild.ChannelInformation;

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
        if (!sender.hasPermission()) {
            sender.sendMessage("你是谁？");
            return true;
        }
//        if (args.length > 0) {
//            switch (args[0].toLowerCase()) {
//                case "-l" -> {
//                    sendChannelQueryResult(sender, ChannelInformation.getJoinedGuilds());
//                    return true;
//                }
//                case "-i" -> {
//                    if (args.length < 2) {
//                        sender.sendMessage("用法: /test -i <guildId>");
//                        return true;
//                    }
//                    sendChannelQueryResult(sender, ChannelInformation.getGuildInfo(args[1]));
//                    return true;
//                }
//                case "-p" -> {
//                    if (args.length < 2) {
//                        sender.sendMessage("用法: /test -p <guildId>");
//                        return true;
//                    }
//                    sendChannelQueryResult(sender, ChannelInformation.getChannelList(args[1]));
//                    return true;
//                }
//                default -> {
//                    // Keep the existing /test behavior for other arguments.
//                }
//            }
//        }
//        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;
//        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
//        String url = ResourcesProperties.A_SILENT_MIRROR_MP3;
//        GroupChat.replyMessage(sender.getGroupId(), sender.getMessageId(), 3, url);
//        String url = ResourcesProperties.WELCOME_IMG;
//        Markdown md = TC.md(
//                "欢迎新人喵~\n\n" +
//                        Markdown.img(url, 1238 ,564) + "\n\n" + Markdown.link("https://hypixel.net/threads/add-an-achievement-or-something-for-clearing-the-cobwebs-in-the-haunted-biome.6129847", "查看原帖")
//        );
//        ChannelPosts.sendMessage("82565391648687862", "739210805", "Minecraft News!", md);
//        ChannelPosts.sendMessage("82565391648687862", "739210805", ImageComponent.imageOf("https://api.yzljc.top/v2/atrimeow/image-dump/d5411a16-bfdd-3e5d-93da-5fb43b923ef2"));
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
//            sender.sendMessage(ImageComponent.imageOf(t.imageUrl()));
//            System.out.println("已为用户 " + sender.getUserId() + " 生成免费抽奖卡片");
//            return true;
//        }
//
//        String url = LootService.renderOverviewCard(sender.getUserId());
//        System.out.println(url);
//        sender.sendMessage(ImageComponent.imageOf(url));
//        sender.sendMessage(ImageComponent.imageOf("https://thirdqq.qlogo.cn/g?b=oidb&k=9ibwZcgtYsVOkxNVvIbaeSg&kti=adPQXgwBHsE&s=0&t=1775489118"));
//        ImageSourceClient.migrateUnreviewedToDirs();

//        var user = (QQCommandSender) sender;
//        Markdown md = TC.md("1");
//        Object btn1 = TC.keyboard(List.of(
//                List.of(new Button("c1", "按钮1", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND)),
//                List.of(new Button("c2", "按钮2", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ), ButtonSize.SMALL);
//        Object btn2 = TC.keyboard(List.of(
//                List.of(new Button("c1", "按钮1", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND)),
//                List.of(new Button("c2", "按钮2", "/test -g", true, ButtonStyle.BLUE, ButtonType.COMMAND))
//        ));
//        user.sendMessage(md, btn1);
//        user.sendMessage(md, btn2);
        var art = new Ark23("标题", "内容", List.of(
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                Ark23.Item.text("描述"),
                new Ark23.Item("描述2", "https://qun.qq.com")
        ));

        var user = (QQCommandSender) sender;
        GroupChat.replyMessage(user.getGroupId(), user.getMessage().getMessageId(), art);
        return true;
    }

    private static void sendChannelQueryResult(CommandSender sender, ChannelCliResult result) {
        if (result.success()) {
            sender.sendMessage(result.getData().toPrettyString());
            return;
        }
        sender.sendMessage("查询失败: " + result.getError().toString());
    }

//    @EventHandler
//    public void onGroupAtMessageCreate(OfficialGroupAtMessageCreateEvent event) {
//        if (UsersListed.isUserRecorded(event.getUser().getUserId())) return;
//        event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), TC.md(Markdown.at(event.getUser().getUserId()) + "(仅供娱乐)\n\n" + Markdown.colored(new HexColor("#FF8FAB"), "七夕节哦，我喜欢你")), false);
//        UsersListed.recordUser(event.getUser().getUserId());
//    }
//
//    @EventHandler
//    public void onGroupMessageCreateButAt(OfficialGroupMessageCreateEvent event) {
//        if (event.isAtBot()) {
//            if (UsersListed.isUserRecorded(event.getUser().getUserId())) return;
//            event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), TC.md(Markdown.at(event.getUser().getUserId()) + "(仅供娱乐)\n\n" + Markdown.colored(new HexColor("#FF8FAB"), "七夕节哦，我喜欢你")), false);
//            UsersListed.recordUser(event.getUser().getUserId());
//        }
//    }
//
//    @EventHandler
//    public void onC2CMessageCreate(OfficialC2CMessageCreateEvent event) {
//        if (UsersListed.isUserRecorded(event.getUser().getUserId())) return;
//        event.getUser().sendMessage(event.getMessage().getMessageId(), TC.md("(仅供娱乐)\n\n" + Markdown.colored(new HexColor("#FF8FAB"), "七夕节哦，我喜欢你")));
//        UsersListed.recordUser(event.getUser().getUserId());
//    }
}
