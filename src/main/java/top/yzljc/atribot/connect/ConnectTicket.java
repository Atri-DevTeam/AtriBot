package top.yzljc.atribot.connect;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.function.Supplier;

/** Connect 服务凭证，使用配置密钥签名，有效期五分钟 */
public final class ConnectTicket {
    public static final long VALID_SECONDS = 300;
    private final Supplier<String> secret;
    private final Clock clock;

    public ConnectTicket(Supplier<String> secret) { this(secret, Clock.systemUTC()); }

    ConnectTicket(Supplier<String> secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    public record Issued(boolean enabled, String ticket, long expiresAt) {}

    public boolean enabled() { return validSecret(secret.get()); }

    /** 返回短期凭证及毫秒级过期时间，不返回配置密钥 */
    public Issued issue() {
        String key = secret.get();
        if (!validSecret(key)) return new Issued(false, null, 0);
        long issuedAt = clock.instant().getEpochSecond();
        String payload = "v1." + issuedAt;
        return new Issued(true, payload + "." + sign(key, payload), (issuedAt + VALID_SECONDS) * 1000);
    }

    /** 校验格式、签名和时间窗口，拒绝未来时间及过期凭证 */
    public boolean verify(String ticket) {
        String key = secret.get();
        if (!validSecret(key) || ticket == null || !ticket.matches("v1\\.[0-9]{1,12}\\.[A-Za-z0-9_-]{43}")) return false;
        String[] parts = ticket.split("\\.");
        long issuedAt = Long.parseLong(parts[1]);
        long now = clock.instant().getEpochSecond();
        if (issuedAt > now || now - issuedAt >= VALID_SECONDS) return false;
        return MessageDigest.isEqual(sign(key, parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean validSecret(String secret) {
        return secret != null && !secret.isBlank() && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(("connect\n" + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Connect 签名失败", e);
        }
    }
}
