package top.yzljc.qqbot.official.function;

import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;
import top.yzljc.qqbot.service.userinfo.GetUserInfo;

/**
 * @Author YZ_Ljc_
 * @ClassName AccountInfo
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.impl
 */
public class AccountInfo implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (label) {
            case "0" -> {
                String info = "user_id: " + sender.userId() + "\n" +
                        "user_name: " + GetUserInfo.getUserName(sender.userId()) + "\n" +
                        "group_id: " + sender.groupId() + "\n" +
                        "group_name: " + GetGroupInfo.getGroupName(sender.groupId()) + "\n" +
                        "message_id: " + sender.messageId() + "\n" +
                        "is_admin: " + sender.isAdmin() + "\n" +
                        "is_debug: " + sender.isDebug() + "\n\n" +
                        "注意，本条指令专为官机测试使用，第三方机器人仅作占位，无实际意义";
                sender.reply(info);
            }
            case "2" -> {
                String markdownInfo = "```\n" +
                        "user_openId: " + sender.userOpenId() + "\n" +
                        "group_openId: " + sender.groupOpenId() + "\n" +
                        "message_openId: " + sender.messageOpenId() + "\n" +
                        "is_admin: " + sender.isAdmin() + "\n" +
                        "is_debug: " + sender.isDebug() + "\n" +
                        "```\n\n" +
                        "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";
                sender.officialGroupReplyMarkdown(markdownInfo);
            }
            case "1" -> {
                String markdownInfo = "```\n" +
                        "member_openId: " + sender.userOpenId() + "\n" +
                        "message_openId: " + sender.messageOpenId() + "\n" +
                        "is_admin: " + sender.isAdmin() + "\n" +
                        "is_debug: " + sender.isDebug() + "\n" +
                        "```\n\n" +
                        "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";
                sender.officialPrivateReplyMarkdown(markdownInfo);
            }
        }
        return true;
    }
}