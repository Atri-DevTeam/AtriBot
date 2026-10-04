package top.yzljc.atribot.platform.kook;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName KookWebhookHandler
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.kook
 */
@Slf4j
public final class KookWebhookHandler implements AutoCloseable {
    private final KookWebhookDecoder decoder;
    private final KookEventQueue queue;
    private volatile boolean ready;

    KookWebhookHandler(String verifyToken, String encryptKey, KookEventQueue queue) {
        this.decoder = new KookWebhookDecoder(verifyToken, encryptKey);
        this.queue = queue;
    }

    public void handle(Context ctx) {
        int bodyBytes = 0;
        try {
            byte[] body = ctx.req().getInputStream().readNBytes(KookWebhookDecoder.MAX_BODY_BYTES + 1);
            bodyBytes = body.length;
            if (body.length > KookWebhookDecoder.MAX_BODY_BYTES) {
                logRejected(ctx, 413, "请求体超过大小限制", bodyBytes);
                ctx.status(413).result("payload too large");
                return;
            }
            JsonNode payload = decoder.decode(body);
            JsonNode data = payload.path("d");
            if ("WEBHOOK_CHALLENGE".equals(data.path("channel_type").asText())) {
                if (!data.path("challenge").isTextual() || data.path("type").asInt() != 255) {
                    logRejected(ctx, 400, "Challenge 缺少有效的 challenge 或 type 字段", bodyBytes);
                    ctx.status(400).result("invalid challenge");
                    return;
                }
                ctx.json(Map.of("challenge", data.path("challenge").asText()));
                return;
            }
            if (!payload.path("sn").isIntegralNumber() || !data.path("msg_id").isTextual()
                    || data.path("msg_id").asText().isBlank()
                    || !data.path("type").isIntegralNumber() || !data.path("channel_type").isTextual()) {
                logRejected(ctx, 400, "事件缺少有效的 sn、msg_id、type 或 channel_type 字段", bodyBytes);
                ctx.status(400).result("invalid event");
                return;
            }
            if (!ready || !queue.offer(payload)) {
                logRejected(ctx, 503, ready ? "事件队列暂不可用" : "适配器尚未就绪", bodyBytes);
                ctx.header("Retry-After", "1").status(503).result("event queue unavailable");
                return;
            }
            ctx.contentType("application/json").result("{}");
        } catch (SecurityException e) {
            logRejected(ctx, 403, e.getMessage(), bodyBytes);
            ctx.status(403).result("forbidden");
        } catch (GeneralSecurityException e) {
            logRejected(ctx, 403, "消息解密失败，请检查 Encrypt Key 是否与 KOOK 后台一致", bodyBytes);
            ctx.status(403).result("forbidden");
        } catch (IOException | IllegalArgumentException e) {
            logRejected(ctx, 400, "请求体解析失败: " + e.getClass().getSimpleName(), bodyBytes);
            ctx.status(400).result("invalid payload");
        }
    }

    private static void logRejected(Context ctx, int status, String reason, int bodyBytes) {
        log.warn("KOOK Webhook 请求被拒绝: path={}, status={}, reason={}, bodyBytes={}",
                ctx.path(), status, reason, bodyBytes);
    }

    void start() {
        ready = true;
    }

    @Override
    public void close() {
        ready = false;
        queue.close();
    }
}
