package top.yzljc.atribot;

import cn.dev33.satoken.sign.SaSignManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.CorsPluginConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.UACommand;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.napcat.FriendList;
import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.chat.official.moderation.GroupJoinReviewListener;
import top.yzljc.atribot.chat.official.moderation.GroupModerationListener;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.ErrorReportRepository;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.database.repo.ModerationLogRepository;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;
import top.yzljc.atribot.database.repo.EventLogRepository;
import top.yzljc.atribot.function.admin.*;
import top.yzljc.atribot.function.command.*;
import top.yzljc.atribot.function.command.HelpCommand;
import top.yzljc.atribot.function.games.ClickTrainGame;
import top.yzljc.atribot.function.games.ConnectFourGame;
import top.yzljc.atribot.function.games.LuckyRouletteGame;
import top.yzljc.atribot.function.games.MinesweeperGame;
import top.yzljc.atribot.function.games.RockPaperScissorsGame;
import top.yzljc.atribot.function.minecraft.McVersionImpl;
import top.yzljc.atribot.function.minecraft.PackVersion;
import top.yzljc.atribot.function.minecraft.SkyblockPackCheckImpl;
import top.yzljc.atribot.function.qqguild.HypixelStatusCommand;
import top.yzljc.atribot.function.qqguild.HypixelTNTWizardsCommand;
import top.yzljc.atribot.function.qqguild.HypixelZombiesCommand;
import top.yzljc.atribot.function.tasks.*;
import top.yzljc.atribot.function.utils.*;
import top.yzljc.atribot.function.utils.general.*;
import top.yzljc.atribot.function.utils.napcat.*;
import top.yzljc.atribot.function.utils.official.*;
import top.yzljc.atribot.function.command.PicStatsCommand;
import top.yzljc.atribot.function.command.PicSubmitCommand;
import top.yzljc.atribot.function.command.DrawCommand;
import top.yzljc.atribot.function.utils.official.minecraft.MinecraftBind;
import top.yzljc.atribot.function.utils.official.minecraft.MinecraftRemote;
import top.yzljc.atribot.function.utils.personal.*;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.test.*;
import top.yzljc.atribot.utils.notify.PendingNoticeDispatcher;
import top.yzljc.atribot.database.repo.SignRepository;
import top.yzljc.atribot.database.repo.TufeElecRepository;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.function.utils.like.AutoLikeCommand;
import top.yzljc.atribot.function.utils.like.CardLike;
import top.yzljc.atribot.function.impl.tufe.TufeCheckHelp;
import top.yzljc.atribot.function.command.TufeElectricBindCommand;
import top.yzljc.atribot.function.command.TufeElectricQueryCommand;
import top.yzljc.atribot.function.task.*;
import top.yzljc.atribot.platform.napcat.RequestReceiver;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigInfo;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupModeManager;
import top.yzljc.atribot.platform.discord.DiscordManager;
import top.yzljc.atribot.platform.qq.OfficialManager;
import top.yzljc.atribot.platform.qq.QQWebhookHandler;
import top.yzljc.atribot.platform.qq.TokenManager;
import top.yzljc.atribot.service.ai.AiService;
import top.yzljc.atribot.service.Scheduler;
import top.yzljc.atribot.service.email.IMAP;
import top.yzljc.atribot.service.runtime.ConsoleManager;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.service.timer.RunScheduleTask;
import top.yzljc.atribot.service.taskscheduler.TaskScheduler;
import top.yzljc.atribot.service.taskscheduler.TaskSchedulerRegistry;
import top.yzljc.atribot.function.admin.update.UpdatePushCommand;
import top.yzljc.atribot.utils.socket.MinecraftSocket;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;
import top.yzljc.atribot.utils.tools.RM;
import top.yzljc.atribot.webui.WebUIRouter;
import top.yzljc.atribot.webui.WebUISessionManager;
import top.yzljc.sakuraba_ema.ChannelCliClient;
import top.yzljc.sakuraba_ema.groups.GroupBotClient;
import top.yzljc.sakuraba_ema.groups.GroupBotConfig;
import top.yzljc.sakuraba_ema.groups.GroupBotRouteStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Atri {

    @Getter
    private static Atri instance;
    @Getter
    private TokenManager tokenManager;
    @Getter
    private ChatService chatService;
    @Getter
    private Scheduler scheduler;
    private TaskScheduler taskScheduler;
    @Getter
    private AiService aiService;
    @Getter
    private static MinecraftSocket minecraftSocket;
    @Getter
    private final MojiraStatus checkMojira;
    @Getter
    private final CardLike cardLike;
    @Getter
    private final RebootCommand reboot;
    @Getter
    private final McVersionImpl minecraftVersionCheck;
    @Getter
    private final MinecraftNews minecraftNews;
    @Getter
    private final HypixelAnnouncements hypixelAnnouncements;
    @Getter
    private final HypixelAlphaForums hypixelAlphaForums;
    @Getter
    private final SkyblockPackCheckImpl skyblockPackCheck;
    private final DiscordManager discordManager;
    @Getter
    private final ChannelCliClient tencentChannelCliClient;
    @Getter
    private final CalendarTask calendarTask;
    private final Javalin server;
    private final OfficialManager qqBotManagerService;
    private final QQWebhookHandler qqWebhookHandler;
    @Getter
    private final List<GroupBotClient> qqGroupBotClients;
    private IMAP imap;
    @Getter
    public static final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean disabled = new AtomicBoolean(false);
    /** 记录已访问过 webui api 的 IP */
    private final Set<String> seenWebuiApiIps = ConcurrentHashMap.newKeySet();

    public Atri() {
        if (instance != null) {
            throw new RuntimeException("AtriBot 已在运行");
        }
        instance = this;
        Config config = Config.getInstance();
        this.aiService = new AiService(config.getAiPropertiesMap(), objectMapper);
        this.tokenManager = new TokenManager(config.getQqAppId(), config.getQqClientSecret());
        this.qqBotManagerService = new OfficialManager(config.getQqApiBaseUrl(), tokenManager, config.getQqConnectionMode());
        this.qqWebhookHandler = new QQWebhookHandler(config.getQqAppId(), config.getQqClientSecret());
        this.chatService = new ChatService(config.getQqApiBaseUrl(), tokenManager);
        this.qqGroupBotClients = createGroupBotClients(config);
        this.checkMojira = new MojiraStatus();
        this.cardLike = new CardLike();
        this.reboot = new RebootCommand();
        this.minecraftVersionCheck = new McVersionImpl();
        this.minecraftNews = new MinecraftNews();
        this.hypixelAnnouncements = new HypixelAnnouncements();
        this.hypixelAlphaForums = new HypixelAlphaForums();
        this.skyblockPackCheck = new SkyblockPackCheckImpl();
        this.tencentChannelCliClient = new ChannelCliClient(
                config.isTencentChannelEnabled(),
                config.getTencentChannelCliPath(),
                config.getTencentChannelLoginToken(),
                Duration.ofSeconds(config.getTencentChannelTimeoutSeconds()),
                objectMapper
        );
        this.calendarTask = new CalendarTask();
        this.discordManager = config.isDiscordEnabled() && config.getDiscordBotToken() != null && !config.getDiscordBotToken().isBlank()
                ? new DiscordManager(config.getDiscordApiBaseUrl(), config.getDiscordBotToken(), config.getDiscordIntents())
                : null;

        int qqBotPort = config.getListenPort();

        QQBot.fetchBotInfo();

        server = Javalin.create(cfg -> {
            cfg.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/webui";
                staticFiles.directory = "/official-webui";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.headers.put("X-Frame-Options", "DENY");
            });
            cfg.jetty.modifyHttpConfiguration(http -> http.addCustomizer(
                    new org.eclipse.jetty.server.ForwardedRequestCustomizer()
            ));
            cfg.http.maxRequestSize = 10_000_000;
        }).start(qqBotPort);

        server.before("/webui/api/*", ctx -> {
            String ip = ctx.ip();
            if (seenWebuiApiIps.add(ip)) {
                log.info("[!] 检测到新 IP 访问 WebUI API: {} {} from {}", ctx.method(), ctx.fullUrl(), ip);
            }
        });

        server.post("/", ctx -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            String result = RequestReceiver.handle(body);
            ctx.result(result).contentType("application/json");
        });

        if (config.isOfficialBotEnabled() && "webhook".equals(config.getQqConnectionMode())) {
            server.post(config.getQqWebhookPath(), qqWebhookHandler::handle);
            log.info("QQ Webhook 回调已启用: POST {}", config.getQqWebhookPath());
        }

        for (GroupBotClient groupBotClient : qqGroupBotClients) {
            server.post(groupBotClient.getConfig().webhookPath(), groupBotClient::handleWebhook);
            log.info("QQ 群聊 Bot 实例 {} 的 Webhook 已启用: POST {}",
                    groupBotClient.key(), groupBotClient.getConfig().webhookPath());
        }

        WebUIRouter.register(server);

        log.info("HTTP 服务器已在端口 {} 上启动", qqBotPort);

        SaSignManager.getConfig().setSecretKey(Config.getInstance().getSaSignSecretKey());
    }

    public void onEnable() {
        System.out.println("====== ATRI IS STARTING ======");
        EventManager.getInstance().registerEvents(new HitokotoCommand());
        EventManager.getInstance().registerEvents(new AutoAcceptFriend());
        EventManager.getInstance().registerEvents(new CommandManager());
//        EventManager.getInstance().registerEvents(new DenyFuckGuys());
        EventManager.getInstance().registerEvents(new UnknownInvitation());
        EventManager.getInstance().registerEvents(this.cardLike);
        EventManager.getInstance().registerEvents(new Notify());
        EventManager.getInstance().registerEvents(new Repeater());
        EventManager.getInstance().registerEvents(new BiliBiliResolver());
        EventManager.getInstance().registerEvents(new AutoPokeBack());
//        EventManager.getInstance().registerEvents(new GroupMessageCheck());
        EventManager.getInstance().registerEvents(new NotifyRecalled());
        EventManager.getInstance().registerEvents(new GroupModeManager());
        EventManager.getInstance().registerEvents(new AnnoyUser());
        EventManager.getInstance().registerEvents(new HypixelRewardCommand());
        EventManager.getInstance().registerEvents(new GroupContentRecord());
        EventManager.getInstance().registerEvents(new AtriChat());
        EventManager.getInstance().registerEvents(new BotRuntimeData());
        EventManager.getInstance().registerEvents(new Test());
        EventManager.getInstance().registerEvents(new VerifyMinecraftCommand());
        EventManager.getInstance().registerEvents(new QQEventRecord());
        EventManager.getInstance().registerEvents(new QQChatContentRecord());
        EventManager.getInstance().registerEvents(new FeedbackCommand());
        EventManager.getInstance().registerEvents(new AutoSendPtt());
        EventManager.getInstance().registerEvents(new WebUICommand());
        EventManager.getInstance().registerEvents(new FullMessageEnableCommand());
        ClickTrainGame clickTrainGame = new ClickTrainGame();
        EventManager.getInstance().registerEvents(clickTrainGame);
        RockPaperScissorsGame rockPaperScissorsGame = new RockPaperScissorsGame();
        EventManager.getInstance().registerEvents(rockPaperScissorsGame);
        EventManager.getInstance().registerEvents(new SignCommand());
        UpdatePushCommand updatePushCommand = new UpdatePushCommand();
        EventManager.getInstance().registerEvents(updatePushCommand);
        EventManager.getInstance().registerEvents(new EmailNotify());
        EventManager.getInstance().registerEvents(new PendingNoticeDispatcher());
        EventManager.getInstance().registerEvents(new BasicReply());
        EventManager.getInstance().registerEvents(new UACommand());
        EventManager.getInstance().registerEvents(new GroupModerationListener());
        EventManager.getInstance().registerEvents(new GroupJoinReviewListener());
        EventManager.getInstance().registerEvents(new WhatFuckingPing());

        CommandManager.reload();
        CommandManager.getCommand("newyear").setExecutor(new HappyNewYearCommand());
//        CommandManager.getCommand("bc").setExecutor(new Broadcast());
        CommandManager.getCommand("anan").setExecutor(AnAnGirlEmoji.INSTANCE);
        CommandManager.getCommand("reboot").setExecutor(this.reboot);
        CommandManager.getCommand("search").setExecutor(new SearchRelevant());
        CommandManager.getCommand("recall").setExecutor(new RM());
        CommandManager.getCommand("rollback").setExecutor(new RollbackMessages());
        CommandManager.getCommand("gt").setExecutor(CucumberGirl.INSTANCE);
        CommandManager.getCommand("mojang").setExecutor(new McStatusCommand());
        CommandManager.getCommand("cl").setExecutor(new HypixelRewardCommand());
        CommandManager.getCommand("preferences").setExecutor(new PreferencesSettingsCommand());
        CommandManager.getCommand("check-mc").setExecutor(this.minecraftNews);
        CommandManager.getCommand("manodate").setExecutor(new ManosabaDate());
        CommandManager.getCommand("github").setExecutor(new GithubCommitNotify());
        CommandManager.getCommand("signall").setExecutor(new AutoSign());
        CommandManager.getCommand("chat").setExecutor(new MessageStats());
        CommandManager.getCommand("groupinfo").setExecutor(new GroupConfigInfo());
//        CommandManager.getCommand("calendar").setExecutor(new Calendar());
        CommandManager.getCommand("check-mojira").setExecutor(this.checkMojira);
        CommandManager.getCommand("emj").setExecutor(new AnnoyUser());
        CommandManager.getCommand("py").setExecutor(PinYin.INSTANCE);
        CommandManager.getCommand("autolike").setExecutor(new AutoLikeCommand());
        CommandManager.getCommand("tufe").setExecutor(new TufeClassAlert());
        CommandManager.getCommand("verify").setExecutor(new VerifyMinecraftCommand());
        CommandManager.getCommand("info").setExecutor(new SizeNtUid());

        CommandManager.getCommand("stats").setExecutor(new PlayerProfile());
        CommandManager.getCommand("rc").setExecutor(new RconHandler());
        CommandManager.getCommand("test-whoami").setExecutor(new DebugWhoAmI());
        CommandManager.getCommand("mc").setExecutor(new MinecraftCommand());
        CommandManager.getCommand("test").setExecutor(new Test());
        CommandManager.getCommand("feedback").setExecutor(new FeedbackCommand());
        CommandManager.getCommand("ogroup").setExecutor(new GroupManagementCommand());
        CommandManager.getCommand("perm").setExecutor(new UserManagementCommand());
        CommandManager.getCommand("today").setExecutor(this.calendarTask);
        CommandManager.getCommand("help").setExecutor(new HelpCommand());
        CommandManager.getCommand("minesweeper").setExecutor(new MinesweeperGame());
        CommandManager.getCommand("connect4").setExecutor(new ConnectFourGame());
        CommandManager.getCommand("clicktrain").setExecutor(clickTrainGame);
        CommandManager.getCommand("roulette").setExecutor(new LuckyRouletteGame());
        CommandManager.getCommand("hitokoto").setExecutor(new HitokotoCommand());
        CommandManager.getCommand("贡献名单").setExecutor(new SponsorCommand());
        CommandManager.getCommand("webui").setExecutor(new WebUICommand());
        CommandManager.getCommand("全量消息").setExecutor(new FullMessageEnableCommand());
        CommandManager.getCommand("推送任务").setExecutor(new PushTaskCommand());
        CommandManager.getCommand("四子棋").setExecutor(new ConnectFourGame());
        CommandManager.getCommand("rsp").setExecutor(rockPaperScissorsGame);

        CommandManager.getCommand("查询帮助").setExecutor(new TufeCheckHelp());
        CommandManager.getCommand("绑定").setExecutor(new TufeElectricBindCommand());
        CommandManager.getCommand("宿舍电表").setExecutor(new TufeElectricQueryCommand(0, "宿舍电表", "宿舍电表"));
        CommandManager.getCommand("空调电表").setExecutor(new TufeElectricQueryCommand(1, "空调电表", "空调电表"));
        CommandManager.getCommand("sign").setExecutor(new SignCommand());
        CommandManager.getCommand("debug").setExecutor(new DebugCommand());
        CommandManager.getCommand("check-hyp").setExecutor(this.hypixelAnnouncements);
        CommandManager.getCommand("check-hyp-alpha").setExecutor(this.hypixelAlphaForums);
        CommandManager.getCommand("games").setExecutor(new MiniGameCommand());
        CommandManager.getCommand("music").setExecutor(new MusicCommand());
        CommandManager.getCommand("ping").setExecutor(new PingCommand());
        CommandManager.getCommand("boop").setExecutor(BoopCommand.INSTANCE);
        CommandManager.getCommand("update").setExecutor(updatePushCommand);
        CommandManager.getCommand("spc").setExecutor(new YunLandSpecialCommand());
        CommandManager.getCommand("golds").setExecutor(new GoldsCommand());
        CommandManager.getCommand("hypstatus").setExecutor(new HypixelStatusCommand());
        CommandManager.getCommand("submit").setExecutor(new PicSubmitCommand());
        CommandManager.getCommand("图源").setExecutor(new PicStatsCommand());
        CommandManager.getCommand("pause").setExecutor(new AdminPauseCommand());
        CommandManager.getCommand("地球online").setExecutor(new EarthOnline());
        CommandManager.getCommand("drawitem").setExecutor(new DrawCommand());
        CommandManager.getCommand("recovergolds").setExecutor(new RecoverLostGolds());
        CommandManager.getCommand("refresh").setExecutor(RefreshGroupProfilesTask.INSTANCE);
        CommandManager.getCommand("ua").setExecutor(new UACommand());
        CommandManager.getCommand("wz").setExecutor(new HypixelTNTWizardsCommand());
        CommandManager.getCommand("zs").setExecutor(new HypixelZombiesCommand());
        CommandManager.getCommand("time").setExecutor(new TimezoneCommand());
        CommandManager.getCommand("bantrack").setExecutor(new BanTrackCommand());
        CommandManager.getCommand("weather").setExecutor(new WeatherCommand());
        CommandManager.getCommand("mcv").setExecutor(new MinecraftVersionCommand());
        CommandManager.getCommand("mccape").setExecutor(new MinecraftCapeCommand());
        CommandManager.getCommand("skbpack").setExecutor(new SkyblockPackCommand());

        // TODO: Evaluate official group active-message permissions before wiring scheduled weather delivery.

        CommandManager.getCommand("hyp").setExecutor(new HypixelCommand());
        CommandManager.getCommand("mctool").setExecutor(new MinecraftToolsCommand());
        CommandManager.getCommand("whoami").setExecutor(new WhoAmICommand());

        // ----------- DEBUG COMMANDS -----------
        CommandManager.getCommand("test-mcnews").setExecutor(new MinecraftNewsDebug());
        CommandManager.getCommand("test-markdown").setExecutor(new MarkdownDisplayTest());

        this.scheduler = new Scheduler();
        try {
            BotRuntimeData.init();
        } catch (Exception e) {
            log.error("BotRuntimeData 初始化失败: {}", e.getMessage());
        }

        System.setProperty("java.awt.headless", "true");
        System.out.println("==== AtriBot ====");

        Config settings = Config.getInstance();
        QQChatContentRecord.init();
        MinecraftNews.loadHistory();

        MinecraftBind.init();
        OfficialGroups.init();
        GroupBotRouteStore.initialize(qqGroupBotClients);
        OfficialUsers.init();
        TufeElecRepository.init();
        SignRepository.init();
        LootRepository.init();
        CoinGainLogRepository.init();
        FeedbackRepository.init();
        ErrorReportRepository.init();
        OfficialSendLogRepository.init();
        EventLogRepository.init();
        ModerationLogRepository.init();
        ImageSourceRepository.init();
        PendingNoticeRepository.init();
        UnifiedAuthentication.init();
        PackVersion.init();

        RunScheduleTask.runAllTasks();
        this.taskScheduler = new TaskScheduler();
        TaskSchedulerRegistry.registerAll(taskScheduler);

        String webhookPath = settings.getGithubWebhookPath();
        String webhookSecret = settings.getGithubWebhookSecret();

        if (settings.isNapcatEnabled()) {
            GroupConfigManager.refreshAllConfigs();
            FriendList.updateFriendList();
            SetProjectInfo.setInfo();
            GithubCommitNotify.register(server, webhookPath, webhookSecret);

            GroupConfigManager.registerFeature("auto_sign", true);
//            GroupConfigManager.registerFeature("mc_news", false);
//            GroupConfigManager.registerFeature("hyp_news", false);
//            GroupConfigManager.registerFeature("hyp_alpha_news", false);
            GroupConfigManager.registerFeature("annoy_user", true);
            GroupConfigManager.registerFeature("new_year", true);
            GroupConfigManager.registerFeature("one_text", true);
            GroupConfigManager.registerFeature("repeat_msg", false);
            GroupConfigManager.registerFeature("send_poke", true);
            GroupConfigManager.registerFeature("like_user", false);
//            GroupConfigManager.registerFeature("mojang_status", true);
//            GroupConfigManager.registerFeature("hypixel_status", true);
            GroupConfigManager.registerFeature("github_info", false);
            GroupConfigManager.registerFeature("bv_check", false);
            GroupConfigManager.registerFeature("mojira_tracker", false);
//            GroupConfigManager.registerFeature("broadcast", true);
//            GroupConfigManager.registerFeature("calendar", true);
            GroupConfigManager.registerFeature("get_hypixel_reward", false);
            GroupConfigManager.registerFeature("atri_chat", false);
            GroupConfigManager.registerFeature("tufe_class_alert", false);
            GroupConfigManager.registerFeature("private_func", false);
            GroupConfigManager.registerFeature("illegal_words_check", false);
        }

        if (settings.getEnv().equals("dev")) {
            WebUISessionManager.start();
        }

        if (settings.isEmailEnabled()) {
            try {
                this.imap = new IMAP();
                this.imap.start();
            } catch (Exception e) {
                this.imap = null;
                log.error("IMAP 邮件监听启动失败: {}", e.getMessage(), e);
            }
        }

        try {
            if (Config.getInstance().isVerifyEnabled()) {
                String mcIp = settings.getVerifyHost();
                int mcPort = settings.getVerifyPort();
                String pubKey = settings.getVerifyKey();

                minecraftSocket = new MinecraftSocket(mcIp, mcPort, pubKey);
                MinecraftRemote.connect("atri", mcIp, mcPort, Config.getInstance().getNapcatDebugGroupUin(), pubKey);
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
        HypixelRewardCommand.shutdown();
        RunScheduleTask.shutdown();
        if (imap != null) {
            imap.close();
            imap = null;
        }
        if (discordManager != null) {
            discordManager.stop();
        }
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        if (server != null) {
            server.stop();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
        tencentChannelCliClient.close();
        qqGroupBotClients.forEach(GroupBotClient::close);
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

        if (bot.discordManager != null) {
            try {
                bot.discordManager.start();
            } catch (Exception e) {
                log.error("Discord Bot 初始化失败: {}", e.getMessage(), e);
            }
        }

        ConsoleManager.start();
    }

    private static List<GroupBotClient> createGroupBotClients(Config config) {
        Set<String> webhookPaths = new HashSet<>();
        Set<String> appIds = new HashSet<>();
        if (config.isOfficialBotEnabled()) {
            appIds.add(config.getQqAppId());
            if ("webhook".equals(config.getQqConnectionMode())) {
                webhookPaths.add(config.getQqWebhookPath());
            }
        }

        List<GroupBotClient> clients = new ArrayList<>();
        for (GroupBotConfig groupBotConfig : config.getQqGroupBots()) {
            if (!groupBotConfig.enabled()) {
                continue;
            }
            groupBotConfig.validateEnabled();
            if (!webhookPaths.add(groupBotConfig.webhookPath())) {
                throw new IllegalArgumentException(
                        "Duplicate QQ webhook path: " + groupBotConfig.webhookPath());
            }
            if (!appIds.add(groupBotConfig.appId())) {
                throw new IllegalArgumentException(
                        "Duplicate QQ bot app-id for group instance: " + groupBotConfig.key());
            }
            clients.add(new GroupBotClient(groupBotConfig));
        }
        return List.copyOf(clients);
    }
}
