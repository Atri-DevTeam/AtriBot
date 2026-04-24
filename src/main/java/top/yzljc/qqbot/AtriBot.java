package top.yzljc.qqbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import lombok.Getter;
import top.yzljc.qqbot.service.clock.RunScheduleTask;
import top.yzljc.qqbot.functions.GroupContentRecord;
import top.yzljc.qqbot.service.scheduler.Scheduler;
import top.yzljc.qqbot.service.userinfo.GetFriendList;
import top.yzljc.qqbot.functions.GroupMessageCheck;
import top.yzljc.qqbot.command.CommandManager;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupModeManager;
import top.yzljc.qqbot.event.EventManager;
import top.yzljc.qqbot.feature.*;
import top.yzljc.qqbot.feature.github.WebhookServer;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.functions.minecraftnews.MinecraftNews;
import top.yzljc.qqbot.command.impl.Reboot;
import top.yzljc.qqbot.command.impl.RollbackMessages;
import top.yzljc.qqbot.command.impl.SearchRelevant;
import top.yzljc.qqbot.config.Reload;
import top.yzljc.qqbot.config.groups.GroupConfigInfo;
import top.yzljc.qqbot.debug.Broadcast;
import top.yzljc.qqbot.service.tools.RM;
import top.yzljc.qqbot.feature.minecraft.BedwarsChallenge;
import top.yzljc.qqbot.feature.minecraft.HypixelReward;
import top.yzljc.qqbot.feature.minecraft.MojangStatus;
import top.yzljc.qqbot.feature.minecraft.Motd;
import top.yzljc.qqbot.feature.minecraft.specificserver.Verify;
import top.yzljc.qqbot.feature.schedule.*;
import top.yzljc.qqbot.functions.*;
import top.yzljc.qqbot.functions.Repeater;
import top.yzljc.qqbot.utils.AtriHelp;
import top.yzljc.qqbot.utils.BotRuntimeData;
import top.yzljc.qqbot.utils.Logger;
import top.yzljc.qqbot.utils.draft.AutoLikeCommand;
import top.yzljc.qqbot.socket.MinecraftVerify;
import top.yzljc.qqbot.socket.SocketManager;
import top.yzljc.qqbot.utils.SetProjectInfo;
import top.yzljc.qqbot.utils.draft.Scratch;

import java.util.Collections;
import java.util.Scanner;

@SpringBootApplication
public class AtriBot {
    @Getter
    private static MinecraftVerify minecraftVerify;
    @Getter
    private static AtriBot instance;
    @Getter
    private Scheduler scheduler;

    public AtriBot() {
        if (instance != null) {
            throw new RuntimeException("AtriBot 已在运行");
        }
        instance = this;
        Runtime.getRuntime().addShutdownHook(new Thread(this::onDisable));
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
        CommandManager.getCommand("stats").setExecutor(new MessageStats());
        CommandManager.getCommand("groupinfo").setExecutor(new GroupConfigInfo());
        CommandManager.getCommand("calendar").setExecutor(new Calendar());
        CommandManager.getCommand("atrihelp").setExecutor(new AtriHelp());
        CommandManager.getCommand("emj").setExecutor(new AnnoyUser());
        CommandManager.getCommand("reload").setExecutor(new Reload());
        CommandManager.getCommand("autolike").setExecutor(new AutoLikeCommand());
        CommandManager.getCommand("tufe").setExecutor(new TufeClassAlert());
        CommandManager.getCommand("verify").setExecutor(new Verify());

        this.scheduler = new Scheduler();
        try {
            BotRuntimeData.init();
        } catch (Exception e) {
            Logger.error("BotRuntimeData 初始化失败: {}", e.getMessage());
        }

        System.setProperty("java.awt.headless", "true");
        System.out.println("==== AtriBot ====");

        Settings settings = Config.getInstance();

        int socketPort = settings.getListenPort();
        int qqBotPort = settings.getQqBotPort();
        int webhookPort = settings.getGithubWebhookPort();
        String webhookSecret = settings.getGithubWebhookSecret();

        ServerRcon.loadAdminConfig();

        RunScheduleTask.runAllTasks();

        GroupConfigManager.refreshAllConfigs();
        GetFriendList.updateFriendList();

        MinecraftNews.loadHistory();
        HypixelNews.loadHistory();

        // RequestReceiver is now controlled by Spring Boot Controller

        SocketManager.loadConfig();
        SocketManager.start(socketPort);
        WebhookServer.start(webhookPort, webhookSecret);

        // 同步commit信息
        SetProjectInfo.setInfo();

        // 群功能开关及默认值
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

        try {
            String mcIp = settings.getVarietyHost();
            int mcPort = settings.getVarietyPort();
            String pubKey = settings.getVarietyKey();

            minecraftVerify = new MinecraftVerify(mcIp, mcPort, pubKey);
        } catch (Exception e) {
            Logger.error("MinecraftVerify 初始化失败: {}", e.getMessage());
        }
    }

    public void onDisable() {
        scheduler.cancelTask(BotRuntimeData.getTask());
        System.out.println("====== ATRI IS SHUTTING DOWN ======");
        System.out.println("==== AtriBot Disabled ====");
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AtriBot.class);

        // Define port pragmatically from config
        int qqBotPort = Config.getInstance().getQqBotPort();
        app.setDefaultProperties(Collections.singletonMap("server.port", qqBotPort));

        ApplicationContext context = app.run(args);

        AtriBot bot = context.getBean(AtriBot.class);
        bot.onEnable();

        new Thread(() -> {
            java.util.Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if ("stop".equalsIgnoreCase(line.trim())) {
                    System.out.println("正在关闭 AtriBot...");
                    SpringApplication.exit(context, () -> 0);
                    System.exit(0);
                    break;
                }
            }
        }, "Console-Listener").start();
    }
}
