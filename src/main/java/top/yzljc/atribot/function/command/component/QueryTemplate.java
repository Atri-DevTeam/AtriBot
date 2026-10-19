package top.yzljc.atribot.function.command.component;

import org.jetbrains.annotations.Nullable;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ImageDelivery;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atricore.platform.qq.QQBot;
import top.yzljc.atribot.service.request.Requests;
import top.yzljc.atricore.utils.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName QueryTemplate
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command.component
 */
public final class QueryTemplate {

    public static String getTemplate(ImageDTO i, String userOpenId, String queriedPlayer) {
        return (
                Markdown.img("atri_bot_pic", i.url(), i.width(), i.height()) + "\n\n" +
                        Markdown.at(userOpenId) + " 根据开放平台要求，用户提交的自定义内容须经过审查后才能显示，请使用 " + Markdown.enterCommand("/feedback 白名单审查 " + queriedPlayer, "/反馈 玩家名") + " 提交审核。"
        );
    }

    public static Keyboard getKeyboard(String commandHeader, String subCommand) {
        return new Keyboard(
                List.of(
                        List.of(
                                new Button("t1", "再次查询", commandHeader+ " " + subCommand + " ", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                                // new Button("t2", "加白审核", "/feedback 白名单审查 " + queriedPlayer, false, ButtonStyle.BLUE, ButtonType.COMMAND).setModal("你确定要提交当前被查询玩家的名称和皮肤进行审查吗？", "提交", "取消")
                                new Button("l2", "自定义背景", "/bg", true, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND)
                        )
//                        List.of(
//                                new Button("t1", "再次查询", "/hyp " + subCommand + " ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
//                        )
                )
        );
    }

    public static List<Button> getQueryButtonTemplate(String commandHeader, String subCommand) {
        return List.of(
                new Button("t1", "再次查询", commandHeader + " " + subCommand + " ", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                new Button("l2", "自定义背景", "/bg", true, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND)
        );
    }

    public static @Nullable String getPlayerOrNull(String userOpenId, String queriedPlayer) {
        if (queriedPlayer != null && !StringUtils.isBlank(queriedPlayer)) return queriedPlayer;

        if (userOpenId == null) return null;

        var a = UnifiedAuthentication.findByQqUserOpenId(userOpenId);
        if (a == null) return null;
        return a.minecraftUuid();
    }

    public static Result customQueryRequest(String api, Map<?, ?> requestBody) {
        return customQueryRequest(api, requestBody, "Authorization", bearer());
    }

    public static Result customQueryRequest(String api, Map<?, ?> requestBody, String... headers) {
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

    // 提交等待并执行撤回任务
    public static <T> T withQueryProgress(QQCommandSender user, String message, Supplier<T> query) {
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

    public static Map<String, Object> getRequestBody(String userOpenId, String queriedPlayer) {
        Map<String, Object> r = new LinkedHashMap<>();

        r.put("player", queriedPlayer);
        BackgroundService.applyApprovedBackground(r, userOpenId);
        return r;
    }

    private static String bearer() {
        return "Bearer " + Config.getInstance().getAtribotKeySecret();
    }
}