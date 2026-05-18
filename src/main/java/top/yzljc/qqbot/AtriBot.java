package top.yzljc.qqbot;

import cn.dev33.satoken.sign.SaSignManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.plugin.bundled.CorsPluginConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.command.CommandManager;
import top.yzljc.qqbot.command.impl.Reboot;
import top.yzljc.qqbot.command.impl.RollbackMessages;
import top.yzljc.qqbot.command.impl.SearchRelevant;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Reload;
import top.yzljc.qqbot.config.groups.GroupConfigInfo;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.groups.GroupModeManager;
import top.yzljc.qqbot.config.webui.WebUIController;
import top.yzljc.qqbot.config.webui.exception.ExceptionController;
import top.yzljc.qqbot.config.webui.exception.FeatureNotFoundException;
import top.yzljc.qqbot.debug.Broadcast;
import top.yzljc.qqbot.event.EventManager;
import top.yzljc.qqbot.feature.*;
import top.yzljc.qqbot.feature.github.WebhookServer;
import top.yzljc.qqbot.feature.minecraft.*;
import top.yzljc.qqbot.official.function.*;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.schedule.*;
import top.yzljc.qqbot.functions.*;
import top.yzljc.qqbot.functions.Repeater;
import top.yzljc.qqbot.functions.minecraftnews.MinecraftNews;
import top.yzljc.qqbot.official.impl.BindMinecraft;
import top.yzljc.qqbot.official.service.QQBotManagerService;
import top.yzljc.qqbot.official.service.QQBotMessageService;
import top.yzljc.qqbot.official.service.QQBotTokenManager;
import top.yzljc.qqbot.service.ai.AiService;
import top.yzljc.qqbot.service.clock.RunScheduleTask;
import top.yzljc.qqbot.service.request.RequestReceiver;
import top.yzljc.qqbot.service.scheduler.Scheduler;
import top.yzljc.qqbot.service.tools.RM;
import top.yzljc.qqbot.service.userinfo.GetFriendList;
import top.yzljc.qqbot.socket.MinecraftVerify;
import top.yzljc.qqbot.socket.SocketManager;
import top.yzljc.qqbot.test.Test;
import top.yzljc.qqbot.utils.AtriHelp;
import top.yzljc.qqbot.utils.BotRuntimeData;
import top.yzljc.qqbot.utils.SetProjectInfo;
import top.yzljc.qqbot.utils.draft.AutoLikeCommand;
import top.yzljc.qqbot.utils.draft.Scratch;

import java.util.Scanner;

@Slf4j
public class AtriBot {
    @Getter
    private static MinecraftVerify minecraftVerify;
    @Getter
    private static AtriBot instance;
    @Getter
    private Scheduler scheduler;
    @Getter
    private QQBotMessageService messageService;
    @Getter
    private AiService aiService;
    @Getter
    private QQBotTokenManager tokenManager;

    private final Javalin server;
    private final QQBotManagerService qqBotManagerService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AtriBot() {
        if (instance != null) {
            throw new RuntimeException("AtriBot 已在运行");
        }
        instance = this;
        Runtime.getRuntime().addShutdownHook(new Thread(this::onDisable));

        Config config = Config.getInstance();

        this.aiService = new AiService(config.getAiBotProperties(), objectMapper);
        this.tokenManager = new QQBotTokenManager(config.getQqAppId(), config.getQqClientSecret());
        this.messageService = new QQBotMessageService(config.getQqApiBaseUrl(), tokenManager);
        this.qqBotManagerService = new QQBotManagerService(config.getQqApiBaseUrl(), tokenManager);

        int qqBotPort = Config.getInstance().getQqBotPort();

        server = Javalin.create(cfg -> cfg.bundledPlugins.enableCors(cors -> cors.addRule(CorsPluginConfig.CorsRule::anyHost))).start(config.getQqBotPort());

        // Register routes
        server.post("/", ctx -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            String result = RequestReceiver.handle(body);
            ctx.result(result).contentType("application/json");
        });

        String base = "/webui/v1/atribot/settings";
        server.get(base + "/get/{groupId}", WebUIController::getGroupSettings);
        server.post(base + "/set/{groupId}", WebUIController::setGroupSetting);
        server.get(base + "/listgroups", WebUIController::listGroups);
        server.post(base + "/fetchmessages", WebUIController::fetchMessages);
        server.post(base + "/recallmessage", WebUIController::recallMessage);

        // Exception handler
        server.exception(FeatureNotFoundException.class, ExceptionController::handleFeatureNotFound);

        log.info("HTTP 服务器已在端口 {} 上启动", qqBotPort);

        SaSignManager.getConfig().setSecretKey(Config.getInstance().getSaSignSecretKey());
    }

    public void onEnable() {
        System.out.println("====== ATRI IS STARTING ======");
        EventManager.getInstance().registerEvents(new Hitokoto());
        EventManager.getInstance().registerEvents(new AutoAccept());
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
        EventManager.getInstance().registerEvents(new ServerRcon());
        EventManager.getInstance().registerEvents(new Scratch());
        EventManager.getInstance().registerEvents(new BotRuntimeData());
        EventManager.getInstance().registerEvents(new Test());
        EventManager.getInstance().registerEvents(new VerifyMinecraftCommand());

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

        CommandManager.getCommand("stats").setExecutor(new PlayerProfile());
        CommandManager.getCommand("rc").setExecutor(new RconController());
        CommandManager.getCommand("myinfo").setExecutor(new AccountInfo());
        CommandManager.getCommand("mc").setExecutor(new MinecraftUtils());
        CommandManager.getCommand("test").setExecutor(new Test());
        CommandManager.getCommand("feedback").setExecutor(new Feedback());

        this.scheduler = new Scheduler();
        try {
            BotRuntimeData.init();
        } catch (Exception e) {
            log.error("BotRuntimeData 初始化失败: {}", e.getMessage());
        }

        System.setProperty("java.awt.headless", "true");
        System.out.println("==== AtriBot ====");

        Config settings = Config.getInstance();

        int socketPort = settings.getListenPort();
        int webhookPort = settings.getGithubWebhookPort();
        String webhookSecret = settings.getGithubWebhookSecret();

        ServerRcon.loadAdminConfig();

        RunScheduleTask.runAllTasks();

        GroupConfigManager.refreshAllConfigs();
        GetFriendList.updateFriendList();

        MinecraftNews.loadHistory();
        HypixelNews.loadHistory();

        SocketManager.loadConfig();
        SocketManager.start(socketPort);
        WebhookServer.start(webhookPort, webhookSecret);

        BindMinecraft.init();

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
            String mcIp = settings.getVarietyHost();
            int mcPort = settings.getVarietyPort();
            String pubKey = settings.getVarietyKey();

            minecraftVerify = new MinecraftVerify(mcIp, mcPort, pubKey);
        } catch (Exception e) {
            log.error("MinecraftVerify 初始化失败: {}", e.getMessage());
        }

        BotRuntimeData.callStartUp();
    }

    public void onDisable() {
        scheduler.cancelTask(BotRuntimeData.getTask());
        if (server != null) {
            server.stop();
        }
        System.out.println("==== AtriBot Disabled ====");
    }

    public static void main(String[] args) {
        AtriBot bot = new AtriBot();
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