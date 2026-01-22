package top.yzljc.qqbot;

import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.feature.HappyNewYear;
import top.yzljc.qqbot.feature.github.WebhookServer;
import top.yzljc.qqbot.utils.MessageStats;
import top.yzljc.qqbot.feature.ManosabaDate;
import top.yzljc.qqbot.botkits.message.MessageProcessor;
import top.yzljc.qqbot.botkits.message.MessageReceiver;
import top.yzljc.qqbot.feature.minecraft.SendCommand;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.socket.SocketManager;
import top.yzljc.qqbot.feature.AutoSign;
import top.yzljc.qqbot.web.WebDashboardAPI;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        System.out.println("==== YZ_Ljc_ QQ Bot Edition ====");

        Settings settings = Config.getInstance();

        int socketPort = settings.getListenPort();
        int qqBotPort = settings.getQqBotPort();
        int webhookPort = settings.getGithubWebhookPort();
        int webDashboardPort = settings.getWebDashboardPort();
        String webhookSecret = settings.getGithubWebhookSecret();

        // 初始化配置与权限
        SendCommand.loadAdminConfig();
        // 启动定时重新加载权限配置的任务 (每60秒)
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                SendCommand::loadAdminConfig, 60, 60, TimeUnit.SECONDS
        );

        GroupConfigManager.refreshAllConfigs();

        AutoSign.startScheduler();
        MinecraftNews.startScheduler();
        ManosabaDate.startAutoDailyTask();
        HypixelNews.startScheduler();
        MessageStats.startDailyReportScheduler();
        HappyNewYear.startAutoDailyTask();

        MessageReceiver.start(qqBotPort, MessageProcessor::processMessage);

        SocketManager.loadConfig();
        SocketManager.start(socketPort);
        WebhookServer.start(webhookPort, webhookSecret);
        WebDashboardAPI.start(webDashboardPort);

        // 群功能开关及默认值
        GroupConfigManager.registerFeature("auto_sign", true);     // 自动签到
        GroupConfigManager.registerFeature("mc_news", true);        // MC新闻
        GroupConfigManager.registerFeature("hyp_news", true);       // Hypixel新闻
        GroupConfigManager.registerFeature("electric_check", false);      // 电费查询
        GroupConfigManager.registerFeature("annoy_user", true);    // 骚扰功能
        GroupConfigManager.registerFeature("new_year",true);
        GroupConfigManager.registerFeature("one_text",true);
        GroupConfigManager.registerFeature("repeat_msg", true); // 复读机
        GroupConfigManager.registerFeature("send_poke",true);
        GroupConfigManager.registerFeature("like_user", true);
        GroupConfigManager.registerFeature("mojang_status", true);
        GroupConfigManager.registerFeature("github_info",false);
    }
}
