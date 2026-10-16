package top.yzljc.atricore;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import top.yzljc.atricore.chat.official.ChatService;
import top.yzljc.atricore.command.CommandManager;
import top.yzljc.atricore.event.EventManager;
import top.yzljc.atricore.platform.qq.OfficialManager;
import top.yzljc.atricore.platform.qq.QQBot;
import top.yzljc.atricore.platform.qq.TokenManager;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.core.AccessService;
import top.yzljc.atricore.core.BotCommand;
import top.yzljc.atricore.core.LogRecorder;
import top.yzljc.atricore.core.AtriSettings;
import top.yzljc.atricore.core.CoreConfig;
import top.yzljc.atricore.platform.napcat.NapcatEventQueue;
import top.yzljc.atricore.platform.qq.QQWebhookHandler;
import top.yzljc.atricore.service.Scheduler;
import top.yzljc.atricore.service.taskscheduler.TaskScheduler;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 框架服务入口，负责基础服务的生命周期并提供业务扩展点
 * <p>运行服务继承此类，按需覆盖权限、记录和指令策略
 *
 * @Author YZ_Ljc_
 * @ClassName AtriProvider
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore
 */
public abstract class AtriProvider implements AutoCloseable {
    private static volatile AtriProvider instance;
    private final Scheduler scheduler = new Scheduler();
    private final TaskScheduler taskScheduler = new TaskScheduler();
    private final AccessService accessService = new AccessService();
    private final LogRecorder logRecorder = new LogRecorder();
    private final BotCommand botCommand = new BotCommand();
    private volatile ChatService chatService;
    private OfficialManager officialManager;
    private CommandManager commandListener;
    private boolean initialized;
    private boolean started;
    private volatile boolean closed;
    private Javalin server;
    private NapcatEventQueue napcatEventQueue;
    private QQWebhookHandler qqWebhookHandler;
    private int listenPort;
    private boolean serverClosed;

    /**
     * 获取当前运行服务提供的服务
     *
     * @return 已注册的 Provider
     * @throws IllegalStateException 运行服务尚未注册 Provider
     */
    public static AtriProvider getInstance() {
        AtriProvider current = instance;
        if (current == null) throw new IllegalStateException("AtriProvider 尚未初始化，请先调用 initialize");
        return current;
    }

    /**
     * 注册运行服务实现；应在初始化依赖框架的业务、注册指令和接收事件之前调用
     *
     * @param provider 运行服务实现，不得为 null
     * @throws NullPointerException Provider 或其配置、权限、记录器、指令策略为 null
     */
    public static void setInstance(AtriProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(provider.getSettings(), "settings");
        Objects.requireNonNull(provider.getAccessService(), "accessService");
        Objects.requireNonNull(provider.getLogRecorder(), "logRecorder");
        Objects.requireNonNull(provider.getBotCommand(), "botCommand");
        instance = provider;
    }

    /**
     * 使用默认 HTTP 配置初始化框架，暂不接收平台事件
     *
     * @throws IllegalStateException 当前实例已初始化、已关闭或已有其他 Provider
     */
    public final void initialize() {
        initialize(config -> {
        });
    }

    /**
     * 注册当前实例并准备聊天、HTTP 和指令服务，初始化期间不请求平台接口
     *
     * @param configure 静态资源、CORS 和访问日志等 HTTP 配置
     * @throws IllegalStateException 当前实例已初始化、已关闭或已有其他 Provider
     */
    public final synchronized void initialize(Consumer<JavalinConfig> configure) {
        if (initialized || closed || server != null) throw new IllegalStateException("Provider 已初始化或已关闭");
        Objects.requireNonNull(configure, "configure");
        synchronized (AtriProvider.class) {
            if (instance != null && instance != this && !instance.closed) {
                throw new IllegalStateException("已有其他 Provider，请勿重复初始化");
            }
            setInstance(this);
        }
        try {
            AtriSettings settings = getSettings();
            if (settings.isOfficialBotEnabled()) {
                TokenManager tokens = new TokenManager(settings.getQqAppId(), settings.getQqClientSecret());
                chatService = new ChatService(settings.getQqApiBaseUrl(), tokens);
                officialManager = new OfficialManager(settings.getQqApiBaseUrl(), tokens, settings.getQqConnectionMode());
            }
            initializeServer(configure);
            CommandManager.reload();
            commandListener = new CommandManager();
            EventManager.getInstance().registerEvents(commandListener);
            initialized = true;
        } catch (RuntimeException | Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    /**
     * 启动共享 HTTP 服务和已启用的平台连接，重复调用不重复启动
     * 未初始化时使用默认配置执行 initialize
     *
     * @throws IllegalStateException Provider 已关闭或平台启动失败
     */
    public final synchronized void start() {
        if (closed) throw new IllegalStateException("Provider 已关闭");
        if (started) return;
        if (!initialized) initialize();
        try {
            if (officialManager != null) QQBot.fetchBotInfo();
            server.start(listenPort);
            if (officialManager != null) officialManager.start();
            started = true;
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            closeAfterFailure(failure);
            throw new IllegalStateException("框架启动失败", failure);
        } catch (Error failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * @return 平台连接及框架配置，不得为 null
     */
    public AtriSettings getSettings() {
        return CoreConfig.getInstance().getSettings();
    }

    private void initializeServer(Consumer<JavalinConfig> configure) {
        AtriSettings settings = getSettings();
        listenPort = settings.getListenPort();
        boolean webhookEnabled = settings.isOfficialBotEnabled() && "webhook".equals(settings.getQqConnectionMode());
        server = Javalin.create(config -> {
            if (webhookEnabled) QQWebhookHandler.configureResponseCompletion(config);
            config.jetty.modifyHttpConfiguration(http -> http.addCustomizer(new ForwardedRequestCustomizer()));
            config.http.maxRequestSize = 10_000_000;
            configure.accept(config);
        });
        napcatEventQueue = new NapcatEventQueue();
        server.post("/", ctx -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            if (body == null || !body.isObject()) {
                ctx.status(400).json(Map.of("status", 432, "message", "请求体格式无效。"));
            } else if (napcatEventQueue.offer(body, ctx.bodyAsBytes().length)) {
                ctx.result("{\"status\":\"ok\"}").contentType("application/json");
            } else {
                ctx.header("Retry-After", "1").status(503)
                        .json(Map.of("status", 432, "message", "事件处理队列繁忙，请稍后重试。"));
            }
        });
        if (webhookEnabled) {
            qqWebhookHandler = new QQWebhookHandler(settings.getQqAppId(), settings.getQqClientSecret());
            server.post(settings.getQqWebhookPath(), qqWebhookHandler::handle);
        }
    }

    /**
     * 获取共享 HTTP 服务，运行服务通过该实例挂载业务路由
     *
     * @return 已初始化的 Javalin 实例
     * @throws IllegalStateException 服务尚未初始化或已关闭
     */
    public final synchronized Javalin getServer() {
        if (server == null || serverClosed) throw new IllegalStateException("HTTP 服务尚未初始化或已关闭");
        return server;
    }

    private void stopServer() {
        if (serverClosed) return;
        serverClosed = true;
        try {
            if (server != null) server.stop();
        } finally {
            try {
                if (qqWebhookHandler != null) qqWebhookHandler.close();
            } finally {
                if (napcatEventQueue != null) napcatEventQueue.close();
            }
        }
    }

    /**
     * 获取当前 Provider 管理的即时、延迟与固定间隔调度器
     *
     * @return 共享调度器，由 Provider 统一关闭
     * @throws IllegalStateException Provider 未注册或已关闭
     */
    public final synchronized Scheduler getScheduler() {
        if (instance != this || closed) throw new IllegalStateException("Provider 未注册或已关闭");
        return scheduler;
    }

    /**
     * 获取当前 Provider 管理的计划调度器
     *
     * @return 用于注册业务计划任务的共享调度器
     * @throws IllegalStateException Provider 未注册或已关闭
     */
    public final synchronized TaskScheduler getTaskScheduler() {
        if (instance != this || closed) throw new IllegalStateException("Provider 未注册或已关闭");
        return taskScheduler;
    }

    /**
     * 关闭 HTTP、平台连接、聊天队列、指令监听器及两种调度器，重复调用不重复关闭
     * 已开始执行的任务不受中断，完成后不再安排下次执行
     */
    @Override
    public final synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            stopServer();
        } finally {
            try {
                if (officialManager != null) officialManager.stop();
            } finally {
                try {
                    if (commandListener != null) EventManager.getInstance().unregisterEvents(commandListener);
                    try {
                        taskScheduler.close();
                    } finally {
                        scheduler.shutdown();
                    }
                } finally {
                    if (chatService != null) chatService.close();
                }
            }
        }
    }

    /**
     * @return 账号查询与访问控制实现，不得为 null
     */
    public AccessService getAccessService() {
        return accessService;
    }

    /**
     * @return 日志、消息记录及运行通知实现，不得为 null
     */
    public LogRecorder getLogRecorder() {
        return logRecorder;
    }

    /**
     * @return 指令资源与运行服务策略，不得为 null
     */
    public BotCommand getBotCommand() {
        return botCommand;
    }

    /**
     * 获取框架创建的 QQ 聊天服务，与平台连接共用令牌缓存
     *
     * @return 已初始化的 QQ 聊天服务
     * @throws IllegalStateException Provider 尚未初始化、已关闭或 QQ 未启用
     */
    public ChatService getChatService() {
        if (chatService == null || closed) throw new IllegalStateException("QQ 聊天服务尚未初始化、已关闭或未启用");
        return chatService;
    }

    /**
     * 获取 QQ 平台连接与聊天服务共用的令牌服务
     *
     * @return QQ 令牌服务
     * @throws IllegalStateException Provider 尚未初始化、已关闭或 QQ 未启用
     */
    public final TokenManager getTokenManager() {
        return getChatService().getTokenManager();
    }

    /**
     * @return 被动回复的维护图片；未配置时返回 null，暂停期间不发送替代消息
     */
    public ImageComponent getMaintenanceMessage() {
        return null;
    }
}
