package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.WebUISessionManager;

import static top.yzljc.atribot.webui.WebUiSupport.isBlank;

/** 认证会话 + 机器人基础配置 */
public class AuthController {

    public static void getConfig(Context ctx) {
        ctx.json(Result.success(new ConfigDTO(
                Config.getInstance().getQqAppId(),
                Config.getInstance().getOfficialOpenId(),
                QQBot.BOT_NAME,
                Config.getInstance().getQqApiBaseUrl(),
                Config.getInstance().getDebugGroupOpenId(),
                Config.getInstance().getSuperAdminId()
        )));
    }

    public static void createChallenge(Context ctx) {
        if (isBlank(getConfiguredToken())) {
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            return;
        }

        String nonce = WebUISessionManager.createChallenge();
        ctx.header("Cache-Control", "no-store");
        ctx.json(Result.success(new ChallengeDTO(
                nonce,
                WebUISessionManager.challengeExpiresAt().toString(),
                "HMAC-SHA256"
        )));
    }

    public static void login(Context ctx) {
        String configuredToken = getConfiguredToken();
        if (isBlank(configuredToken)) {
            clearSessionCookies(ctx);
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            return;
        }

        LoginDTO dto = ctx.bodyAsClass(LoginDTO.class);
        if (dto == null || !WebUISessionManager.verifyChallenge(dto.getNonce(), dto.getProof(), configuredToken)) {
            clearSessionCookies(ctx);
            ctx.status(401).json(Result.fail(401, "未授权"));
            return;
        }

        String sessionId = WebUISessionManager.createSession();
        setSessionCookie(ctx, sessionId);
        ctx.json(Result.success("ok"));
    }

    public static void verifyToken(Context ctx) {
        ctx.json(Result.success("ok"));
    }

    public static void logout(Context ctx) {
        String sessionId = ctx.cookie(WebUISessionManager.SESSION_COOKIE);
        WebUISessionManager.removeSession(sessionId);
        clearSessionCookies(ctx);
        ctx.json(Result.success("ok"));
    }

    private static String getConfiguredToken() {
        String token = Config.getInstance().getOfficialWebuiToken();
        if (token == null || token.isBlank() || "null".equalsIgnoreCase(token)) {
            return null;
        }
        return token;
    }

    private static void setSessionCookie(Context ctx, String sessionId) {
        clearLegacyTokenCookie(ctx);
        ctx.res().addHeader("Set-Cookie", WebUISessionManager.SESSION_COOKIE + "=" + sessionId
                + "; Path=/; Max-Age=" + WebUISessionManager.SESSION_TTL_SECONDS
                + "; HttpOnly; SameSite=Strict" + secureAttribute(ctx));
    }

    private static void clearSessionCookies(Context ctx) {
        ctx.res().addHeader("Set-Cookie", WebUISessionManager.SESSION_COOKIE
                + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict" + secureAttribute(ctx));
        clearLegacyTokenCookie(ctx);
    }

    private static void clearLegacyTokenCookie(Context ctx) {
        ctx.res().addHeader("Set-Cookie", "webui_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict" + secureAttribute(ctx));
    }

    /**
     * HTTPS 下追加 Secure，浏览器只会在 TLS 连接回传该 cookie，避免明文 HTTP 泄露会话。
     * 依赖 Jetty ForwardedRequestCustomizer 读取反代的 X-Forwarded-Proto；裸 HTTP 部署时不加，否则浏览器拒存 cookie 导致无法登录。
     */
    private static String secureAttribute(Context ctx) {
        return "https".equalsIgnoreCase(ctx.req().getScheme()) ? "; Secure" : "";
    }

    public record ChallengeDTO(String nonce, String expiresAt, String algorithm) {
    }

    @Data
    public static class LoginDTO {
        private String nonce;
        private String proof;
    }

    public record ConfigDTO(String appId, String botOpenId, String botName, String apiBaseUrl,
                            String debugGroupId, String superAdminId) {
    }
}
