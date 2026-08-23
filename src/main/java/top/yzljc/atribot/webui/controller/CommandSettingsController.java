package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.command.CommandDefinition;
import top.yzljc.atribot.command.CommandDisableService;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.webui.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandSettingsController
 * @Created_at 2026/08/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.controller
 */
public final class CommandSettingsController {
    private CommandSettingsController() {}

    public static void list(Context ctx) {
        Map<String, CommandDisableService.CommandRules> rules = CommandDisableService.snapshot();
        List<CommandItem> items = new ArrayList<>();
        for (CommandDefinition definition : CommandManager.getDefinitions()) {
            items.add(new CommandItem(definition.name(), definition.description(), definition.usage(),
                    definition.aliases(), CommandManager.getCommand(definition.name()).getExecutor() != null,
                    rules.get(definition.name().toLowerCase())));
        }
        ctx.json(Result.success(items));
    }

    public static void setGlobal(Context ctx) {
        run(ctx, () -> CommandDisableService.setGlobal(ctx.pathParam("commandName"), parseRule(ctx)));
    }

    public static void clearGlobal(Context ctx) {
        run(ctx, () -> CommandDisableService.clearGlobal(ctx.pathParam("commandName")));
    }

    public static void setGroup(Context ctx) {
        run(ctx, () -> {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            LinkedHashSet<String> groupIds = new LinkedHashSet<>();
            if (body != null && body.path("groupIds").isArray()) {
                body.path("groupIds").forEach(node -> {
                    if (node.isTextual() && !node.asText().isBlank()) groupIds.add(node.asText().trim());
                });
            }
            if (body != null && body.path("groupId").isTextual() && !body.path("groupId").asText().isBlank()) {
                groupIds.add(body.path("groupId").asText().trim());
            }
            CommandDisableService.setGroups(ctx.pathParam("commandName"), groupIds, parseRule(body));
        });
    }

    public static void clearGroup(Context ctx) {
        run(ctx, () -> CommandDisableService.clearGroup(ctx.pathParam("commandName"), ctx.pathParam("groupId")));
    }

    private static CommandDisableService.DisableRule parseRule(Context ctx) {
        return parseRule(ctx.bodyAsClass(JsonNode.class));
    }

    private static CommandDisableService.DisableRule parseRule(JsonNode body) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException("请求内容不能为空");
        return new CommandDisableService.DisableRule(text(body, "reason"), text(body, "startsAt"), text(body, "endsAt"));
    }

    private static String text(JsonNode body, String key) {
        JsonNode node = body.get(key);
        return node == null || node.isNull() ? null : node.asText(null);
    }

    private static void run(Context ctx, CheckedAction action) {
        try {
            action.run();
            ctx.json(Result.success(null));
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
        } catch (IOException e) {
            ctx.json(Result.fail(500, "保存指令规则失败: " + e.getMessage()));
        }
    }

    private interface CheckedAction { void run() throws IOException; }

    public record CommandItem(String name, String description, String usage, List<String> aliases,
                              boolean registered, CommandDisableService.CommandRules rules) {}
}
