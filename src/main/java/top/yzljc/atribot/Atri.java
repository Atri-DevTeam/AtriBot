package top.yzljc.atribot;

import cn.dev33.satoken.sign.SaSignManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.plugin.bundled.CorsPluginConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.napcat.FriendList;
import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.ErrorReportRepository;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;
import top.yzljc.atribot.function.official.imagesource.ImageSourceStatsCommand;
import top.yzljc.atribot.function.official.imagesource.ImageSubmitCommand;
import top.yzljc.atribot.function.official.minecraft.*;
import top.yzljc.atribot.platform.official.OfficialBot;
import top.yzljc.atribot.utils.notify.PendingNoticeDispatcher;
import top.yzljc.atribot.database.repo.SignRepository;
import top.yzljc.atribot.database.repo.TufeElecRepository;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.function.general.*;
import top.yzljc.atribot.function.napcat.*;
import top.yzljc.atribot.function.napcat.GithubCommitNotify;
import top.yzljc.atribot.function.napcat.like.AutoLikeCommand;
import top.yzljc.atribot.function.napcat.like.CardLike;
import top.yzljc.atribot.function.napcat.personal.*;
import top.yzljc.atribot.function.official.*;
import top.yzljc.atribot.function.official.tufe.ElectricCheck;
import top.yzljc.atribot.function.task.*;
import top.yzljc.atribot.function.task.Calendar;
import top.yzljc.atribot.platform.napcat.RequestReceiver;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigInfo;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupModeManager;
import top.yzljc.atribot.platform.discord.DiscordEvents;
import top.yzljc.atribot.platform.discord.DiscordManager;
import top.yzljc.atribot.platform.official.OfficialManager;
import top.yzljc.atribot.platform.official.TokenManager;
import top.yzljc.atribot.service.ai.AiService;
import top.yzljc.atribot.service.Scheduler;
import top.yzljc.atribot.service.email.IMAP;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.service.timer.RunScheduleTask;
import top.yzljc.atribot.service.taskscheduler.TaskScheduler;
import top.yzljc.atribot.service.taskscheduler.TaskSchedulerRegistry;
import top.yzljc.atribot.test.MinecraftNewsDebug;
import top.yzljc.atribot.test.Test;
import top.yzljc.atribot.test.YunLandSpecialCommand;
import top.yzljc.atribot.utils.debug.DebugCommand;
import top.yzljc.atribot.utils.update.UpdatePushCommand;
import top.yzljc.atribot.utils.socket.MinecraftSocket;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;
import top.yzljc.atribot.utils.tools.RM;
import top.yzljc.atribot.webui.impl.WebUIRouter;
import top.yzljc.atribot.webui.impl.WebUISessionManager;

import java.util.Scanner;
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
    private final Reboot reboot;
    @Getter
    private final MinecraftVersionChecker minecraftVersionCheck;
    @Getter
    private final MinecraftNews minecraftNews;
    @Getter
    private final HypixelAnnouncements hypixelAnnouncements;
    @Getter
    private final HypixelAlphaForums hypixelAlphaForums;
    @Getter
    private final SkyblockResourcePackChecker skyblockResourcePackChecker;
    private final DiscordManager discordManager;
    private final Javalin server;
    private final OfficialManager qqBotManagerService;
    private IMAP imap;
    @Getter
    public static final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean disabled = new AtomicBoolean(false);

    public Atri() {
        if (instance != null) {
            throw new RuntimeException("AtriBot 已在运行");
        }
        instance = this;
        Config config = Config.getInstance();
        this.aiService = new AiService(config.getAiPropertiesMap(), objectMapper);
        this.tokenManager = new TokenManager(config.getQqAppId(), config.getQqClientSecret());
        this.qqBotManagerService = new OfficialManager(config.getQqApiBaseUrl(), tokenManager);
        this.chatService = new ChatService(config.getQqApiBaseUrl(), tokenManager);
        this.checkMojira = new MojiraStatus();
        this.cardLike = new CardLike();
        this.reboot = new Reboot();
        this.minecraftVersionCheck = new MinecraftVersionChecker();
        this.minecraftNews = new MinecraftNews();
        this.hypixelAnnouncements = new HypixelAnnouncements();
        this.hypixelAlphaForums = new HypixelAlphaForums();
        this.skyblockResourcePackChecker = new SkyblockResourcePackChecker();
        this.discordManager = config.isDiscordEnabled() && config.getDiscordBotToken() != null && !config.getDiscordBotToken().isBlank()
                ? new DiscordManager(config.getDiscordApiBaseUrl(), config.getDiscordBotToken(), config.getDiscordIntents())
                : null;

        int qqBotPort = config.getListenPort();

        OfficialBot.fetchBotInfo();

        server = Javalin.create(cfg -> {
            cfg.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost));
            cfg.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/webui";
                staticFiles.directory = "/official-webui";
                staticFiles.location = Location.CLASSPATH;
            });
            cfg.jetty.modifyHttpConfiguration(http -> http.addCustomizer(
                    new org.eclipse.jetty.server.ForwardedRequestCustomizer()
            ));
            cfg.http.maxRequestSize = 10_000_000;
        }).start(qqBotPort);

        server.before("/webui/*", ctx -> {
            log.info("{} {} from {}", ctx.method(), ctx.fullUrl(), ctx.ip());
        });

        server.post("/", ctx -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            String result = RequestReceiver.handle(body);
            ctx.result(result).contentType("application/json");
        });

        WebUIRouter.register(server);

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
        EventManager.getInstance().registerEvents(this.cardLike);
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
        EventManager.getInstance().registerEvents(new AtriChat());
        EventManager.getInstance().registerEvents(new BotRuntimeData());
        EventManager.getInstance().registerEvents(new Test());
        EventManager.getInstance().registerEvents(new VerifyMinecraftCommand());
        EventManager.getInstance().registerEvents(new EventRecord());
        EventManager.getInstance().registerEvents(new DiscordEvents());
        EventManager.getInstance().registerEvents(new ChatContentRecord());
        EventManager.getInstance().registerEvents(new Feedback());
        EventManager.getInstance().registerEvents(new AutoSendPtt());
        EventManager.getInstance().registerEvents(new WebUICommand());
        EventManager.getInstance().registerEvents(new FullMessageEnableCommand());
        EventManager.getInstance().registerEvents(new ConnectFourGame());
        RockPaperScissorsGame rockPaperScissorsGame = new RockPaperScissorsGame();
        EventManager.getInstance().registerEvents(rockPaperScissorsGame);
        EventManager.getInstance().registerEvents(new SignCommand());
        UpdatePushCommand updatePushCommand = new UpdatePushCommand();
        EventManager.getInstance().registerEvents(updatePushCommand);
        EventManager.getInstance().registerEvents(new EmailNotify());
        EventManager.getInstance().registerEvents(new PendingNoticeDispatcher());

        CommandManager.reload();
        CommandManager.getCommand("newyear").setExecutor(new HappyNewYear());
        CommandManager.getCommand("bc").setExecutor(new Broadcast());
        CommandManager.getCommand("anan").setExecutor(AnAnGirlEmoji.INSTANCE);
        CommandManager.getCommand("reboot").setExecutor(this.reboot);
        CommandManager.getCommand("search").setExecutor(new SearchRelevant());
        CommandManager.getCommand("recall").setExecutor(new RM());
        CommandManager.getCommand("rollback").setExecutor(new RollbackMessages());
        CommandManager.getCommand("gt").setExecutor(CucumberGirl.INSTANCE);
        CommandManager.getCommand("mojang").setExecutor(new MojangStatus());
        CommandManager.getCommand("cl").setExecutor(new HypixelReward());
        CommandManager.getCommand("check-mc").setExecutor(this.minecraftNews);
        CommandManager.getCommand("manodate").setExecutor(new ManosabaDate());
        CommandManager.getCommand("github").setExecutor(new GithubCommitNotify());
        CommandManager.getCommand("signall").setExecutor(new AutoSign());
        CommandManager.getCommand("chat").setExecutor(new MessageStats());
        CommandManager.getCommand("groupinfo").setExecutor(new GroupConfigInfo());
        CommandManager.getCommand("calendar").setExecutor(new Calendar());
        CommandManager.getCommand("check-mojira").setExecutor(this.checkMojira);
        CommandManager.getCommand("emj").setExecutor(new AnnoyUser());
        CommandManager.getCommand("py").setExecutor(PinYin.INSTANCE);
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
        CommandManager.getCommand("perm").setExecutor(new UserMgrCommand());
        CommandManager.getCommand("today").setExecutor(new top.yzljc.atribot.function.general.Calendar());
        CommandManager.getCommand("help").setExecutor(new HelpCommand());
        CommandManager.getCommand("minesweeper").setExecutor(new MinesweeperGame());
        CommandManager.getCommand("connect4").setExecutor(new ConnectFourGame());
        CommandManager.getCommand("hitokoto").setExecutor(new Hitokoto());
        CommandManager.getCommand("贡献名单").setExecutor(new SponsorCommand());
        CommandManager.getCommand("webui").setExecutor(new WebUICommand());
        CommandManager.getCommand("全量消息").setExecutor(new FullMessageEnableCommand());
        CommandManager.getCommand("推送任务").setExecutor(new PushTaskCommand());
        CommandManager.getCommand("四子棋").setExecutor(new ConnectFourGame());
        CommandManager.getCommand("rsp").setExecutor(rockPaperScissorsGame);

        CommandManager.getCommand("elec").setExecutor(new ElectricCheck());
        CommandManager.getCommand("打卡").setExecutor(new SignCommand());
        CommandManager.getCommand("debug").setExecutor(new DebugCommand());
        CommandManager.getCommand("check-hyp").setExecutor(this.hypixelAnnouncements);
        CommandManager.getCommand("check-hyp-alpha").setExecutor(this.hypixelAlphaForums);
        CommandManager.getCommand("games").setExecutor(new MiniGameCommand());
        CommandManager.getCommand("music").setExecutor(new MusicCommand());
        CommandManager.getCommand("ping").setExecutor(new PingCommand());
        CommandManager.getCommand("boop").setExecutor(BoopCommand.INSTANCE);
        CommandManager.getCommand("update").setExecutor(updatePushCommand);
        CommandManager.getCommand("spc").setExecutor(new YunLandSpecialCommand());
        CommandManager.getCommand("golds").setExecutor(new CoinsCommand());
        CommandManager.getCommand("hypstatus").setExecutor(new HypixelStatus());
        CommandManager.getCommand("投稿").setExecutor(new ImageSubmitCommand());
        CommandManager.getCommand("图源").setExecutor(new ImageSourceStatsCommand());
        CommandManager.getCommand("pause").setExecutor(new AdminPauseCommand());
        CommandManager.getCommand("地球online").setExecutor(new EarthOnline());

        // ----------- DEBUG COMMANDS -----------
        CommandManager.getCommand("test-mcnews").setExecutor(new MinecraftNewsDebug());

        this.scheduler = new Scheduler();
        try {
            BotRuntimeData.init();
        } catch (Exception e) {
            log.error("BotRuntimeData 初始化失败: {}", e.getMessage());
        }

        System.setProperty("java.awt.headless", "true");
        System.out.println("==== AtriBot ====");

        Config settings = Config.getInstance();
        ChatContentRecord.init();
        MinecraftNews.loadHistory();

        MinecraftBind.init();
        OfficialGroups.init();
        OfficialUsers.init();
        TufeElecRepository.init();
        SignRepository.init();
        FeedbackRepository.init();
        ErrorReportRepository.init();
        OfficialSendLogRepository.init();
        ImageSourceRepository.init();
        PendingNoticeRepository.init();
        PackVersion.init();

        RunScheduleTask.runAllTasks();
        this.taskScheduler = new TaskScheduler();
        TaskSchedulerRegistry.registerAll(taskScheduler);

        int webhookPort = settings.getGithubWebhookPort();
        String webhookSecret = settings.getGithubWebhookSecret();

        if (settings.isNapcatEnabled()) {
            GroupConfigManager.refreshAllConfigs();
            FriendList.updateFriendList();
            SetProjectInfo.setInfo();
            GithubCommitNotify.start(webhookPort, webhookSecret);

            GroupConfigManager.registerFeature("auto_sign", true);
            GroupConfigManager.registerFeature("mc_news", false);
            GroupConfigManager.registerFeature("hyp_news", false);
            GroupConfigManager.registerFeature("hyp_alpha_news", false);
            GroupConfigManager.registerFeature("annoy_user", true);
            GroupConfigManager.registerFeature("new_year", true);
            GroupConfigManager.registerFeature("one_text", true);
            GroupConfigManager.registerFeature("repeat_msg", false);
            GroupConfigManager.registerFeature("send_poke", true);
            GroupConfigManager.registerFeature("like_user", false);
            GroupConfigManager.registerFeature("mojang_status", true);
            GroupConfigManager.registerFeature("hypixel_status", true);
            GroupConfigManager.registerFeature("github_info", false);
            GroupConfigManager.registerFeature("bv_check", false);
            GroupConfigManager.registerFeature("mojira_tracker", false);
            GroupConfigManager.registerFeature("broadcast", true);
            GroupConfigManager.registerFeature("calendar", true);
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
        GithubCommitNotify.stop();
        HypixelReward.shutdown();
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
