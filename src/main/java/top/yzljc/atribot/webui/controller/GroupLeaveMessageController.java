package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.utils.notify.GroupLeaveMessageService;
import top.yzljc.atribot.webui.Result;

import java.util.Map;

@Slf4j
public final class GroupLeaveMessageController {
    public static void list(Context ctx) {
        String groupId = groupId(ctx);
        if (groupId == null) return;
        try {
            ctx.json(Result.success(GroupLeaveMessageService.list(groupId)));
        } catch (IllegalStateException e) {
            fail(ctx, e);
        }
    }

    public static void add(Context ctx) {
        String groupId = groupId(ctx);
        if (groupId == null) return;
        String content;
        boolean triggerOnMemberJoin;
        try {
            JsonNode body = ctx.bodyAsClass(JsonNode.class);
            if (body == null || !body.path("content").isTextual()) throw new IllegalArgumentException();
            content = body.path("content").asText();
            if (body.has("triggerOnMemberJoin") && !body.get("triggerOnMemberJoin").isBoolean())
                throw new IllegalArgumentException();
            triggerOnMemberJoin = body.path("triggerOnMemberJoin").asBoolean(false);
        } catch (Exception e) {
            ctx.status(400).json(Result.customFailure("留言内容或触发设置无效"));
            return;
        }
        try {
            ctx.json(Result.success(Map.of("id", GroupLeaveMessageService.add(groupId, content, triggerOnMemberJoin))));
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Result.customFailure(e.getMessage()));
        } catch (IllegalStateException e) {
            fail(ctx, e);
        }
    }

    public static void delete(Context ctx) {
        String groupId = groupId(ctx);
        if (groupId == null) return;
        try {
            if (!GroupLeaveMessageService.delete(groupId, ctx.pathParam("id"))) {
                ctx.status(409).json(Result.customFailure("留言已发送或已删除，请刷新列表"));
                return;
            }
            ctx.json(Result.success("ok"));
        } catch (IllegalStateException e) {
            fail(ctx, e);
        }
    }

    private static String groupId(Context ctx) {
        String id = ctx.pathParam("groupOpenId");
        if (OfficialGroups.listGroups().stream().noneMatch(group -> group.groupOpenId().equals(id))) {
            ctx.status(404).json(Result.customFailure("群不存在，请刷新后重试"));
            return null;
        }
        return id;
    }

    private static void fail(Context ctx, IllegalStateException e) {
        log.error("群留言操作失败", e);
        ctx.status(500).json(Result.customFailure(e.getMessage()));
    }
}
