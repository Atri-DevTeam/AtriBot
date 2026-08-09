package top.yzljc.atribot.function.official;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * @ClassName WhoAmI
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.official.function
 */
public class WhoAmI implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (sender.getPlatform()) {
            case OFFICIAL_GROUP -> {
                boolean fullMessage = sender.getEventType() == EventType.OFFICIAL_GROUP_MESSAGE;

                ObjectNode json = JsonNodeFactory.instance.objectNode();
                json.put("user_openId", sender.getUserId());
                json.put("group_openId", sender.getGroupId());
                json.put("message_openId", sender.getMessageId());
                json.put("user_bot_role", String.valueOf(OfficialUsers.getRole(sender.getUserId())));
                json.put("user_group_role", String.valueOf(sender.getRole()));
                json.put("enable_full_message", fullMessage);
                json.put("enable_active_message", true);
                json.put("is_group_whitelist", OfficialGroups.isWhitelist(sender.getGroupId()));

                String mdText = "```json\n"
                        + json.toPrettyString()
                        + "\n```\n\n"
                        + "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";

                String messageId = GroupChat.sendMessage(sender.getGroupId(), TC.md(mdText));

                if (messageId == null) {
                    json.put("enable_active_message", false);

                    sender.sendMessage(
                            TC.md(
                                    "```json\n"
                                            + json.toPrettyString()
                                            + "\n```\n\n"
                                            + "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义"
                            )
                    );
                }

                return true;
            }

            case OFFICIAL_C2C -> {
                ObjectNode json = JsonNodeFactory.instance.objectNode();
                json.put("union_openId", sender.getUserId());
                json.put("message_id", sender.getMessageId());
                json.put("user_bot_role", String.valueOf(OfficialUsers.getRole(sender.getUserId())));

                Markdown md = TC.md(
                        "```json\n"
                                + json.toPrettyString()
                                + "\n```\n\n"
                                + "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义"
                );

                sender.sendMessage(md);
                return true;
            }

            case NAPCAT_GROUP -> {
                ObjectNode json = JsonNodeFactory.instance.objectNode();
                json.put("user_id", sender.getUserId());
                json.put("group_id", sender.getGroupId());
                json.put("message_id", sender.getMessageId());
                json.put("user_group_role", String.valueOf(sender.getRole()));

                sender.sendMessage(
                        "```json\n"
                                + json.toPrettyString()
                                + "\n```\n\n"
                                + "注意，本条指令专为获取相关鉴权数据使用，无实际意义"
                );

                return true;
            }

            case OFFICIAL_GUILD_CHANNEL -> {
                String text = "用户ID: " + sender.getUserId() + "\n"
                        + "频道ID: " + sender.getGroupId() + "\n"
                        + "消息ID: " + sender.getMessageId() + "\n"
                        + "注意，本条指令专为获取相关鉴权数据使用，无实际意义";
                sender.sendMessage(text);
                return true;
            }
        }

        return true;
    }
}