package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author YZ_Ljc_
 * @ClassName QQConnectionLatency
 * @Created_at 2026/09/25
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.qq
 * @Description 开放平台消息链路延迟测试
 */
public final class QQConnectionLatency {

    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static final Cache<Key, Sample> PENDING = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public static synchronized boolean toggle() {
        boolean enabled = !ENABLED.get();
        ENABLED.set(enabled);
        PENDING.invalidateAll();
        return enabled;
    }

    public static boolean isEnabled() {
        return ENABLED.get();
    }

    public static void received(String eventType, JsonNode data, String transport, long receivedNanos) {
        if (!isEnabled()) return;
        String scene = sceneForEvent(eventType);
        if (scene == null || data == null) return;
        String messageId = data.path("id").asText(null);
        if (messageId == null || messageId.isBlank()) return;
        PENDING.asMap().putIfAbsent(new Key(scene, messageId), new Sample(transport, receivedNanos));
    }

    public static void dispatched(String eventType, JsonNode data) {
        if (!isEnabled()) return;
        String scene = sceneForEvent(eventType);
        if (scene == null || data == null) return;
        String messageId = data.path("id").asText(null);
        if (messageId == null || messageId.isBlank()) return;
        Sample sample = PENDING.getIfPresent(new Key(scene, messageId));
        if (sample != null && sample.dispatchedNanos == 0) {
            sample.dispatchedNanos = System.nanoTime();
        }
    }

    public static void commandStarted(String scene, String messageId) {
        if (!isEnabled()) return;
        if (messageId == null || messageId.isBlank()) return;
        Sample sample = PENDING.getIfPresent(new Key(scene, messageId));
        if (sample != null) sample.commandNanos = System.nanoTime();
    }

    public static Result sent(String scene, String messageId, long sendStartedNanos, long confirmedNanos) {
        if (!isEnabled()) return null;
        if (messageId == null || messageId.isBlank()) return null;
        Sample sample = PENDING.asMap().remove(new Key(scene, messageId));
        if (sample == null) return null;
        long dispatchedNanos = sample.dispatchedNanos == 0 ? sendStartedNanos : sample.dispatchedNanos;
        return new Result(scene, sample.transport,
                millis(dispatchedNanos - sample.receivedNanos),
                millis(sendStartedNanos - dispatchedNanos),
                millis(confirmedNanos - sendStartedNanos),
                millis(confirmedNanos - sample.receivedNanos),
                sample.commandNanos == 0 ? null : millis(confirmedNanos - sample.commandNanos));
    }

    private static String sceneForEvent(String eventType) {
        if (eventType == null) return null;
        return switch (eventType) {
            case "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE" -> "群聊";
            case "C2C_MESSAGE_CREATE" -> "单聊";
            default -> null;
        };
    }

    private static double millis(long nanos) {
        return Math.max(0, nanos) / (double) TimeUnit.MILLISECONDS.toNanos(1);
    }

    private record Key(String scene, String messageId) {
    }

    private static final class Sample {
        private final String transport;
        private final long receivedNanos;
        private volatile long dispatchedNanos;
        private volatile long commandNanos;

        private Sample(String transport, long receivedNanos) {
            this.transport = transport;
            this.receivedNanos = receivedNanos;
        }
    }

    public record Result(String scene, String transport, double receiveToDispatchMs,
                         double dispatchToSendMs, double qqApiMs, double totalMs, Double commandToConfirmMs) {
        public boolean isBoop() {
            return commandToConfirmMs != null;
        }

        public String describe() {
            return String.format(java.util.Locale.ROOT,
                    "官方 QQ %s（%s）本机接收→接口确认 %.1f ms；接收→分发 %.1f ms，分发→发送 %.1f ms，QQ 接口 %.1f ms%s",
                    scene, transport, totalMs, receiveToDispatchMs, dispatchToSendMs, qqApiMs,
                    commandToConfirmMs == null ? "" : String.format(java.util.Locale.ROOT,
                            "，命令→接口确认 %.1f ms", commandToConfirmMs));
        }
    }
}
