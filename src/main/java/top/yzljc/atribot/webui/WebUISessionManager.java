package top.yzljc.atribot.webui;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class WebUISessionManager {

    public static final String SESSION_COOKIE = "webui_session_yzljc_" + System.currentTimeMillis() + "_d";
    public static final int SESSION_TTL_SECONDS = 24 * 60 * 60;
    private static final long CHALLENGE_TTL_MILLIS = 2 * 60 * 1000L;
    private static final long CHALLENGE_TTL_NANOS = TimeUnit.MILLISECONDS.toNanos(CHALLENGE_TTL_MILLIS);
    private static final long SESSION_TTL_NANOS = TimeUnit.SECONDS.toNanos(SESSION_TTL_SECONDS);
    private static final int MAX_CHALLENGES = 1024;
    private static final int MAX_SESSIONS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Object AUTH_LOCK = new Object();
    private static final AtomicBoolean active = new AtomicBoolean(false);
    // 固定有效期与插入顺序一致，清理只需删除队首已过期记录。
    private static final LinkedHashMap<String, Long> challenges = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Long> sessions = new LinkedHashMap<>();

    public static void start() {
        synchronized (AUTH_LOCK) {
            active.set(false);
            clearAuthState();
            active.set(true);
        }
        log.info("WebUI 服务已开启");
    }

    public static void stop() {
        synchronized (AUTH_LOCK) {
            active.set(false);
            clearAuthState();
        }
        log.info("WebUI 服务已关闭，所有连接已断开");
    }

    public static boolean isActive() {
        return active.get();
    }

    public static String createChallenge() {
        synchronized (AUTH_LOCK) {
            if (!active.get()) return null;
            long now = System.nanoTime();
            cleanupExpiredChallenges(now);
            if (challenges.size() >= MAX_CHALLENGES) return null;
            String nonce = randomToken(32);
            challenges.put(nonce, now + CHALLENGE_TTL_NANOS);
            return nonce;
        }
    }

    /** 挑战消费与会话签发共用生命周期锁，关闭或重新开启后不能沿用旧认证结果。 */
    public static LoginResult login(String nonce, String proof, String token) {
        synchronized (AUTH_LOCK) {
            if (!active.get()) return new LoginResult(LoginStatus.UNAVAILABLE, null);
            if (nonce == null || nonce.length() != 43 || proof == null || proof.length() != 43 || isBlank(token)) {
                return new LoginResult(LoginStatus.INVALID, null);
            }
            long now = System.nanoTime();
            Long expiresAt = challenges.remove(nonce);
            if (expiresAt == null || now - expiresAt >= 0) {
                return new LoginResult(LoginStatus.INVALID, null);
            }
            String expected = hmacSha256Base64Url(token, nonce);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), proof.getBytes(StandardCharsets.UTF_8))) {
                return new LoginResult(LoginStatus.INVALID, null);
            }
            cleanupExpiredSessions(now);
            if (sessions.size() >= MAX_SESSIONS) return new LoginResult(LoginStatus.UNAVAILABLE, null);
            String sessionId = randomToken(32);
            sessions.put(sessionId, now + SESSION_TTL_NANOS);
            return new LoginResult(LoginStatus.SUCCESS, sessionId);
        }
    }

    public static boolean verifySession(String sessionId) {
        if (sessionId == null || sessionId.length() != 43) return false;
        synchronized (AUTH_LOCK) {
            if (!active.get()) return false;
            Long expiresAt = sessions.get(sessionId);
            if (expiresAt == null) return false;
            if (System.nanoTime() - expiresAt >= 0) {
                sessions.remove(sessionId);
                return false;
            }
            // 普通请求不续期，服务端与浏览器 Cookie 使用同一最长有效期
            return true;
        }
    }

    public static void removeSession(String sessionId) {
        if (isBlank(sessionId)) return;
        synchronized (AUTH_LOCK) {
            sessions.remove(sessionId);
        }
    }

    public static Instant challengeExpiresAt() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + CHALLENGE_TTL_MILLIS);
    }

    private static void clearAuthState() {
        challenges.clear();
        sessions.clear();
        // 只标记并唤醒旧连接，不在生命周期锁中执行网络写入
        SseBroadcaster.closeAll();
    }

    private static void cleanupExpiredChallenges(long now) {
        var iterator = challenges.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() < 0) break;
            iterator.remove();
        }
    }

    private static void cleanupExpiredSessions(long now) {
        var iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (now - entry.getValue() < 0) break;
            iterator.remove();
        }
    }

    private static String hmacSha256Base64Url(String token, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(nonce.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("WebUI HMAC 计算失败", e);
        }
    }

    private static String randomToken(int bytes) {
        byte[] raw = new byte[bytes];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum LoginStatus {
        SUCCESS, INVALID, UNAVAILABLE
    }

    public record LoginResult(LoginStatus status, String sessionId) {
    }
}
