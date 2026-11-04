package top.yzljc.atribot.survey;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.webui.ApiCode;
import top.yzljc.atribot.webui.Result;

import java.sql.SQLException;
import java.util.Locale;

/**
 * @Author YZ_Ljc_
 * @ClassName SurveyHttp
 * @Created_at 2026/10/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.survey
 */
public final class SurveyHttp {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private SurveyHttp() {}

    public static <T> T body(Context ctx, Class<T> type) {
        if (ctx.contentType() == null || !ctx.contentType().toLowerCase(Locale.ROOT).startsWith("application/json")
                || ctx.bodyAsBytes().length > 131072) throw new SurveyDefinition.Failure(ApiCode.SURVEY_INVALID);
        try {
            T result = JSON.readValue(ctx.body(), type);
            if (result == null) throw new IllegalArgumentException();
            return result;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new SurveyDefinition.Failure(ApiCode.SURVEY_INVALID);
        }
    }

    public static String id(Context ctx) {
        String id = ctx.pathParam("id");
        if (!SurveyId.isValid(id)) throw new SurveyDefinition.Failure(ApiCode.SURVEY_NOT_FOUND);
        return id;
    }

    public static long number(String value, long min, long max) {
        try {
            long result = Long.parseLong(value);
            if (result < min || result > max) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException e) { throw new SurveyDefinition.Failure(ApiCode.SURVEY_INVALID); }
    }

    public static int offset(Context ctx) {
        return ctx.queryParam("offset") == null ? 0 : (int) number(ctx.queryParam("offset"), 0, 1000000);
    }

    public static void handle(Context ctx, Operation operation) {
        ctx.header("Cache-Control", "no-store");
        try { ctx.json(Result.success(operation.run())); }
        catch (SurveyDefinition.Failure e) { ctx.status(e.code.getHttpStatus()).json(Result.failure(e.code)); }
        catch (SQLException e) {
            LoggerFactory.getLogger(SurveyHttp.class).error("问卷操作失败", e);
            ctx.status(500).json(Result.failure(ApiCode.INTERNAL_ERROR));
        }
    }

    @FunctionalInterface
    public interface Operation { Object run() throws SQLException; }
}
