package top.yzljc.atribot.platform.kook;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.KookButtonClickEvent;
import top.yzljc.atribot.event.events.KookChannelMessageCreateEvent;
import top.yzljc.atribot.event.events.KookDirectMessageCreateEvent;
import top.yzljc.atribot.event.events.KookSystemEvent;
import top.yzljc.atribot.command.impl.KookSenderImpl;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @Author YZ_Ljc_
 * @ClassName KookManager
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.kook
 */
@Slf4j
public final class KookManager implements AutoCloseable {
    @Getter private final KookApiClient api;
    @Getter private final KookWebhookHandler webhookHandler;
    @Getter private volatile JsonNode botInfo;
    private final Set<String> adminIds;
    private final Consumer<Event> eventConsumer;

    public KookManager(KookApiClient api, String verifyToken, String encryptKey, Set<String> adminIds) {
        this(api, verifyToken, encryptKey, adminIds, EventManager.getInstance()::callEvent);
    }

    KookManager(KookApiClient api, String verifyToken, String encryptKey, Set<String> adminIds,
                Consumer<Event> eventConsumer) {
        this.api = api;
        this.adminIds = Set.copyOf(adminIds);
        this.eventConsumer = eventConsumer;
        this.webhookHandler = new KookWebhookHandler(verifyToken, encryptKey,
                new KookEventQueue(256, ThreadManager::execute, this::dispatch));
    }

    public void start() {
        webhookHandler.start();
        ThreadManager.execute(() -> {
            botInfo = api.get("/user/me", Map.of());
            if (botInfo != null) {
                log.info("KOOK Bot 已加载: id={}, username={}", botInfo.path("id").asText(), botInfo.path("username").asText());
            } else {
                log.warn("KOOK Bot 信息获取失败，请检查 Bot Token 和 API 连接");
            }
        });
    }

    void dispatch(JsonNode payload) {
        JsonNode data = payload.path("d");
        JsonNode extra = data.path("extra");
        if (data.path("type").asInt() == 255) {
            String eventType = extra.path("type").asText();
            eventConsumer.accept("message_btn_click".equals(eventType)
                    ? new KookButtonClickEvent(data) : new KookSystemEvent(eventType, data));
            return;
        }
        Platform platform = switch (data.path("channel_type").asText()) {
            case "GROUP" -> Platform.KOOK_CHANNEL;
            case "PERSON" -> Platform.KOOK_DM;
            default -> null;
        };
        String authorId = data.path("author_id").asText();
        if (platform == null || authorId.isBlank() || data.path("target_id").asText().isBlank()) return;
        KookUser user = new KookUser(platform, authorId, extra.path("author"), api, adminIds);
        if (user.isBot() || botInfo != null && authorId.equals(botInfo.path("id").asText())) return;
        ArrayList<User> mentions = new ArrayList<>();
        JsonNode mentionPart = extra.path("kmarkdown").path("mention_part");
        if (mentionPart.isArray()) {
            for (JsonNode mention : mentionPart) {
                String id = mention.path("id").asText();
                if (!id.isBlank()) mentions.add(new KookUser(platform, id, mention, api, adminIds));
            }
        }
        KookMessage message = new KookMessage(platform, data, mentions, api);
        KookSenderImpl sender = new KookSenderImpl(user, message, api);
        eventConsumer.accept(platform == Platform.KOOK_CHANNEL
                ? new KookChannelMessageCreateEvent(user, message, sender)
                : new KookDirectMessageCreateEvent(user, message, sender));
    }

    @Override
    public void close() {
        webhookHandler.close();
    }
}

