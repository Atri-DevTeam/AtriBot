package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author YZ_Ljc_
 * @ClassName BanTracker
 * @Created_at 2026/06/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public final class BanTracker {

    private static final Set<String> VALID_TYPES = Set.of("30min", "1h", "3h", "24h", "7d", "30d");
    private static final long COOLDOWN_MILLIS = 30_000L;
    private static final Map<String, Long> LAST_ACCESS_TIMES = new ConcurrentHashMap<>();

    public static boolean handle(QQCommandSender sender, Command command, String label, String[] args) {

        if (!Objects.equals(label, "bt") && !Objects.equals(label, "bantracker")) {
            sender.sendMessage(Identifier.ONLY_OFFICIAL);
            return true;
        }

        if (isCoolingDown(sender)) {
            return true;
        }

        ImageDTO data = requestData(resolveType(args));
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage("数据获取失败: 发生未知错误!");
            return true;
        }

        Markdown md = TC.md(
                Markdown.img(data.url(), data.width(), data.height())
        );

        Object buttons = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "30m", "/bantracker 30min", true, ButtonStyle.BLUE, ButtonType.COMMAND),
//                                new Button("c2", "1h", "/bantracker 1h", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "3h", "/bantracker 3h", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "24h", "/bantracker 24h", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c5", "7d", "/bantracker 7d", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c6", "30d", "/bantracker 30d", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("c7", "自定义查询时长", "/bantracker ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );

        sender.sendMessage(md, buttons);

        return true;
    }

    public static boolean handle(QQGuildCommandSender sender, Command command, String label, String[] args) {
        if (!Objects.equals(label, "bt") && !Objects.equals(label, "bantracker")) {
            return true;
        }

        if (!isValidType(args)) {
            sender.sendMessage("参数错误: 查询时长必须为 10 分钟至 3 个月之间的时间段，支持的单位包括分钟 (min/m)、小时 (h)、天 (d)、周 (w) 和月 (mo)");
            return true;
        }

        if (isCoolingDown(sender)) {
            return true;
        }

        String type = resolveType(args);
        ImageDTO data = requestData(type);
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage("数据获取失败: 发生未知错误!");
            return true;
        }

        String tip = "当前查询范围: " + type
                + "\n调用方式: /bantracker <30min|1h|3h|24h|7d|30d>";
        sender.sendMessage(ImageComponent.imageOf(data.url()).setText(tip));
        return true;
    }

    private static String resolveType(String[] args) {
        if (args.length < 1) {
            return "30min";
        }
        return args[0];
    }

    private static boolean isValidType(String[] args) {
        if (args.length < 1) {
            return false;
        }

        String type = args[0].toLowerCase();

        if (!type.matches("\\d+(min|m|h|d|w|mo)")) {
            return false;
        }

        long minutes;

        if (type.endsWith("mo")) {
            int value = Integer.parseInt(type.substring(0, type.length() - 2));
            minutes = value * 30L * 24 * 60;
        } else if (type.endsWith("min")) {
            int value = Integer.parseInt(type.substring(0, type.length() - 3));
            minutes = value;
        } else if (type.endsWith("m")) {
            int value = Integer.parseInt(type.substring(0, type.length() - 1));
            minutes = value;
        } else if (type.endsWith("h")) {
            int value = Integer.parseInt(type.substring(0, type.length() - 1));
            minutes = value * 60L;
        } else if (type.endsWith("d")) {
            int value = Integer.parseInt(type.substring(0, type.length() - 1));
            minutes = value * 24L * 60;
        } else {
            int value = Integer.parseInt(type.substring(0, type.length() - 1));
            minutes = value * 7L * 24 * 60;
        }

        return minutes >= 10 && minutes <= 3L * 30 * 24 * 60;
    }

    private static ImageDTO requestData(String type) {
        String url = ResourcesProperties.BAN_TRACKER + "?" + System.currentTimeMillis();
        return PreImageGenerate.dump(url, Map.of("window", type));
    }

    private static boolean isCoolingDown(CommandSender sender) {
        if (sender.hasPermission()) return false;
        long now = System.currentTimeMillis();
        AtomicLong remainingMillis = new AtomicLong();

        LAST_ACCESS_TIMES.compute(sender.getUserId(), (userId, lastAccessTime) -> {
            if (lastAccessTime != null) {
                long remaining = COOLDOWN_MILLIS - (now - lastAccessTime);
                if (remaining > 0) {
                    remainingMillis.set(remaining);
                    return lastAccessTime;
                }
            }
            return now;
        });

        if (remainingMillis.get() <= 0) {
            return false;
        }

        double remainingSeconds = Math.ceil(remainingMillis.get() / 100.0) / 10.0;
        sender.sendMessage("请求过于频繁，请在 %.1f 秒后重试".formatted(remainingSeconds));
        return true;
    }
}