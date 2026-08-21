package top.yzljc.atribot.function.general;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.function.official.BanTracker;
import top.yzljc.atribot.function.official.minecraft.*;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftCommand
 * @Created_at 2026/05/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class MinecraftCommand implements CommandExecutor {

    private static final Markdown ValidCommands = TC.md(
            Markdown.img(ResourcesProperties.GRASS_BLOCK_IMG, 24, 24) + " **Minecraft 指令列表**\n\n" +
                    "1. " + Markdown.enterCommand("/mc dice", "/mc dice") + " - Skyblock High Class Archfiend Dice\n\n" +
                    "2. " + Markdown.enterCommand("/mc ver", "/mc ver") + " - 查看当前最新的MC版本\n\n" +
                    "3. " + Markdown.enterCommand("/bantracker", "/bantracker") + " - Hypixel BanTracker查询\n\n" +
                    "4. " + Markdown.enterCommand("/mc capes", "/mc capes") + " - Minecraft全部Capes使用情况\n\n" +
                    "5. " + Markdown.enterCommand("/mc pack ", "/mc pack [版本]") + " - 生成MC资源包版本信息\n\n" +
                    "6. " + Markdown.enterCommand("/mc sr", "/mc sr") + " - Hypixel Skyblock资源包版本信息\n\n" +
                    "7. " + Markdown.enterCommand("/mc lb", "/mc lb [玩家名/UUID]") + " - 查询玩家的Location Bar颜色\n\n"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQGuildCommandSender guildSender) {
            if (label.equals("bt") || label.equals("bantracker")) {
                return BanTracker.handle(guildSender, command, label, args);
            } else if (label.equals("mcv") || (args.length > 0 &&
                    (args[0].equalsIgnoreCase("version") || args[0].equalsIgnoreCase("ver")))) {
                MinecraftVersionChecker.onCommand(guildSender);
            } else if (label.equals("skbpack") || (args.length > 0 &&
                    (args[0].equalsIgnoreCase("sr") || args[0].equalsIgnoreCase("skb") ||
                            args[0].equalsIgnoreCase("skbresource")))) {
                return Atri.getInstance().getSkyblockResourcePackChecker()
                        .onCommand(guildSender, command, label, args);
            } else {
                guildSender.sendMessage("QQ频道当前支持 /mc ver、/mc sr 和 /bantracker 查询");
            }
            return true;
        }

        if (!(sender instanceof QQCommandSender qq)) return true;

        switch (label) {
            case "bt", "bantracker" -> {
                return BanTracker.handle(qq, command, label, args);
            }
            case "mcv" -> {
                MinecraftVersionChecker.onCommand(qq);
                return true;
            }
            case "skbpack" -> {
                return Atri.getInstance().getSkyblockResourcePackChecker().onCommand(qq, command, label, args);
            }
        }

        if (args.length < 1) {
            qq.sendMessage(ValidCommands);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equalsIgnoreCase("dice")) {
            DiceImpl.handle(qq, args);
            return true;
        }

        switch (subCommand) {
            case "version", "ver" -> {
                MinecraftVersionChecker.onCommand(qq);
                return true;
            }
            case "capes", "cape" -> {
                return MinecraftCapes.handleCapesCommand(qq);
            }
            case "pack" -> {
                PackMcmetaGenerator.handle(qq, args);
                return true;
            }
            case "sr", "skb", "skbresource" -> {
                return Atri.getInstance().getSkyblockResourcePackChecker().onCommand(qq, command, label, args);
            }
            case "lb" -> {
                if (args.length < 2) {
                    qq.sendMessage("笨蛋喵，你没有输入玩家名或UUID，怎么查？");
                    return true;
                }
                var var1 = args[1];
                if (var1.length() > 16 && var1.length() != 32 && var1.length() != 36) {
                    qq.sendMessage("笨蛋喵，你输入的玩家名或UUID不合法，请检查输入是否正确。");
                    return true;
                }
                var d = FetchMinecraftProfile.find(var1);
                UUID uuidOffline;

                if (var1.length() == 36) {
                    try {
                        uuidOffline = UUID.fromString(var1);
                    } catch (Exception _) {
                        qq.sendMessage("在执行查询时出现错误：UUID不合法，请检查输入是否正确。");
                        return true;
                    }
                } else {
                    uuidOffline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + var1).getBytes(StandardCharsets.UTF_8)
                    );
                }

                int colorOnline = 0;
                String hexColorOnline = null;
                String onlineHeadPic = null;
                if (d != null) {
                    colorOnline =  MinecraftLocationBarColor.getPlayerRGBColor(d.uuid());
                    hexColorOnline = String.format("#%06X", colorOnline & 0xFFFFFF);
                    if (MinecraftWhitelist.isNameWhitelisted(d.username())) {
                        onlineHeadPic = FetchMinecraftProfile.getPlayerHead(d.uuid().toString());
                    }
                }
                int colorOffline = MinecraftLocationBarColor.getPlayerRGBColor(uuidOffline);
                String hexColorOffline = String.format("#%06X", colorOffline & 0xFFFFFF);

                Markdown md = TC.md( (onlineHeadPic != null ? Markdown.img("pic", onlineHeadPic, 16, 16) + d.username() : "") + " **查询结果如下**\n\n" +
                        "离线UUID: `" + uuidOffline + "`\n\n" +
                        "RGB颜色代码: `" + hexColorOffline + "`\n\n" +
                        "> 参考颜色: " + "$\\textcolor{" + hexColorOffline + "}{\\text{" + "▄" + "}}$\n\n" +
                        "正版UUID: `" + (d != null ? d.uuid() : "无效") + "`\n\n" +
                        "RGB颜色代码: `" + (d != null ? hexColorOnline : "无效") + "`\n\n" +
                        "> 参考颜色: " + (d != null ? "$\\textcolor{" + hexColorOnline + "}{\\text{" + "▄" + "}}$" : "无效")
                );
                qq.sendMessage(md);
                return true;
            }
        }

        qq.sendMessage("笨蛋喵，你输入的子命令不存在，请检查子命令参数是否正确。");
        return true;
    }
}
