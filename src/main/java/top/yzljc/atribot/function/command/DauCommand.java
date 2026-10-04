package top.yzljc.atribot.function.command;

import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.chat.official.card.Card;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.webui.repo.PublicOfficialQueryRepo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @Author YZ_Ljc_
 * @ClassName DauCommand
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class DauCommand implements CommandExecutor {
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    /** 北京时间今日向机器人发送过消息的去重用户数，合并群聊和私聊。 */
    public static long getDau() {
        LocalDateTime start = LocalDate.now(BEIJING_ZONE).atStartOfDay();
        return PublicOfficialQueryRepo.queryDau(start, start.plusDays(1), null, null)
                .totalReceiveUsers();
    }

    /** 历史平均 DAU，仅计入有活跃用户的日期；没有记录时返回 0。 */
    public static double getAverageDailyDau() {
        return PublicOfficialQueryRepo.queryAverageDailyDau();
    }

    /** 历史总发送消息数，合并机器人在群聊和私聊中发出的消息。 */
    public static long getTotalSentMessages() {
        return PublicOfficialQueryRepo.countGroupMessages(true, null, null, null)
                + PublicOfficialQueryRepo.countC2CMessages(true, null, null, null);
    }

    /** 历史总接收消息数，合并用户通过群聊和私聊发给机器人的消息。 */
    public static long getTotalReceivedMessages() {
        return PublicOfficialQueryRepo.countGroupMessages(false, null, null, null)
                + PublicOfficialQueryRepo.countC2CMessages(false, null, null, null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        String title = "日活: " + getDau() + " 平均日活: " + String.format("%.2f", getAverageDailyDau());
        String description = "总发送消息数：" + getTotalSentMessages() + "\n总接收消息数：" + getTotalReceivedMessages();
        String pic_url = QQBot.BOT_AVATAR_URL;
        if (sender instanceof QQCommandSender user) {
            if (user.getPlatform() == Platform.OFFICIAL_GROUP) {
                GroupChat.replyMessage(user.getGroupId(), RT.message(user.getMessage().getMessageId()), Card.tuWen(title, description, pic_url, "https://q.qq.com"));
            } else {
                C2CChat.replyMessage(user.getUserId(), RT.message(user.getMessage().getMessageId()), Card.tuWen(title, description, pic_url, "https://q.qq.com"));
            }
        }

        return true;
    }
}
