package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.chat.official.panel.Panel;
import top.yzljc.atribot.webui.Result;

import java.util.ArrayList;
import java.util.List;

import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;
import static top.yzljc.atribot.webui.WebUiSupport.stringList;

/** 指令面板管理 */
public class PanelController {

    /** 分页查询指定场景下的面板列表，query: scope / cursor / limit */
    public static void listPanels(Context ctx) {
        String scope = ctx.queryParam("scope");
        String cursor = ctx.queryParam("cursor");
        int limit = parseInt(ctx.queryParam("limit"), 50);
        if (isBlank(scope)) {
            ctx.json(Result.fail(400, "scope 不能为空"));
            return;
        }
        Panel.PanelListResult result = Panel.listPanels(scope, cursor, limit);
        if (result == null) {
            ctx.json(Result.fail(500, "查询指令面板列表失败"));
            return;
        }
        ctx.json(Result.success(result));
    }

    /** 创建指令面板，body: {scope, targetType?, userOpenIds?, groupOpenIds?, panel: {items, remark?}} */
    public static void createPanel(Context ctx) {
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String scope = body != null ? body.path("scope").asText(null) : null;
        String targetType = body != null ? body.path("targetType").asText(null) : null;
        List<String> userOpenIds = stringList(body, "userOpenIds");
        List<String> groupOpenIds = stringList(body, "groupOpenIds");
        Panel.PanelData panel = parsePanelRequest(body != null ? body.path("panel") : null);
        if (isBlank(scope) || panel == null) {
            ctx.json(Result.fail(400, "scope、panel 不能为空"));
            return;
        }
        // 全局(all)面板每个场景仅允许一个，已存在时只允许修改、不允许新建，防止覆盖
        String effectiveTargetType = isBlank(targetType) ? "all" : targetType;
        if ("all".equalsIgnoreCase(effectiveTargetType) && hasGlobalPanel(scope)) {
            ctx.json(Result.fail(400, "该场景已存在全局面板，全局面板仅允许修改，请直接编辑现有面板"));
            return;
        }
        String panelId = Panel.createPanel(scope, targetType, userOpenIds, groupOpenIds, panel);
        if (panelId == null) {
            ctx.json(Result.fail(500, "创建指令面板失败"));
            return;
        }
        ctx.json(Result.success(panelId));
    }

    /** 判断指定场景下是否已存在全局(all)面板 */
    private static boolean hasGlobalPanel(String scope) {
        Panel.PanelListResult list = Panel.listPanels(scope, null, 50);
        if (list == null || list.records() == null) {
            return false;
        }
        for (Panel.PanelRecord record : list.records()) {
            if ("all".equalsIgnoreCase(record.targetType())) {
                return true;
            }
        }
        return false;
    }

    /** 查询指令面板详情 */
    public static void getPanelDetail(Context ctx) {
        String panelId = ctx.pathParam("panelId");
        if (isBlank(panelId)) {
            ctx.json(Result.fail(400, "panelId 不能为空"));
            return;
        }
        Panel.PanelDetail detail = Panel.getPanelDetail(panelId);
        if (detail == null) {
            ctx.json(Result.fail(500, "查询指令面板详情失败"));
            return;
        }
        ctx.json(Result.success(detail));
    }

    /** 修改指令面板，body: {panel: {items, remark?}}，返回新版本号 */
    public static void updatePanel(Context ctx) {
        String panelId = ctx.pathParam("panelId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        Panel.PanelData panel = parsePanelRequest(body != null ? body.path("panel") : null);
        if (isBlank(panelId) || panel == null) {
            ctx.json(Result.fail(400, "panelId、panel 不能为空"));
            return;
        }
        Integer version = Panel.updatePanel(panelId, panel);
        if (version == null) {
            ctx.json(Result.fail(500, "修改指令面板失败"));
            return;
        }
        ctx.json(Result.success(version));
    }

    /** 删除指令面板 */
    public static void deletePanel(Context ctx) {
        String panelId = ctx.pathParam("panelId");
        if (isBlank(panelId)) {
            ctx.json(Result.fail(400, "panelId 不能为空"));
            return;
        }
        if (panelId.startsWith("mp_")) {
            ctx.json(Result.fail(400, "旧版面板不可删除"));
            return;
        }
        if (!Panel.deletePanel(panelId)) {
            ctx.json(Result.fail(500, "删除指令面板失败"));
            return;
        }
        ctx.json(Result.success(null));
    }

    /** 修改指令面板关联对象，body: {op: "add"|"del", userOpenIds?, groupOpenIds?} */
    public static void updatePanelTarget(Context ctx) {
        String panelId = ctx.pathParam("panelId");
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        String op = body != null ? body.path("op").asText(null) : null;
        List<String> userOpenIds = stringList(body, "userOpenIds");
        List<String> groupOpenIds = stringList(body, "groupOpenIds");
        if (isBlank(panelId) || isBlank(op) || (userOpenIds.isEmpty() && groupOpenIds.isEmpty())) {
            ctx.json(Result.fail(400, "panelId、op、关联对象不能为空"));
            return;
        }
        Panel.TargetOp targetOp = "del".equalsIgnoreCase(op) ? Panel.TargetOp.DEL : Panel.TargetOp.ADD;
        if (!Panel.updatePanelTarget(panelId, targetOp, userOpenIds, groupOpenIds)) {
            ctx.json(Result.fail(500, "修改指令面板关联对象失败"));
            return;
        }
        ctx.json(Result.success(null));
    }

    /** 从 WebUI 请求体解析面板配置（camelCase），无效返回 null */
    private static Panel.PanelData parsePanelRequest(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        List<Panel.PanelItem> items = new ArrayList<>();
        for (JsonNode item : node.path("items")) {
            if (item != null && item.isObject()) {
                items.add(new Panel.PanelItem(
                        item.path("name").asText(null),
                        item.path("desc").asText(null),
                        item.path("type").asText(null),
                        item.path("onlyAdmin").asBoolean(false),
                        item.path("link").asText(null)));
            }
        }
        return new Panel.PanelData(items, node.path("remark").asText(null));
    }
}
