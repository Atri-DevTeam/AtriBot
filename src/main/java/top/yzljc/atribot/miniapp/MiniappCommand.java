package top.yzljc.atribot.miniapp;

import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.Platform;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappCommand
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public final class MiniappCommand implements CommandExecutor {
    private final MiniappSessions sessions;
    private final BooleanSupplier enabled;
    private final Supplier<String> baseUrl;

    public MiniappCommand(MiniappSessions sessions) {
        this(sessions, () -> Config.getInstance().isMiniappEnabled(), () -> Config.getInstance().getMiniappBaseUrl());
    }

    MiniappCommand(MiniappSessions sessions, BooleanSupplier enabled, Supplier<String> baseUrl) {
        this.sessions = sessions;
        this.enabled = enabled;
        this.baseUrl = baseUrl;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq) || qq.getPlatform() != Platform.OFFICIAL_C2C || qq.isBot()) {
            sender.sendMessage("[!] 该指令仅可在私聊中使用！");
            return true;
        }

//        if (!sender.hasPermission("atri.miniapp")) {
//            sender.sendMessage("[!] 该指令正在内测中，请联系开发者获取使用权限！");
//            return true;
//        }

        String entryUrl = publicEntryUrl(baseUrl.get());
        if (!enabled.getAsBoolean() || entryUrl == null) {
            sender.sendMessage("[!] 档案查看暂未开放，敬请期待！");
            return true;
        }
        MiniappSessions.Issued issued = sessions.issue(sender.getUserId(), sender.getUsername());
        if (issued == null) {
            sender.sendMessage("[!] 档案查看申请过于频繁，请稍后重试！");
            return true;
        }
        String url = entryUrl + "?_nav_alpha=0&userId="
                + URLEncoder.encode(sender.getUserId(), StandardCharsets.UTF_8) + "&ticket=" + issued.ticket();

        var md = TC.md("【内测中】请从此处查看个人档案");
        Object keyboard = TC.keyboard(
                List.of(
                        List.of(
                                new Button("btn", "我的个人档案", url, true, ButtonStyle.BLUE, ButtonType.LINK)
                        )
                )
        );

        qq.sendMessage(md, keyboard);
        return true;
    }

    static String publicEntryUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.trim());
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (!("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) return null;
            String path = uri.getRawPath();
            if (path.isEmpty() || "/".equals(path)) path = "/atrimeow/profile/";
            return uri.getScheme() + "://" + uri.getRawAuthority() + path;
        } catch (IllegalArgumentException ignored) { return null; }
    }
}
