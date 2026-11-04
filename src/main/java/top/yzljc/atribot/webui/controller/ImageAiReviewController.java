package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.PicRepository;
import top.yzljc.atribot.function.impl.pic.ImageAiReviewClient;
import top.yzljc.atribot.function.impl.pic.ImageAiReviewService;
import top.yzljc.atribot.function.impl.pic.ImageAiReviewSettings;
import top.yzljc.atribot.service.ai.AiProvider;
import top.yzljc.atribot.webui.Result;

import java.io.IOException;

/** 图源初审管理接口，沿用 WebUI 登录与管理员鉴权 */
public final class ImageAiReviewController {
    public record Settings(boolean enabled, String prompt, boolean configured, String model,
                           boolean rejectInappropriate, boolean rejectOffTopic) {}

    public static void getSettings(Context ctx) {
        var config = Config.getInstance();
        var properties = config.getAiBotProperties(AiProvider.DEEPSEEK_IMAGE);
        var settings = ImageAiReviewSettings.current();
        ctx.json(Result.success(new Settings(settings.enabled(), settings.prompt(),
                ImageAiReviewClient.configured(properties), properties.getModel(), settings.rejectInappropriate(), settings.rejectOffTopic())));
    }

    public static void saveSettings(Context ctx) {
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        if (body == null || !body.path("enabled").isBoolean() || !body.path("prompt").isTextual()
                || !body.path("rejectInappropriate").isBoolean() || !body.path("rejectOffTopic").isBoolean()
                || body.path("prompt").asText().isBlank() || body.path("prompt").asText().length() > 8000) {
            ctx.status(400).json(Result.customFailure("请填写启用状态和1至8000字的审核标准"));
            return;
        }
        var config = Config.getInstance();
        if (body.path("enabled").asBoolean()
                && !ImageAiReviewClient.configured(config.getAiBotProperties(AiProvider.DEEPSEEK_IMAGE))) {
            ctx.status(400).json(Result.customFailure("请先在 config.yml 中配置 ai.deepseek_image"));
            return;
        }
        try {
            ImageAiReviewSettings.save(new ImageAiReviewSettings.Settings(body.path("enabled").asBoolean(), body.path("prompt").asText(),
                    body.path("rejectInappropriate").asBoolean(), body.path("rejectOffTopic").asBoolean()));
        } catch (IOException | IllegalStateException e) {
            ctx.status(500).json(Result.customFailure("保存初审设置失败"));
            return;
        }
        ImageAiReviewService.start();
        getSettings(ctx);
    }

    public static void retry(Context ctx) {
        String id = ctx.pathParam("id");
        var image = PicRepository.findById(id);
        if (image == null) {
            ctx.status(404).json(Result.customFailure("投稿记录不存在"));
            return;
        }
        if (!"PENDING".equals(image.getReviewStatus())) {
            ctx.status(400).json(Result.customFailure("仅待审核投稿可以初审"));
            return;
        }
        if (!ImageAiReviewSettings.current().enabled()) {
            ctx.status(400).json(Result.customFailure("AI 初审尚未启用"));
            return;
        }
        if (!ImageAiReviewService.enqueue(id)) {
            ctx.status(409).json(Result.customFailure("投稿正在初审或暂时无法入队"));
            return;
        }
        ctx.json(Result.success("已提交初审"));
    }
}
