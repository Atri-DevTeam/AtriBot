package top.yzljc.atribot.webui.official;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class WebUISessionManager {

    public static final String SESSION_COOKIE = "webui_session";
    public static final int SESSION_TTL_SECONDS = 24 * 60 * 60;
    private static final long CHALLENGE_TTL_MILLIS = 2 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final AtomicBoolean active = new AtomicBoolean(false);
    private static final ConcurrentHashMap<String, Long> challenges = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();

    public static void start() {
        active.set(true);
        clearAuthState();
        log.info("WebUI 服务已开启");
    }

    public static void stop() {
        active.set(false);
        clearAuthState();
        SseBroadcaster.closeAll();
        log.info("WebUI 服务已关闭，所有连接已断开");
    }

    public static boolean isActive() {
        return active.get();
    }

    public static String createChallenge() {
        cleanupExpired();
        String nonce = randomToken(32);
        challenges.put(nonce, System.currentTimeMillis() + CHALLENGE_TTL_MILLIS);
        return nonce;
    }

    public static boolean verifyChallenge(String nonce, String proof, String token) {
        if (!isActive() || isBlank(nonce) || isBlank(proof) || isBlank(token)) {
            return false;
        }

        Long expiresAt = challenges.remove(nonce);
        if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
            return false;
        }

        String expected = hmacSha256Base64Url(token, nonce);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                proof.getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String createSession() {
        cleanupExpired();
        String sessionId = randomToken(32);
        sessions.put(sessionId, System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000L);
        return sessionId;
    }

    public static boolean verifySession(String sessionId) {
        if (!isActive() || isBlank(sessionId)) {
            return false;
        }

        Long expiresAt = sessions.get(sessionId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            sessions.remove(sessionId);
            return false;
        }

        sessions.put(sessionId, System.currentTimeMillis() + SESSION_TTL_SECONDS * 1000L);
        return true;
    }

    public static void removeSession(String sessionId) {
        if (!isBlank(sessionId)) {
            sessions.remove(sessionId);
        }
    }

    public static Instant challengeExpiresAt() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + CHALLENGE_TTL_MILLIS);
    }

    private static void clearAuthState() {
        challenges.clear();
        sessions.clear();
    }

    private static void cleanupExpired() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(entry -> entry.getValue() < now);
        sessions.entrySet().removeIf(entry -> entry.getValue() < now);
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
}
