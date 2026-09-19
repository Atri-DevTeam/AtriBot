package top.yzljc.atribot.function.tasks;

import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.service.taskscheduler.*;
import top.yzljc.atribot.function.tasks.HypixelAnnouncements.Announcement;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelSkyblockAnnouncements
 * @Created_at 2026/09/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
public final class SkyblockAnnouncements implements CommandExecutor, ScheduledTask {
    private static final HypixelAnnouncementFeed FEED = new HypixelAnnouncementFeed(
            "https://hypixel.net/forums/skyblock-patch-notes.158/index.rss",
            "Hypixel SkyBlock", Properties.HYPIXEL_ANNOUNCEMENTS);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender) && !(sender instanceof ConsoleCommandSender)) return true;
        if (!feedAnnouncements()) sender.sendMessage("暂无新的 Hypixel SkyBlock 公告");
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
        return HypixelAnnouncementFeed.pushAnnouncements(checkForNewAnnouncements(), "hyp_alpha_news");
    }

    public static List<Announcement> checkForNewAnnouncements() {
        return FEED.checkForNewAnnouncements();
    }
}
