package top.yzljc.atribot.function.command;

import org.jspecify.annotations.NonNull;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.McLocationBarColorImpl;
import top.yzljc.atribot.function.minecraft.McPackMetaImpl;
import top.yzljc.atribot.function.minecraft.McVersionImpl;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;
import top.yzljc.atribot.utils.tools.MinecraftProfile;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftCommand
 * @Created_at 2026/08/25
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 * @Description Minecraft Tools 二级指令菜单
 */
public class MinecraftToolsCommand implements CommandExecutor {

    private static final String commandHeader = "/mctool";
    private static final Set<SubCommand> availableSubCommands = Set.of(
            new SubCommand("ver", "查询最新的MC版本"),
            new SubCommand("cape", "查询MC披风拥有情况"),
            new SubCommand("lb", "查询玩家Location Bar颜色"),
            new SubCommand("pack", "查询MC资源包版本信息")
    );

    private static boolean isValidSubCommand(String prefix) {
        for (var cmd : availableSubCommands) {
            if (cmd.prefix().equalsIgnoreCase(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Markdown getSubCommands() {
        StringBuilder s = new StringBuilder();
        String title = "**MC综合指令二级菜单**";
        s.append(title).append("\n\n");
        for (var cmd : availableSubCommands) {
            s.append(cmd.toString());
        }

        s.append("\nTips: 上方指令可直接点击哦~");
        return new Markdown(s.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            if (args.length == 0) {
                user.sendMessage(getSubCommands());
                return true;
            }

            String sub = args[0].toLowerCase();
            if (!isValidSubCommand(sub)) {
                user.sendMessage("未知的子命令，请使用 /mctool 查看可用的子命令列表");
                return true;
            }

            switch (sub) {
                case "ver" -> {
                    return McVersionImpl.onCommand(user);
                }
                case "cape", "capes" -> {
                    return checkMinecraftCape(user);
                }
                case "pack" -> {
                    McPackMetaImpl.handle(user, args);
                    return true;
                }
                case "lb" -> {
                    if (args.length < 2) {
                        user.sendMessage("笨蛋喵，你没有输入玩家名或UUID，怎么查？");
                        return true;
                    }
                    var var1 = args[1];
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
                    user.sendMessage(md);
                    return true;
                }
            }
        }

        if (sender instanceof QQGuildCommandSender user) {
            if (label.equals("mcv")) {
                return McVersionImpl.onCommand(user);
            }
            if (label.equals("mccape")) {
                return checkMinecraftCape(user);
            }
        }

        return true;
    }

    private record SubCommand(String prefix, String description) {

        @Override
        public @NonNull String toString() {
            return "> " + Markdown.enterCommand(commandHeader + " " + prefix + " ", commandHeader + " " + prefix) + " - " + description + "\n";
        }
    }

    private static boolean checkMinecraftCape(CommandSender sender) {
        var data = PreImageGenerate.dump(ResourcesProperties.MINECRAFT_CAPES_API, Map.of());
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
            return true;
        }

        if (sender instanceof QQCommandSender) {
            ((QQCommandSender) sender).sendMessage(ImageComponent.imageOf(data.url()));
        } else if (sender instanceof QQGuildCommandSender) {
            ((QQGuildCommandSender) sender).sendMessage(ImageComponent.imageOf(data.url()));
        }
        return true;
    }
}