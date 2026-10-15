package top.yzljc.atribot.plugins.kook;

import java.util.*;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.kook.KookCard;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ImageDelivery;
import top.yzljc.atribot.function.minecraft.HypixelDiceImpl;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;

/**
 * @Author YZ_Ljc_
 * @ClassName KookHypixelCommand
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugins.kook
 */
public final class KookHypixelCommand implements CommandExecutor {
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


    private static final Map<String, Entry> COMMANDS = createCommands();
    private static Map<String, Entry> createCommands() {
        Map<String, Entry> commands = new LinkedHashMap<>();
        commands.put("wz", new Entry("查询玩家法师掘战详细数据", false));
        commands.put("zs", new Entry("查询玩家僵尸末日详细数据", false));
        commands.put("gs", new Entry("全服小游戏在线情况", false));
        commands.put("pack", new Entry("查询Skyblock资源包版本信息", true));
        commands.put("dice", new Entry("随机Skyblock Dice(鉴定你的欧气)", true));
        commands.put("coop", new Entry("查询玩家Skyblock Coop在线情况", true));
        commands.put("dungeon", new Entry("查询玩家最近地牢游玩场次", true));
        commands.put("lf", new Entry("查询玩家大厅钓鱼数据", false));
        commands.put("hotf", new Entry("查看玩家Skyblock树心数据", true));
        commands.put("pr", new Entry("查询玩家大厅跑酷详细数据", false));
        commands.put("dpr", new Entry("查询玩家街机心跳水立方详细数据", false));
        commands.put("bw", new Entry("查询玩家起床战争详细数据", false));
        commands.put("sh", new Entry("查询玩家起床战争入梦酒店任务树", false));
        commands.put("cr", new Entry("查询Skyblock日历", true));
        commands.put("ip", new Entry("查询Skyblock物品价格信息", true));
        commands.put("hotm", new Entry("查询玩家Skyblock山之心数据", true));
        return Collections.unmodifiableMap(commands);
    }
    private record Entry(String description, boolean skyblock) {}
    private static final Map<String, String> PLAYER_APIS = Map.ofEntries(
            Map.entry("wz", "bot.hypixel.tnt-wizards"), Map.entry("zs", "bot.hypixel.zombies"),
            Map.entry("coop", "bot.hypixel.skyblock.coop"), Map.entry("dungeon", "bot.hypixel.skyblock.dungeons"),
            Map.entry("lf", "bot.hypixel.fishing"), Map.entry("pr", "bot.hypixel.parkour"),
            Map.entry("dpr", "bot.hypixel.dropper"), Map.entry("bw", "bot.hypixel.bedwars"),
            Map.entry("sh", "bot.hypixel.bedwars.slumber"), Map.entry("hotf", "bot.hypixel.skyblock.forest"),
            Map.entry("hotm", "bot.hypixel.skyblock.mountain"));

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof KookCommandSender kook)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("skb")) {
            if (args.length > 1) kook.sendMessage("用法: /hyp skb\n查看分类后，请使用菜单中显示的原有指令。");
            else sendMenu(kook, args.length > 0);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!COMMANDS.containsKey(sub)) {
            kook.sendMessage("未知的子命令，请使用 /hyp 查看可用的子命令列表");
            return true;
        }
        String[] parameters = Arrays.copyOfRange(args, 1, args.length);
        if (sub.equals("pack")) return KookCommands.sendPack(kook);
        if (sub.equals("dice")) {
            long count = 1;
            if (parameters.length > 0) {
                try { count = Long.parseLong(parameters[0]); } catch (NumberFormatException ignored) { }
            }
            String result = HypixelDiceImpl.result(Math.max(1, count), "");
            kook.sendKMarkdown(result.replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "") + "\n再次投掷：`/hyp dice [次数]`");
            return true;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        String key;
        String search = null;
        if (sub.equals("gs")) {
            if (parameters.length > 1) {
                kook.sendMessage("用法: /hyp gs [小游戏]\n支持: " + GAME_ALIAS_HELP);
                return true;
            }
            if (parameters.length == 1) {
                String game = GAME_IDS_BY_ALIAS.get(parameters[0].toLowerCase(Locale.ROOT));
                if (game == null) { kook.sendMessage("未知的小游戏编号，请在总人数列表里查看可用参数。"); return true; }
                request.put("game", game);
            }
            key = "bot.hypixel.status";
        } else if (sub.equals("cr")) {
            key = "bot.hypixel.skyblock.calendar";
        } else if (sub.equals("ip")) {
            if (parameters.length == 0) { kook.sendMessage("未指定查询物品，请指定查询目标。"); return true; }
            int end = parameters.length;
            if (end >= 3 && parameters[end - 2].equals("--cursor")) {
                request.put("cursor", parameters[end - 1]); end -= 2;
            }
            search = String.join(" ", Arrays.copyOf(parameters, end));
            request.put("q", search);
            key = "bot.hypixel.skyblock.price";
        } else {
            if (parameters.length == 0) { kook.sendMessage("请指定 Minecraft 玩家名或 UUID。"); return true; }
            request.put("player", parameters[0]);
            if ((sub.equals("hotf") || sub.equals("hotm")) && parameters.length == 2) request.put("profile", parameters[1]);
            key = PLAYER_APIS.get(sub);
        }
        String api = OpenApi.get(key);
        var result = KookCommands.withProgress(kook, "正在查询相关数据，请稍等片刻...",
                () -> Requests.post(api, request, "Authorization", "Bearer " + Config.getInstance().getAtribotKeySecret()));
        if (!result.isSuccess()) { kook.sendMessage(result.message()); return true; }
        var data = result.data();
        String url = ImageDelivery.resolve(data, api, kook.getPlatform());
        if (url == null) { kook.sendMessage("图片地址无效，请稍后重试。"); return true; }
        StringBuilder text = new StringBuilder();
        if (sub.equals("gs")) text.append("查询指定小游戏：/hyp gs <编号>\n").append(GAME_ALIAS_HELP);
        if (sub.equals("hotf") || sub.equals("hotm")) {
            for (var profile : data.path("availableProfiles")) {
                text.append("/hyp ").append(sub).append(' ').append(parameters[0]).append(' ').append(profile.asText()).append('\n');
            }
            if (!text.isEmpty()) text.insert(0, "切换存档：\n");
        }
        if (sub.equals("ip")) {
            String previous = data.path("pre_cursor").asText(null), next = data.path("next_cursor").asText(null);
            if (previous != null && !previous.isBlank()) text.append("上一页：/hyp ip ").append(search).append(" --cursor ").append(previous).append('\n');
            if (next != null && !next.isBlank()) text.append("下一页：/hyp ip ").append(search).append(" --cursor ").append(next);
        }
        kook.sendMessage(ImageComponent.imageOf(url).setText(text.isEmpty() ? null : text.toString()));
        return true;
    }

    private static void sendMenu(KookCommandSender sender, boolean skyblock) {
        StringBuilder text = new StringBuilder();
        if (!skyblock) text.append("`/hyp skb` SkyBlock 指令二级菜单\n");
        COMMANDS.forEach((name, entry) -> {
            if (entry.skyblock() == skyblock) text.append("`/hyp ").append(name).append("` ").append(entry.description()).append('\n');
        });
        sender.sendCard(new KookCard().header("Hypixel " + (skyblock ? "SkyBlock" : "通用") + "菜单").markdown(text.toString()));
    }
}
