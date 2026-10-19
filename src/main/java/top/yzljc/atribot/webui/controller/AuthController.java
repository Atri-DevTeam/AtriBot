package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.dashboard.DashboardSessions;
import top.yzljc.atribot.dashboard.DashboardSettings;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.WebUISessionManager;
import top.yzljc.atricore.auth.AtriAccount;
import top.yzljc.atricore.core.CoreConfig;
import top.yzljc.atricore.platform.qq.QQBot;

/**
 * QQ 登录账号信息与机器人基础配置
 *
 * @Author YZ_Ljc_
 * @ClassName AuthController
 * @Created_at 2026/10/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
public final class AuthController {
    private AuthController() {
    }

    public static void getConfig(Context ctx) {
        ctx.json(Result.success(new ConfigDTO(
                CoreConfig.getInstance().getSettings().getQqAppId(),
                Config.getInstance().getOfficialOpenId(),
                QQBot.BOT_NAME,
                CoreConfig.getInstance().getSettings().getQqApiBaseUrl(),
                CoreConfig.getInstance().getSettings().getDebugGroupOpenId(),
                Config.getInstance().getSuperAdminId()
        )));
    }

    public static void verifyToken(Context ctx) {
        AtriAccount account = ctx.attribute("webui.account");
        if (!WebUISessionManager.isAdministrator(account)) {
            ctx.status(403).json(Result.fail(403, "需要 ADMIN 或 OWNER 身份"));
            return;
        }
        String name = account.username() == null || account.username().isBlank() ? "管理员" : account.username();
        DashboardSessions.Login login = ctx.attribute("webui.login");
        ctx.header("Cache-Control", "no-store").json(Result.success(new UserDTO(account.qqUserOpenId(), name,
                account.role().name(), login == null ? null : login.avatarUrl())));
    }

    public static void logout(Context ctx) {
        WebUISessionManager.logout(ctx.cookie(DashboardSessions.SESSION_COOKIE));
        ctx.res().addHeader("Set-Cookie", DashboardSessions.SESSION_COOKIE
                + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + (DashboardSettings.current().secure() ? "; Secure" : ""));
        ctx.header("Cache-Control", "no-store").json(Result.success("ok"));
    }

    public record UserDTO(String userId, String displayName, String role, String avatarUrl) {
    }

    public record ConfigDTO(String appId, String botOpenId, String botName, String apiBaseUrl,
                            String debugGroupId, String superAdminId) {
    }
}
