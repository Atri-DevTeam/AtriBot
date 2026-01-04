package top.yzljc.qqbot;

import top.yzljc.qqbot.gordonhim.ServerStatusReport;
import top.yzljc.qqbot.utils.MessageStats;
import top.yzljc.qqbot.img.ManosabaDate;
import top.yzljc.qqbot.messages.MessageProcessor;
import top.yzljc.qqbot.messages.MessageReceiver;
import top.yzljc.qqbot.minecraft.SendCommand;
import top.yzljc.qqbot.news.HypixelNews;
import top.yzljc.qqbot.news.MinecraftNews;
import top.yzljc.qqbot.socket.SocketManager;
import top.yzljc.qqbot.utils.AutoSign;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {
    private static final int LISTEN_PORT = 37142;
    private static final int QQ_BOT_PORT = 8851;

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");

        System.out.println("==== YZ_Ljc_ QQ Bot Edition ====");

        // 初始化配置与权限
        SendCommand.loadAdminConfig();
        // 启动定时重新加载权限配置的任务 (每60秒)
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                SendCommand::loadAdminConfig, 60, 60, TimeUnit.SECONDS
        );

        // 启动各类定时任务
        AutoSign.startScheduler();
        MinecraftNews.startScheduler();
        ManosabaDate.startAutoDailyTask();
        HypixelNews.startScheduler();
        MessageStats.startDailyReportScheduler();

        MessageReceiver.start(QQ_BOT_PORT, MessageProcessor::processMessage);

        SocketManager.loadConfig();
        SocketManager.start(LISTEN_PORT);
    }
}