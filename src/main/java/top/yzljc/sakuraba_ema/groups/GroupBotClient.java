package top.yzljc.sakuraba_ema.groups;

import io.javalin.http.Context;
import lombok.Getter;
import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.platform.qq.TokenManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime state for one webhook-only, group-only QQ bot.
 *
 * <p>Every instance owns its token manager and chat service. No credential or
 * token cache is shared with the primary bot or another group bot instance.</p>
 */
@Getter
public final class GroupBotClient implements AutoCloseable {

    private final GroupBotConfig config;
    private final TokenManager tokenManager;
    private final ChatService chatService;
    private final GroupEventDispatcher eventDispatcher;
    private final GroupWebhookHandler webhookHandler;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public GroupBotClient(GroupBotConfig config) {
        this.config = config;
        config.validateEnabled();
        this.tokenManager = new TokenManager(
                config.appId(), config.clientSecret(), "groups:" + config.key());
        this.chatService = new ChatService(
                config.apiBaseUrl(), tokenManager, "group-bot:" + config.key());
        this.eventDispatcher = new GroupEventDispatcher(this);
        this.webhookHandler = new GroupWebhookHandler(this, eventDispatcher);
    }

    public String key() {
        return config.key();
    }

    public String apiBaseUrl() {
        return config.apiBaseUrl();
    }

    public String accessToken() {
        return tokenManager.getAccessToken();
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
            GroupBotRegistry.remove(this);
        }
    }
}
