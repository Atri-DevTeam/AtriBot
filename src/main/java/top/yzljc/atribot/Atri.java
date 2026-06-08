package top.yzljc.atribot;

import cn.dev33.satoken.sign.SaSignManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.CorsPluginConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.debug.OfficialBotDebug;
import top.yzljc.atribot.feature.Reboot;
import top.yzljc.atribot.feature.RollbackMessages;
import top.yzljc.atribot.feature.SearchRelevant;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.config.Reload;
import top.yzljc.atribot.config.groups.GroupConfigInfo;
import top.yzljc.atribot.config.groups.GroupConfigManager;
import top.yzljc.atribot.config.groups.GroupModeManager;
import top.yzljc.atribot.webui.onebot.OneBotWebUIRouter;
import top.yzljc.atribot.functions.official.ChatContentRecord;
import top.yzljc.atribot.feature.Broadcast;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.feature.*;
import top.yzljc.atribot.feature.github.WebhookServer;
import top.yzljc.atribot.feature.like.LikeUser;
import top.yzljc.atribot.feature.minecraft.*;
import top.yzljc.atribot.feature.schedule.Calendar;
import top.yzljc.atribot.functions.official.*;
import top.yzljc.atribot.functions.official.tufe.ElectricCheck;
import top.yzljc.atribot.functions.official.tufe.TufeElecBind;
import top.yzljc.atribot.functions.onebot.*;
import top.yzljc.atribot.functions.overall.Feedback;
import top.yzljc.atribot.functions.overall.Hitokoto;
import top.yzljc.atribot.functions.overall.HypixelReward;
import top.yzljc.atribot.functions.overall.MojangStatus;
import top.yzljc.atribot.functions.thirdpartservice.GroupJoinVerify;
import top.yzljc.atribot.feature.news.HypixelNews;
import top.yzljc.atribot.feature.schedule.*;
import top.yzljc.atribot.functions.onebot.Repeater;
import top.yzljc.atribot.functions.overall.minecraftnews.MinecraftNews;
import top.yzljc.atribot.functions.official.minecraft.MinecraftRemote;
import top.yzljc.atribot.functions.official.minecraft.MinecraftBind;
import top.yzljc.atribot.functions.official.EventRecord;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.functions.official.permission.C2CList;
import top.yzljc.atribot.service.official.OfficialManager;
import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.service.official.OfficialTokenManager;
import top.yzljc.atribot.service.ai.AiService;
import top.yzljc.atribot.service.timer.RunScheduleTask;
import top.yzljc.atribot.service.request.RequestReceiver;
import top.yzljc.atribot.service.Scheduler;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.utils.tools.RM;
import top.yzljc.atribot.chat.onebot.FriendList;
import top.yzljc.atribot.socket.MinecraftSocket;
import top.yzljc.atribot.test.Test;
import top.yzljc.atribot.utils.AtriHelp;
import top.yzljc.atribot.utils.BotRuntimeData;
import top.yzljc.atribot.utils.SetProjectInfo;
import top.yzljc.atribot.feature.like.AutoLikeCommand;
import top.yzljc.atribot.test.draft.Scratch;
import top.yzljc.atribot.webui.official.OfficialWebUIRouter;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Atri {
    @Getter
    private static MinecraftSocket minecraftSocket;
    @Getter
    private static Atri instance;
    @Getter
    private Scheduler scheduler;
    @Getter
    private ChatService chatService;
    @Getter
    private AiService aiService;
    @Getter
    private OfficialTokenManager tokenManager;

    private final Javalin server;
    private final OfficialManager qqBotManagerService;
    private final AtomicBoolean disabled = new AtomicBoolean(false);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Atri() {
        if (instance != null) {
            throw new RuntimeException("AtriBot 已在运行");
        }
        instance = this;
        Runtime.getRuntime().addShutdownHook(new Thread(this::onDisable));

        Config config = Config.getInstance();

        this.aiService = new AiService(config.getAiBotProperties(), objectMapper);
        this.tokenManager = new OfficialTokenManager(config.getQqAppId(), config.getQqClientSecret());
        this.chatService = new ChatService(config.getQqApiBaseUrl(), tokenManager);
        this.qqBotManagerService = new OfficialManager(config.getQqApiBaseUrl(), tokenManager);

        int qqBotPort = config.getQqBotPort();

        server = Javalin.create(cfg -> {
            cfg.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/official-webui";
                staticFiles.directory = "/official-webui";
                staticFiles.location = Location.CLASSPATH;
            });
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/webui";
                staticFiles.directory = "/official-webui";
                staticFiles.location = Location.CLASSPATH;
            });
            cfg.jetty.modifyHttpConfiguration(http -> http.addCustomizer(
                    new org.eclipse.jetty.server.ForwardedRequestCustomizer()
            ));
        }).start(config.getQqBotPort());

        server.before("/official-webui/*", ctx -> {
            log.info("{} {} from {}", ctx.method(), ctx.fullUrl(), ctx.ip());
        });
        server.before("/webui/*", ctx -> {
            log.info("{} {} from {}", ctx.method(), ctx.fullUrl(), ctx.ip());
        });

        server.post("/", ctx -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            String result = RequestReceiver.handle(body);
            ctx.result(result).contentType("application/json");
        });

        OneBotWebUIRouter.register(server);
        OfficialWebUIRouter.register(server);

        log.info("HTTP 服务器已在端口 {} 上启动", qqBotPort);

        SaSignManager.getConfig().setSecretKey(Config.getInstance().getSaSignSecretKey());
    }

    public void onEnable() {
        System.out.println("====== ATRI IS STARTING ======");
        EventManager.getInstance().registerEvents(new Hitokoto());
        EventManager.getInstance().registerEvents(new AutoAcceptFriend());
        EventManager.getInstance().registerEvents(new CommandManager());
        EventManager.getInstance().registerEvents(new DenyFuckGuys());
        EventManager.getInstance().registerEvents(new UnknownInvitation());
        EventManager.getInstance().registerEvents(new LikeUser());
        EventManager.getInstance().registerEvents(new Notify());
        EventManager.getInstance().registerEvents(new Repeater());
        EventManager.getInstance().registerEvents(new CheckBilibili());
        EventManager.getInstance().registerEvents(new AutoPokeBack());
        EventManager.getInstance().registerEvents(new GroupMessageCheck());
        EventManager.getInstance().registerEvents(new NotifyRecalled());
        EventManager.getInstance().registerEvents(new GroupModeManager());
        EventManager.getInstance().registerEvents(new AnnoyUser());
        EventManager.getInstance().registerEvents(new HypixelReward());
        EventManager.getInstance().registerEvents(new GroupContentRecord());
        EventManager.getInstance().registerEvents(new ElectricCheck());
        EventManager.getInstance().registerEvents(new Scratch());
        EventManager.getInstance().registerEvents(new BotRuntimeData());
        EventManager.getInstance().registerEvents(new Test());
        EventManager.getInstance().registerEvents(new VerifyMinecraftCommand());
        EventManager.getInstance().registerEvents(new EventRecord());
        EventManager.getInstance().registerEvents(new ChatContentRecord());
        EventManager.getInstance().registerEvents(new Feedback());
        EventManager.getInstance().registerEvents(new GroupJoinVerify());
        EventManager.getInstance().registerEvents(new OfficialBotDebug());
        EventManager.getInstance().registerEvents(new WebUICommand());
        EventManager.getInstance().registerEvents(new FullMessageEnableCommand());

        CommandManager.reload();

        CommandManager.getCommand("happynewyear").setExecutor(new HappyNewYear());
        CommandManager.getCommand("bc").setExecutor(new Broadcast());
        CommandManager.getCommand("wakeup").setExecutor(new WakeUp());
        CommandManager.getCommand("reboot").setExecutor(new Reboot());
        CommandManager.getCommand("search").setExecutor(new SearchRelevant());
        CommandManager.getCommand("recall").setExecutor(new RM());
        CommandManager.getCommand("rollback").setExecutor(new RollbackMessages());
        CommandManager.getCommand("motd").setExecutor(new Motd());
        CommandManager.getCommand("mojang").setExecutor(new MojangStatus());
        CommandManager.getCommand("cl").setExecutor(new HypixelReward());
        CommandManager.getCommand("bwc").setExecutor(new BedwarsChallenge());
        CommandManager.getCommand("checkmcnews").setExecutor(new MinecraftNews());
        CommandManager.getCommand("checkhypnews").setExecutor(new HypixelNews());
        CommandManager.getCommand("manodate").setExecutor(new ManosabaDate());
        CommandManager.getCommand("github").setExecutor(new WebhookServer());
        CommandManager.getCommand("signall").setExecutor(new AutoSign());
        CommandManager.getCommand("chat").setExecutor(new MessageStats());
        CommandManager.getCommand("groupinfo").setExecutor(new GroupConfigInfo());
        CommandManager.getCommand("calendar").setExecutor(new Calendar());
        CommandManager.getCommand("atrihelp").setExecutor(new AtriHelp());
        CommandManager.getCommand("emj").setExecutor(new AnnoyUser());
        CommandManager.getCommand("reload").setExecutor(new Reload());
        CommandManager.getCommand("autolike").setExecutor(new AutoLikeCommand());
        CommandManager.getCommand("tufe").setExecutor(new TufeClassAlert());
        CommandManager.getCommand("verify").setExecutor(new VerifyMinecraftCommand());
        CommandManager.getCommand("info").setExecutor(new SizeNtUid());

        CommandManager.getCommand("stats").setExecutor(new PlayerProfile());
        CommandManager.getCommand("rc").setExecutor(new RconHandler());
        CommandManager.getCommand("whoami").setExecutor(new WhoAmI());
        CommandManager.getCommand("mc").setExecutor(new MinecraftCommand());
        CommandManager.getCommand("test").setExecutor(new Test());
        CommandManager.getCommand("feedback").setExecutor(new Feedback());
        CommandManager.getCommand("ogroup").setExecutor(new GroupCommand());
        CommandManager.getCommand("permission").setExecutor(new PermissionCommand());
        CommandManager.getCommand("today").setExecutor(new top.yzljc.atribot.functions.official.Calendar());
        CommandManager.getCommand("help").setExecutor(new HelpCommand());
        CommandManager.getCommand("minesweeper").setExecutor(new MinesweeperGame());
        CommandManager.getCommand("hitokoto").setExecutor(new Hitokoto());
        CommandManager.getCommand("贡献名单").setExecutor(new SponsorCommand());
        CommandManager.getCommand("webui").setExecutor(new WebUICommand());
        CommandManager.getCommand("全量消息").setExecutor(new FullMessageEnableCommand());

        CommandManager.getCommand("elec").setExecutor(new ElectricCheck());

        this.scheduler = new Scheduler();
        try {
            BotRuntimeData.init();
        } catch (Exception e) {
            log.error("BotRuntimeData 初始化失败: {}", e.getMessage());
        }

        System.setProperty("java.awt.headless", "true");
        System.out.println("==== AtriBot ====");

        Config settings = Config.getInstance();

        int webhookPort = settings.getGithubWebhookPort();
        String webhookSecret = settings.getGithubWebhookSecret();

        RunScheduleTask.runAllTasks();

        GroupConfigManager.refreshAllConfigs();
        FriendList.updateFriendList();
        ChatContentRecord.init();

        MinecraftNews.loadHistory();
        HypixelNews.loadHistory();

        if (!Config.getInstance().isDebugMode()) {
            WebhookServer.start(webhookPort, webhookSecret);
            MinecraftBind.init();
            GroupList.init();
            C2CList.init();
            TufeElecBind.init();
        }

        SetProjectInfo.setInfo();

        GroupConfigManager.registerFeature("auto_sign", true);
        GroupConfigManager.registerFeature("mc_news", false);
        GroupConfigManager.registerFeature("hyp_news", false);
        GroupConfigManager.registerFeature("electric_check", false);
        GroupConfigManager.registerFeature("annoy_user", true);
        GroupConfigManager.registerFeature("new_year", true);
        GroupConfigManager.registerFeature("one_text", true);
        GroupConfigManager.registerFeature("repeat_msg", false);
        GroupConfigManager.registerFeature("send_poke", true);
        GroupConfigManager.registerFeature("like_user", false);
        GroupConfigManager.registerFeature("mojang_status", true);
        GroupConfigManager.registerFeature("motd", false);
        GroupConfigManager.registerFeature("github_info", false);
        GroupConfigManager.registerFeature("bv_check", false);
        GroupConfigManager.registerFeature("wakeup_send", false);
        GroupConfigManager.registerFeature("broadcast", true);
        GroupConfigManager.registerFeature("calendar", true);
        GroupConfigManager.registerFeature("get_hypixel_reward", false);
        GroupConfigManager.registerFeature("bedwars_challenge", true);
        GroupConfigManager.registerFeature("tufe_class_alert", false);
        GroupConfigManager.registerFeature("verify_server", false);
        GroupConfigManager.registerFeature("illegal_words_check", false);

        try {
            if (!Config.getInstance().isDebugMode()) {
                String mcIp = settings.getVarietyHost();
                int mcPort = settings.getVarietyPort();
                String pubKey = settings.getVarietyKey();

                minecraftSocket = new MinecraftSocket(mcIp, mcPort, pubKey);
                MinecraftRemote.connect("atri", mcIp, mcPort, Config.getInstance().getDebugGroupId(), pubKey);
            }
        } catch (Exception e) {
            log.error("MinecraftVerify 初始化失败: {}", e.getMessage());
        }

        BotRuntimeData.callStartUp();
    }

    public void onDisable() {
        if (!disabled.compareAndSet(false, true)) {
            return;
        }

        if (scheduler != null) {
            scheduler.cancelTask(BotRuntimeData.getTask());
        }
        MinecraftRemote.disconnect();
        qqBotManagerService.stop();
        WebhookServer.stop();
        HypixelReward.shutdown();
        RunScheduleTask.shutdown();
        if (server != null) {
            server.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        ThreadManager.shutdown();
        System.out.println("==== AtriBot Disabled ====");
    }

    public static void main(String[] args) {
        Atri bot = new Atri();
        bot.onEnable();

        try {
            bot.qqBotManagerService.start();
        } catch (Exception e) {
            log.error("QQ Bot 初始化失败: {}", e.getMessage());
        }

        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if ("stop".equalsIgnoreCase(line.trim())) {
                    BotRuntimeData.save();
                    System.out.println("正在关闭 AtriBot...");
                    bot.onDisable();
                    System.exit(0);
                    break;
                }
            }
        }, "Console-Listener").start();
    }
}
