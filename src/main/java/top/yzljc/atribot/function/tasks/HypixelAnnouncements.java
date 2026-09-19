package top.yzljc.atribot.function.tasks;

import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.service.taskscheduler.*;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelAnnouncements
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
public final class HypixelAnnouncements implements CommandExecutor, ScheduledTask {
    private static final HypixelAnnouncementFeed FEED = new HypixelAnnouncementFeed(
            "https://hypixel.net/forums/news-and-announcements.4/index.rss",
            "Hypixel", Properties.HYPIXEL_ANNOUNCEMENTS);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender) && !(sender instanceof ConsoleCommandSender)) return true;
        if (!feedAnnouncements()) sender.sendMessage("暂无新的 Hypixel 公告");
        return true;
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.hourly);
    }

    @Override
    public void run() {
        feedAnnouncements();
    }

    public static boolean feedAnnouncements() {
        return HypixelAnnouncementFeed.pushAnnouncements(checkForNewAnnouncements(), "hyp_news");
    }

    public static List<Announcement> checkForNewAnnouncements() {
        return FEED.checkForNewAnnouncements();
    }

    /**
     * @param title       标题
     * @param author      作者
     * @param publishTime 发布时间
     * @param link        原始链接
     * @param guid        RSS 唯一标识
     * @param headerImage 头图
     * @param intro       简介
     * @param source      来源（如 Hypixel、Hypixel Skyblock）
     */
    public record Announcement(
            String title,
            String author,
            String publishTime,
            String link,
            String guid,
            String headerImage,
            String intro,
            String source
    ) {
    }
}
