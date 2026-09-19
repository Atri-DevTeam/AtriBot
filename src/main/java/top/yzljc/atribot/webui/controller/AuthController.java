package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.WebUIAuthRateLimiter;
import top.yzljc.atribot.webui.WebUISessionManager;

import java.io.IOException;

import static top.yzljc.atribot.webui.WebUiSupport.isBlank;

/** 认证会话 + 机器人基础配置 */
public class AuthController {
    private static final int MAX_LOGIN_BODY_BYTES = 4 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

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
        ctx.header("Cache-Control", "no-store");
        if (!allowRequest(ctx, WebUIAuthRateLimiter.checkChallenge(ctx.ip()))) return;
        if (isBlank(getConfiguredToken())) {
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            return;
        }

        String nonce = WebUISessionManager.createChallenge();
        if (nonce == null) {
            if (WebUISessionManager.isActive()) {
                rejectTooManyRequests(ctx, 60);
            } else {
                ctx.status(503).json(Result.fail(503, "WebUI 暂不可用，请稍后重试"));
            }
            return;
        }
        ctx.json(Result.success(new ChallengeDTO(
                nonce,
                WebUISessionManager.challengeExpiresAt().toString(),
                "HMAC-SHA256"
        )));
    }

    public static void login(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        if (!allowRequest(ctx, WebUIAuthRateLimiter.checkLogin(ctx.ip()))) return;
        String configuredToken = getConfiguredToken();
        if (isBlank(configuredToken)) {
            clearSessionCookies(ctx);
            ctx.status(401).json(Result.fail(401, "Official WebUI Token 未配置"));
            return;
        }

        LoginDTO dto = readLogin(ctx);
        if (dto == null) return;
        WebUISessionManager.LoginResult result = WebUISessionManager.login(dto.getNonce(), dto.getProof(), configuredToken);
        switch (result.status()) {
            case INVALID -> {
                clearSessionCookies(ctx);
                ctx.status(401).json(Result.fail(401, "未授权"));
            }
            case UNAVAILABLE -> ctx.status(503).json(Result.fail(503, "WebUI 暂不可用，请稍后重试"));
            case SUCCESS -> {
                setSessionCookie(ctx, result.sessionId());
                ctx.json(Result.success("ok"));
            }
        }
    }

    private static LoginDTO readLogin(Context ctx) {
        if (ctx.req().getContentLengthLong() > MAX_LOGIN_BODY_BYTES) {
            ctx.status(413).json(Result.fail(413, "登录请求体过大"));
            return null;
        }
        try {
            // 限定实际读取量，未声明 Content-Length 的请求同样受限。
            byte[] body = ctx.req().getInputStream().readNBytes(MAX_LOGIN_BODY_BYTES + 1);
            if (body.length > MAX_LOGIN_BODY_BYTES) {
                ctx.status(413).json(Result.fail(413, "登录请求体过大"));
                return null;
            }
            LoginDTO dto = JSON.readValue(body, LoginDTO.class);
            if (dto != null) return dto;
        } catch (IOException | IllegalArgumentException ignored) {
            // 读取或解析失败统一返回格式错误，不回显请求内容。
        }
        ctx.status(400).json(Result.fail(400, "登录请求格式无效"));
        return null;
    }

    private static boolean allowRequest(Context ctx, WebUIAuthRateLimiter.Decision decision) {
        if (decision.allowed()) return true;
        rejectTooManyRequests(ctx, decision.retryAfterSeconds());
        return false;
    }

    private static void rejectTooManyRequests(Context ctx, int retryAfterSeconds) {
        ctx.header("Cache-Control", "no-store");
        ctx.header("Retry-After", Integer.toString(retryAfterSeconds));
        ctx.status(429).json(Result.fail(429, "登录请求过于频繁，请稍后重试"));
    }

    public static void verifyToken(Context ctx) {
        ctx.header("Cache-Control", "no-store");
        ctx.json(Result.success("ok"));
    }

    public static void logout(Context ctx) {
        ctx.header("Cache-Control", "no-store");
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
