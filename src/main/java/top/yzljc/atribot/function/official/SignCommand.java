package top.yzljc.atribot.function.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.*;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.SignRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.time.LocalTime;
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        int flag;
        if (sender.getPlatform() == Platform.OFFICIAL_C2C) {
            flag = 1;
        } else if (sender.getPlatform() == Platform.OFFICIAL_GROUP) {
            flag = 2;
        } else {
            return true;
        }

        handleCheckIn(flag, sender.getUserId(), sender.getGroupId(), sender.getMessageId());
        return true;
    }

    @EventHandler
    public void onGroupChat(OfficialGroupMessageCreateEvent event) {
        if (isMatch(event.getMessage().getContent())) {
            handleCheckIn(2, event.getUser().getUserId(), event.getGroupId(), event.getMessage().getMessageId());
        }
    }

    @EventHandler
    public void onC2CChat(OfficialC2CMessageCreateEvent event) {
        if (isMatch(event.getMessage().getContent())) {
            handleCheckIn(1, event.getUser().getUserId(), null, event.getMessage().getMessageId());
        }
    }

    @EventHandler
    public void onGroupAt(OfficialGroupAtMessageCreateEvent event) {
        if (isMatch(event.getMessage().getContent())) {
            handleCheckIn(2, event.getUser().getUserId(), event.getGroupId(), event.getMessage().getMessageId());
        }
    }

    private static void handleCheckIn(int label, String unionOpenId, String groupOpenId, String messageOpenId) {
        ThreadManager.execute(() -> {

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

            SignRepository.checkIn(unionOpenId);

            Markdown md = TC.md(
                    "## 打卡成功\n\n" +
                            "> 你已累计打卡**" + SignRepository.getTotalCount(unionOpenId) + "**次！\n" +
                            "> 今天已有**" + SignRepository.getTodayCount() + "**人参与了打卡！\n\n" +
                            getGreetingByTime()
            );

            Object buttons = TC.keyboard(List.of(
                    List.of(new Button("c1", "我也要打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND))
            ));

            if (label == 1) {
                C2CChat.replyMessage(unionOpenId, messageOpenId, md, buttons);
            } else {
                GroupChat.replyMessage(groupOpenId, unionOpenId, messageOpenId, md, buttons);
            }
        });
    }

    public static String getGreetingByTime() {
        int hour = LocalTime.now().getHour();
        if (hour < 5) return "夜阑人静，星月交辉";
        if (hour < 12) return "晨光熹微，万物初醒";
        if (hour < 14) return "日正中天，光阴正好";
        if (hour < 18) return "午后斜阳，岁月从容";
        return "暮色苍茫，灯火可亲";
    }

    private static boolean isMatch(String message) {
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
