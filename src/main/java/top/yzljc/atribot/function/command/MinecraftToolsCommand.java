package top.yzljc.atribot.function.command;

import java.nio.charset.StandardCharsets;
import java.util.*;

import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.command.QQGuildCommandSender;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atribot.function.command.component.QueryTemplate;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.McLocationBarColorImpl;
import top.yzljc.atribot.function.minecraft.McPackMetaImpl;
import top.yzljc.atribot.function.minecraft.McVersionImpl;
import top.yzljc.atribot.function.minecraft.PlayerProfileImpl;
import top.yzljc.atribot.i18n.I18N;
import top.yzljc.atricore.platform.Identifier;
import top.yzljc.atricore.platform.qq.QQBot;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;
import top.yzljc.atribot.utils.tools.MinecraftProfile;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftToolsCommand
 * @Created_at 2026/08/25
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 * @Description Minecraft Tools 二级指令菜单
 */
public class MinecraftToolsCommand implements CommandExecutor {
    private static final String COMMAND_HEADER = "/mctool";
    private static final Map<String, SubCommand> SUB_COMMANDS = createSubCommands();

    private static Map<String, SubCommand> createSubCommands() {
        Map<String, SubCommand> commands = new LinkedHashMap<>();
        register(commands, new SubCommand("ver", "查询最新的Minecraft版本", "resource.image.grass-block", MinecraftToolsCommand::handleVersion));
        register(commands, new SubCommand("cape", "查询全局玩家披风拥有情况", "resource.image.minecraft-cape-example", MinecraftToolsCommand::handleCape));
        register(commands, new SubCommand("pf", "查询Minecraft玩家档案", "resource.image.icon-slumber-hotel", MinecraftToolsCommand::handleProfile));
        register(commands, new SubCommand("lb", "查询玩家Locator Bar颜色", "resource.image.icon-verdant", MinecraftToolsCommand::handleLocationBar));
        register(commands, new SubCommand("pack", "查询当前资源包版本信息", "resource.image.icon-knowledge-book", MinecraftToolsCommand::handlePack));
        return Collections.unmodifiableMap(commands);
    }

    private static void register(Map<String, SubCommand> commands, SubCommand command) {
        if (commands.putIfAbsent(command.prefix(), command) != null) {
            throw new IllegalArgumentException("重复的 Minecraft 子命令: " + command.prefix());
        }
    }

    private static Markdown getSubCommands() {
        StringBuilder text = new StringBuilder("**MC综合指令二级菜单**\n\n");
        text.append("> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n");
        text.append("---\n\n");
        for (var s : SUB_COMMANDS.values()) {
            text.append("> ").append(Markdown.img(RS.get(s.iconKey()), 16, 16)).append(Markdown.enterCommand(COMMAND_HEADER + " " + s.prefix(), s.description())).append("\n");
        }
        text.append("> ").append(Markdown.img(RS.get("resource.image.icon-ec"), 16, 16))
                .append(Markdown.enterCommand("/ec", "附魔台物品附魔模拟")).append("\n");
        return new Markdown(text.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQCommandSender qq) {
            if (args.length == 0) {
                sendMenu(sender);
                return true;
            }

            String prefix = args[0].toLowerCase(Locale.ROOT);
            if (prefix.equals("capes")) prefix = "cape";
            SubCommand sub = SUB_COMMANDS.get(prefix);
            if (sub == null) {
                sender.sendMessage("未知的子命令，请使用 /mctool 查看可用的子命令列表");
                return true;
            }
            return sub.handler().execute(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        if (sender instanceof QQGuildCommandSender guild) {
            if (label.equals("mcv")) return McVersionImpl.onCommand(guild);
            if (label.equals("mccape")) return handleCape(guild, new String[0]);
        }
        return true;
    }

    public static final Keyboard keyboard = new Keyboard(
            List.of(
                    List.of(
                            new Button("s1", "问题反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setModal("对" + QQBot.BOT_NAME + "的部分内容有更改建议？遇到了问题？欢迎向开发者反馈喵~", "我要反馈", "以后再说"),
                            new Button("l2", "添加到群", "https://web.qun.qq.com/qunrobot/jump.html?robot_uin=" + QQBot.BOT_UIN + "&target=2", true, ButtonStyle.BLUE, ButtonType.LINK)
                    )
            )
    );

    private static void sendMenu(CommandSender sender) {
        if (sender instanceof QQCommandSender qq) {
            qq.sendMessage(getSubCommands().setKeyboard(keyboard));
        }
    }

    private static boolean handleVersion(CommandSender sender, String[] args) {
        return new MinecraftVersionCommand().onCommand(sender, null, "mcv", new String[0]);
    }

    private static boolean handlePack(CommandSender sender, String[] args) {
        String[] legacyArgs = new String[args.length + 1];
        legacyArgs[0] = "pack";
        System.arraycopy(args, 0, legacyArgs, 1, args.length);
        McPackMetaImpl.handle(sender, legacyArgs);
        return true;
    }

    private static boolean handleProfile(CommandSender sender, String[] args) {
        String player = args.length > 0 ? args[0] : null;

        String p = QueryTemplate.getPlayerOrNull(sender.getUserId(), player);
        if (p == null) {
            sender.sendMessage(I18N.text("command.mc.noplayer"));
            return true;
        }

        return PlayerProfileImpl.onQuery((QQCommandSender) sender, p);
    }

    private static boolean handleCape(CommandSender sender, String[] args) {
        sendImageResult(sender, requestImage(sender, "bot.minecraft.capes", Map.of()));
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
        ((QQCommandSender) user).sendMessage(md);
        return true;
    }

    private static String getPlayer(CommandSender sender, String[] args) {
        if (args.length > 0) return args[0].trim();
        if (!(sender instanceof QQCommandSender)) return null;
        var account = UnifiedAuthentication.findByQqUserOpenId(sender.getUserId());
        return account == null ? null : account.minecraftUuid();
    }

    private static ImageDTO requestImage(CommandSender sender, String apiKey, Map<String, ?> parameters) {
        Map<String, Object> request = new LinkedHashMap<>(parameters);
        if (sender instanceof QQCommandSender) BackgroundService.applyApprovedBackground(request, sender.getUserId());
        return PreImageGenerate.dump(OpenApi.get(apiKey), request, sender.getPlatform());
    }

    private static void sendImageResult(CommandSender sender, ImageDTO result) {
        if (result == null) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
        } else if (result.isError()) {
            sender.sendMessage(result.errorMessage());
        } else if (result.url() == null || result.url().isBlank()) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
        } else {
            ImageComponent image = ImageComponent.imageOf(result.url());
            switch (sender) {
                case QQCommandSender qq -> qq.sendMessage(image);
                case QQGuildCommandSender guild -> guild.sendMessage(image);
                default -> {
                }
            }
        }
    }

    private record SubCommand(String prefix, String description, String iconKey, SubCommandHandler handler) {}

    @FunctionalInterface
    private interface SubCommandHandler {
        /**
         * @param sender 指令发送者
         * @param args 子命令后的参数，不包含子命令名称
         * @return 是否已处理指令
         */
        boolean execute(CommandSender sender, String[] args);
    }
}
