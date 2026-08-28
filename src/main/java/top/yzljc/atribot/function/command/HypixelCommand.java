package top.yzljc.atribot.function.command;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.DiceImpl;
import top.yzljc.atribot.function.utils.official.minecraft.MinecraftBind;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelCommand
 * @Created_at 2026/08/25
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 * @Description Hypixel -> 综合查询二级菜单
 */
public class HypixelCommand implements CommandExecutor {

    private static final Map<String, String> GAME_IDS_BY_ALIAS = Map.ofEntries(
            Map.entry("sb", "SKYBLOCK"),
            Map.entry("bw", "BEDWARS"),
            Map.entry("arc", "ARCADE"),
            Map.entry("duel", "DUELS"),
            Map.entry("duels", "DUELS"),
            Map.entry("sw", "SKYWARS"),
            Map.entry("bb", "BUILD_BATTLE"),
            Map.entry("mm", "MURDER_MYSTERY"),
            Map.entry("tnt", "TNTGAMES"),
            Map.entry("wool", "WOOL_GAMES"),
            Map.entry("uhc", "UHC"),
            Map.entry("bsg", "SURVIVAL_GAMES"),
            Map.entry("sg", "SURVIVAL_GAMES"),
            Map.entry("mw", "WALLS3"),
            Map.entry("wl", "BATTLEGROUND"),
            Map.entry("cvc", "MCGO"),
            Map.entry("smash", "SUPER_SMASH"),
            Map.entry("classic", "LEGACY"),
            Map.entry("proto", "PROTOTYPE"),
            Map.entry("pit", "PIT"),
            Map.entry("smp", "SMP"),
            Map.entry("housing", "HOUSING"));
    private static final String GAME_ALIAS_HELP =
            "sb / bw / arc / duel / sw / bb / mm / tnt / wool / uhc / bsg / mw / wl / cvc / "
                    + "smash / classic / proto / pit / smp / housing";

    private static final Set<SubCommand> availableSubCommands = Set.of(
            new SubCommand("wz", "查询玩家TNT游戏法师掘战详细数据", ResourcesProperties.HYPIXEL_TNT_WIZARDS_API, true, false),
            new SubCommand("zs", "查询玩家街机游戏僵尸末日详细数据", ResourcesProperties.HYPIXEL_ZOMBIES_API, true, false),
            new SubCommand("gs", "查询各小游戏在线情况", ResourcesProperties.HYPIXEL_STATUS_API, false, false),
            new SubCommand("pack", "查询Skyblock资源包版本信息", ResourcesProperties.SKB_PACK_VERSION_API, false, true),
            new SubCommand("dice", "随机Skyblock Dice(鉴定你的欧气)", null, false, true)
    );

    private static Markdown getSubCommands() {
        StringBuilder s = new StringBuilder();
        String title = "**Hypixel 综合查询二级菜单**\n\n";
        String cmdPrefix = "/hyp ";
        s.append(title);
        for (var cmd : availableSubCommands) {
            s.append("> ").append(Markdown.enterCommand(cmdPrefix + cmd.prefix() + " ", cmdPrefix + cmd.prefix()))
                    .append(" - ").append(cmd.description()).append("\n");
        }
        s.append("\nTips: 上方指令可直接点击哦~");
        return new Markdown(s.toString());
    }

    private static boolean isAvailableSubCommand(String var) {
        for (var cmd : availableSubCommands) {
            if (cmd.prefix().equals(var)) {
                return true;
            }
        }
        return false;
    }

    private static SubCommand getCommand(String var) {
        for (var cmd : availableSubCommands) {
            if (cmd.prefix().equals(var)) {
                return cmd;
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            if (args.length == 0) {
                user.sendMessage(getSubCommands());
                return true;
            }

            if (!isAvailableSubCommand(args[0].toLowerCase())) {
                user.sendMessage("未知的子命令，请使用 /hyp 查看可用的子命令列表");
                return true;
            }

            var sub = getCommand(args[0].toLowerCase(Locale.ROOT));
            if (sub == null) {
                user.sendMessage("执行指令时出现错误：请向开发者报告此问题！");
                return true;
            }

            if (!sub.special()) {
                if (sub.needPlayer()) {
                    String player = getPlayer(user.getUserId(), args);
                    if (player == null) {
                        user.sendMessage("笨蛋喵，你没有绑定用户信息，请阅读帮助文档查看绑定事项，或在指令后指明查询用户，例如: /hyp " + sub.prefix() + " Steve。");
                        return true;
                    }

                    String msgId = user.sendMessage("正在查询目标玩家数据，请稍等片刻...");
                    var d = PreImageGenerate.dump(sub.api(), Map.of("player", player));

                    user.getMessage().recall(msgId);

                    if (!d.isError()) {
                        if (d.url() != null) {
                            user.sendMessage(ImageComponent.imageOf(d.url()).setText("根据开放平台要求，自定义内容须审核后才能显示，请使用 /反馈 <用户名> 提交审核。"));
                            return true;
                        }
                    }
                } else {
                    Map<String, Object> request = Map.of();
                    if ("gs".equals(sub.prefix()) && args.length > 1) {
                        if (args.length > 2) {
                            user.sendMessage("用法: /hyp gs [游戏代称]\n支持: " + GAME_ALIAS_HELP);
                            return true;
                        }
                        String alias = args[1].toLowerCase(Locale.ROOT);
                        String gameId = GAME_IDS_BY_ALIAS.get(alias);
                        if (gameId == null) {
                            user.sendMessage("未知的游戏代称，支持: " + GAME_ALIAS_HELP);
                            return true;
                        }
                        request = Map.of("game", gameId);
                    }
                    String msgId = user.sendMessage("正在查询目标数据，请稍等片刻...");
                    var d = PreImageGenerate.dump(sub.api(), request);

                    user.getMessage().recall(msgId);

                    if (!d.isError()) {
                        if (d.url() != null) {
                            user.sendMessage(ImageComponent.imageOf(d.url()));
                            return true;
                        }
                    }

                }
            } else {
                switch (sub.prefix()) {
                    case "pack" -> {
                        return Atri.getInstance().getSkyblockPackCheck().onCommand(user);
                    }
                    case "dice" -> {
                        DiceImpl.handle(user, args);
                        return true;
                    }
                }
            }
            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        }

        if (sender instanceof QQGuildCommandSender user) {
            if (label.equals("skbpack")) {
                return Atri.getInstance().getSkyblockPackCheck().onCommand(user);
            }
        }

        return true;
    }

    private static String getPlayer(String userId, String[] args) {
        String player = null;

        if (args.length == 1) {
            if (MinecraftBind.getDataByOpenId(userId).uuid() != null) {
                player = MinecraftBind.getDataByOpenId(userId).uuid();
            }
        } else {
            if (args.length < 1) return null;
            else player = args[1];
        }
        return player;
    }

    private record SubCommand(String prefix, String description, String api, boolean needPlayer, boolean special) {}
}
