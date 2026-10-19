package top.yzljc.atribot.function.minecraft;

import top.yzljc.atribot.function.command.component.QueryTemplate;
import top.yzljc.atribot.i18n.I18N;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.command.QQCommandSender;

import java.util.List;
import java.util.Objects;

import static top.yzljc.atribot.function.command.component.QueryTemplate.customQueryRequest;
import static top.yzljc.atribot.function.command.component.QueryTemplate.withQueryProgress;

/**
 * @Author YZ_Ljc_
 * @ClassName SkywarsProfileImpl
 * @Created_at 2026/10/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.minecraft
 */
public final class SkywarsProfileImpl {

    public static boolean getSkywarsGeneralProfile(QQCommandSender sender, String player) {
        String userOpenId = sender.getUserId();
        var r = QueryTemplate.getRequestBody(userOpenId, player);
        var api = OpenApi.get("bot.hypixel.skywars");

        var q = withQueryProgress(sender, I18N.text("command.mc.querying"), ()-> customQueryRequest(api, r));

        if (!q.success()) {
            sender.sendMessage(q.message());
            return true;
        }

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(q.i(), userOpenId, player)).setKeyboard(QueryTemplate.getKeyboard("/hyp", "sw").append(getSkywarsButtons(player), true)), false);
        return true;
    }

    public static boolean getSkywarsKit(QQCommandSender sender, String player, String mode) {
        String userOpenId = sender.getUserId();
        var r = QueryTemplate.getRequestBody(userOpenId, player);

        if (!Objects.equals(mode, "mini") && !Objects.equals(mode, "normal") && !Objects.equals(mode, "insane") && !Objects.equals(mode, "mega")) {
            sender.sendMessage("职业查询失败：无效的模式标识");
            return true;
        }

        r.put("mode", mode);
        var api = OpenApi.get("bot.hypixel.skywars.kits");

        var q = withQueryProgress(sender, I18N.text("command.mc.querying"), ()-> customQueryRequest(api, r));

        if (!q.success()) {
            sender.sendMessage(q.message());
            return true;
        }

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(q.i(), userOpenId, player)).setKeyboard(QueryTemplate.getKeyboard("/hyp", "sw").append(getSkywarsButtons(player), true)), false);
        return true;
    }

    public static boolean getSkywarsAngels(QQCommandSender sender, String player) {
        String userOpenId = sender.getUserId();
        var r = QueryTemplate.getRequestBody(userOpenId, player);
        var api = OpenApi.get("bot.hypixel.skywars.angels-descent");

        var q = withQueryProgress(sender, I18N.text("command.mc.querying"), ()-> customQueryRequest(api, r));

        if (!q.success()) {
            sender.sendMessage(q.message());
            return true;
        }

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(q.i(), userOpenId, player)).setKeyboard(QueryTemplate.getKeyboard("/hyp", "angels").append(getSkywarsButtons(player), true)), false);
        return true;
    }

    private static List<List<Button>> getSkywarsButtons(String player) {
        return List.of(
                List.of(
                        new Button("Mini职业", "/hyp sw " + player + " mini", ButtonStyle.GRAY),
                        new Button("天使血统", "/hyp angels " + player, ButtonStyle.RED),
                        new Button("普通职业", "/hyp sw " + player + " normal", ButtonStyle.GRAY)
                ),
                List.of(
                        new Button("疯狂职业", "/hyp sw " + player + " insane", ButtonStyle.GRAY),
                        new Button("超级职业", "/hyp sw " + player + " mega", ButtonStyle.GRAY)
                )
        );
    }
}