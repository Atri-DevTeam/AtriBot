package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.function.admin.update.UpdateNoticeContent;
import top.yzljc.atribot.function.admin.update.UpdateNoticeRecord;
import top.yzljc.atribot.webui.ApiCode;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atricore.platform.qq.QQBot;

import java.io.IOException;
import java.util.Map;

/**
 * WebUI 更新通知内容及推送设置接口，鉴权由 WebUIRouter 统一处理
 *
 * @Author YZ_Ljc_
 * @ClassName UpdateNoticeController
 * @Created_at 2026/11/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
public final class UpdateNoticeController {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(UpdateNoticeController.class);

    public static void get(Context ctx) {
        ctx.json(Result.success(Map.of("content", UpdateNoticeRecord.getContent(),
                "createdAt", UpdateNoticeRecord.getCreatedAt(), "notifiedGroups", UpdateNoticeRecord.getNotifiedGroups().size(),
                "joinEventPush", UpdateNoticeRecord.isJoinEventPushEnabled(),
                "passiveMessagePush", UpdateNoticeRecord.isPassiveMessagePushEnabled(),
                "enabled", UpdateNoticeRecord.isEnabled(),
                "botName", QQBot.BOT_NAME == null ? "机器人" : QQBot.BOT_NAME)));
    }

    public static void saveSettings(Context ctx) {
        try {
            var body = JSON.readTree(ctx.body());
            if (body == null || !body.isObject() || (!body.has("enabled") && !body.has("joinEventPush") && !body.has("passiveMessagePush"))
                    || (body.has("enabled") && !body.get("enabled").isBoolean())
                    || (body.has("passiveMessagePush") && !body.get("passiveMessagePush").isBoolean())
                    || (body.has("joinEventPush") && !body.get("joinEventPush").isBoolean())) {
                ctx.status(400).json(Result.failure(ApiCode.INVALID_PARAMETER));
                return;
            }
            UpdateNoticeRecord.setSettings(body.has("enabled") ? body.get("enabled").booleanValue() : null,
                    body.has("joinEventPush") ? body.get("joinEventPush").booleanValue() : null,
                    body.has("passiveMessagePush") ? body.get("passiveMessagePush").booleanValue() : null);
            ctx.json(Result.success(Map.of("joinEventPush", UpdateNoticeRecord.isJoinEventPushEnabled(),
                    "passiveMessagePush", UpdateNoticeRecord.isPassiveMessagePushEnabled(), "enabled", UpdateNoticeRecord.isEnabled())));
        } catch (JsonProcessingException e) {
            ctx.status(400).json(Result.failure(ApiCode.INVALID_PARAMETER));
        } catch (IOException e) {
            LOG.error("保存更新通知推送设置失败", e);
            ctx.status(500).json(Result.customFailure("保存推送设置失败，请稍后重试"));
        }
    }

    public static void save(Context ctx) {
        if (ctx.body().length() > 131072) {
            ctx.status(413).json(Result.failure(ApiCode.INVALID_PARAMETER));
            return;
        }
        try {
            var content = JSON.readValue(ctx.body(), UpdateNoticeContent.class);
            if (content == null) throw new IllegalArgumentException("通知内容不能为空");
            UpdateNoticeRecord.setContent(content);
            get(ctx);
        } catch (JsonProcessingException failure) {
            ctx.status(400).json(Result.customFailure("消息格式或按钮配置无效，请检查后重试"));
        } catch (IllegalArgumentException failure) {
            ctx.status(400).json(Result.customFailure(failure.getMessage()));
        } catch (IOException failure) {
            LOG.error("保存更新通知内容失败", failure);
            ctx.status(500).json(Result.customFailure("保存更新通知失败，请稍后重试"));
        }
    }

    public static void saveLayouts(Context ctx) {
        if (ctx.body().length() > 131072) {
            ctx.status(413).json(Result.failure(ApiCode.INVALID_PARAMETER));
            return;
        }
        try {
            var body = JSON.readTree(ctx.body());
            if (body == null || !body.path("textLayout").isObject() || !body.path("markdownLayout").isObject()) {
                ctx.status(400).json(Result.failure(ApiCode.INVALID_PARAMETER));
                return;
            }
            var plain = JSON.treeToValue(body.get("textLayout"), UpdateNoticeContent.MessageLayout.class);
            var markdown = JSON.treeToValue(body.get("markdownLayout"), UpdateNoticeContent.MessageLayout.class);
            UpdateNoticeRecord.setLayouts(plain, markdown);
            ctx.json(Result.success(Map.of("textLayout", plain, "markdownLayout", markdown)));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            ctx.status(400).json(Result.customFailure("标题与落款格式无效，每项不能超过 4000 字符"));
        } catch (IOException e) {
            LOG.error("保存更新通知标题与落款失败", e);
            ctx.status(500).json(Result.customFailure("保存标题与落款失败，请稍后重试"));
        }
    }

    public static void saveButtons(Context ctx) {
        if (ctx.body().length() > 131072) {
            ctx.status(413).json(Result.failure(ApiCode.INVALID_PARAMETER));
            return;
        }
        try {
            var body = JSON.readTree(ctx.body());
            if (body == null || !body.path("buttons").isArray()) {
                ctx.status(400).json(Result.failure(ApiCode.INVALID_PARAMETER));
                return;
            }
            var content = JSON.treeToValue(body, UpdateNoticeContent.class);
            UpdateNoticeRecord.setButtons(content.buttons());
            ctx.json(Result.success(Map.of("buttons", content.buttons())));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            ctx.status(400).json(Result.customFailure("按钮配置无效，请检查后重试"));
        } catch (IOException e) {
            LOG.error("保存更新通知默认按钮失败", e);
            ctx.status(500).json(Result.customFailure("保存默认按钮失败，请稍后重试"));
        }
    }

    private UpdateNoticeController() {}
}
