package top.yzljc.atribot.function.minecraft;

import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atribot.function.command.component.QueryTemplate;
import top.yzljc.atribot.i18n.I18N;
import top.yzljc.atribot.service.request.OpenApi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName PlayerProfileImpl
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.minecraft
 */
public final class PlayerProfileImpl {

    public static boolean onQuery(QQCommandSender sender, String p) {

        Map<String, Object> rq = new LinkedHashMap<>();
        rq.put("player", p);
        BackgroundService.applyApprovedBackground(rq, sender.getUserId());

        var r = QueryTemplate.withQueryProgress(sender, I18N.text("command.mc.querying"), () -> QueryTemplate.customQueryRequest(OpenApi.get("bot.minecraft.profile"), rq));

        if (!r.success()) {
            sender.sendMessage(r.message());
            return true;
        }

        sender.sendMessage(TC.md(QueryTemplate.getTemplate(r.i(), sender.getUserId(), p)).setKeyboard(QueryTemplate.getKeyboard("/mctool", "pf")), false);

        return true;
    }
}