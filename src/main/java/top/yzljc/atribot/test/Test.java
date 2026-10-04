package top.yzljc.atribot.test;

import lombok.extern.slf4j.Slf4j;

import top.yzljc.atribot.chat.official.*;
import top.yzljc.atribot.chat.official.ark.Ark;
import top.yzljc.atribot.chat.official.ark.Ark23;
import top.yzljc.atribot.chat.official.button.*;
import top.yzljc.atribot.chat.official.card.Card;
import top.yzljc.atribot.chat.official.media.HexColor;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.function.impl.drawitem.LootService;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.util.List;
import java.util.Locale;

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
//        GroupChat.replyMessage(sender.getGroupId(), RT.message(sender.getMessageId()), 3, url);
//        String url = ResourcesProperties.WELCOME_IMG;
//        Markdown md = TC.md(
//                "欢迎新人喵~\n\n" +
//                        Markdown.img(url, 1238 ,564) + "\n\n" + Markdown.link("https://hypixel.net/threads/add-an-achievement-or-something-for-clearing-the-cobwebs-in-the-haunted-biome.6129847", "查看原帖")
//        );
//        ChannelPosts.sendMessage("82565391648687862", "739210805", "Minecraft News!", md);
//        ChannelPosts.sendMessage("82565391648687862", "739210805", ImageComponent.imageOf("https://api.yzljc.top/v2/atrimeow/image-dump/d5411a16-bfdd-3e5d-93da-5fb43b923ef2"));
//        Markdown md = TC.md("111");
//        Object buttons = TC.promptKeyboard(
//                List.of(
//                        List.of(new Button("c1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                                new Button("c2", "功能", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                                new Button("c3", "提建议", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
//                )
//        );
//        ((QQCommandSender)sender).sendMessage(md, buttons);
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
//        String streamMessageId = C2CChat.replyStreamDeltas(sender.getUserId(), RT.message(sender.getMessageId()), List.of(
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
        if (args.length > 0 && args[0].toLowerCase(Locale.ROOT).startsWith("ark")) {
            if (!(sender instanceof QQCommandSender qq)
                    || (qq.getPlatform() != Platform.OFFICIAL_GROUP && qq.getPlatform() != Platform.OFFICIAL_C2C)) {
                sender.sendMessage("Ark 测试仅支持 QQ 官方群聊和私聊");
                return true;
            }

            String imageUrl = "https://res.yzljc.top/images/birthday.jpeg";
            String jumpUrl = "https://www.yzljc.top";
            Ark ark = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "ark23" -> Ark.ark23("标题", "内容", List.of(
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
                        new Ark23.Item("描述2", jumpUrl)
                ));
                case "ark24" -> Ark.ark24("描述", "内容", "标题", "描述2", imageUrl, jumpUrl, "子标题");
                case "ark37" -> Ark.ark37("内容", "标题", "子标题", imageUrl, jumpUrl);
                default -> null;
            };
            if (ark == null) {
                sender.sendMessage("用法: /test <ark23|ark24|ark37>");
                return true;
            }

            // Ark 测试使用主动消息，不携带当前命令的消息 ID。
            if (qq.getPlatform() == Platform.OFFICIAL_GROUP) {
                GroupChat.sendMessage(qq.getGroupId(), ark);
            } else {
                C2CChat.sendMessage(qq.getUserId(), ark);
            }
            return true;
        }
        var u = (QQCommandSender) sender;
        var d = Card.tuWen("被动图文消息", "这是一条被动图文消息测试", "https://res.yzljc.top/images/birthday.jpeg", "https://q.qq.com");
        GroupChat.replyMessage(u.getGroupId(), RT.message(u.getMessage().getMessageId()), d);
        return true;
    }

    private static Keyboard getMiniGames() {
        return new Keyboard(
                List.of(
                        List.of(
                                new Button("bw", "起床", "/hyp gs bw", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("sw", "空岛", "/hyp gs sw", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("arc", "街机", "/hyp gs arc", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("pit", "决斗", "/hyp gs duel", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("skb", "Skyblock", "/hyp gs skb", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("mm", "密室杀手", "/hyp gs mm", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("bb", "建筑大师", "/hyp gs bb", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("wool", "羊毛游戏", "/hyp gs wool", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("tnt", "TNT游戏", "/hyp gs tnt", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("classic", "经典游戏", "/hyp gs classic", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("bsg", "饥饿游戏", "/hyp gs bsg", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("ptl", "实验大厅", "/hyp gs ptl", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("mw", "超级战墙", "/hyp gs mw", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("global", "全部小游戏", "/hyp gs", ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );
    }

    private static void sendChannelQueryResult(CommandSender sender, ChannelCliResult result) {
        if (result.success()) {
            sender.sendMessage(result.getData().toPrettyString());
            return;
        }
        sender.sendMessage("查询失败: " + result.getError().toString());
    }

    @EventHandler
    public void onGroupAtMessageCreate(OfficialGroupAtMessageCreateEvent event) {
        if (UsersListed.isUserRecorded(event.getUser().getUserId())) return;

        String useId = event.getUser().getUserId();
        String itemId = "1bd353b7-932a-400e-aa27-e7849dc6a5c7";

        var item = LootService.getCatalog(false).stream()
                .filter(it -> it.itemId().equals(itemId))
                        .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Item with ID " + itemId + " not found in catalog"));

        var record = LootRepository.appendLoot(useId, item.itemId(), item.displayName(), "2026中秋节活动", item.special());

        if (record == null) {
            event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), "出现未知错误，请联系开发者处理！");
            return;
        } else {
            event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), TC.md("中秋节快乐，获得物品 " + Markdown.colored(HexColor.GOLD, "「" + record.displayName() + "」")));
        }

//        event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), ImageComponent.imageOf("https://res.yzljc.top/images/birthday.jpeg").setText("今天是8月28日，是亚托莉的生日，邀请亚托莉喵到5个群，在潜水的时候就会遇到一个躺在机器里的仿生人，我试过了是假的，但是今天真的是亚托莉的生日，亚托莉生日快乐！"));
        UsersListed.recordUser(event.getUser().getUserId());
    }

    @EventHandler
    public void onGroupMessageCreateButAt(OfficialGroupMessageCreateEvent event) {
        if (event.isAtBot()) {
            if (UsersListed.isUserRecorded(event.getUser().getUserId())) return;

            String useId = event.getUser().getUserId();
            String itemId = "1bd353b7-932a-400e-aa27-e7849dc6a5c7";

            var item = LootService.getCatalog(false).stream()
                    .filter(it -> it.itemId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Item with ID " + itemId + " not found in catalog"));

            var record = LootRepository.appendLoot(useId, item.itemId(), item.displayName(), "2026中秋节活动", item.special());

            if (record == null) {
                event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), "出现未知错误，请联系开发者处理！");
                return;
            } else {
                event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), TC.md("中秋节快乐，获得物品 " + Markdown.colored(HexColor.GOLD, "「" + record.displayName() + "」")));
            }

//            event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), ImageComponent.imageOf("https://res.yzljc.top/images/birthday.jpeg").setText("今天是8月28日，是亚托莉的生日，邀请亚托莉喵到5个群，在潜水的时候就会遇到一个躺在机器里的仿生人，我试过了是假的，但是今天真的是亚托莉的生日，亚托莉生日快乐！"));
            UsersListed.recordUser(event.getUser().getUserId());
        }
    }

    @EventHandler
    public void onC2CMessageCreate(OfficialC2CMessageCreateEvent event) {
        if (UsersListed.isUserRecorded(event.getUser().getUserId())) return;

        String useId = event.getUser().getUserId();
        String itemId = "1bd353b7-932a-400e-aa27-e7849dc6a5c7";

        var item = LootService.getCatalog(false).stream()
                .filter(it -> it.itemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item with ID " + itemId + " not found in catalog"));

        var record = LootRepository.appendLoot(useId, item.itemId(), item.displayName(), "2026中秋节活动", item.special());

        if (record == null) {
            event.getUser().sendMessage(event.getMessage().getMessageId(), "出现未知错误，请联系开发者处理！");
            return;
        } else {
            event.getUser().sendMessage(event.getMessage().getMessageId(), TC.md("中秋节快乐，获得物品 " + Markdown.colored(HexColor.GOLD, "「" + record.displayName() + "」")));
        }

//        event.getUser().sendMessage(event.getMessage().getMessageId(), ImageComponent.imageOf("https://res.yzljc.top/images/birthday.jpeg").setText("今天是8月28日，是亚托莉的生日，邀请亚托莉喵到5个群，在潜水的时候就会遇到一个躺在机器里的仿生人，我试过了是假的，但是今天真的是亚托莉的生日，亚托莉生日快乐！"));
        UsersListed.recordUser(event.getUser().getUserId());
    }
}
