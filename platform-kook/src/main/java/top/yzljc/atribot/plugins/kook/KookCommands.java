package top.yzljc.atribot.plugins.kook;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.kook.KookCard;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.command.BanTrackCommand;
import top.yzljc.atribot.function.command.PingCommand;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.*;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;

/**
 * @Author YZ_Ljc_
 * @ClassName KookCommands
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugins.kook
 */
public final class KookCommands implements CommandExecutor {
    private final KookHypixelCommand hypixel = new KookHypixelCommand();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof KookCommandSender kook)) return true;
        return switch (command.getName()) {
            case "help" -> help(kook);
            case "hyp" -> hypixel.onCommand(kook, command, label, args);
            case "hypstatus", "wz", "zs" -> {
                String[] query = new String[args.length + 1];
                query[0] = command.getName().equals("hypstatus") ? "gs" : command.getName();
                System.arraycopy(args, 0, query, 1, args.length);
                yield hypixel.onCommand(kook, command, "hyp", query);
            }
            case "mctool" -> tools(kook, args);
            case "mccape" -> image(kook, "bot.minecraft.capes", Map.of(), null, false);
            case "mcv" -> version(kook);
            case "skbpack" -> sendPack(kook);
            case "newyear" -> image(kook, "bot.happy-new-year", Map.of(), null, false);
            case "hitokoto" -> image(kook, "bot.hitokoto", Map.of(), null, false);
            case "today" -> image(kook, "bot.calendar", Map.of("system", false),
                    "现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis()), false);
            case "mojang" -> image(kook, "bot.minecraft.status", Map.of(), null, true);
            case "bantrack" -> banTrack(kook, args);
            case "whoami" -> whoami(kook);
            case "ping" -> new PingCommand().onCommand(kook, command, label, args);
            default -> false;
        };
    }

    private static boolean tools(KookCommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendCard(new KookCard().header("Minecraft 工具").markdown("""
                    `/mctool ver` 查询最新的MC版本
                    `/mctool cape` 查询MC披风拥有情况
                    `/mctool pf <玩家>` 查询MC玩家档案
                    `/mctool lb <玩家>` 查询玩家Location Bar颜色
                    `/mctool pack <版本>` 查询MC资源包版本信息
                    """));
            return true;
        }
        String[] parameters = Arrays.copyOfRange(args, 1, args.length);
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "ver" -> version(sender);
            case "cape", "capes" -> image(sender, "bot.minecraft.capes", Map.of(), null, false);
            case "pf" -> {
                if (parameters.length == 0 || parameters[0].isBlank()) sender.sendMessage("请指定 Minecraft 玩家名或 UUID。用法: /mctool pf <玩家名或UUID>");
                else image(sender, "bot.minecraft.profile", Map.of("player", parameters[0].trim()), null, true);
                yield true;
            }
            case "lb" -> handleLocationBar(sender, parameters);
            case "pack" -> {
                if (parameters.length == 0) sender.sendMessage("用法: /mctool pack <版本>\n示例: /mctool pack 1.21.2");
                else sender.sendKMarkdown(McPackMetaImpl.describe(parameters[0], false));
                yield true;
            }
            default -> { sender.sendMessage("未知的子命令，请使用 /mctool 查看可用的子命令列表"); yield true; }
        };
    }

    private static boolean version(KookCommandSender sender) {
        var versions = McVersionImpl.checkCurrentVersion();
        sender.sendKMarkdown("**Minecraft 最新版本**\n正式版：" + formatVersion(versions.get("release"))
                + "\n快照版：" + formatVersion(versions.get("snapshot")));
        return true;
    }

    private static String formatVersion(McVersionImpl.VersionInfo version) {
        return version == null ? "未知" : version.id() + "\n发布于 " + FormatTools.formatIsoTime(version.releaseTime()).trim();
    }

    static boolean sendPack(KookCommandSender sender) {
        var result = SkyblockPackCheckImpl.query(sender.getPlatform());
        sendImage(sender, result.image(), "上一次的更新时间为 " + FormatTools.formatTimestampMilli(result.lastUpdated()));
        return true;
    }

    private static boolean banTrack(KookCommandSender sender, String[] args) {
        if (BanTrackCommand.isCoolingDown(sender)) return true;
        String window = args.length == 0 ? "30min" : args[0].toLowerCase(Locale.ROOT);
        if (args.length > 1 || !BanTrackCommand.isValidType(window)) {
            sender.sendMessage("用法: /bantrack [10min 至 3mo 之间的时间范围]");
            return true;
        }
        var result = PreImageGenerate.dump(OpenApi.get("bot.hypixel.bantracker") + "?" + System.currentTimeMillis(),
                Map.of("window", window), sender.getPlatform());
        sendImage(sender, result, "当前查询范围: " + window);
        return true;
    }

    private static boolean whoami(KookCommandSender kook) {
        String scene = kook.getPlatform() == Platform.KOOK_CHANNEL
                ? "服务器 ID: " + kook.getGuildId() + "\n频道 ID: " + kook.getChannelId() : "场景: KOOK 私信";
        kook.sendMessage("当前用户信息\n用户 ID: " + kook.getUserId() + "\n" + scene + "\n机器人管理员: " + kook.hasPermission());
        return true;
    }

    private static boolean image(KookCommandSender sender, String key, Map<String, ?> body, String text, boolean progress) {
        Supplier<ImageDTO> request = () -> PreImageGenerate.dump(OpenApi.get(key), body, sender.getPlatform());
        var result = progress ? withProgress(sender, "正在查询相关数据，请稍等片刻...", request) : request.get();
        sendImage(sender, result, text);
        return true;
    }

    private static void sendImage(KookCommandSender sender, ImageDTO result, String text) {
        if (result == null || result.url() == null || result.isError()) {
            sender.sendMessage(result != null && result.isError() ? result.errorMessage() : "图片地址无效，请稍后重试。");
        } else sender.sendMessage(ImageComponent.imageOf(result.url()).setText(text));
    }

    static <T> T withProgress(KookCommandSender sender, String message, Supplier<T> query) {
        String id = sender.sendMessage(message);
        try { return query.get(); }
        finally { if (id != null && !id.isBlank()) sender.recall(id); }
    }

    private static boolean help(KookCommandSender kook) {
            String prefix = Config.getInstance().getCommandPrefix();
            kook.sendCard(new KookCard().header("KOOK 指令帮助")
                    .markdown("**Hypixel**\n"
                            + "`" + prefix + "hyp` 综合查询菜单\n"
                            + "`" + prefix + "hyp skb` SkyBlock 菜单\n"
                            + "`" + prefix + "bantrack [时间范围]` 封禁统计\n"
                            + "`" + prefix + "hypstatus` 服务器状态\n"
                            + "`" + prefix + "wz <玩家>` 法师决战\n"
                            + "`" + prefix + "zs <玩家>` 僵尸末日\n"
                            + "`" + prefix + "skbpack` SkyBlock 资源包")
                    .markdown("**Minecraft**\n"
                            + "`" + prefix + "mctool` 工具菜单\n"
                            + "`" + prefix + "mcv` 最新版本\n"
                            + "`" + prefix + "mccape` 披风状态\n"
                            + "`" + prefix + "mojang` 验证服务器状态")
                    .markdown("**其他**\n"
                            + "`" + prefix + "today` 今日日历\n"
                            + "`" + prefix + "newyear` 新年倒计时\n"
                            + "`" + prefix + "hitokoto` 一言\n"
                            + "`" + prefix + "whoami` 用户与会话信息\n"
                            + "`" + prefix + "ping` 运行状态")
                    .context("玩家查询需显式填写玩家名或 UUID"));
        return true;
    }

    private static boolean handleLocationBar(CommandSender user, String[] args) {
        if (args.length == 0) {
            user.sendMessage("笨蛋喵，你没有输入玩家名或UUID，怎么查？");
            return true;
        }
        var var1 = args[0];
        if (var1.length() > 16 && var1.length() != 32 && var1.length() != 36) {
            user.sendMessage("笨蛋喵，你输入的玩家名或UUID不合法，请检查输入是否正确。");
            return true;
        }
        var d = FetchMinecraftProfile.find(var1);
        UUID uuidOffline;

        if (var1.length() == 36) {
            try {
                uuidOffline = UUID.fromString(var1);
            } catch (Exception _) {
                user.sendMessage("在执行查询时出现错误：UUID不合法，请检查输入是否正确。");
                return true;
            }
        } else {
            uuidOffline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + var1).getBytes(StandardCharsets.UTF_8));
        }

        int colorOnline = 0;
        String hexColorOnline = null;
        if (d != null) {
            colorOnline = McLocationBarColorImpl.getPlayerRGBColor(d.uuid());
            hexColorOnline = String.format("#%06X", colorOnline & 0xFFFFFF);
        }
        int colorOffline = McLocationBarColorImpl.getPlayerRGBColor(uuidOffline);
        String hexColorOffline = String.format("#%06X", colorOffline & 0xFFFFFF);

        if (user instanceof KookCommandSender kook) {
            kook.sendKMarkdown("**Location Bar 颜色**\n离线 UUID：`" + uuidOffline
                    + "`\n离线颜色：`" + hexColorOffline + "`\n正版 UUID：`" + (d == null ? "无效" : d.uuid())
                    + "`\n正版颜色：`" + (d == null ? "无效" : hexColorOnline) + "`");
            return true;
        }

        return true;
    }
}
