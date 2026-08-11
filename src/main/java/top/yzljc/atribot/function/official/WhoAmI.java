package top.yzljc.atribot.function.official;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.chat.official.TC;

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
        if (sender instanceof QQCommandSender qq) {
            switch (qq.getPlatform()) {
                case OFFICIAL_GROUP -> {
                    ObjectNode json = JsonNodeFactory.instance.objectNode();
                    json.put("user_openId", qq.getUserId());
                    json.put("group_openId", qq.getGroupId());
                    json.put("message_openId", qq.getMessage().getMessageId());
                    json.put("user_bot_role", String.valueOf(OfficialUsers.getRole(qq.getUserId())));
                    json.put("user_group_role", String.valueOf(qq.getRole()));
                    json.put("is_group_whitelist", OfficialGroups.isWhitelist(qq.getGroupId()));

                    String mdText = "```json\n"
                            + json.toPrettyString()
                            + "\n```\n\n"
                            + "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";

                    qq.sendMessage(TC.md(mdText));
                    return true;
                }

                case OFFICIAL_C2C -> {
                    ObjectNode json = JsonNodeFactory.instance.objectNode();
                    json.put("union_openId", qq.getUserId());
                    json.put("message_id", qq.getMessage().getMessageId());
                    json.put("user_bot_role", String.valueOf(OfficialUsers.getRole(qq.getUserId())));

                    qq.sendMessage(
                            TC.md(
                                    "```json\n"
                                            + json.toPrettyString()
                                            + "\n```\n\n"
                                            + "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义"
                            )
                    );
                    return true;
                }

                default -> {
                    return true;
                }
            }
        } else if (sender instanceof NapcatCommandSender nc) {
            ObjectNode json = JsonNodeFactory.instance.objectNode();
            json.put("user_id", nc.getUserId());
            json.put("group_id", nc.getGroupId());
            json.put("message_id", nc.getMessage().getMessageId());
            json.put("user_group_role", String.valueOf(nc.getRole()));

            nc.sendMessage(
                    "```json\n"
                            + json.toPrettyString()
                            + "\n```\n\n"
                            + "注意，本条指令专为获取相关鉴权数据使用，无实际意义"
            );
            return true;
        }
        return true;
    }
}