package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.EdECPrivateKeySpec;
import java.security.spec.NamedParameterSpec;
import java.time.Duration;
import java.util.Map;

/**
 * QQ webhook transport for one group-only bot instance.
 */
@Slf4j
public final class GroupWebhookHandler {

    private static final int ED25519_SEED_SIZE = 32;

    private final GroupBotClient client;
    private final GroupEventDispatcher eventDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Cache<String, Boolean> receivedEventIds = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(20_000)
            .build();

    GroupWebhookHandler(GroupBotClient client, GroupEventDispatcher eventDispatcher) {
        this.client = client;
        this.eventDispatcher = eventDispatcher;
    }

    public void handle(Context context) {
        String callbackAppId = context.header("X-Bot-Appid");
        if (callbackAppId != null && !callbackAppId.isBlank()
                && !client.getConfig().appId().equals(callbackAppId)) {
            context.status(403).result("forbidden");
            return;
        }

        try {
            JsonNode payload = context.bodyAsClass(JsonNode.class);
            int op = payload.path("op").asInt(-1);
            if (op == 13) {
                handleValidation(context, payload.path("d"));
                return;
            }
            if (op == 0) {
                enqueueEvent(payload);
            } else {
                log.debug("实例 {} 忽略 QQ Webhook 操作码 {}", client.key(), op);
            }
            writeEmptyResponse(context);
        } catch (Exception e) {
            log.error("实例 {} 处理 QQ 群聊 Webhook 失败", client.key(), e);
            context.status(400).result("bad request");
        }
    }

    private void handleValidation(Context context, JsonNode validationData) throws Exception {
        String plainToken = validationData.path("plain_token").asText(null);
        String eventTs = validationData.path("event_ts").asText(null);
        String clientSecret = client.getConfig().clientSecret();
        if (plainToken == null || eventTs == null || clientSecret.isBlank()) {
            context.status(400).result("invalid validation payload");
            return;
        }

        byte[] seed = deriveSeed(clientSecret);
        EdECPrivateKeySpec privateKeySpec = new EdECPrivateKeySpec(NamedParameterSpec.ED25519, seed);
        PrivateKey privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(privateKeySpec);

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update((eventTs + plainToken).getBytes(StandardCharsets.UTF_8));

        context.result(objectMapper.writeValueAsString(Map.of(
                        "plain_token", plainToken,
                        "signature", toHex(signer.sign())
                )))
                .contentType("application/json");
    }

    private void enqueueEvent(JsonNode payload) {
        String eventType = payload.path("t").asText(null);
        JsonNode eventData = payload.path("d");
        if (!eventDispatcher.accepts(eventType, eventData)) {
            log.debug("实例 {} 忽略非群聊 Webhook 事件: {}", client.key(), eventType);
            return;
        }

        String eventId = payload.path("id").asText(null);
        if (eventId != null && !eventId.isBlank()
                && receivedEventIds.asMap().putIfAbsent(eventId, Boolean.TRUE) != null) {
            log.debug("实例 {} 忽略重复 Webhook 事件: {}", client.key(), eventId);
            return;
        }

        ThreadManager.execute(() -> {
            try {
                eventDispatcher.dispatch(eventType, eventId, eventData);
            } catch (Exception e) {
                log.error("实例 {} 派发 QQ 群聊事件 {} 失败", client.key(), eventType, e);
            }
        });
    }

    static byte[] deriveSeed(String secret) {
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

    private static void writeEmptyResponse(Context context) {
        context.result("{}").contentType("application/json");
    }
}
