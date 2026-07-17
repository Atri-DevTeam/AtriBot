package top.yzljc.atribot.function.official;

import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialActiveMessageFailEvent;
import top.yzljc.atribot.event.events.OfficialC2CPushFailEvent;
import top.yzljc.atribot.platform.Platform;

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
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP)  return true;

        if (args.length < 1) {
            Markdown md = TC.md(
                    "启用全量消息\n\n" +
                            "全量消息包括`主动消息`和`获取全部消息`，群主完成授权启用主动消息后，"
                            + Config.getInstance().getOfficialUsername() +
                            "可以通过" + Markdown.enterCommand("/推送任务", "主动推送") + "提供部分推送功能；" +
                            "启用获取全部消息后，无需@" + Config.getInstance().getOfficialUsername() + "即可处理指令\n\n" +
                            Markdown.link("https://docs.qq.com/doc/DUHJQVG9VVE5yQU1S", "查看启用教程")
            );
            sender.sendMessage(md);
            return true;
        }
//        String groupRealId = args[0];
//
//        try {
//            Long.parseLong(groupRealId);
//        } catch (NumberFormatException e) {
//            sender.sendMessage("请输入正确的群号");
//            return true;
//        }
//
//        Markdown md = TC.md(
//                "**全量消息权限申请**\n\n" +
//                        "> 请群主按下方操作开启权限，需要QQ版本为9.2.90及以上，IOS未知\n\n" +
//                        Markdown.img(ResourcesProperties.FULL_MESSAGE_GUIDE_IMG, 600, 552) + "\n\n" +
//                        "> 群主批准后，机器人可主动发送消息，指令不再需要@，并获取群内全部消息，以提供更加便捷的服务，授权完成后，请点击第二个按钮完成验证\n\n"
//        );
//        String url = "https://club.vip.qq.com/transfer?open_kuikly_info=%7B%22page_name%22%3A%20%22ai_group_service_agreement_pop_page%22%2C%22groupCode%22%3A" + groupRealId + "%2C%22botUin%22%3A3889798968%2C%22botUid%22%3A%22u_zm4xuLKgDNsyTJvJ4eIzRg%22%2C%22screen%22%3A1%7D";
//        Button linkButton = new Button("c1", "群主大大请点击这里授权", url, true, ButtonStyle.BLUE, ButtonType.LINK);
//        Button verifyButton = new Button("c2", "点我验证", "full_message_enable_verify", true, ButtonStyle.BLUE, ButtonType.CALLBACK);
//        linkButton.setPermissionType(PermissionType.ADMIN);
//        Object keyboard = TC.keyboard(
//                List.of(
//                        List.of(linkButton),
//                        List.of(verifyButton)
//                )
//        );
//        sender.sendMessage(md, keyboard);
//        OfficialGroups.setRealGroupId(sender.getGroupId(), Long.parseLong(groupRealId));
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
    public void onFullMessageFail(OfficialActiveMessageFailEvent event) {
        String groupOpenId = event.getGroupOpenId();
        if (OfficialGroups.isAllowedActiveMessages(groupOpenId) && event.getErrorMessage().equals("主动消息失败, 无权限") && groupOpenId != null) {
            OfficialGroups.setAllowedActiveMessage(groupOpenId, false);
            for (var task : PushTaskCommand.getTasks()) {
                if (task.isGroupEnabled(groupOpenId)) {
                    if (task.getFunctionId().equals("member_add_welcome")) continue;
                    OfficialGroups.setFunctionEnabled(groupOpenId, task.getFunctionId(), false, "system_active_message_fail");
                }
            }
        }
    }

    @EventHandler
    public void onC2CPushFail(OfficialC2CPushFailEvent event) {
        String userId = event.getUserId();
        if (OfficialUsers.isC2CPushEnabled(userId) && event.getErrorCode() == 40034105 && userId != null) {
            OfficialUsers.setC2CPush(userId, false);
        }
    }
}