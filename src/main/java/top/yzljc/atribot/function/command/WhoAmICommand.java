package top.yzljc.atribot.function.command;

import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName WhoAmI
 * @Created_at 2026/08/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 * @Description 信息查询命令
 */
public class WhoAmICommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            String title = "**当前场景下用户信息**\n\n";
            String footer = "> 相关信息仅供问题排查和鉴权使用！";

            if (user.getPlatform().equals(Platform.OFFICIAL_GROUP)) {
                Markdown md = TC.md(title + "开放平台ID:\n\n" + "```text\n" + user.getUserId() + "\n```\n\n" + "群开放平台ID:\n\n" +
                        "```text\n" + user.getGroupId() + "\n```\n\n" + "用户群内身份: `" + user.getRole() + "`\n\n用户机器人身份: `" + OfficialUsers.getRole(user.getUserId()) + "`\n\n"
                        + footer);
                user.sendMessage(md);
            } else {
                Markdown md = TC.md(title + "开放平台ID:\n\n" + "```text\n" + user.getUserId() + "\n```\n\n" + "用户机器人身份: `" + OfficialUsers.getRole(user.getUserId()) + "`\n\n"
                        + footer);
                user.sendMessage(md);
            }
        }

        if (sender instanceof QQGuildCommandSender user) {
            String text = "当前场景下用户信息\n\n" +
                    "开放平台ID: " + user.getUserOpenId() + "\n" +
                    "用户频道ID: " + user.getUserId() + "\n" +
                    "$channel" +
                    "频道ID: " + user.getGuildId() + "\n" +
                    "用户机器人身份: " + OfficialUsers.getRole(user.getUserOpenId()) + "\n\n" +
                    "相关信息仅供问题排查和鉴权使用！";
            if (user.getPlatform().equals(Platform.OFFICIAL_GUILD_CHANNEL)) {
                user.sendMessage(text.replace("$channel", "文字子频道ID: " + user.getChannelId() + "\n"));
            } else {
                user.sendMessage(text.replace("$channel", ""));
            }
        }

        return true;
    }
}