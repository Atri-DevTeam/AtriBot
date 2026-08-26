package top.yzljc.atribot.function.general;

import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.function.minecraft.DiceImpl;
import top.yzljc.atribot.function.minecraft.McPackMetaImpl;
import top.yzljc.atribot.function.official.minecraft.McCapesImpl;
import top.yzljc.atribot.function.minecraft.McLocationBarColorImpl;
import top.yzljc.atribot.function.minecraft.McVersionImpl;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;
import top.yzljc.atribot.utils.tools.MinecraftProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftCommand
 * @Created_at 2026/05/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
@Deprecated(since = "3.2.2")
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
            if (label.equals("mcv") || (args.length > 0 && (args[0].equalsIgnoreCase("version") || args[0].equalsIgnoreCase("ver")))) {
                McVersionImpl.onCommand(guildSender);
            } else {
                guildSender.sendMessage("QQ频道当前支持 /mc ver、/mc sr 和 /bantracker 查询");
            }
            return true;
        }

        if (!(sender instanceof QQCommandSender qq)) return true;

        switch (label) {
            case "mcv" -> {
                McVersionImpl.onCommand(qq);
                return true;
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
                McVersionImpl.onCommand(qq);
                return true;
            }
            case "capes", "cape" -> {
                return McCapesImpl.handleCapesCommand(qq);
            }
//            case "pack" -> {
//                McPackMetaImpl.handle(qq, args);
//                return true;
//            }
            case "sr", "skb", "skbresource" -> {
                qq.sendMessage("该指令已调整，请使用 /hyp pack 完成查询。");
                return true;
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
                    uuidOffline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + var1).getBytes(StandardCharsets.UTF_8));
                }

                int colorOnline = 0;
                String hexColorOnline = null;
                MinecraftProfile onlineHeadPic = null;
                if (d != null) {
                    colorOnline = McLocationBarColorImpl.getPlayerRGBColor(d.uuid());
                    hexColorOnline = String.format("#%06X", colorOnline & 0xFFFFFF);
                    onlineHeadPic = FetchMinecraftProfile.getPlayerProfile(d.uuid().toString());
                }
                int colorOffline = McLocationBarColorImpl.getPlayerRGBColor(uuidOffline);
                String hexColorOffline = String.format("#%06X", colorOffline & 0xFFFFFF);

                Markdown md = TC.md(((onlineHeadPic != null && onlineHeadPic.avatarUrl() != null && onlineHeadPic.username() != null) ? Markdown.img("pic", onlineHeadPic.avatarUrl(), 16, 16) + onlineHeadPic.username() : "") + " **查询结果如下**\n\n" +
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
