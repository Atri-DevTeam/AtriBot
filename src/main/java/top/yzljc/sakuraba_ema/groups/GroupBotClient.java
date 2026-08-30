package top.yzljc.sakuraba_ema.groups;

import io.javalin.http.Context;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime state for one webhook-only, group-only QQ bot.
 *
 * <p>Every instance owns its credentials, token cache, message sequence,
 * webhook handler and event listeners. It never routes through the primary
 * bot by group OpenID.</p>
 */
@Getter
@Slf4j
public final class GroupBotClient implements AutoCloseable {

    private final GroupBotConfig config;
    private final GroupBotTokenManager tokenManager;
    private final GroupBotChat chat;
    private final GroupEventDispatcher eventDispatcher;
    private final GroupWebhookHandler webhookHandler;
    @Getter(AccessLevel.NONE)
    private final CopyOnWriteArrayList<GroupBotEventListener> eventListeners = new CopyOnWriteArrayList<>();
    @Getter(AccessLevel.NONE)
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public GroupBotClient(GroupBotConfig config, String apiBaseUrl) {
        this.config = config;
        config.validateEnabled();
        this.tokenManager = new GroupBotTokenManager(config.key(), config.appId(), config.clientSecret());
        this.chat = new GroupBotChat(apiBaseUrl, tokenManager);
        this.eventDispatcher = new GroupEventDispatcher(this);
        this.webhookHandler = new GroupWebhookHandler(this, eventDispatcher);
    }

    public String key() {
        return config.key();
    }

    public void addEventListener(GroupBotEventListener listener) {
        eventListeners.addIfAbsent(Objects.requireNonNull(listener, "listener"));
    }

    public void removeEventListener(GroupBotEventListener listener) {
        eventListeners.remove(listener);
    }

    void publish(GroupBotMessageEvent event) {
        for (GroupBotEventListener listener : eventListeners) {
            try {
                listener.onGroupMessage(event);
            } catch (Exception e) {
                log.error("QQ 群聊 Bot 实例 {} 的消息监听器执行失败", key(), e);
            }
        }
    }

    void publish(GroupBotButtonInteractionEvent event) {
        for (GroupBotEventListener listener : eventListeners) {
            try {
                listener.onButtonInteraction(event);
            } catch (Exception e) {
                log.error("QQ 群聊 Bot 实例 {} 的按钮监听器执行失败", key(), e);
            }
        }
    }

    public void handleWebhook(Context context) {
        if (closed.get()) {
            context.status(503).result("group bot is shutting down");
            return;
        }
        webhookHandler.handle(context);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            eventListeners.clear();
        }
    }
}
