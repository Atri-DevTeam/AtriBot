package top.yzljc.qqbot.config.webui.exception;

import io.javalin.http.Context;
import top.yzljc.qqbot.config.Result;
import top.yzljc.qqbot.config.webui.WebUIController;

public class ExceptionController {

    public static void handleFeatureNotFound(FeatureNotFoundException e, Context ctx) {
        ctx.json(Result.fail(404, e.getMessage()));
    }
}