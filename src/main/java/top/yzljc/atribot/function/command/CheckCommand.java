package top.yzljc.atribot.function.command;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.function.tasks.HypixelAlphaForums;
import top.yzljc.atribot.function.tasks.HypixelAnnouncements;
import top.yzljc.atribot.function.tasks.MinecraftNews;
import top.yzljc.atribot.function.tasks.SkyblockAnnouncements;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.service.runtime.ThreadManager;

/**
 * @Author YZ_Ljc_
 * @ClassName CheckCommand
 * @Created_at 2026/10/27
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public final class CheckCommand implements CommandExecutor {

    private static final String LIST = """
            检查对象列表
            /check mc — Minecraft 新闻
            /check mojira — Mojira 漏洞追踪
            /check hyp — Hypixel 公告
            /check hyp-skyblock — SkyBlock 公告
            /check hyp-alpha — Alpha 公告与 SkyBlock 版本
            /check skbpack — SkyBlock 资源包
            """.strip();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender user)) return true;
        if (!user.hasPermission()) {
            user.sendMessage("你是谁？");
            return true;
        }
        if (args.length == 0 || (args.length == 1 && args[0].equals("-l"))) {
            user.sendMessage(LIST);
            return true;
        }
        if (args.length != 1) {
            user.sendMessage("用法：/check <对象>，使用 /check -l 查看列表");
            return true;
        }
        switch (args[0]) {
            case "mc" -> {
                ThreadManager.execute(() -> MinecraftNews.checkNews(true));
                user.sendMessage("正在检查 Minecraft 最新资讯...");
            }
            case "mojira" -> {
                Atri.getInstance().getCheckMojira().run();
                user.sendMessage("已请求检查 Mojira 最新动态");
            }
            case "hyp" -> replyResult(user, HypixelAnnouncements.feedAnnouncements(), "Hypixel 公告");
            case "hyp-skyblock" -> replyResult(user, SkyblockAnnouncements.feedAnnouncements(), "SkyBlock 公告");
            case "hyp-alpha" -> replyResult(user, HypixelAlphaForums.feedAnnouncements(), "Alpha 公告与 SkyBlock 版本");
            case "skbpack" -> {
                var result = Atri.getInstance().getSkyblockPackCheck().checkAndPush();
                user.sendMessage(switch (result) {
                    case UPDATED -> "SkyBlock 资源包更新已提交推送。";
                    case UNCHANGED -> "SkyBlock 资源包暂无更新。";
                    case FAILED -> "SkyBlock 资源包检查或图片生成失败，请查看日志。";
                });
            }
            default -> user.sendMessage("未知检查对象，使用 /check -l 查看列表");
        }
        return true;
    }

    private static void replyResult(QQCommandSender user, boolean updated, String name) {
        user.sendMessage(updated ? name + "更新已提交推送" : name + "本次没有可推送的更新");
    }
}
