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