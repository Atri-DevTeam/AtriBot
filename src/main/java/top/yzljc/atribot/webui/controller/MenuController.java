package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.chat.official.menu.Menu;
import top.yzljc.atribot.webui.Result;

import java.util.ArrayList;
import java.util.List;

/** 自定义菜单配置 */
public class MenuController {

    /** 查询全局自定义菜单 */
    public static void queryMenu(Context ctx) {
        Menu.MenuQueryResult result = Menu.queryMenu();
        if (result == null) {
            ctx.json(Result.fail(500, "查询自定义菜单失败"));
            return;
        }
        ctx.json(Result.success(result));
    }

    /** 修改全局自定义菜单，body: {menu: {items: [...]}}，返回新版本号 */
    public static void updateMenu(Context ctx) {
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        JsonNode menuNode = body != null ? body.path("menu") : null;
        Menu.MenuData menu = parseMenuRequest(menuNode);
        if (menu == null || menu.items() == null || menu.items().isEmpty()) {
            ctx.json(Result.fail(400, "菜单配置不能为空"));
            return;
        }
        Integer version = Menu.updateMenu(menu);
        if (version == null) {
            ctx.json(Result.fail(500, "修改自定义菜单失败"));
            return;
        }
        ctx.json(Result.success(version));
    }

    /** 从 WebUI 请求体解析菜单配置（camelCase），无效返回 null */
    private static Menu.MenuData parseMenuRequest(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        List<Menu.MenuItem> items = new ArrayList<>();
        for (JsonNode item : node.path("items")) {
            Menu.MenuItem parsed = parseMenuItem(item);
            if (parsed != null) {
                items.add(parsed);
            }
        }
        return new Menu.MenuData(items);
    }

    private static Menu.MenuItem parseMenuItem(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        List<Menu.SubMenuItem> subItems = new ArrayList<>();
        for (JsonNode sub : node.path("subMenuItems")) {
            if (sub != null && sub.isObject()) {
                subItems.add(new Menu.SubMenuItem(
                        sub.path("name").asText(null),
                        sub.path("type").asText(null),
                        sub.path("sendMessage").asText(null),
                        sub.path("link").asText(null)));
            }
        }
        JsonNode sw = node.path("switchConfig");
        Menu.Switch switchConfig = sw.isObject()
                ? new Menu.Switch(sw.path("switchId").asText(null), sw.path("defaultOn").asBoolean(false))
                : null;
        return new Menu.MenuItem(
                node.path("name").asText(null),
                node.path("type").asText(null),
                subItems.isEmpty() ? null : subItems,
                node.path("sendMessage").asText(null),
                node.path("link").asText(null),
                switchConfig,
                node.path("align").asText(null));
    }
}
