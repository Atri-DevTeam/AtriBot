package top.yzljc.qqbot;

import top.yzljc.qqbot.botkits.clock.RunScheduleTask;
import top.yzljc.qqbot.botkits.findinfo.GetFriendList;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.feature.minecraft.HypixelReward;
import top.yzljc.qqbot.feature.schedule.*;
import top.yzljc.qqbot.feature.github.WebhookServer;
import top.yzljc.qqbot.botkits.request.DataProcessor;
import top.yzljc.qqbot.botkits.request.RequestReceiver;
import top.yzljc.qqbot.feature.minecraft.ServerRcon;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.socket.SocketManager;
import top.yzljc.qqbot.utils.SetProjectInfo;

public class YzLjcBot {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        System.out.println("==== YZ_Ljc_ QQ Bot Edition ====");

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

        RequestReceiver.start(qqBotPort, DataProcessor::processMessage);

        SocketManager.loadConfig();
        SocketManager.start(socketPort);
        WebhookServer.start(webhookPort, webhookSecret);

        // 同步commit信息
        SetProjectInfo.setInfo();

        // 群功能开关及默认值
        GroupConfigManager.registerFeature("auto_sign", true);
        GroupConfigManager.registerFeature("mc_news", true);
        GroupConfigManager.registerFeature("hyp_news", true);
        GroupConfigManager.registerFeature("electric_check", false);
        GroupConfigManager.registerFeature("annoy_user", true);
        GroupConfigManager.registerFeature("new_year", true);
        GroupConfigManager.registerFeature("one_text", true);
        GroupConfigManager.registerFeature("repeat_msg", true);
        GroupConfigManager.registerFeature("send_poke", true);
        GroupConfigManager.registerFeature("like_user", true);
        GroupConfigManager.registerFeature("mojang_status", true);
        GroupConfigManager.registerFeature("motd", false);
        GroupConfigManager.registerFeature("github_info", false);
        GroupConfigManager.registerFeature("bv_check", false);
        GroupConfigManager.registerFeature("wakeup_send", false);
        GroupConfigManager.registerFeature("broadcast", true);
        GroupConfigManager.registerFeature("calendar",true);
        GroupConfigManager.registerFeature("get_hypixel_reward",false);
    }
}
