package top.yzljc.atribot.function.official;

import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.EventType;

/**
 * @Author YZ_Ljc_
 * @ClassName AccountInfo
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.impl
 */
public class WhoAmI implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (sender.getPlatform()) {
            case OFFICIAL_GROUP -> {
                boolean fullMessage = sender.getEventType() == EventType.OFFICIAL_GROUP_MESSAGE;
                String mdText = "```json\n" +
                        "user_openId: " + sender.getUserId() + "\n" +
                        "group_openId: " + sender.getGroupId() + "\n" +
                        "message_openId: " + sender.getMessageId() + "\n" +
                        "user_bot_role: " + OfficialUsers.getRole(sender.getUserId()) + "\n" +
                        "user_group_role：" + sender.getRole() + "\n" +
                        "enable_full_message: " + fullMessage + "\n" +
                        "enable_active_message: " + "$msg" + "\n" +
                        "```\n\n" +
                        "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";

                String messageId = GroupChat.sendMessage(sender.getGroupId(), TC.md(mdText.replace("$msg", "true")));
                if (messageId == null) {
                    sender.sendMessage(TC.md(mdText.replace("$msg", "false")));
                }
                return true;
            }

            case OFFICIAL_C2C -> {
                Markdown md = TC.md("```json\n" +
                        "union_openId: " + sender.getUserId() + "\n" +
                        "message_id: " + sender.getMessageId() + "\n" +
                        "user_bot_role: " + OfficialUsers.getRole(sender.getUserId()) + "\n" +
                        "```\n\n" +
                        "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义");
                sender.sendMessage(md);
                return true;
            }

            case NAPCAT_GROUP -> {
                String text = "user_id: " + sender.getUserId() + "\n" +
                        "group_id: " + sender.getGroupId() + "\n" +
                        "message_id: " + sender.getMessageId() + "\n" +
                        "user_group_role：" + sender.getRole() + "\n" +
                        "注意，本条指令专为获取相关鉴权数据使用，无实际意义";
                sender.sendMessage(text);
                return true;
            }
        }
        return true;
    }
}