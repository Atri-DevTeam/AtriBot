package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import top.yzljc.atribot.chat.official.moderation.GroupModerationSettings;
import top.yzljc.atribot.chat.official.moderation.GroupModerationStore;
import top.yzljc.atribot.database.repo.ModerationLogRepository;
import top.yzljc.atribot.webui.Result;

import java.util.List;

import static top.yzljc.atribot.webui.WebUiSupport.parseInt;

/**
* @Author AndyOctopus
* @ClassName GroupModerationController
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.webui.controller
*/
public final class GroupModerationController {

    public static void getSettings(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        ctx.json(Result.success(GroupModerationStore.get(groupOpenId)));
    }

    public static void saveSettings(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        GroupModerationSettings settings = ctx.bodyAsClass(GroupModerationSettings.class);
        GroupModerationStore.save(groupOpenId, settings);
        ctx.json(Result.success(settings));
    }

    public static void listLogs(Context ctx) {
        String groupOpenId = ctx.pathParam("groupOpenId");
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 30);
        List<ModerationLogRepository.LogRow> rows = ModerationLogRepository.list(groupOpenId, page, pageSize);
        ctx.json(Result.success(rows));
    }
}
