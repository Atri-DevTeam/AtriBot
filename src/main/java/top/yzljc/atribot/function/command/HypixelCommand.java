package top.yzljc.atribot.function.command;

import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.function.minecraft.BedwarsProfileImpl;
import top.yzljc.atribot.function.minecraft.SkywarsProfileImpl;
import top.yzljc.atribot.function.skyblockprice.SkyblockPriceAlertService;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atribot.function.command.component.QueryTemplate;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.HypixelDiceImpl;
import top.yzljc.atribot.i18n.I18N;
import top.yzljc.atricore.platform.qq.QQBot;
import top.yzljc.atribot.service.request.OpenApi;

import static top.yzljc.atribot.function.command.component.QueryTemplate.withQueryProgress;
import static top.yzljc.atribot.function.command.component.QueryTemplate.customQueryRequest;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelCommand
 * @Created_at 2026/08/25
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 * @Description Hypixel -> 综合查询二级菜单
 */
public class HypixelCommand implements CommandExecutor {

    private static final long QUERY_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final Cache<String, Long> QUERY_TIMES = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS).build();

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
        register(commands, new SubCommand("lb", Category.GENERAL, "查询玩家大厅总览数据", RS.get("resource.image.icon-compass"), HypixelCommand::handleLobbyStats));
        register(commands, new SubCommand("wz", Category.GENERAL, "查询玩家法师掘战详细数据", RS.get("resource.image.icon-tnt"), HypixelCommand::handleWizards));
        register(commands, new SubCommand("zs", Category.GENERAL, "查询玩家僵尸末日详细数据", RS.get("resource.image.icon-zombie-head"), HypixelCommand::handleZombies));
        register(commands, new SubCommand("gs", Category.GENERAL, "全服小游戏在线情况", RS.get("resource.image.hypixel-header"), HypixelCommand::handleGameStatus));
        register(commands, new SubCommand("pack", Category.SKYBLOCK, "查询sᴋʏʙʟᴏᴄᴋ资源包版本信息", RS.get("resource.image.icon-knowledge-book"), HypixelCommand::handlePack));
        register(commands, new SubCommand("dice", Category.SKYBLOCK, "随机sᴋʏʙʟᴏᴄᴋ ᴅɪᴄᴇ(鉴定你的欧气)", RS.get("resource.image.dice-result-template").replace("<id>", "6"), HypixelCommand::handleDice));
        register(commands, new SubCommand("coop", Category.SKYBLOCK, "查询玩家sᴋʏʙʟᴏᴄᴋ ᴄᴏᴏᴘ在线情况", RS.get("resource.image.icon-diamond-pickaxe"), HypixelCommand::handleCoop));
        register(commands, new SubCommand("dungeon", Category.SKYBLOCK, "查询玩家最近地牢游玩场次", RS.get("resource.image.icon-skyblock-dungeon"), HypixelCommand::handleDungeon));
        register(commands, new SubCommand("lf", Category.GENERAL, "查询玩家大厅钓鱼数据", RS.get("resource.image.icon-fishing-rod"), HypixelCommand::handleLobbyFishing));
        register(commands, new SubCommand("hotf", Category.SKYBLOCK, "查看玩家sᴋʏʙʟᴏᴄᴋ树心数据", RS.get("resource.image.icon-hotf"), HypixelCommand::handleHotf));
        register(commands, new SubCommand("pr", Category.GENERAL, "查询玩家大厅跑酷详细数据", RS.get("resource.image.icon-parkour"), HypixelCommand::handleParkour));
        register(commands, new SubCommand("dpr", Category.GENERAL, "查询玩家街机心跳水立方详细数据", RS.get("resource.image.icon-dropper"), HypixelCommand::handleArcadeDropper));
        register(commands, new SubCommand("bw", Category.GENERAL, "查询玩家起床战争详细数据", RS.get("resource.image.icon-bedwars"), HypixelCommand::handleBedwars));
        register(commands, new SubCommand("sh", Category.GENERAL, "查询玩家起床战争入梦酒店任务树", RS.get("resource.image.icon-slumber-hotel"), HypixelCommand::handleSlumberHotel));
        register(commands, new SubCommand("cr", Category.SKYBLOCK, "查询sᴋʏʙʟᴏᴄᴋ日历", RS.get("resource.image.icon-skyblock-calendar"), HypixelCommand::handleSkyblockCalendar));
        register(commands, new SubCommand("ip", Category.SKYBLOCK, "查询sᴋʏʙʟᴏᴄᴋ物品价格信息", RS.get("resource.image.skyblock-bank-logo"), HypixelCommand::handleSearchSkyblockItemPrice));
        register(commands, new SubCommand("hotm", Category.SKYBLOCK, "查询玩家sᴋʏʙʟᴏᴄᴋ山之心数据", RS.get("resource.image.icon-skyblock-hotm"), HypixelCommand::handleHotm));
        register(commands, new SubCommand("rift", Category.SKYBLOCK, "查询玩家sᴋʏʙʟᴏᴄᴋ ʀɪғᴛ数据", RS.get("resource.image.icon-skyblock-rift"), HypixelCommand::handleRift));
        register(commands, new SubCommand("sw", Category.GENERAL, "查询玩家空岛战争详细数据", RS.get("resource.image.icon-skywars"), HypixelCommand::handleSkywars));
        register(commands, new SubCommand("angels", Category.GENERAL, "查询玩家空岛战争天使血统进度", RS.get("resource.image.icon-skywars-angels"), HypixelCommand::handleSkywarsAngels));
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

    private static Markdown getSubCommands(QQCommandSender sender, Category category) {
        StringBuilder s = new StringBuilder();
        String title = category == Category.GENERAL ? "**ʜʏᴘɪxᴇʟ 综合查询菜单**\n\n" : "**ʜʏᴘɪxᴇʟ " + category.title + " 指令菜单**\n\n";
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
            if (cmd.prefix().equals("ip") && !sender.hasPermission(SkyblockPriceAlertService.USE_PERMISSION)) continue;
            s.append("> ").append(Markdown.img(cmd.icon(), 16, 16)).append(Markdown.enterCommand(cmdPrefix + cmd.prefix() + " ", cmd.description())).append("\n");
        }
        return new Markdown(s.toString());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQGuildCommandSender user && label.equals("skbpack")) {
            return Atri.getInstance().getSkyblockPackCheck().onCommand(user);
        }
        if (!(sender instanceof QQCommandSender user)) return true;
        if (args.length == 0) {
            sendMenu(user, Category.GENERAL);
            return true;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        Category category = Category.fromCommand(prefix);
        if (category != null) {
            if (args.length != 1) {
                user.sendMessage("用法: /hyp " + category.command + "\n查看分类后，请使用菜单中显示的原有指令。");
            } else {
                sendMenu(user, category);
            }
            return true;
        }
        var sub = SUB_COMMANDS.get(prefix);
        if (sub == null) {
            user.sendMessage("未知的子命令，请使用 /hyp 查看可用的子命令列表");
            return true;
        }
//        if (prefix.equals("ip") && !user.hasPermission(SkyblockPriceAlertService.USE_PERMISSION)) {
//            user.sendMessage("你没有使用此功能的权限（bot.skyblock.price）。");
//            return true;
//        }
        if (isCoolingDown(user)) return true;
        return sub.handler().execute(user, Arrays.copyOfRange(args, 1, args.length));
    }

    private static boolean isCoolingDown(QQCommandSender user) {
        if (user.hasPermission()) return false;
        long now = System.nanoTime();
        Long previous = QUERY_TIMES.asMap().putIfAbsent(user.getUserId(), now);
        if (previous == null) return false;

        long remaining = Math.max(1, (long) Math.ceil((QUERY_COOLDOWN_NANOS - (now - previous)) / 1_000_000_000.0));
        user.sendMessage("查询过于频繁，请在 " + remaining + " 秒后重试喵");
        return true;
    }

    private static void sendMenu(QQCommandSender sender, Category category) {
        sender.sendMessage(getSubCommands(sender, category), keyboard);
    }

    private static void sendMissingPlayer(QQCommandSender sender) {
        sender.sendMessage("笨蛋喵，你没有绑定用户信息，请指定一个玩家或使用/bind完成绑定。");
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

        Map<String, Object> request = new LinkedHashMap<>();
        if (args.length != 0) {
            String gameId = GAME_IDS_BY_ALIAS.get(args[0].toLowerCase(Locale.ROOT));
            if (gameId == null) {
                user.sendMessage("未知的小游戏编号，请在总人数列表里查看可用参数。");
                return true;
            }
            request.put("game", gameId);
        }

        BackgroundService.applyApprovedBackground(request, user.getUserId());

        var result = withQueryProgress(user, "正在查询目标数据，请稍等片刻...",
                () -> PreImageGenerate.dump(OpenApi.get("bot.hypixel.status"), request));
        if (result.isError() || result.url() == null) {
            sendImageResult(user, result, null);
            return true;
        }
        if (OfficialUsers.isUserUnsupportedKeyboard(user.getUserId())) {
            user.sendMessage(TC.md(Markdown.img("atri_bot_pic", result.url(), result.width(), result.height()) + "\n\n" + Markdown.at(user.getUserId())).setKeyboard(getMiniGames(), true), false);
        } else {
            user.sendMessage(TC.md(Markdown.img("atri_bot_pic", result.url(), result.width(), result.height()) + "\n\n" + Markdown.at(user.getUserId())), getMiniGames(), false);
        }
        return true;
    }

    private static boolean handlePack(QQCommandSender user, String[] args) {
        return new SkyblockPackCommand().onCommand(user, null, "skbpack", args);
    }

    private static boolean handleDice(QQCommandSender user, String[] args) {
        HypixelDiceImpl.handle(user, args);
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
        String player = args.length > 0 ? args[0] : null;

        String p = QueryTemplate.getPlayerOrNull(user.getUserId(), player);
        if (p == null) {
            user.sendMessage(I18N.text("command.mc.noplayer"));
            return true;
        }
        return BedwarsProfileImpl.slumberHotelProfile(user, p);
    }

    private static boolean handleBedwars(QQCommandSender user, String[] args) {
        String player = args.length > 0 ? args[0] : null;

        String p = QueryTemplate.getPlayerOrNull(user.getUserId(), player);
        if (p == null) {
            user.sendMessage(I18N.text("command.mc.noplayer"));
            return true;
        }
        return BedwarsProfileImpl.bedwarsProfile(user, p);
    }

    private static boolean handleSkywars(QQCommandSender user, String[] args) {
        String player = args.length > 0 ? args[0] : null;

        String p = QueryTemplate.getPlayerOrNull(user.getUserId(), player);
        if (p == null) {
            user.sendMessage(I18N.text("command.mc.noplayer"));
            return true;
        }

        if (args.length >= 2) {
            String mode = args[1].toLowerCase();
            return SkywarsProfileImpl.getSkywarsKit(user, p, mode);
        }

        return SkywarsProfileImpl.getSkywarsGeneralProfile(user, p);
    }

    private static boolean handleSkywarsAngels(QQCommandSender user, String[] args) {
        String player = args.length > 0 ? args[0] : null;

        String p = QueryTemplate.getPlayerOrNull(user.getUserId(), player);
        if (p == null) {
            user.sendMessage(I18N.text("command.mc.noplayer"));
            return true;
        }

        return SkywarsProfileImpl.getSkywarsAngels(user, p);
    }

    private static boolean handleSkyblockCalendar(QQCommandSender user, String[] args) {
        Map<String, Object> request = new LinkedHashMap<>();
        BackgroundService.applyApprovedBackground(request, user.getUserId());

        var result = withQueryProgress(user, "正在查询相关数据，请稍等片刻...",
                () -> customQueryRequest(OpenApi.get("bot.hypixel.skyblock.calendar"), request, "Authorization", bearer()));

        if (!result.success()) {
            user.sendMessage(result.message());
            return true;
        }

        sendImageResult(user, result.i(), null);
        return true;
    }

    private static boolean handleHotf(QQCommandSender user, String[] args) {
        return multipleProfileQuery(user, args, OpenApi.get("bot.hypixel.skyblock.forest"), "hotf");
    }

    private static boolean handleHotm(QQCommandSender user, String[] args) {
        return multipleProfileQuery(user, args, OpenApi.get("bot.hypixel.skyblock.mountain"), "hotm");
    }

    private static boolean handleRift(QQCommandSender user, String[] args) {
        return multipleProfileQuery(user, args, OpenApi.get("bot.hypixel.skyblock.rift"), "rift");
    }

    private static boolean multipleProfileQuery(QQCommandSender user, String[] args, String api, String subCommand) {
        String player = getPlayer(user, args);
        if (player == null) {
            sendMissingPlayer(user);
            return true;
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("player", player);
        if (args.length == 2) {
            requestBody.put("profile", args[1]);
        }
        BackgroundService.applyApprovedBackground(requestBody, user.getUserId());

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
            for (var p : pn) {
                profiles.add(p.asText());
            }
        }


        List<List<Button>> buttons = new ArrayList<>();
        for (int i = 0; i < profiles.size(); i += 3) {
            List<Button> row = new ArrayList<>();
            for (int j = i; j < Math.min(i + 3, profiles.size()); j++) {
                String profile = profiles.get(j);
                row.add(new Button("btn_" + System.currentTimeMillis(), profile, "/hyp " + subCommand + " " + player + " " + profile, true, ButtonStyle.GRAY, ButtonType.COMMAND));
            }
            buttons.add(row);
        }
        buttons.add(QueryTemplate.getQueryButtonTemplate("/hyp", subCommand));

        Object keyboard = TC.keyboard(buttons);
        user.sendMessage(TC.md(QueryTemplate.getTemplate(result.i(), user.getUserId(), player)), keyboard, false);
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

        String cursor = null;
        int end = args.length;
        if (args.length >= 3 && args[args.length - 2].equals("--cursor")) {
            cursor = args[args.length - 1];
            end -= 2;
        }
        String search = String.join(" ", Arrays.copyOf(args, end));
        Map<String, Object> request = priceRequest(search, cursor, user.getUserId());
        var result = withQueryProgress(user, "正在查询相关数据，请稍等片刻...",
                () -> customQueryRequest(OpenApi.get("bot.hypixel.skyblock.price"), request, "Authorization", bearer()));

        if (!result.success()) {
            user.sendMessage(result.message());
            return true;
        }


        if (!user.hasPermission(SkyblockPriceAlertService.USE_PERMISSION)) {
            user.sendMessage("你没有使用此功能的权限（bot.skyblock.price）。");
            return true;
        }
        String nextCursor = result.d().path("next_cursor").asText(null);
        String previousCursor = result.d().path("pre_cursor").asText(null);

        user.sendMessage(getSkyblockPriceMarkdown(result.i().url(), result.i().width(), result.i().height(), user.getUserId())
                .setKeyboard(getSkyblockPriceKeyboard(search, previousCursor, nextCursor)), false);

        return true;
    }

    private static Map<String, Object> priceRequest(String query, String cursor, String userId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("q", query);
        if (cursor != null) request.put("cursor", cursor);
        BackgroundService.applyApprovedBackground(request, userId);
        return request;
    }

    private static Keyboard getSkyblockPriceKeyboard(String search, String previousCursor, String nextCursor) {
        List<List<Button>> rows = new ArrayList<>();
        List<Button> pages = new ArrayList<>();
        String command = "/hyp ip " + search + " --cursor ";
        if (previousCursor != null && !previousCursor.isBlank()) {
            pages.add(new Button("skyblock_previous", "上一页", command + previousCursor, true, ButtonStyle.GRAY, ButtonType.COMMAND));
        }
        if (nextCursor != null && !nextCursor.isBlank()) {
            pages.add(new Button("skyblock_next", "下一页", command + nextCursor, true, ButtonStyle.GRAY, ButtonType.COMMAND));
        }
        if (!pages.isEmpty()) rows.add(pages);
        rows.add(List.of(new Button("c1", "查询其他物品", "/hyp ip ", false, ButtonStyle.BLUE, ButtonType.COMMAND)));
        return new Keyboard(rows);
    }

    private static Markdown getSkyblockPriceMarkdown(String url, int w, int h, String userOpenId) {
        return TC.md(
                Markdown.img("atri_bot_pic", url, w, h) + "\n\n" + Markdown.at(userOpenId)
        );
    }

    private static String getPlayer(QQCommandSender sender, String[] args) {
        if (args.length > 0) {
            return args[0];
        }
        var account = UnifiedAuthentication.findByQqUserOpenId(sender.getUserId());
        return account == null ? null : account.minecraftUuid();
    }

    private static void sendImageResult(QQCommandSender user, ImageDTO result, String text) {
        if (result == null) {
            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        } else if (result.isError()) {
            user.sendMessage(result.errorMessage());
        } else if (result.url() == null || result.url().isBlank()) {
            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        } else {
            ImageComponent image = ImageComponent.imageOf(result.url()).setText(text);
            user.sendMessage(image);
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
        String player = getPlayer(sender, args);
        if (player == null) {
            sendMissingPlayer(sender);
            return true;
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("player", player);
        BackgroundService.applyApprovedBackground(request, sender.getUserId());
        var result = withQueryProgress(sender, I18N.text("command.mc.querying"),
                () -> customQueryRequest(api, request, "Authorization", bearer()));

        if (!result.success()) {
            sender.sendMessage(result.message());
            return true;
        }

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(result.i(), sender.getUserId(), player)).setKeyboard(QueryTemplate.getKeyboard("/hyp", subCommand)), false);

        return true;
    }

    private enum Category {
        GENERAL("", "通用", RS.get("resource.image.hypixel-header")),
        SKYBLOCK("skb", "sᴋʏʙʟᴏᴄᴋ", RS.get("resource.image.skyblock-logo"));

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

    private record SubCommand(String prefix, Category category, String description, String icon,
                              SubCommandHandler handler) {
    }

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

    private static boolean handleLobbyStats(QQCommandSender sender, String[] args) {
        String player = args.length > 0 ? args[0] : null;

        String p = QueryTemplate.getPlayerOrNull(sender.getUserId(), player);
        if (p == null) {
            sender.sendMessage(I18N.text("command.mc.noplayer"));
            return true;
        }

        String userOpenId = sender.getUserId();
        var r = QueryTemplate.getRequestBody(userOpenId, p);
        var api = OpenApi.get("bot.hypixel.profile");

        var q = withQueryProgress(sender, I18N.text("command.mc.querying"), () -> customQueryRequest(api, r));

        if (!q.success()) {
            sender.sendMessage(q.message());
            return true;
        }

        List<List<Button>> btns = List.of(
                List.of(
                        new Button("在线情况", "/hyp gs", ButtonStyle.GRAY),
                        new Button("大厅钓鱼", "/hyp lf " + p, ButtonStyle.GRAY),
                        new Button("大厅跑酷", "/hyp pr " + p, ButtonStyle.GRAY)
                )
        );

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(q.i(), userOpenId, p)).setKeyboard(QueryTemplate.getKeyboard("/hyp", "lb").append(btns, true)), false);
        return true;
    }
}
