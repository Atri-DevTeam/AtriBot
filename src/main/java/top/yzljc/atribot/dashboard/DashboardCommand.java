package top.yzljc.atribot.dashboard;

import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Platform;

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
        if (!(sender instanceof QQCommandSender qq) || qq.getPlatform() != Platform.OFFICIAL_C2C || qq.isBot()) {
            sender.sendMessage("请在机器人私聊中使用 /dashboard");
            return true;
        }
        var settings = DashboardSettings.current();
        if (!settings.available()) {
            sender.sendMessage("个人面板暂未开放");
            return true;
        }
        String ticket = sessions.issue(qq.getUserId(), qq.getUsername(), settings);
        if (ticket == null) {
            sender.sendMessage("申请过于频繁，请稍后重试");
            return true;
        }
        String url = settings.entryUrl() + "?_nav_alpha=0&userOpenId=" + QqConnectClient.encode(qq.getUserId()) + "&ticket=" + ticket;
        qq.sendMessage(TC.md("请使用 QQ 登录并绑定个人面板\n\n绑定链接 5 分钟内有效，请勿转发。绑定后可直接访问个人面板登录。"),
                TC.keyboard(List.of(List.of(new Button("dashboard", "打开个人面板", url, true, ButtonStyle.BLUE, ButtonType.LINK)))));
        return true;
    }
}
