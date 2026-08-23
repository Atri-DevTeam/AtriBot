package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.utils.CommentPreservingYaml;
import top.yzljc.atribot.webui.Result;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName BotSettingsController
 * @Created_at 2026/08/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
public final class BotSettingsController {

    private record SettingDescriptor(String key, String label, String description, String type, boolean restartRequired) {
    }

    private static final List<SettingDescriptor> EDITABLE_SETTINGS = List.of(
            new SettingDescriptor("command-prefix", "指令前缀", "机器人指令触发前缀", "string", false),
            new SettingDescriptor("debug-command-suffix", "Debug 指令后缀", "Debug指令调试后缀", "string", false),
            new SettingDescriptor("debug-mode", "Debug 模式", "开启后输出更详细的调试日志", "boolean", false),
            new SettingDescriptor("qq.debug-group-openId", "调试群 OpenId", "机器人调试群", "string", false),
            new SettingDescriptor("qq.super_admin_id", "超级管理员 OpenId", "机器人超级管理员账号", "string", false)
    );

    public static void getProfile(Context ctx) {
        Config config = Config.getInstance();
        String appId = normalize(config.getQqAppId());
        String openId = normalize(config.getOfficialOpenId());
        String apiBaseUrl = config.getQqApiBaseUrl();
        String avatarUrl = appId != null && openId != null
                ? "https://thirdqq.qlogo.cn/qqapp/" + appId + "/" + openId + "/100"
                : normalize(QQBot.BOT_AVATAR_URL);
        ctx.json(Result.success(new BotProfileDTO(
                normalize(QQBot.BOT_NAME) == null ? "AtriBot" : QQBot.BOT_NAME,
                appId,
                openId,
                normalize(QQBot.BOT_UNIONID),
                avatarUrl,
                apiBaseUrl,
                config.getEnv(),
                apiBaseUrl != null && apiBaseUrl.contains("sandbox"),
                config.getQqConnectionMode(),
                normalize(QQBot.BOT_SHARE_LINK),
                normalize(config.getQqBotUin())
        )));
    }

    public static void getSettings(Context ctx) {
        ctx.json(Result.success(currentItems()));
    }

    public static void updateSettings(Context ctx) {
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        JsonNode updates = body == null ? null : body.path("updates");
        if (body == null || !updates.isObject() || updates.isEmpty()) {
            ctx.json(Result.fail(400, "缺少 updates 字段"));
            return;
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : updates.properties()) {
            SettingDescriptor descriptor = findDescriptor(entry.getKey());
            if (descriptor == null) {
                ctx.json(Result.fail(400, "不允许修改配置项: " + entry.getKey() + "（仅基础设置白名单内的项可改）"));
                return;
            }
            Object converted = convert(descriptor, entry.getValue());
            if (converted instanceof InvalidValue invalid) {
                ctx.json(Result.fail(400, "配置项 " + descriptor.label() + " 的值类型应为 " + descriptor.type()
                        + "，收到: " + invalid.received));
                return;
            }
            changes.put(descriptor.key(), converted);
        }

        try {
            CommentPreservingYaml.update(Paths.get(Properties.CONFIG), changes);
        } catch (IOException e) {
            ctx.json(Result.fail(500, "写入配置文件失败: " + e.getMessage()));
            return;
        }
        Config.getInstance().reload();
        ctx.json(Result.success(currentItems()));
    }

    private static List<SettingItemDTO> currentItems() {
        List<SettingItemDTO> items = new ArrayList<>();
        for (SettingDescriptor descriptor : EDITABLE_SETTINGS) {
            items.add(new SettingItemDTO(
                    descriptor.key(),
                    descriptor.label(),
                    descriptor.description(),
                    descriptor.type(),
                    currentValue(descriptor.key()),
                    descriptor.restartRequired()
            ));
        }
        return items;
    }

    private static Object currentValue(String key) {
        Config config = Config.getInstance();
        return switch (key) {
            case "command-prefix" -> normalize(config.getCommandPrefix());
            case "debug-command-suffix" -> normalize(config.getDebugCommandSuffix());
            case "debug-mode" -> config.isDebugMode();
            case "qq.debug-group-openId" -> normalize(config.getDebugGroupOpenId());
            case "qq.super_admin_id" -> normalize(config.getSuperAdminId());
            default -> null;
        };
    }

    private static SettingDescriptor findDescriptor(String key) {
        for (SettingDescriptor descriptor : EDITABLE_SETTINGS) {
            if (descriptor.key().equals(key)) {
                return descriptor;
            }
        }
        return null;
    }

    private static Object convert(SettingDescriptor descriptor, JsonNode node) {
        return switch (descriptor.type()) {
            case "boolean" -> node.isBoolean() ? node.asBoolean() : new InvalidValue(node.getNodeType().toString());
            case "int" -> node.canConvertToInt() ? node.asInt() : new InvalidValue(node.getNodeType().toString());
            default -> node.isTextual() ? node.asText().trim() : new InvalidValue(node.getNodeType().toString());
        };
    }

    /** config.yml 用字符串 "null" 作占位，统一归一化避免前端拿到伪值 */
    private static String normalize(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return value.trim();
    }

    private record InvalidValue(String received) {
    }

    public record BotProfileDTO(String botName, String appId, String openId, String unionId, String avatarUrl,
                                String apiBaseUrl, String env, boolean apiSandbox, String connectionMode,
                                String shareUrl, String botUin) {
    }

    public record SettingItemDTO(String key, String label, String description, String type, Object value,
                                 boolean restartRequired) {
    }
}
