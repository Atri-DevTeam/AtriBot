package top.yzljc.atribot.function.command;

import java.util.*;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.Keyboard;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ImageDelivery;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialButtonInteractionEvent;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.DiceImpl;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelCommand
 * @Created_at 2026/08/25
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 * @Description Hypixel -> 综合查询二级菜单
 */
public class HypixelCommand implements CommandExecutor, Listener {

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
        register(commands, new SubCommand("wz", Category.GENERAL, "查询玩家法师掘战详细数据", ResourcesProperties.ICON_TNT, HypixelCommand::handleWizards));
        register(commands, new SubCommand("zs", Category.GENERAL, "查询玩家僵尸末日详细数据", ResourcesProperties.ICON_ZOMBIE_HEAD, HypixelCommand::handleZombies));
        register(commands, new SubCommand("gs", Category.GENERAL, "全服小游戏在线情况", ResourcesProperties.HYPIXEL_HEADER_IMG, HypixelCommand::handleGameStatus));
        register(commands, new SubCommand("pack", Category.SKYBLOCK, "查询Skyblock资源包版本信息", ResourcesProperties.ICON_KNOWLEDGE_BOOK, HypixelCommand::handlePack));
        register(commands, new SubCommand("dice", Category.SKYBLOCK, "随机Skyblock Dice(鉴定你的欧气)", ResourcesProperties.DICE_RENDER_RESULT_IMG_T.replace("<id>", "6"), HypixelCommand::handleDice));
        register(commands, new SubCommand("coop", Category.SKYBLOCK, "查询玩家Skyblock Coop在线情况", ResourcesProperties.ICON_DIAMOND_PICKAXE, HypixelCommand::handleCoop));
        register(commands, new SubCommand("dungeon", Category.SKYBLOCK, "查询玩家最近地牢游玩场次", ResourcesProperties.ICON_SKYBLOCK_DUNGEON, HypixelCommand::handleDungeon));
        register(commands, new SubCommand("lf", Category.GENERAL, "查询玩家大厅钓鱼数据", ResourcesProperties.ICON_FISHING_ROD, HypixelCommand::handleLobbyFishing));
        register(commands, new SubCommand("hotf", Category.SKYBLOCK, "查看玩家Skyblock树心数据", ResourcesProperties.ICON_HOTF, HypixelCommand::handleHotf));
        register(commands, new SubCommand("pr", Category.GENERAL, "查询玩家大厅跑酷详细数据", ResourcesProperties.ICON_PARKOUR, HypixelCommand::handleParkour));
        register(commands, new SubCommand("dpr", Category.GENERAL, "查询玩家街机心跳水立方详细数据", ResourcesProperties.ICON_DROPPER, HypixelCommand::handleArcadeDropper));
        register(commands, new SubCommand("bw", Category.GENERAL, "查询玩家起床战争详细数据", ResourcesProperties.ICON_BEDWARS, HypixelCommand::handleBedwars));
        register(commands, new SubCommand("sh", Category.GENERAL, "查询玩家起床战争入梦酒店任务树", ResourcesProperties.ICON_SLUMBER_HOTEL, HypixelCommand::handleSlumberHotel));
        register(commands, new SubCommand("cr", Category.SKYBLOCK, "查询Skyblock日历", ResourcesProperties.ICON_SKYBLOCK_CALENDAR, HypixelCommand::handleSkyblockCalendar));
        register(commands, new SubCommand("ip", Category.SKYBLOCK, "查询Skyblock物品价格信息", ResourcesProperties.SKB_BANK_LOGO_IMG, HypixelCommand::handleSearchSkyblockItemPrice));
        register(commands, new SubCommand("hotm", Category.SKYBLOCK, "查询玩家Skyblock山之心数据", ResourcesProperties.ICON_SKYBLOCK_HOTM, HypixelCommand::handleHotm));
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
        return getSubCommands(Category.GENERAL);
    }

    private static Markdown getSubCommands(Category category) {
        StringBuilder s = new StringBuilder();
        String title = category == Category.GENERAL ? "**Hypixel 综合查询菜单**\n\n" : "**Hypixel " + category.title + " 指令菜单**\n\n";
        String cmdPrefix = "/hyp ";
        s.append(title);
        s.append("> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n");
        s.append("---\n\n");
        if (category == Category.GENERAL) {
            for (Category entry : Category.values()) {
                if (entry.command.isEmpty()) continue;
                s.append("> ").append(Markdown.img(entry.icon, 16, 16)).append(Markdown.enterCommand(cmdPrefix + entry.command,
                        entry.title + " 指令二级菜单")).append("\n");
            }
        }
        for (var cmd : SUB_COMMANDS.values()) {
            if (cmd.category() != category) continue;
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

            String prefix = args[0].toLowerCase(Locale.ROOT);
            Category category = Category.fromCommand(prefix);
            if (category != null) {
                if (args.length != 1) {
                    user.sendMessage("用法: /hyp " + category.command + "\n查看分类后，请使用菜单中显示的原有指令。");
                } else {
                    user.sendMessage(getSubCommands(category), keyboard);
                }
                return true;
            }

            var sub = SUB_COMMANDS.get(prefix);
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
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.tnt-wizards"), "wz");
    }

    private static boolean handleZombies(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.zombies"), "zs");
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
                () -> PreImageGenerate.dump(OpenApi.get("bot.hypixel.status"), request));
        if (OfficialUsers.isUserUnsupportedKeyboard(user.getUserId())) {
            user.sendMessage(TC.md(Markdown.img("player-stats", result.url(), result.width(), result.height()) + "\n\n" + Markdown.at(user.getUserId())).setKeyboard(getMiniGames(), true), false);
        } else {
            user.sendMessage(TC.md(Markdown.img("player-stats", result.url(), result.width(), result.height()) + "\n\n" + Markdown.at(user.getUserId())), getMiniGames(), false);
        }
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
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.skyblock.coop"), "coop");
    }

    private static boolean handleDungeon(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.skyblock.dungeons"), "dungeon");
    }

    private static boolean handleLobbyFishing(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.fishing"), "lf");
    }

    private static boolean handleSlumberHotel(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.bedwars.slumber"), "sh");
    }

    private static boolean handleBedwars(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.bedwars"), "bw");
    }

    private static boolean handleSkyblockCalendar(QQCommandSender user, String[] args) {

        var result = withQueryProgress(user, "正在查询相关数据，请稍等片刻...",
                () -> customQueryRequest(OpenApi.get("bot.hypixel.skyblock.calendar"), Map.of(), "Authorization", bearer()));

        if (!result.success()) {
            user.sendMessage(result.message());
            return true;
        }

        user.sendMessage(ImageComponent.imageOf(result.i.url()));
        return true;
    }

    private static boolean handleHotf(QQCommandSender user, String[] args) {
        return handleHeartTasks(user, args, OpenApi.get("bot.hypixel.skyblock.forest"), "hotf");
    }

    private static boolean handleHotm(QQCommandSender user, String[] args) {
        return handleHeartTasks(user, args, OpenApi.get("bot.hypixel.skyblock.mountain"), "hotm");
    }

    private static boolean handleHeartTasks(QQCommandSender user, String[] args, String api, String subCommand) {
        String player = getPlayer(user.getUserId(), args);
        if (player == null) {
            user.sendMessage("笨蛋喵，你没有绑定用户信息，请指定一个玩家或使用/bind完成绑定。");
            return true;
        }

        Map<String, String> requestBody;
        if (args.length == 2) {
            requestBody = Map.of("player", player, "profile", args[1]);
        } else {
            requestBody = Map.of("player", player);
        }

        var result = withQueryProgress(user, "正在查询目标玩家数据，请稍等片刻...",
                () -> customQueryRequest(api, requestBody, "Authorization", bearer()));

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
            pair.add(new Button("btn_" + System.currentTimeMillis(), profiles.get(i), "/hyp " + subCommand + " " + player + " " + profiles.get(i), true, ButtonStyle.BLUE, ButtonType.COMMAND));
            if (i + 1 < profiles.size()) {
                pair.add(new Button("btn_" + System.currentTimeMillis(), profiles.get(i + 1), "/hyp " + subCommand + " " + player + " " + profiles.get(i + 1), true, ButtonStyle.BLUE, ButtonType.COMMAND));
            }
            buttons.add(pair);
        }

        Object keyboard = TC.keyboard(buttons);
        user.sendMessage(TC.md(getTemplate(result.i.url(), result.i().width(), result.i().height(), user.getUserId())), keyboard, false);
        return true;
    }

    private static boolean handleParkour(QQCommandSender user, String[] args) {
        return queryPlayer(user, args, OpenApi.get("bot.hypixel.parkour"), "pr");
    }

    private static boolean handleArcadeDropper(QQCommandSender sender, String[] args) {
        return queryPlayer(sender, args, OpenApi.get("bot.hypixel.dropper"), "dpr");
    }

    private static boolean handleSearchSkyblockItemPrice(QQCommandSender user, String[] args) {
        if (args.length < 1) {
            user.sendMessage("未指定查询物品，请指定查询目标。");
            return true;
        }

        var search = String.join(" ", args);
        var result = withQueryProgress(user, "正在查询相关数据，请稍等片刻...",
                () -> customQueryRequest(OpenApi.get("bot.hypixel.skyblock.price"), Map.of("q", search), "Authorization", bearer()));

        if (!result.success()) {
            user.sendMessage(result.message());
            return true;
        }

        var next_cursor = result.d().path("next_cursor").asText(null);
        int type = next_cursor == null ? 0 : 1;

        user.sendMessage(getSkyblockPriceMarkdown(result.i().url(), result.i().width(), result.i().height(), user.getUserId()).setKeyboard(getSkyblockPriceKeyboard(type, next_cursor, null, search)), false);

        return true;
    }

    @EventHandler
    public void onButtonInteractionEvent(OfficialButtonInteractionEvent event) {
        if (!event.getButtonId().equals("skyblock_search")) return;

        String d = event.getButtonValue();
        int offsetSeparator = d.lastIndexOf(':');
        int querySeparator = offsetSeparator < 0
                ? -1
                : d.lastIndexOf(':', offsetSeparator - 1);

        if (querySeparator <= 0) {
            event.sendMessage("分页参数无效，请重新查询。");
            return;
        }

        String itemId = d.substring(0, querySeparator);
        String cursor = d.substring(querySeparator + 1);

        var result = customQueryRequest(OpenApi.get("bot.hypixel.skyblock.price"), Map.of("q", itemId, "cursor", cursor), "Authorization", bearer());

        if (!result.success()) {
            event.sendMessage(result.message());
            return;
        }

        var next_cursor = result.d().path("next_cursor").asText(null);
        var pre_cursor = result.d().path("pre_cursor").asText(null);

        int type = 0;
        if (next_cursor == null && pre_cursor != null) type = 3;
        else if (next_cursor != null && pre_cursor == null) type = 1;
        else if (next_cursor != null) type = 2;

        event.replyMessage(getSkyblockPriceMarkdown(result.i().url(), result.i().width(), result.i().height(), event.getUserOpenId()).setKeyboard(getSkyblockPriceKeyboard(type, next_cursor, pre_cursor, itemId)), false);
    }

    private static Keyboard getSkyblockPriceKeyboard(int type, String var1, String var2, String itemId) {
        return switch (type) {
            case 1 -> new Keyboard(
                    List.of(
                            List.of(new Button("skyblock_search", "下一页", itemId + ":" + var1, ButtonStyle.GRAY, ButtonType.CALLBACK)),
                            List.of(new Button("c1", "查询其他物品", "/hyp ip ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                    )
            );
            case 2 -> new Keyboard(
                    List.of(
                            List.of(new Button("skyblock_search", "上一页", itemId + ":" + var2, ButtonStyle.GRAY, ButtonType.CALLBACK),
                                    new Button("skyblock_search", "下一页", itemId + ":" + var1, ButtonStyle.GRAY, ButtonType.CALLBACK)),
                            List.of(new Button("c1", "查询其他物品", "/hyp ip ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                    )
            );
            case 3 -> new Keyboard(
                    List.of(
                            List.of(new Button("skyblock_search", "上一页", itemId + ":" + var2, ButtonStyle.GRAY, ButtonType.CALLBACK)),
                            List.of(new Button("c1", "查询其他物品", "/hyp ip ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                    )
            );
            default -> new Keyboard(
                    List.of(
                            List.of(new Button("c1", "查询其他物品", "/hyp ip ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                            )
                    ));
        };
    }

    private static Markdown getSkyblockPriceMarkdown(String url, int w, int h, String userOpenId) {
        return TC.md(
                Markdown.img("atri_bot_pic", url, w, h) + "\n\n" + Markdown.at(userOpenId)
        );
    }

    @Deprecated(forRemoval = true)
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
        String messageId = null;
        // 允许不发消息
        if (message != null && !message.isBlank()) {
            messageId = user.sendMessage(message);
        }
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
                () -> customQueryRequest(api, Map.of("player", player), "Authorization", bearer()));

        if (!result.success()) {
            sender.sendMessage(result.message());
            return true;
        }

        sender.sendMessage(TC.md(getTemplate(result.i().url(), result.i().width(), result.i().height(), sender.getUserId())), getKeyboard(subCommand, player), false);

        return true;
    }

    private static String getTemplate(String url, int w, int h, String userOpenId) {
        return (
                Markdown.img("atri_bot_pic", url, w, h) + "\n\n" +
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
        var response = Requests.post(api, requestBody, headers);
        if (!response.isSuccess()) {
            return new Result(false, response.message(), null, null);
        }
        var d = response.data();
        if (d == null || !d.isObject()) return new Result(false, "图片响应数据无效", null, null);

        var url = ImageDelivery.resolve(d);
        if (url == null || url.isBlank()) {
            return new Result(false, "图片地址无效，请稍后重试！", d, null);
        }
        var w = d.path("width").asInt(0);
        var h = d.path("height").asInt(0);
        return new Result(true, "ok", d, new ImageDTO(url, w, h));
    }

    private enum Category {
        GENERAL("", "通用", ResourcesProperties.HYPIXEL_HEADER_IMG),
        SKYBLOCK("skb", "SkyBlock", ResourcesProperties.SKB_LOGO_IMG);

        private final String command;
        private final String title;
        private final String icon;

        Category(String command, String title, String icon) {
            this.command = command;
            this.title = title;
            this.icon = icon;
        }

        static Category fromCommand(String command) {
            for (Category category : values()) {
                if (!category.command.isEmpty() && category.command.equalsIgnoreCase(command)) {
                    return category;
                }
            }
            return null;
        }
    }

    private record SubCommand(String prefix, Category category, String description, String icon, SubCommandHandler handler) {}

    private record Result(boolean success, String message, JsonNode d, ImageDTO i) {}

    private static String bearer() {
        return "Bearer " + Config.getInstance().getAtribotKeySecret();
    }

    private static Keyboard getMiniGames() {
        return new Keyboard(
                List.of(
                        List.of(
                                new Button("bw", "起床", "/hyp gs bw", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("sw", "空岛", "/hyp gs sw", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("arc", "街机", "/hyp gs arc", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("pit", "决斗", "/hyp gs duel", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("skb", "Skyblock", "/hyp gs skb", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("mm", "密室杀手", "/hyp gs mm", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("bb", "建筑大师", "/hyp gs bb", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("wool", "羊毛游戏", "/hyp gs wool", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("tnt", "TNT游戏", "/hyp gs tnt", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("classic", "经典游戏", "/hyp gs classic", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("bsg", "饥饿游戏", "/hyp gs bsg", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("ptl", "实验大厅", "/hyp gs ptl", ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("mw", "超级战墙", "/hyp gs mw", ButtonStyle.GRAY, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("global", "全部小游戏", "/hyp gs", ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );
    }
}
