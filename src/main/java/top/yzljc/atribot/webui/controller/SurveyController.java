package top.yzljc.atribot.webui.controller;

import io.javalin.Javalin;
import top.yzljc.atribot.dashboard.DashboardSettings;
import top.yzljc.atribot.database.repo.SurveyRepository;
import top.yzljc.atribot.survey.SurveyDefinition;
import top.yzljc.atribot.survey.SurveyHttp;
import top.yzljc.atribot.webui.ApiCode;
import top.yzljc.atribot.webui.WebUISessionManager;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SurveyController
 * @Created_at 2026/10/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
public final class SurveyController {
    private SurveyController() {}
    private record Edit(int revision, SurveyDefinition definition) {}
    private record State(int revision, SurveyDefinition.Status status) {}
    private record Star(Boolean starred) {}
    private record Settings(int revision, Integer rewardGold, Boolean visible, Boolean pinned, Boolean repeatable) {}
    private record Create(String title, String description, java.util.List<SurveyDefinition.Question> questions,
                          Integer rewardGold, Boolean visible, Boolean pinned, Boolean repeatable) {}

    public static void register(Javalin app) {
        var repository = new SurveyRepository();
        String root = "/webui/api/surveys";
        app.beforeMatched(ctx -> {
            if (ctx.endpointHandlerPath().startsWith(root)) WebUISessionManager.require(ctx);
        });
        app.get(root, ctx -> SurveyHttp.handle(ctx, () -> {
            var page = repository.list(null, SurveyHttp.offset(ctx));
            return Map.of("items", page.items(), "total", page.total(), "entryBase", java.util.Objects.toString(DashboardSettings.current().origin(), ""));
        }));
        app.post(root, ctx -> SurveyHttp.handle(ctx, () -> {
            var body = SurveyHttp.body(ctx, Create.class);
            return Map.of("id", repository.create(new SurveyDefinition(body.title, body.description, body.questions),
                    body.rewardGold == null ? 0 : body.rewardGold, body.visible == null || body.visible, Boolean.TRUE.equals(body.pinned), Boolean.TRUE.equals(body.repeatable)));
        }));
        app.put(root + "/{id}/settings", ctx -> SurveyHttp.handle(ctx, () -> {
            var body = SurveyHttp.body(ctx, Settings.class);
            if (body.rewardGold == null || body.visible == null || body.pinned == null || body.repeatable == null) throw new SurveyDefinition.Failure(ApiCode.SURVEY_INVALID);
            return repository.settings(SurveyHttp.id(ctx), body.revision, body.rewardGold, body.visible, body.pinned, body.repeatable);
        }));
        app.get(root + "/{id}", ctx -> SurveyHttp.handle(ctx, () -> repository.detail(SurveyHttp.id(ctx), null)));
        app.put(root + "/{id}", ctx -> SurveyHttp.handle(ctx, () -> {
            var body = SurveyHttp.body(ctx, Edit.class);
            if (body.definition == null) throw new SurveyDefinition.Failure(ApiCode.SURVEY_INVALID);
            repository.update(SurveyHttp.id(ctx), body.revision, body.definition);
            return null;
        }));
        app.post(root + "/{id}/status", ctx -> SurveyHttp.handle(ctx, () -> {
            var body = SurveyHttp.body(ctx, State.class);
            repository.status(SurveyHttp.id(ctx), body.revision, body.status);
            return null;
        }));
        app.delete(root + "/{id}", ctx -> SurveyHttp.handle(ctx, () -> { repository.delete(SurveyHttp.id(ctx)); return null; }));
        app.get(root + "/{id}/responses", ctx -> SurveyHttp.handle(ctx, () -> repository.responses(SurveyHttp.id(ctx),
                ctx.queryParam("page") == null ? 1 : (int) SurveyHttp.number(ctx.queryParam("page"), 1, Integer.MAX_VALUE),
                ctx.queryParam("snapshot") == null ? 0 : SurveyHttp.number(ctx.queryParam("snapshot"), 0, Long.MAX_VALUE), starredOnly(ctx))));
        app.get(root + "/{id}/responses/at/{position}", ctx -> SurveyHttp.handle(ctx, () -> repository.responseAt(SurveyHttp.id(ctx),
                SurveyHttp.number(ctx.pathParam("position"), 1, Long.MAX_VALUE),
                ctx.queryParam("snapshot") == null ? 0 : SurveyHttp.number(ctx.queryParam("snapshot"), 0, Long.MAX_VALUE), starredOnly(ctx))));
        app.put(root + "/{id}/responses/{responseId}/star", ctx -> SurveyHttp.handle(ctx, () -> {
            var body = SurveyHttp.body(ctx, Star.class);
            if (body.starred == null) throw new SurveyDefinition.Failure(ApiCode.SURVEY_INVALID);
            repository.starResponse(SurveyHttp.id(ctx), SurveyHttp.number(ctx.pathParam("responseId"), 1, Long.MAX_VALUE), body.starred);
            return null;
        }));
    }

    private static boolean starredOnly(io.javalin.http.Context ctx) {
        String value = ctx.queryParam("starredOnly");
        if (value == null || value.equals("false")) return false;
        if (value.equals("true")) return true;
        throw new SurveyDefinition.Failure(ApiCode.SURVEY_INVALID);
    }
}
