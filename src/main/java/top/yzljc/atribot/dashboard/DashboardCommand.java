package top.yzljc.atribot.dashboard;

import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.QQMessageSendException;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.event.impl.ErrorCode;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.platform.qq.QQBot;

import java.util.List;

/**
 * Dashboard 账号绑定入口
 *
 * @Author YZ_Ljc_
 * @ClassName DashboardCommand
 * @Created_at 2026/10/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.dashboard
 */
public final class DashboardCommand implements CommandExecutor {
    private final DashboardSessions sessions;

    public DashboardCommand(DashboardSessions sessions) { this.sessions = sessions; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }

        if (qq.isBot()) return true;
//        if (qq.getPlatform() != Platform.OFFICIAL_C2C && qq.getPlatform() != Platform.OFFICIAL_GROUP) {
//            sender.sendMessage("请在机器人群聊或私聊中使用 /dashboard");
//            return true;
//        }
        var settings = DashboardSettings.current();
        if (!settings.available()) {
            sender.sendMessage("个人面板正在维护中，敬请期待！");
            return true;
        }
        var account = UnifiedAuthentication.findByQqUserOpenId(qq.getUserId());
        boolean bound = account != null && account.qqConnectOpenId() != null && !account.qqConnectOpenId().isBlank();
        String url = settings.entryUrl() + "?_wwv=9983&qq=1";
        String text = "登录个人面板";
        if (!bound) {

            String ticket = sessions.issue(qq.getUserId(), qq.getUsername(), settings);
            if (ticket == null) {
                sender.sendMessage("申请过于频繁，请稍后重试");
                return true;
            }
            url += "&userOpenId=" + QqConnectClient.encode(qq.getUserId()) + "&ticket=" + ticket;
            text = "请使用 QQ 登录并绑定个人面板\n\n绑定链接 5 分钟内有效，请勿转发。绑定后可直接访问个人面板登录。";

            if (qq.getPlatform() != Platform.OFFICIAL_C2C) {

                if (OfficialUsers.isFriend(qq.getUserId())) {
                    try {
                        text = text + "\n\n> " + Markdown.link(url, "点击此处或下方按钮打开个人面板");
                        C2CChat.sendMessage(qq.getUserId(), TC.md(text),
                                TC.keyboard(List.of(List.of(new Button("dashboard", "打开个人面板", url, true, ButtonStyle.BLUE, ButtonType.LINK)))));

                        qq.sendMessage(QQBot.BOT_NAME + "已通过私聊向你发送绑定信息，请注意查收，如未收到，请使用 /dash 指令获取。");
                        return true;
                    } catch (QQMessageSendException _) {
                    }
                }

                sender.sendMessage("首次绑定请私聊机器人发送 /dash，绑定后即可在群内获取个人面板入口");
                return true;
            }
        }

        text = text + "\n\n> " + Markdown.link(url, "点击此处或下方按钮打开个人面板");
        qq.sendMessage(TC.md(text),
                TC.keyboard(List.of(List.of(new Button("dashboard", "打开个人面板", url, true, ButtonStyle.BLUE, ButtonType.LINK)))));
        return true;
    }
}
