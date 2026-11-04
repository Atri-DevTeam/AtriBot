package top.yzljc.atribot.miniapp;

import io.javalin.Javalin;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atribot.database.repo.SurveyRepository;
import top.yzljc.atribot.survey.SurveyDefinition;
import top.yzljc.atribot.survey.SurveyHttp;
import top.yzljc.atribot.webui.ApiCode;

import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappSurveyRoutes
 * @Created_at 2026/10/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
final class MiniappSurveyRoutes {
    private MiniappSurveyRoutes() {}
    private record Submission(int revision, Map<String, List<String>> answers, String requestId) {}

    static void register(Javalin app, MiniappSessions sessions) {
        var repository = new SurveyRepository();
        String root = "/atrimeow/profile/api/surveys";
        app.get(root, ctx -> {
            var identity = MiniappRouter.requireIdentity(ctx, sessions);
            SurveyHttp.handle(ctx, () -> repository.list(account(identity), SurveyHttp.offset(ctx)));
        });
        app.get(root + "/{id}", ctx -> {
            var identity = MiniappRouter.requireIdentity(ctx, sessions);
            SurveyHttp.handle(ctx, () -> repository.detail(SurveyHttp.id(ctx), account(identity)));
        });
        app.post(root + "/{id}/responses", ctx -> {
            var identity = MiniappRouter.requireIdentity(ctx, sessions);
            SurveyHttp.handle(ctx, () -> {
                var body = SurveyHttp.body(ctx, Submission.class);
                return repository.submit(SurveyHttp.id(ctx), account(identity), identity.userId(), identity.displayName(), body.revision, body.answers, body.requestId);
            });
        });
    }

    private static String account(MiniappSessions.Identity identity) {
        var account = UnifiedAuthentication.getCachedQqUserAccount(identity.userId());
        if (account == null) throw new SurveyDefinition.Failure(ApiCode.ACCOUNT_NOT_REGISTERED);
        return account.uuid().toString();
    }
}
