package top.yzljc.atribot.auth;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;

import java.time.Clock;

/**
 * @Author YZ_Ljc_
 * @ClassName LoginCommand
 * @Created_at 2026/09/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public class LoginCommand implements CommandExecutor {
    private final LoginService service;
    private final AuthRateLimiter attempts = new AuthRateLimiter(Clock.systemUTC());

    public LoginCommand() { this(LoginService.getInstance()); }

    public LoginCommand(LoginService service) { this.service = service; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender)) return true;

        String source = sender.getClass().getName();
        if (!attempts.acquire(source + ":" + sender.getUserId(), 10)) {
            sender.sendMessage("[!] 尝试过于频繁，请稍后再试");
            return true;
        }
        if (args.length != 1 || !args[0].matches("[0-9]{6}")) {
            sender.sendMessage("[!] 用法：/login <六位验证码>");
            return true;
        }
        String permission = service.requiredPermission(args[0]);
        if (permission == null) {
            sender.sendMessage("[!] 验证码无效、已使用或已过期");
            return true;
        }
        if (!canApprove(sender, permission)) {
            sender.sendMessage("[!] 你无权限登陆");
            return true;
        }
        LoginService.Approval result = service.approve(args[0], new LoginService.LoginIdentity(
                sender.getUserId(), sender.getUsername(), source), permission);
        sender.sendMessage(switch (result) {
            case APPROVED -> "[!] 登录授权成功";
            case INVALID_CODE -> "[!] 验证码无效或已过期";
        });
        return true;
    }

    protected boolean canApprove(CommandSender sender) {
        return sender.hasPermission("api.login");
    }
    protected boolean canApprove(CommandSender sender, String permission) {
        return "api.login".equals(permission) ? canApprove(sender) : sender.hasPermission(permission);
    }
}
