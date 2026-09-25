package top.yzljc.atribot.function.command;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ImageDelivery;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.DiceImpl;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.service.request.HttpService;

import java.util.*;
import java.util.function.Supplier;

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
            Map.entry("skb", "SKYBLOCK"),
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
            Map.entry("ptl", "PROTOTYPE"),
            Map.entry("proto", "PROTOTYPE"),
            Map.entry("pit", "PIT"),
            Map.entry("smp", "SMP"),
            Map.entry("housing", "HOUSING"));
    private static final String GAME_ALIAS_HELP =
            "sb / bw / arc / duel / sw / bb / mm / tnt / wool / uhc / bsg / mw / wl / cvc / "
                    + "smash / classic / proto / pit / smp / housing";

    private static final Map<String, SubCommand> SUB_COMMANDS = createSubCommands();

    private static Map<String, SubCommand> createSubCommands() {
        Map<String, SubCommand> commands = new LinkedHashMap<>();
        register(commands, new SubCommand("wz", "查询玩家法师掘战详细数据", ResourcesProperties.ICON_TNT, HypixelCommand::handleWizards));
        register(commands, new SubCommand("zs", "查询玩家僵尸末日详细数据", ResourcesProperties.ICON_ZOMBIE_HEAD, HypixelCommand::handleZombies));
        register(commands, new SubCommand("gs", "全服小游戏在线情况", ResourcesProperties.HYPIXEL_HEADER_IMG, HypixelCommand::handleGameStatus));
        register(commands, new SubCommand("pack", "查询Skyblock资源包版本信息", ResourcesProperties.ICON_KNOWLEDGE_BOOK, HypixelCommand::handlePack));
        register(commands, new SubCommand("dice", "随机Skyblock Dice(鉴定你的欧气)", ResourcesProperties.DICE_RENDER_RESULT_IMG_T.replace("<id>", "6"), HypixelCommand::handleDice));
        register(commands, new SubCommand("coop", "查询玩家Skyblock Coop在线情况", ResourcesProperties.ICON_DIAMOND_PICKAXE, HypixelCommand::handleCoop));
        register(commands, new SubCommand("dungeon", "查询玩家最近地牢游玩场次", ResourcesProperties.ICON_SKYBLOCK_DUNGEON, HypixelCommand::handleDungeon));
        register(commands, new SubCommand("lf", "查询玩家大厅钓鱼数据", ResourcesProperties.ICON_FISHING_ROD, HypixelCommand::handleLobbyFishing));
        register(commands, new SubCommand("hotf", "查看玩家Skyblock树心数据", ResourcesProperties.ICON_HOTF, HypixelCommand::handleHotf));
        register(commands, new SubCommand("pr", "查询玩家大厅跑酷详细数据", ResourcesProperties.ICON_PARKOUR, HypixelCommand::handleParkour));
        register(commands, new SubCommand("dpr", "查询玩家街机心跳水立方详细数据", ResourcesProperties.ICON_DROPPER, HypixelCommand::handleArcadeDropper));
        return Collections.unmodifiableMap(commands);
    }

    private static void register(Map<String, SubCommand> commands, SubCommand command) {
        if (commands.putIfAbsent(command.prefix(), command) != null) {
            throw new IllegalArgumentException("重复的 Hypixel 子命令: " + command.prefix());
        }
    }

    public static final Object keyboard = TC.keyboard(
            List.of(
                    List.of(
                            new Button("s1", "问题反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setModal("对" + QQBot.BOT_NAME + "的部分内容有更改建议？遇到了问题？欢迎向开发者反馈喵~", "我要反馈", "以后再说"),
                            new Button("l2", "添加到群", "https://web.qun.qq.com/qunrobot/jump.html?robot_uin=" + QQBot.BOT_UIN + "&target=2", true, ButtonStyle.BLUE, ButtonType.LINK)
                    )
            )
    );

    private static Markdown getSubCommands() {
        StringBuilder s = new StringBuilder();
        String title = "**Hypixel 综合查询二级菜单**\n\n";
        String cmdPrefix = "/hyp ";
        s.append(title);
        s.append("> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n");
        s.append("---\n\n");
        for (var cmd : SUB_COMMANDS.values()) {
            s.append("> ").append(Markdown.img(cmd.icon(), 16, 16)).append(Markdown.enterCommand(cmdPrefix + cmd.prefix() + " ", cmd.description())).append("\n");
        }
        return new Markdown(s.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            if (args.length == 0) {
                user.sendMessage(getSubCommands(), keyboard);
                return true;
            }

            var sub = SUB_COMMANDS.get(args[0].toLowerCase(Locale.ROOT));
            if (sub == null) {
                user.sendMessage("未知的子命令，请使用 /hyp 查看可用的子命令列表");
                return true;
            }

            return sub.handler().execute(user, Arrays.copyOfRange(args, 1, args.length));
        }

        if (sender instanceof QQGuildCommandSender user) {
            if (label.equals("skbpack")) {
                return Atri.getInstance().getSkyblockPackCheck().onCommand(user);
            }
        }

        return true;
    }

    private static boolean handleWizards(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, ResourcesProperties.HYPIXEL_TNT_WIZARDS_API, "wz");
    }

    private static boolean handleZombies(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, ResourcesProperties.HYPIXEL_ZOMBIES_API, "zs");
    }

    private static boolean handleGameStatus(QQCommandSender user, String[] args) {
        if (args.length > 1) {
            user.sendMessage("用法: /hyp gs [小游戏]\n支持: " + GAME_ALIAS_HELP);
            return true;
        }

        Map<String, Object> request;
        if (args.length == 0) {
            request = Map.of();
        } else {
            String gameId = GAME_IDS_BY_ALIAS.get(args[0].toLowerCase(Locale.ROOT));
            if (gameId == null) {
                user.sendMessage("未知的小游戏编号，请在总人数列表里查看可用参数。");
                return true;
            }
            request = Map.of("game", gameId);
        }

        var result = withQueryProgress(user, "正在查询目标数据，请稍等片刻...",
                () -> PreImageGenerate.dump(ResourcesProperties.HYPIXEL_STATUS_API, request));
        sendImageResult(user, result, "你可以使用 /hyp gs [小游戏] 来查询指定小游戏的在线情况。 ");
        return true;
    }

    private static boolean handlePack(QQCommandSender user, String[] args) {
        return Atri.getInstance().getSkyblockPackCheck().onCommand(user);
    }

    private static boolean handleDice(QQCommandSender user, String[] args) {
        DiceImpl.handle(user, args);
        return true;
    }

    private static boolean handleCoop(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, ResourcesProperties.SKYBLOCK_COOP_API, "coop");
    }

    private static boolean handleDungeon(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, ResourcesProperties.SKYBLOCK_DUNGEON_API, "dungeon");
    }

    private static boolean handleLobbyFishing(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, ResourcesProperties.HYPIXEL_PLAYER_LOBBY_FISHING_API, "lf");
    }

    private static boolean handleHotf(QQCommandSender user, String[] args) {
        String player = getPlayer(user.getUserId(), args);
        if (player == null) {
            user.sendMessage("笨蛋喵，你没有绑定用户信息，请指定一个玩家或使用/bind完成绑定。");
            return true;
        }

        String profile = null;
        Map<String, String> requestBody;
        if (args.length == 2) {
            profile = args[1];
            requestBody = Map.of("player", player, "profile", profile);
        } else {
            requestBody = Map.of("player", player);
        }

        var result = withQueryProgress(user, "正在查询目标玩家数据，请稍等片刻...",
                () -> customQueryRequest(ResourcesProperties.SKYBLOCK_HOTF_API, requestBody, "Authorization", bearer()));

        if (!result.success()) {
            user.sendMessage(result.message());
            return true;
        }

        List<String> profiles = new ArrayList<>();
        var pn = result.d().path("availableProfiles");
        // System.out.println(result.d());
        if (pn.isArray()) {
            for (var p: pn) {
                profiles.add(p.asText());
            }
        }

        List<List<Button>> buttons = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i += 2) {
            List<Button> pair = new ArrayList<>();
            pair.add(new Button("btn_" + System.currentTimeMillis(), profiles.get(i), "/hyp hotf " + player + " " + profiles.get(i), true, ButtonStyle.BLUE, ButtonType.COMMAND));
            if (i + 1 < profiles.size()) {
                pair.add(new Button("btn_" + System.currentTimeMillis(), profiles.get(i + 1), "/hyp hotf " + player + " " + profiles.get(i + 1), true, ButtonStyle.BLUE, ButtonType.COMMAND));
            }
            buttons.add(pair);
        }

        Object keyboard = TC.keyboard(buttons);
        user.sendMessage(TC.md(getTemplate(result.i.url(), result.i().width(), result.i().height(), user.getUserId())), keyboard, false);
        return true;
    }

    private static boolean handleParkour(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, ResourcesProperties.HYPIXEL_LOBBY_PARKOUR_API, "pr");
    }

    private static boolean handleArcadeDropper(QQCommandSender sender, String[] args) {
        return queryPlayer(sender, args, ResourcesProperties.HYPIXEL_ARCADE_DROPPER_API, "dpr");
    }

    // 现有图片查询的可选复用方法；需要不同数据或 Markdown + 按钮的 handler 可自行查询、展示。
    private static boolean queryPlayerImage(QQCommandSender user, String[] args, String api) {
        String player = getPlayer(user.getUserId(), args);
        if (player == null) {
            user.sendMessage("笨蛋喵，你没有绑定用户信息，请指定一个玩家或使用/bind完成绑定。");
            return true;
        }

        var result = withQueryProgress(user, "正在查询目标玩家数据，请稍等片刻...",
                () -> PreImageGenerate.dump(api, Map.of("player", player)));
        sendImageResult(user, result, "根据开放平台要求，自定义内容须审核后才能显示，请使用 /反馈 <用户名> 提交审核。");
        return true;
    }

    private static String getPlayer(String userId, String[] args) {
        if (args.length > 0) {
            return args[0];
        }
        var account = UnifiedAuthentication.findByQqUserOpenId(userId);
        return account == null ? null : account.minecraftUuid();
    }

    // 提交等待并执行撤回任务
    private static <T> T withQueryProgress(QQCommandSender user, String message, Supplier<T> query) {
        String messageId = user.sendMessage(message);
        try {
            return query.get();
        } finally {
            if (messageId != null && !messageId.isBlank()) {
                user.recall(messageId);
            }
        }
    }

    private static void sendImageResult(QQCommandSender user, ImageDTO result, String text) {
        if (result == null) {
            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        } else if (result.isError()) {
            user.sendMessage(result.errorMessage());
        } else if (result.url() == null || result.url().isBlank()) {
            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        } else {
            user.sendMessage(ImageComponent.imageOf(result.url()).setText(text));
        }
    }

    @FunctionalInterface
    private interface SubCommandHandler {
        /**
         * args 只包含子命令后的参数，例如 /hyp gs bw 收到 ["bw"]。
         */
        boolean execute(QQCommandSender user, String[] args);
    }

    private static boolean queryPlayer(QQCommandSender sender, String[] args, String api, String subCommand) {
        String player = getPlayer(sender.getUserId(), args);
        if (player == null) {
            sender.sendMessage("笨蛋喵，你没有绑定用户信息，请指定一个玩家或使用/bind完成绑定。");
            return true;
        }

        var result = withQueryProgress(sender, "正在查询目标玩家数据，请稍等片刻...",
                () -> PreImageGenerate.dump(api, Map.of("player", player)));

        if (result == null) {
            sender.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        } else if (result.isError()) {
            sender.sendMessage(result.errorMessage());
        } else if (result.url() == null || result.url().isBlank()) {
            sender.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        } else {
            sender.sendMessage(TC.md(getTemplate(result.url(), result.width(), result.height(), sender.getUserId())), getKeyboard(subCommand, getPlayer(sender.getUserId(), args)), false);
        }

        return true;
    }

    private static String getTemplate(String url, int w, int h, String userOpenId) {
        return (
                Markdown.img("player-query-img", url, w, h) + "\n\n" +
                        Markdown.at(userOpenId) + " 根据开放平台要求，用户提交的自定义内容须经过审查后才能显示，请使用 `/反馈 玩家名` 提交审核。\n\n" +
                        "> " + Markdown.link("https://web.qun.qq.com/qunrobot/jump.html?robot_uin=" + QQBot.BOT_UIN + "&target=2", "\uD83D\uDD17邀我进群")
        );
    }

    private static Object getKeyboard(String subCommand, String queriedPlayer) {
        return TC.keyboard(
                List.of(
                        List.of(
                                new Button("t1", "再次查询", "/hyp " + subCommand + " ", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("t2", "加白审核", "/feedback 白名单审查 " + queriedPlayer, false, ButtonStyle.BLUE, ButtonType.COMMAND).setModal("你确定要提交当前被查询玩家的名称和皮肤进行审查吗？", "提交", "取消")
                                // new Button("l2", "添加到群", "https://web.qun.qq.com/qunrobot/jump.html?robot_uin=" + QQBot.BOT_UIN + "&target=2", true, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.LINK)
                        )
//                        List.of(
//                                new Button("t1", "再次查询", "/hyp " + subCommand + " ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
//                        )
                )
        );
    }

    private static Result customQueryRequest(String api, Map<?, ?> requestBody, String... headers) {
        var t = HttpService.postJson(api, requestBody, headers);

        if (t == null) return new Result(false, "数据查询失败，服务器未响应，请稍后重试！", null, null);
        var status = t.path("status").asInt(-1);
        if (status != 200) {
            if (status == 432) {
                return new Result(false, t.path("message").asText("玩家数据异常"), null, null);
            }
            return new Result(false, "数据查询失败，服务器未响应，请稍后重试！", null, null);
        }

        var d = t.path("data");
        if (d == null || d.isNull()) {
            return new Result(false, "数据查询失败，服务器未响应，请稍后重试！", null, null);
        }

        var url = ImageDelivery.resolve(d);
        if (url == null || url.isBlank()) {
            return new Result(false, "图片地址无效，请稍后重试！", d, null);
        }
        var w = d.path("width").asInt(0);
        var h = d.path("height").asInt(0);
        return new Result(true, "ok", d, new ImageDTO(url, w, h));
    }

    private record SubCommand(String prefix, String description, String icon, SubCommandHandler handler) {}

    private record Result(boolean success, String message, JsonNode d, ImageDTO i) {}

    private static String bearer() {
        return "Bearer " + Config.getInstance().getAtribotKeySecret();
    }
}
