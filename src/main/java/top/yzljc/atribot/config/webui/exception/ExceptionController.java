package top.yzljc.atribot.config.webui.exception;

import io.javalin.http.Context;
import top.yzljc.atribot.config.webui.Result;

public class ExceptionController {

    public static void handleFeatureNotFound(FeatureNotFoundException e, Context ctx) {
        ctx.json(Result.fail(404, e.getMessage()));
    }
}