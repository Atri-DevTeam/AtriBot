package top.yzljc.atribot.platform.kook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

/**
 * @Author YZ_Ljc_
 * @ClassName KookWebhookDecoder
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.kook
 */
final class KookWebhookDecoder {
    static final int MAX_BODY_BYTES = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final byte[] verifyToken;
    private final byte[] encryptKey;

    KookWebhookDecoder(String verifyToken, String encryptKey) {
        if (verifyToken == null || verifyToken.isBlank()) {
            throw new IllegalArgumentException("KOOK Verify Token 不能为空");
        }
        this.verifyToken = verifyToken.getBytes(StandardCharsets.UTF_8);
        byte[] key = encryptKey == null ? new byte[0] : encryptKey.getBytes(StandardCharsets.UTF_8);
        if (key.length > 32) throw new IllegalArgumentException("KOOK Encrypt Key 不能超过 32 字节");
        this.encryptKey = key.length == 0 ? null : Arrays.copyOf(key, 32);
    }

    ObjectNode decode(byte[] body) throws IOException, GeneralSecurityException {
        if (body.length == 0 || body.length > MAX_BODY_BYTES) throw new IOException("Webhook 请求体长度无效");
        int start = 0;
        while (start < body.length && Character.isWhitespace(body[start])) start++;
        if (start >= body.length || body[start] != '{') {
            try (var input = new InflaterInputStream(new ByteArrayInputStream(body))) {
                body = input.readNBytes(MAX_BODY_BYTES + 1);
            }
            if (body.length > MAX_BODY_BYTES) throw new IOException("Webhook 解压后超过大小限制");
        }
        JsonNode envelope = JSON.readTree(body);
        if (encryptKey != null) {
            if (envelope == null || !envelope.path("encrypt").isTextual()) {
                throw new SecurityException("缺少加密事件");
            }
            envelope = JSON.readTree(decrypt(envelope.get("encrypt").asText()));
        } else if (envelope != null && envelope.has("encrypt")) {
            throw new SecurityException("未配置 Encrypt Key");
        }
        if (!(envelope instanceof ObjectNode payload) || !payload.path("s").isIntegralNumber()
                || !payload.path("s").canConvertToInt() || payload.path("s").intValue() != 0
                || !payload.path("d").isObject()) {
            throw new IOException("Webhook 事件格式无效");
        }
        ObjectNode data = (ObjectNode) payload.get("d");
        JsonNode suppliedToken = data.path("verify_token");
        if (!suppliedToken.isTextual() || !MessageDigest.isEqual(verifyToken,
                suppliedToken.asText().getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Verify Token 不匹配");
        }
        data.remove("verify_token");
        return payload;
    }

    private byte[] decrypt(String content) throws GeneralSecurityException {
        try {
            byte[] envelope = Base64.getDecoder().decode(content);
            if (envelope.length <= 16) throw new GeneralSecurityException("KOOK 密文长度无效");
            byte[] iv = Arrays.copyOfRange(envelope, 0, 16);
            byte[] ciphertext = Base64.getDecoder().decode(Arrays.copyOfRange(envelope, 16, envelope.length));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptKey, "AES"), new IvParameterSpec(iv));
            return cipher.doFinal(ciphertext);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("KOOK 密文格式无效");
        }
    }
}
