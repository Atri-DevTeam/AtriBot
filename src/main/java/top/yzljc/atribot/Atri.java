package top.yzljc.atribot;

import top.yzljc.atricore.core.CoreConfig;

import cn.dev33.satoken.sign.SaSignManager;
import top.yzljc.atricore.AtriProvider;
import top.yzljc.atribot.bootstrap.ApplicationAccessService;
import top.yzljc.atribot.bootstrap.ApplicationBotCommand;
import top.yzljc.atribot.bootstrap.ApplicationLogRecorder;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.core.AccessService;
import top.yzljc.atricore.core.BotCommand;
import top.yzljc.atricore.core.LogRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.CorsPluginConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.UACommand;
import top.yzljc.atribot.auth.LoginCommand;
import top.yzljc.atribot.auth.LoginService;
import top.yzljc.atribot.miniapp.MiniappCommand;
import top.yzljc.atribot.miniapp.MiniappRouter;
import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atricore.chat.napcat.FriendList;
import top.yzljc.atribot.chat.official.moderation.GroupJoinReviewListener;
import top.yzljc.atribot.chat.official.moderation.GroupModerationListener;
import top.yzljc.atricore.command.CommandManager;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.DailyFishingRepository;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.database.repo.ModerationLogRepository;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;
import top.yzljc.atribot.database.repo.EventLogRepository;
import top.yzljc.atribot.function.admin.*;
import top.yzljc.atribot.function.admin.ema.EmaCommand;
import top.yzljc.atribot.function.command.*;
import top.yzljc.atribot.function.reminder.ReminderService;
import top.yzljc.atribot.function.games.ClickTrainGame;
import top.yzljc.atribot.function.games.DailyFishingGame;
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
import top.yzljc.atribot.function.utils.napcat.*;
import top.yzljc.atribot.function.utils.official.*;
import top.yzljc.atribot.function.utils.official.minecraft.MinecraftRemote;
import top.yzljc.atribot.function.utils.personal.*;
import top.yzljc.atricore.platform.qq.QQBot;
import top.yzljc.atribot.test.*;
import top.yzljc.atribot.utils.notify.PendingNoticeDispatcher;
import top.yzljc.atribot.database.repo.SignRepository;
import top.yzljc.atricore.event.EventManager;
import top.yzljc.atribot.function.utils.like.AutoLikeCommand;
import top.yzljc.atribot.function.utils.like.CardLike;
import top.yzljc.atribot.function.task.*;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigInfo;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupModeManager;
import top.yzljc.atribot.service.ai.AiService;
import top.yzljc.atribot.service.email.IMAP;
import top.yzljc.atribot.service.runtime.ConsoleManager;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atribot.service.taskscheduler.TaskSchedulerRegistry;
import top.yzljc.atribot.function.admin.update.UpdatePushCommand;
import top.yzljc.atribot.utils.socket.MinecraftSocket;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;
import top.yzljc.atribot.utils.tools.RM;
import top.yzljc.atribot.webui.WebUIRouter;
import top.yzljc.atribot.webui.WebUISessionManager;
import top.yzljc.atribot.plugin.PluginManager;
import top.yzljc.atribot.plugin.PluginHttpRoutes;
import top.yzljc.sakuraba_ema.ChannelCliClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class Atri extends AtriProvider {
    private final AccessService accessService = new ApplicationAccessService();
    private final LogRecorder logRecorder = new ApplicationLogRecorder();
    private final BotCommand botCommand = new ApplicationBotCommand();
    private final SoundCommand soundCommand = new SoundCommand();

    @Getter
    private static Atri instance;
    @Getter
    private final GroupProfileRefreshBatcher groupProfileRefreshBatcher = new GroupProfileRefreshBatcher(
            (task, delayMillis) -> getScheduler().runTaskLaterAsynchronously(task, delayMillis),
            QQEventRecord::fetchAndSaveGroupProfile);
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
    private final SkyblockAnnouncements hypixelSkyblockAnnouncements;
    @Getter
    private final HypixelAlphaForums hypixelAlphaForums;
    @Getter
    private final SkyblockPackCheckImpl skyblockPackCheck;
    @Getter
    private final ChannelCliClient tencentChannelCliClient;
    @Getter
    private final CalendarTask calendarTask;
    private IMAP imap;
    @Getter
    public static final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean disabled = new AtomicBoolean(false);
    private final MiniappSessions miniappSessions = new MiniappSessions();
    @Getter
    private final PluginManager pluginManager = new PluginManager(Path.of("plugins"));
    /**
     * 记录已访问过 webui api 的 IP
     */
    private final Set<String> seenWebuiApiIps = ConcurrentHashMap.newKeySet();

    public Atri() {
        if (instance != null) {
            throw new RuntimeException("AtriBot 已在运行");
        }
        instance = this;
        Config config = Config.getInstance();
        initialize(cfg -> {
            cfg.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/webui";
                staticFiles.directory = "/official-webui";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.headers.put("X-Frame-Options", "DENY");
            });
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/atrimeow/profile";
                staticFiles.directory = "/miniapp";
                staticFiles.location = Location.CLASSPATH;
            });
            cfg.requestLogger.http((ctx, executionTimeMs) -> {
                if (ctx.path().equals("/openapi") || ctx.path().startsWith("/openapi/")) {
                    log.info("[!] 开放API访问记录 -> IP位置: {} 状态: {} 连接用时: {}",
                            ctx.ip(), ctx.res().getStatus(), Math.round(executionTimeMs));
                }
            });
        });
        try {
            this.aiService = new AiService(config.getAiPropertiesMap(), objectMapper);
            this.checkMojira = new MojiraStatus();
            this.cardLike = new CardLike();
            this.reboot = new RebootCommand();
            this.minecraftVersionCheck = new McVersionImpl();
            this.minecraftNews = new MinecraftNews();
            this.hypixelAnnouncements = new HypixelAnnouncements();
            this.hypixelSkyblockAnnouncements = new SkyblockAnnouncements();
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

            Javalin server = getServer();

            server.before("/webui/api/*", ctx -> {
                String ip = ctx.ip();
                if (seenWebuiApiIps.add(ip)) {
                    log.info("[!] 检测到新 IP 访问 WebUI API: {} {} from {}", ctx.method(), ctx.fullUrl(), ip);
                }
            });

            WebUIRouter.register(server);
            PluginHttpRoutes.attach(server);
            MiniappRouter.register(server, miniappSessions, config.isMiniappEnabled());

            SaSignManager.getConfig().setSecretKey(Config.getInstance().getSaSignSecretKey());
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    @Override
    public AccessService getAccessService() { return accessService; }

    @Override
    public LogRecorder getLogRecorder() { return logRecorder; }

    @Override
    public BotCommand getBotCommand() { return botCommand; }

    @Override
    public ImageComponent getMaintenanceMessage() {
        return ImageComponent.imageOf(RS.get("resource.image.maintenance")).setText("开发者暂且维护中，马上回来！");
    }

    public void onEnable() {
        System.out.println("====== ATRI IS STARTING ======");
        EventManager.getInstance().registerEvents(new HitokotoCommand());
//        EventManager.getInstance().registerEvents(new AutoAcceptFriend()); // disabled
//        EventManager.getInstance().registerEvents(new DenyFuckGuys());
        EventManager.getInstance().registerEvents(new UnknownInvitation());
        EventManager.getInstance().registerEvents(this.cardLike);
        EventManager.getInstance().registerEvents(new Notify());
        EventManager.getInstance().registerEvents(new Repeater());
        EventManager.getInstance().registerEvents(new BiliBiliResolver());
        EventManager.getInstance().registerEvents(new NeteaseMusicResolver());
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
        EventManager.getInstance().registerEvents(new QQEventRecord());
        EventManager.getInstance().registerEvents(new QQChatContentRecord());
        EventManager.getInstance().registerEvents(new FeedbackCommand());
        EventManager.getInstance().registerEvents(new ReminderCommand());
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
        EventManager.getInstance().registerEvents(new GroupJoinWelcome());
        EventManager.getInstance().registerEvents(new ShareBotCommand());

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
        CommandManager.getCommand("manodate").setExecutor(ManosabaDate.INSTANCE);
        CommandManager.getCommand("github").setExecutor(new GithubCommitNotify());
        CommandManager.getCommand("signall").setExecutor(AutoSign.INSTANCE);
        CommandManager.getCommand("chat").setExecutor(MessageStats.INSTANCE);
        CommandManager.getCommand("groupinfo").setExecutor(new GroupConfigInfo());
//        CommandManager.getCommand("calendar").setExecutor(new Calendar());
        CommandManager.getCommand("check-mojira").setExecutor(this.checkMojira);
        CommandManager.getCommand("emj").setExecutor(new AnnoyUser());
        CommandManager.getCommand("py").setExecutor(PinYin.INSTANCE);
        CommandManager.getCommand("autolike").setExecutor(new AutoLikeCommand());
//        CommandManager.getCommand("verify").setExecutor(new VerifyMinecraftCommand());
        CommandManager.getCommand("info").setExecutor(new SizeNtUid());

//        CommandManager.getCommand("stats").setExecutor(new PlayerProfile());
//        CommandManager.getCommand("rc").setExecutor(new RconHandler());
        CommandManager.getCommand("test-whoami").setExecutor(new DebugWhoAmI());
//        CommandManager.getCommand("mc").setExecutor(new MinecraftCommand());
        CommandManager.getCommand("test").setExecutor(new Test());
        CommandManager.getCommand("feedback").setExecutor(new FeedbackCommand());
        CommandManager.getCommand("remind").setExecutor(new ReminderCommand());
        CommandManager.getCommand("ogroup").setExecutor(new GroupManagementCommand());
        CommandManager.getCommand("perm").setExecutor(new UserManagementCommand());
        CommandManager.getCommand("today").setExecutor(this.calendarTask);
        CommandManager.getCommand("help").setExecutor(new HelpCommand());
        CommandManager.getCommand("minesweeper").setExecutor(new MinesweeperGame());
        CommandManager.getCommand("connect4").setExecutor(new ConnectFourGame());
        CommandManager.getCommand("clicktrain").setExecutor(clickTrainGame);
        CommandManager.getCommand("roulette").setExecutor(new LuckyRouletteGame());
        CommandManager.getCommand("hitokoto").setExecutor(new HitokotoCommand());
//        CommandManager.getCommand("贡献名单").setExecutor(new SponsorCommand());
        CommandManager.getCommand("webui").setExecutor(new WebUICommand());
        CommandManager.getCommand("全量消息").setExecutor(new FullMessageEnableCommand());
        CommandManager.getCommand("推送任务").setExecutor(new PushTaskCommand());
        CommandManager.getCommand("四子棋").setExecutor(new ConnectFourGame());
        CommandManager.getCommand("rsp").setExecutor(rockPaperScissorsGame);

        CommandManager.getCommand("sign").setExecutor(new SignCommand());
        CommandManager.getCommand("debug").setExecutor(new DebugCommand());
        CommandManager.getCommand("ema").setExecutor(new EmaCommand());
        CommandManager.getCommand("check-hyp").setExecutor(this.hypixelAnnouncements);
        CommandManager.getCommand("check-hyp-skyblock").setExecutor(this.hypixelSkyblockAnnouncements);
        CommandManager.getCommand("check-hyp-alpha").setExecutor(this.hypixelAlphaForums);
        CommandManager.getCommand("games").setExecutor(new MiniGameCommand());
        CommandManager.getCommand("fish").setExecutor(new DailyFishingGame());
        CommandManager.getCommand("music").setExecutor(new MusicCommand());
        CommandManager.getCommand("sound").setExecutor(soundCommand);
        CommandManager.getCommand("ping").setExecutor(new PingCommand());
        CommandManager.getCommand("boop").setExecutor(BoopCommand.INSTANCE);
        CommandManager.getCommand("update").setExecutor(updatePushCommand);
        CommandManager.getCommand("spc").setExecutor(new YunLandSpecialCommand());
        CommandManager.getCommand("golds").setExecutor(new GoldsCommand());
        CommandManager.getCommand("hypstatus").setExecutor(new HypixelStatusCommand());
        CommandManager.getCommand("submit").setExecutor(new PicSubmitCommand());
        CommandManager.getCommand("bg").setExecutor(new BackgroundCommand());
        CommandManager.getCommand("图源").setExecutor(new PicStatsCommand());
        CommandManager.getCommand("pause").setExecutor(new AdminPauseCommand());
        CommandManager.getCommand("地球online").setExecutor(new EarthOnline());
        CommandManager.getCommand("item").setExecutor(new DrawCommand());
        CommandManager.getCommand("recovergolds").setExecutor(new RecoverLostGolds());
        CommandManager.getCommand("refresh").setExecutor(RefreshGroupProfilesTask.INSTANCE);
        CommandManager.getCommand("ua").setExecutor(new UACommand());
        CommandManager.getCommand("login").setExecutor(new LoginCommand());
        CommandManager.getCommand("profile").setExecutor(new MiniappCommand(miniappSessions));
        CommandManager.getCommand("bind").setExecutor(new McBindCommand());
        CommandManager.getCommand("bvbind").setExecutor(new BilibiliBindCommand());
        CommandManager.getCommand("wz").setExecutor(new HypixelTNTWizardsCommand());
        CommandManager.getCommand("zs").setExecutor(new HypixelZombiesCommand());
        CommandManager.getCommand("time").setExecutor(new TimezoneCommand());
        CommandManager.getCommand("bantrack").setExecutor(new BanTrackCommand());
//        CommandManager.getCommand("weather").setExecutor(new WeatherCommand());
        CommandManager.getCommand("mcv").setExecutor(new MinecraftVersionCommand());
        CommandManager.getCommand("mccape").setExecutor(new MinecraftCapeCommand());
        CommandManager.getCommand("skbpack").setExecutor(new SkyblockPackCommand());

        CommandManager.getCommand("hyp").setExecutor(new HypixelCommand());
        CommandManager.getCommand("mctool").setExecutor(new MinecraftToolsCommand());
        CommandManager.getCommand("whoami").setExecutor(new WhoAmICommand());
        CommandManager.getCommand("how-to-custom-text").setExecutor(new GroupJoinWelcome());
        CommandManager.getCommand("sharebot").setExecutor(new ShareBotCommand());
        CommandManager.getCommand("groupsystemverify").setExecutor(new GroupSystemCommand());
        CommandManager.getCommand("dau").setExecutor(new DauCommand());

        // ----------- DEBUG COMMANDS -----------
        CommandManager.getCommand("test-mcnews").setExecutor(new MinecraftNewsDebug());
        CommandManager.getCommand("test-markdown").setExecutor(new MarkdownDisplayTest());

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

        OfficialGroups.init();
        OfficialUsers.init();
        SignRepository.init();
        LootRepository.init();
        CoinGainLogRepository.init();
        try {
            new DailyFishingRepository().init();
        } catch (java.sql.SQLException e) {
            log.error("每日钓鱼记录表初始化失败", e);
        }
        FeedbackRepository.init();
        try {
            ReminderService.getInstance().init();
            ReminderService.getInstance().start(getScheduler());
        } catch (Exception e) {
            log.warn("提醒功能初始化失败: errorType={}", e.getClass().getSimpleName());
        }
//        ErrorReportRepository.init();
        OfficialSendLogRepository.init();
        EventLogRepository.init();
        ModerationLogRepository.init();
        ImageSourceRepository.init();
        PendingNoticeRepository.init();
        UnifiedAuthentication.init();
        PackVersion.initAsync();

        TaskSchedulerRegistry.registerAll(AtriProvider.getInstance().getTaskScheduler());

        String webhookPath = settings.getGithubWebhookPath();
        String webhookSecret = settings.getGithubWebhookSecret();

        if (CoreConfig.getInstance().getSettings().isNapcatEnabled()) {
            GroupConfigManager.refreshAllConfigs();
            FriendList.updateFriendList();
            SetProjectInfo.setInfo();
            GithubCommitNotify.register(AtriProvider.getInstance().getServer(), webhookPath, webhookSecret);

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
            GroupConfigManager.registerFeature("netease_music", false);
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
                MinecraftRemote.connect("atri", mcIp, mcPort, CoreConfig.getInstance().getSettings().getNapcatDebugGroupUin(), pubKey);
            }
        } catch (Exception e) {
            log.error("MinecraftVerify 初始化失败: {}", e.getMessage());
        }

        pluginManager.loadPlugins();
        start();
        log.info("HTTP 服务器已在端口 {} 上启动", getServer().port());
        BotRuntimeData.callStartUp();
    }

    public void onDisable() {
        if (!disabled.compareAndSet(false, true)) {
            return;
        }

        close();
        miniappSessions.close();
        soundCommand.close();
        groupProfileRefreshBatcher.close();
        pluginManager.close();
        MinecraftRemote.disconnect();
        HypixelRewardCommand.shutdown();
        if (imap != null) {
            imap.close();
            imap = null;
        }
        LoginService.getInstance().close();
        tencentChannelCliClient.close();
        ThreadManager.shutdown();
        System.out.println("==== AtriBot Disabled ====");
    }


    public static void main(String[] args) {
        Atri bot = new Atri();
        try {
            bot.onEnable();
        } catch (RuntimeException | Error failure) {
            try {
                bot.onDisable();
            } catch (RuntimeException | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }


        ConsoleManager.start();
    }
}
