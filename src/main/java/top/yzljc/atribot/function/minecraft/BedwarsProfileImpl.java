package top.yzljc.atribot.function.minecraft;

import top.yzljc.atribot.function.command.component.QueryTemplate;
import top.yzljc.atribot.i18n.I18N;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.command.QQCommandSender;

import java.util.List;
import java.util.UUID;

import static top.yzljc.atribot.function.command.component.QueryTemplate.customQueryRequest;
import static top.yzljc.atribot.function.command.component.QueryTemplate.withQueryProgress;

/**
 * @Author YZ_Ljc_
 * @ClassName BedwarsProfileImpl
 * @Created_at 2026/10/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.minecraft
 */
public final class BedwarsProfileImpl {

    public static boolean bedwarsProfile(QQCommandSender sender, String player) {
        String userOpenId = sender.getUserId();
        var r = QueryTemplate.getRequestBody(userOpenId, player);
        var api = OpenApi.get("bot.hypixel.bedwars");

        var q = withQueryProgress(sender, I18N.text("command.mc.querying"), ()-> customQueryRequest(api, r));

        if (!q.success()) {
            sender.sendMessage(q.message());
            return true;
        }

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(q.i(), userOpenId, q.playerUuid())).setKeyboard(QueryTemplate.getKeyboard("/hyp", "bw").append(getBedwarsButtons(q.playerUuid()), true)), false);
        return true;
    }

    public static boolean slumberHotelProfile(QQCommandSender sender, String player) {
        String userOpenId = sender.getUserId();
        var r = QueryTemplate.getRequestBody(userOpenId, player);
        var api = OpenApi.get("bot.hypixel.bedwars.slumber");

        var q = withQueryProgress(sender, I18N.text("command.mc.querying"), ()-> customQueryRequest(api, r));

        if (!q.success()) {
            sender.sendMessage(q.message());
            return true;
        }

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(q.i(), userOpenId, q.playerUuid())).setKeyboard(QueryTemplate.getKeyboard("/hyp", "sh").append(getHotelButtons(q.playerUuid()), true)), false);
        return true;
    }

    private static List<List<Button>> getBedwarsButtons(UUID playerUuid) {
        if (playerUuid == null) return List.of();
        return List.of(
                List.of(
                        new Button("入梦酒店进度", "/hyp sh " + playerUuid, ButtonStyle.GRAY)
                )
        );
    }

    private static List<List<Button>> getHotelButtons(UUID playerUuid) {
        if (playerUuid == null) return List.of();
        return List.of(
                List.of(
                        new Button("起床战争总数据", "/hyp bw " + playerUuid, ButtonStyle.GRAY)
                )
        );
    }
}
