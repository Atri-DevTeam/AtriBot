package top.yzljc.atribot.function.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.*;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.SignRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.function.official.pic.ImageSourceClient;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName CheckIn
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
@Slf4j
public class SignCommand implements CommandExecutor, Listener {

    private static final Pattern pattern = Pattern.compile("<@[A-F0-9]+> (打卡|签到)\\s*");
    private static boolean banned = false;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender) && !(sender instanceof QQGuildCommandSender)) return true;

        if (args.length > 0 && sender.hasPermission()) {
            if (args[0].equals("ban")) {
                banned = true;
                sender.sendMessage("已禁用打卡功能！");
                return true;
            } else if (args[0].equals("unban")) {
                banned = false;
                sender.sendMessage("已启用打卡功能！");
                return true;
            }
        }

        if (sender instanceof QQGuildCommandSender guildSender) {
            handleGuildCheckIn(guildSender, guildSender.getUserOpenId());
            return true;
        }

        QQCommandSender qq = (QQCommandSender) sender;

        int flag;
        if (qq.getPlatform() == Platform.OFFICIAL_C2C) {
            flag = 1;
        } else {
            flag = 2;
        }

        handleCheckIn(flag, qq.getUserId(), qq.getGroupId(), qq.getMessage().getMessageId());
        return true;
    }

    private static void handleGuildCheckIn(QQGuildCommandSender sender, String userOpenId) {
        ThreadManager.execute(() -> {
            if (userOpenId == null || userOpenId.isBlank()) {
                sender.sendMessage("无法获取频道用户标识，暂时无法打卡");
                return;
            }

            if (banned) {
                sender.sendMessage("由于内容调整，开发者暂时禁用了打卡！");
                return;
            }

            if (SignRepository.isInSettlementWindow()) {
                sender.sendMessage("打卡结算中，暂时无法打卡哦！");
                return;
            }

            if (SignRepository.hasCheckedInToday(userOpenId)) {
                sender.sendMessage("你今天已经打过卡了哦！");
                return;
            }

            SignRepository.CheckInResult result = SignRepository.checkIn(userOpenId);
            if (result == null) {
                sender.sendMessage("打卡失败，请稍后重试");
                return;
            }

            String text = "打卡成功\n" +
                    "你已累计打卡 " + result.totalCount() + " 次！\n" +
                    "今天已有 " + result.rank() + " 人参与了打卡！\n" +
                    "+ " + result.coins() + " 金粒";
            var image = ImageSourceClient.getRandomImage();
            if (image == null || image.url() == null || image.url().isBlank()) {
                sender.sendMessage(text);
                return;
            }
            sender.sendMessage(ImageComponent.imageOf(image.url()).setText(text));
        });
    }

    @EventHandler
    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        if (event.shouldIgnore()) return;
        if (isMatch(event.getMessage().getContent())) {
            handleCheckIn(2, event.getUser().getUserId(), event.getGroupId(), event.getMessage().getMessageId());
        }
    }

    @EventHandler
    public void onC2CChat(OfficialC2CMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        if (event.shouldIgnore()) return;
        if (isMatch(event.getMessage().getContent())) {
            handleCheckIn(1, event.getUser().getUserId(), null, event.getMessage().getMessageId());
        }
    }

    @EventHandler
    public void onGroupAt(OfficialGroupAtMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        if (event.shouldIgnore()) return;
        if (isMatch(event.getMessage().getContent())) {
            handleCheckIn(2, event.getUser().getUserId(), event.getGroupId(), event.getMessage().getMessageId());
        }
    }

    private static void handleCheckIn(int label, String unionOpenId, String groupOpenId, String messageOpenId) {
        ThreadManager.execute(() -> {

            if (banned) {
                if (label == 1) {
                    C2CChat.replyMessage(unionOpenId, messageOpenId, TC.md("> 由于内容调整，开发者暂时禁用了打卡！"));
                } else {
                    GroupChat.replyMessage(groupOpenId, unionOpenId, messageOpenId, TC.md("> 由于内容调整，开发者暂时禁用了打卡！"));
                }
                return;
            }

            if (SignRepository.isInSettlementWindow()) {
                if (label == 1) {
                    C2CChat.replyMessage(unionOpenId, messageOpenId, TC.md("打卡结算中，暂时无法打卡哦！"));
                } else {
                    GroupChat.replyMessage(groupOpenId, unionOpenId, messageOpenId, TC.md("打卡结算中，暂时无法打卡哦！"));
                }
                return;
            }

            if (SignRepository.hasCheckedInToday(unionOpenId)) {
                if (label == 1) {
                    C2CChat.replyMessage(unionOpenId, messageOpenId, TC.md("你今天已经打过卡了哦！"));
                } else {
                    GroupChat.replyMessage(groupOpenId, unionOpenId, messageOpenId, TC.md("你今天已经打过卡了哦！"));
                }
                return;
            }

            SignRepository.CheckInResult result = SignRepository.checkIn(unionOpenId);

            Markdown md = null;
            if (result != null) {
                var d = ImageSourceClient.getRandomImage();
                md = TC.md(
                        (label == 1 ? "" : (Markdown.at(unionOpenId) + " ")) + "打卡成功\n\n" +
                                ((d == null || d.url() == null) ? "" : Markdown.img(d.url(), d.w(), d.h()) + "\n\n") +
                                "> 收集自网络，可联系删除 " + Markdown.enterCommand("/投稿 ", "投稿图片") + "\n" +
                                "> 你已累计打卡**" + result.totalCount() + "**次！\n" +
                                "> 今天已有**" + result.rank() + "**人参与了打卡！\n" +
                                "> " + Markdown.img(ResourcesProperties.GOLD_IMG, 16, 16) + "+ " + result.coins() + "金粒   " + Markdown.enterCommand("/golds", "查看总数"));
            }

            Object buttons = TC.keyboard(List.of(
                    List.of(new Button("c1", "我也要打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                            new Button("c2", "抽MC物品", "/随机物品", true, ButtonStyle.BLUE, ButtonType.COMMAND)))
            );

            if (label == 1) {
                C2CChat.replyMessage(unionOpenId, messageOpenId, md, buttons);
            } else {
                GroupChat.replyMessage(groupOpenId, messageOpenId, md, buttons);
            }
        });
    }
//
//    public static String getGreetingByTime() {
//        int hour = LocalTime.now().getHour();
//        if (hour < 5) return "夜阑人静，星月交辉";
//        if (hour < 12) return "晨光熹微，万物初醒";
//        if (hour < 14) return "日正中天，光阴正好";
//        if (hour < 18) return "午后斜阳，岁月从容";
//        return "暮色苍茫，灯火可亲";
//    }

    public static boolean isMatch(String message) {
        if (message.trim().equals("打卡") || message.trim().equals("签到")) {
            return true;
        }
        for (var k : Config.getInstance().getKeywordsLikeUser()) {
            if (message.trim().equals(k)) {
                return true;
            }
        }
        return pattern.matcher(message).matches();
    }
}
