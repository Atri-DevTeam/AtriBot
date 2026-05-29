package top.yzljc.atribot.functions.official;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.functions.official.permission.PermissionGroup;

/**
 * @Author YZ_Ljc_
 * @ClassName AccountInfo
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.impl
 */
public class WhoAmI implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (label) {
            case "2" -> {
                String markdownInfo = "```json\n" +
                        "union_openId: " + sender.unionOpenId() + "\n" +
                        "group_openId: " + sender.groupOpenId() + "\n" +
                        "message_openId: " + sender.messageOpenId() + "\n" +
                        "is_admin: " + sender.isAdmin() + "\n" +
                        "is_debug: " + sender.isDebug() + "\n" +
                        "permission_group:" + PermissionGroup.getRole(sender.unionOpenId()) + "\n" +
                        "is_group_whitelisted: " + GroupList.isWhitelist(sender.groupOpenId()) + "\n" +
                        "```\n\n" +
                        "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";
                sender.officialGroupReplyMarkdown(markdownInfo);
            }
            case "1" -> {
                String markdownInfo = "```json\n" +
                        "union_openId: " + sender.unionOpenId() + "\n" +
                        "message_openId: " + sender.messageOpenId() + "\n" +
                        "is_admin: " + sender.isAdmin() + "\n" +
                        "is_debug: " + sender.isDebug() + "\n" +
                        "permission_group:" + PermissionGroup.getRole(sender.unionOpenId()) + "\n" +
                        "```\n\n" +
                        "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";
                sender.officialPrivateReplyMarkdown(markdownInfo);
            }
        }
        return true;
    }
}