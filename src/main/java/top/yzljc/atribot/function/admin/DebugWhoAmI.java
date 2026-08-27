package top.yzljc.atribot.function.admin;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.auth.UnifiedAccount;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.chat.official.TC;

/**
 * @Author YZ_Ljc_
 * @ClassName WhoAmI
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.atribot.official.function
 */
public class DebugWhoAmI implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQCommandSender qq) {
            String unifiedUuid = getUnifiedUuid(UnifiedAuthentication.findByQqUserOpenId(qq.getUserId()));
            switch (qq.getPlatform()) {
                case OFFICIAL_GROUP -> {
                    OfficialGroups.GroupData groupData = OfficialGroups.getData(qq.getGroupId());
                    ObjectNode json = JsonNodeFactory.instance.objectNode();
                    json.put("ua_uuid", unifiedUuid);
                    json.put("user_openId", qq.getUserId());
                    json.put("group_openId", qq.getGroupId());
                    json.put("message_openId", qq.getMessage().getMessageId());
                    json.put("user_bot_role", String.valueOf(OfficialUsers.getRole(qq.getUserId())));
                    json.put("user_group_role", String.valueOf(qq.getRole()));
                    json.put("is_group_whitelist", groupData.isWhitelist());
                    json.put("allow_proactive_msg", groupData.allowProactiveMsg());
                    json.put("recv_msg_setting", groupData.recvMsgSetting() == null ? null : groupData.recvMsgSetting().getJsonValue());
                    json.put("bot_member_openId", groupData.memberOpenid());
                    json.put("bot_group_role", groupData.memberRole() == null ? null : groupData.memberRole().name());

                    String mdText = "```json\n"
                            + json.toPrettyString()
                            + "\n```\n\n"
                            + "> 注意，本条指令专为获取相关鉴权数据使用，无实际意义";

                    qq.sendMessage(TC.md(mdText));
                    return true;
                }

                case OFFICIAL_C2C -> {
                    ObjectNode json = JsonNodeFactory.instance.objectNode();
                    json.put("ua_uuid", unifiedUuid);
                    json.put("user_openId", qq.getUserId());
                    json.put("message_id", qq.getMessage().getMessageId());
                    json.put("user_bot_role", String.valueOf(OfficialUsers.getRole(qq.getUserId())));
                    json.put("c2c_push", OfficialUsers.isC2CPushEnabled(qq.getUserId()));

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
        } else if (sender instanceof QQGuildCommandSender guildSender) {
            String userOpenId = guildSender.getUserOpenId();
            String unifiedUuid = getUnifiedUuid(UnifiedAuthentication.findByQqUserOpenId(userOpenId));
            guildSender.sendMessage(
                    "ua_uuid: " + unifiedUuid + "\n"
                            + "user_openid: " + userOpenId + "\n"
                            + "guild_id: " + guildSender.getGuildId() + "\n"
                            + "channel_id: " + guildSender.getChannelId() + "\n"
                            + "message_id: " + guildSender.getMessage().getMessageId() + "\n"
                            + "user_guild_role: " + guildSender.getRole() + "\n\n"
                            + "注意，本条指令专为获取相关鉴权数据使用，无实际意义"
            );
            return true;
        } else if (sender instanceof NapcatCommandSender nc) {
            String unifiedUuid = getUnifiedUuid(UnifiedAuthentication.findByQqUserUin(nc.getUserId()));
            nc.sendMessage(
                    "ua_uuid: " + unifiedUuid + "\n"
                            + "user_id: " + nc.getUserId() + "\n"
                            + "group_id: " + nc.getGroupId() + "\n"
                            + "message_id: " + nc.getMessage().getMessageId() + "\n"
                            + "user_group_role: " + nc.getRole() + "\n\n"
                            + "注意，本条指令专为获取相关鉴权数据使用，无实际意义"
            );
            return true;
        }
        return true;
    }

    private static String getUnifiedUuid(UnifiedAccount account) {
        return account == null ? "未绑定" : account.uuid().toString();
    }
}