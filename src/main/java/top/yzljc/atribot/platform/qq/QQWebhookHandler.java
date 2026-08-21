package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.repo.EventLogRepository;
import top.yzljc.atribot.service.runtime.ThreadManager;

import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName QQWebhookHandler
 * @Created_at 2026/08/20
 * @Project AtriBot
 * @Package top.yzljc.atribot.platform.qq
 */
@Slf4j
public final class QQWebhookHandler {

    private static final int ED25519_SEED_SIZE = 32;

    private final String appId;
    private final String clientSecret;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QQWebhookHandler(String appId, String clientSecret) {
        this.appId = appId == null ? "" : appId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
    }

    public void handle(Context ctx) {
        String callbackAppId = ctx.header("X-Bot-Appid");
        if (callbackAppId != null && !callbackAppId.isBlank() && !appId.equals(callbackAppId)) {
            ctx.status(403).result("forbidden");
            return;
        }

        try {
            JsonNode payload = ctx.bodyAsClass(JsonNode.class);
            int op = payload.path("op").asInt(-1);
            if (op == 13) {
                handleValidation(ctx, payload.path("d"));
                return;
            }
            if (op == 0) {
                handleEvent(payload);
            } else {
                log.debug("[!] 未被处理的官机Webhook操作码: {}", op);
            }
            writeEmptyResponse(ctx);
        } catch (Exception e) {
            log.error("[!] 处理官机Webhook回调失败", e);
            ctx.status(400).result("bad request");
        }
    }

    private void handleValidation(Context ctx, JsonNode validationData) throws Exception {
        String plainToken = validationData.path("plain_token").asText(null);
        String eventTs = validationData.path("event_ts").asText(null);
        if (plainToken == null || eventTs == null || clientSecret.isBlank()) {
            ctx.status(400).result("invalid validation payload");
            return;
        }

        byte[] seed = deriveSeed(clientSecret);
        EdECPrivateKeySpec privateKeySpec = new EdECPrivateKeySpec(
                NamedParameterSpec.ED25519,
                seed
        );
        PrivateKey privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(privateKeySpec);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update((eventTs + plainToken).getBytes(StandardCharsets.UTF_8));
        String signature = toHex(signer.sign());

        ctx.result(objectMapper.writeValueAsString(Map.of(
                        "plain_token", plainToken,
                        "signature", signature
                )))
                .contentType("application/json");
    }

    private void handleEvent(JsonNode payload) {
        String eventType = payload.path("t").asText(null);
        JsonNode eventData = payload.path("d");
        if (eventType == null || eventData.isMissingNode() || eventData.isNull()) {
            log.debug("QQ Webhook 事件缺少 t 或 d: {}", payload);
            return;
        }

        String eventId = payload.path("id").asText(null);
        String rawPayload = payload.toString();
        ThreadManager.execute(() -> {
            EventLogRepository.record(eventType, eventId, null, rawPayload);
            WebSocketClient.dispatchEvent(eventType, eventId, eventData);
        });
    }

    private static byte[] deriveSeed(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length == 0) {
            throw new IllegalArgumentException("QQ client secret cannot be empty");
        }
        byte[] seed = new byte[ED25519_SEED_SIZE];
        for (int i = 0; i < seed.length; i++) {
            seed[i] = secretBytes[i % secretBytes.length];
        }
        return seed;
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }

    private static void writeEmptyResponse(Context ctx) {
        ctx.result("{}").contentType("application/json");
    }
}
