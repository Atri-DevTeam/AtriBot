package top.yzljc.atribot.function.admin.ema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * @Author YZ_Ljc_
 * @ClassName EmaCommand
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.admin.ema
 * @Description 机器人管理员使用的频道第二账号指令
 */
public final class EmaCommand implements CommandExecutor {
    private static final int PAGE_SIZE = 1800;
    private static final long RESULT_TTL = 10 * 60 * 1000L;
    private static final String ROOT_HELP = """
            /ema：频道第二账号操作，仅机器人管理员可用
            格式：/ema <分类> <动作> [可选项] <参数> [正文...]
            /ema help guild 查看分类；/ema help guild join 查看具体用法。
            分类或动作后也可以加 help / --help。

            分类：
            post 帖子查询、发布、图片、Markdown、管理
            comment 评论、回复及其删除、点赞
            guild 频道、版块、入频设置
            member 成员查询、踢出、禁言、解禁
            role 身份组与管理员
            dm 私信
            notice 互动消息与通知
            system 版本、诊断、登录与命令资料

            示例：
            /ema guild list
            /ema guild join 123
            /ema member mute 123 456 10m
            /ema member unmute 123 456
            /ema member kick --yes --blacklist 123 456,789
            /ema dm send 123 456 你好 世界
            /ema post publish --title "今日公告" 123 456 第一行\\n第二行
            /ema comment reply 1 回复正文

            """;
    private final Executor executor;
    private final Dispatcher dispatcher;
    private final Map<String, SavedResult> results = new LinkedHashMap<>();

    public EmaCommand() {
        this(ThreadManager::execute, (route, arguments) -> route.call().apply(arguments));
    }

    EmaCommand(Executor executor, Dispatcher dispatcher) {
        this.executor = executor;
        this.dispatcher = dispatcher;
    }

    @FunctionalInterface
    interface Dispatcher {
        ChannelCliResult execute(EmaRoutes.Route route, EmaArguments arguments);
    }

    private record SavedResult(List<String> pages, long createdAt) {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof NapcatCommandSender)) return true;

        if (!sender.hasPermission()) {
            sender.sendMessage("你没有权限执行此操作！");
            return true;
        }

        try {
            if (args.length == 0) {
                show(sender, ROOT_HELP);
                return true;
            }
            if ("result".equals(args[0])) {
                if (args.length > 2) throw new IllegalArgumentException("用法：/ema result [页码]");
                showPage(sender, args.length == 2 ? Integer.parseInt(args[1]) : 1);
                return true;
            }
            if ("help".equals(args[0]) || "--help".equals(args[0])) {
                help(sender, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                return true;
            }

            EmaRoutes.Route route = null;
            int end = 0;
            // 最多四级业务目录；参数不参与命令名称匹配。
            for (int length = Math.min(4, args.length); length > 0; length--) {
                var candidate = EmaRoutes.ALL.get(String.join(" ", Arrays.copyOfRange(args, 0, length)));
                if (candidate != null) {
                    route = candidate;
                    end = length;
                    break;
                }
            }
            if (route == null) {
                int length = args.length;
                if (Set.of("help", "--help").contains(args[length - 1])) length--;
                help(sender, String.join(" ", Arrays.copyOfRange(args, 0, length)));
                return true;
            }
            if (args.length == end + 1 && Set.of("help", "--help").contains(args[end])) {
                help(sender, route.path());
                return true;
            }
            if (args.length == end && route.parameters().stream().anyMatch(p -> !p.optional())) {
                help(sender, route.path());
                return true;
            }
            EmaArguments input;
            try {
                input = EmaArguments.parse(route, Arrays.copyOfRange(args, end, args.length));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(e.getMessage() + "\n" + route.usage());
                return true;
            }
            EmaRoutes.Route selected = route;
            sender.sendMessage("正在执行 /ema " + route.path() + (input.options().dryRun() ? "（预演）" : ""));
            executor.execute(() -> {
                try {
                    show(sender, "/ema " + selected.path() + "\n" + format(dispatcher.execute(selected, input)));
                } catch (Exception e) {
                    sender.sendMessage("频道调用失败：" + e.getMessage());
                }
            });
        } catch (Exception e) {
            sender.sendMessage("EMA：" + e.getMessage());
        }
        return true;
    }

    private void help(CommandSender sender, String path) {
        if (path.isBlank()) {
            show(sender, ROOT_HELP);
            return;
        }
        EmaRoutes.Route route = EmaRoutes.ALL.get(path);
        if (route != null) {
            StringBuilder message = new StringBuilder(route.usage()).append('\n').append(route.description());
            for (var parameter : route.parameters()) {
                message.append('\n').append(parameter.optional() ? "--" + parameter.name() : parameter.label())
                        .append("：").append(parameter.description());
                if (!parameter.choices().isEmpty()) message.append("；可选 ").append(String.join("、", parameter.choices()));
            }
            message.append("\n示例：/ema ").append(path);
            if (!route.example().isBlank()) message.append(' ').append(route.example());
            message.append(route.supportsOptions() ? "\n可选项放在位置参数前。" : "\n此方法不支持 --yes / --dry-run。");
            appendChildren(message, path);
            show(sender, message.toString());
            return;
        }
        StringBuilder message = new StringBuilder("/ema ").append(path);
        if (!appendChildren(message, path)) {
            sender.sendMessage("未知子指令：" + path + "；使用 /ema help 查看目录");
            return;
        }
        show(sender, message.toString());
    }

    private static boolean appendChildren(StringBuilder message, String path) {
        String prefix = path + " ";
        Map<String, String> children = new LinkedHashMap<>();
        for (EmaRoutes.Route child : EmaRoutes.ALL.values()) {
            if (!child.path().startsWith(prefix)) continue;
            String suffix = child.path().substring(prefix.length());
            int space = suffix.indexOf(' ');
            children.putIfAbsent(space < 0 ? suffix : suffix.substring(0, space),
                    space < 0 ? child.description() : "子分类");
        }
        if (children.isEmpty()) return false;
        message.append("\n子指令：\n");
        children.forEach((name, description) -> message.append(name).append("：").append(description).append('\n'));
        message.append("用 /ema help ").append(path).append(" <子指令> 查看用法。");
        return true;
    }

    /** JSON 结果保留接口错误码及翻页字段，登录凭据不回显到聊天中。 */
    static String format(ChannelCliResult result) {
        String body = result.response().isMissingNode()
                ? result.stdout() + (result.stderr().isBlank() ? "" : "\n" + result.stderr())
                : redact(result.response()).toPrettyString();
        return (result.success() ? "成功" : result.timedOut() ? "执行超时" : "失败，退出码 " + result.exitCode())
                + "\n" + body;
    }

    private static JsonNode redact(JsonNode source) {
        JsonNode copy = source.deepCopy();
        if (copy instanceof ObjectNode object) {
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                String key = name.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
                if (Set.of("token", "logintoken", "accesstoken", "refreshtoken", "cookie", "cookies",
                        "authorization", "password", "secret").contains(key)) {
                    object.put(name, "[已隐藏]");
                } else {
                    object.set(name, redact(object.get(name)));
                }
            }
        } else if (copy.isArray()) {
            for (int i = 0; i < copy.size(); i++) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) copy).set(i, redact(copy.get(i)));
            }
        }
        return copy;
    }

    private static String scope(CommandSender sender) {
        String location = "";
        if (sender instanceof QQCommandSender qq) {
            location = qq.getPlatform() + ":" + qq.getGroupId();
        } else if (sender instanceof QQGuildCommandSender guild) {
            location = guild.getPlatform() + ":" + guild.getGuildId() + ":" + guild.getChannelId();
        } else if (sender instanceof NapcatCommandSender napcat) {
            location = napcat.getPlatform() + ":" + napcat.getGroupId();
        }
        return sender.getClass().getName() + ":" + location + ":" + sender.getUserId();
    }

    private void show(CommandSender sender, String content) {
        List<String> pages = new ArrayList<>();
        for (int start = 0; start < content.length();) {
            int end = Math.min(start + PAGE_SIZE, content.length());
            if (end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) end--;
            pages.add(content.substring(start, end));
            start = end;
        }
        if (pages.isEmpty()) pages.add("（空结果）");
        synchronized (results) {
            results.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().createdAt() > RESULT_TTL);
            results.put(scope(sender), new SavedResult(List.copyOf(pages), System.currentTimeMillis()));
            while (results.size() > 64) results.remove(results.keySet().iterator().next());
        }
        showPage(sender, 1);
    }

    private void showPage(CommandSender sender, int page) {
        SavedResult saved;
        synchronized (results) {
            saved = results.get(scope(sender));
        }
        if (saved == null || System.currentTimeMillis() - saved.createdAt() > RESULT_TTL) {
            sender.sendMessage("没有可查看的结果，或结果已过期。");
        } else if (page < 1 || page > saved.pages().size()) {
            sender.sendMessage("页码范围：1-" + saved.pages().size());
        } else {
            sender.sendMessage(saved.pages().get(page - 1) + (saved.pages().size() > 1
                    ? "\n[" + page + "/" + saved.pages().size() + "] /ema result <页码> 查看其他页" : ""));
        }
    }
}
