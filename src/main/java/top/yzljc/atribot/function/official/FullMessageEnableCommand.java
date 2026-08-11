package top.yzljc.atribot.function.official;

import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupSendFailEvent;
import top.yzljc.atribot.event.events.OfficialC2CSendFailEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.qq.QQBot;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName FullMessageEnable
 * @Created_at 2026/06/13
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class FullMessageEnableCommand implements CommandExecutor, Listener {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;

        if (args.length < 1) {
            var pt = qq.getPlatform() == Platform.OFFICIAL_GROUP ? "群主" : "您";
            Markdown md = TC.md(
                    "启用全量消息\n\n" +
                            "全量消息包括`主动消息`和`获取全部消息`，" + pt + "完成授权启用主动消息后，"
                            + QQBot.BOT_NAME +
                            "可以通过" + Markdown.enterCommand("/推送任务", "主动推送") + "提供部分推送功能；" +
                            "启用获取全部消息后，无需@" + QQBot.BOT_NAME + "即可处理指令（仅群聊）\n\n" +
                            Markdown.link("https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S", "查看启用教程")
            );
            qq.sendMessage(md);
            return true;
        }
        String groupRealId = args[0];

        try {
            Long.parseLong(groupRealId);
        } catch (NumberFormatException e) {
            qq.sendMessage("请输入正确的群号");
            return true;
        }

        Markdown md = TC.md(
                "**编辑消息权限**\n\n" +
                        "> 请群主大大按下方操作开启权限，需要手机QQ版本为9.2.90及以上，IOS未知\n\n" +
                        Markdown.img(ResourcesProperties.FULL_MESSAGE_ENABLE_GUIDE, 540, 479)
        );
        String url = "https://club.vip.qq.com/transfer?open_kuikly_info=%7B%22page_name%22%3A%20%22ai_group_service_agreement_pop_page%22%2C%22groupCode%22%3A" + groupRealId + "%2C%22botUin%22%3A3889798968%2C%22botUid%22%3A%22u_zm4xuLKgDNsyTJvJ4eIzRg%22%2C%22screen%22%3A1%7D";
        Button linkButton = new Button("c1", "群主大大请点击这里授权", url, true, ButtonStyle.BLUE, ButtonType.LINK);
        linkButton.setPermissionType(PermissionType.ADMIN);
        Object keyboard = TC.keyboard(
                List.of(
                        List.of(linkButton)
                )
        );
        qq.sendMessage(md, keyboard);
        OfficialGroups.setRealGroupId(qq.getGroupId(), Long.parseLong(groupRealId));
        return true;
    }
//
//    @EventHandler
//    public void onCallback(OfficialInteractionEvent event) {
//        if (!"full_message_enable_verify".equals(event.getButtonValue())) return;
//        if (OfficialGroups.isAllowedFullMessages(event.getGroupOpenId())) {
//            event.answer(AnswerCode.REPEAT);
//            return;
//        }
//        String messageId = event.sendMessage(TC.md("> 全量消息已开放，若后续检测到主动推送失败将自动关闭"));
//        if (messageId != null) {
//            OfficialGroups.setAllowedFullMessage(event.getGroupOpenId(), true);
//            event.answer(AnswerCode.SUCCESS);
//        } else {
//            event.answer(AnswerCode.FAIL);
//        }
//    }

    @EventHandler
    public void onFullMessageFail(OfficialGroupSendFailEvent event) {
        String groupOpenId = event.getGroupOpenId();
        if (OfficialGroups.allowProactiveMsg(groupOpenId) && groupOpenId != null) {
            OfficialGroups.setAllowProactiveMsg(groupOpenId, false);
            for (var task : PushTaskCommand.getTasks()) {
                if (task.isGroupEnabled(groupOpenId)) {
                    if (!task.isNeedActiveMessage()) continue;
                    OfficialGroups.setFunctionEnabled(groupOpenId, task.getFunctionId(), false, "system_active_message_fail");
                }
            }
        }
    }

    @EventHandler
    public void onC2CPushFail(OfficialC2CSendFailEvent event) {
        String userId = event.getUserId();
        if (OfficialUsers.isC2CPushEnabled(userId) && event.getErrorCode() == 40034105 && userId != null) {
            OfficialUsers.setC2CPush(userId, false);
            for (var task : PushTaskCommand.getTasks()) {
                if (task.isUserEnabled(userId)) {
                    if (!task.isNeedActiveMessage()) continue;
                    OfficialUsers.setFunctionEnabled(userId, task.getFunctionId(), false, "system_c2c_push_fail");
                }
            }
        }
    }
}
