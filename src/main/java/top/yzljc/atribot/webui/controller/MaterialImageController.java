package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.chat.official.media.MaterialImages;
import top.yzljc.atribot.webui.Result;

import java.io.IOException;

/**
 * @Author YZ_Ljc_
 * @ClassName MaterialImageController
 * @Created_at 2026/10/22
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
@Slf4j
public final class MaterialImageController {
    private MaterialImageController() {}

    public static void list(Context ctx) {
        execute(ctx, () -> MaterialImages.list(ctx.queryParam("cursor")));
    }

    public static void upload(Context ctx) {
        execute(ctx, () -> {
            var file = ctx.uploadedFile("image");
            if (file == null) throw new IllegalArgumentException("请选择需要上传的图片");
            try (var stream = file.content()) {
                return MaterialImages.upload(stream.readNBytes(MaterialImages.MAX_IMAGE_BYTES + 1), file.filename());
            }
        });
    }

    public static void delete(Context ctx) {
        execute(ctx, () -> MaterialImages.delete(ctx.pathParam("imageId")));
    }

    private static void execute(Context ctx, Operation operation) {
        ctx.header("Cache-Control", "no-store");
        try {
            ctx.json(Result.success(operation.run()));
        } catch (IllegalArgumentException failure) {
            ctx.status(400).json(Result.customFailure(failure.getMessage()));
        } catch (MaterialImages.ApiException failure) {
            ctx.status(502).json(Result.customFailure("QQ 素材接口失败（" + failure.code() + "）：" + failure.getMessage()));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            ctx.status(502).json(Result.customFailure("素材请求已中断，请刷新列表确认当前状态"));
        } catch (IOException | IllegalStateException failure) {
            log.warn("QQ 图片素材管理失败: {}", failure.getMessage());
            ctx.status(502).json(Result.customFailure("素材请求失败，请刷新列表确认当前状态"));
        }
    }

    @FunctionalInterface
    private interface Operation {
        Object run() throws IOException, InterruptedException;
    }
}
