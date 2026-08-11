package top.yzljc.atribot.chat.official.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.service.request.HttpService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Menu
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.menu
 * @Description 自定义菜单管理
 * 菜单仅在单聊场景展示，设置后对所有用户生效，最多 10 个菜单项，查询 30 QPM，修改 5 QPM
 */
@Slf4j
public final class Menu {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String baseUrl() {
        return Config.getInstance().getQqApiBaseUrl() + "/v2/menu";
    }

    private static String authHeader() {
        return "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
    }

    /** 开关配置，switchId 唯一标识，defaultOn 对应接口字段 default */
    public record Switch(String switchId, boolean defaultOn) {}

    /** 二级菜单项，type 仅支持 send_message / link */
    public record SubMenuItem(String name, String type, String sendMessage, String link) {}

    /** 一级菜单项，type 支持 switch / send_message / link / menu */
    public record MenuItem(String name, String type, List<SubMenuItem> subMenuItems,
                           String sendMessage, String link, Switch switchConfig, String align) {}

    /** 菜单配置，items 最多 10 个 */
    public record MenuData(List<MenuItem> items) {}

    /** 查询结果，未设置过菜单时 menu 为 null */
    public record MenuQueryResult(MenuData menu, int version) {}

    /**
     * 查询全局自定义菜单，失败返回 null
     */
    public static MenuQueryResult queryMenu() {
        String url = baseUrl();
        String scene = "查询自定义菜单";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询自定义菜单失败，HTTP 状态码 {}", result.status());
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            if (response == null || response.isNull() || response.isMissingNode()) {
                log.error("[!] 查询自定义菜单失败，响应为空");
                return null;
            }
            MenuData menu = parseMenu(response.path("menu"));
            return new MenuQueryResult(menu, response.path("version").asInt(0));
        } catch (Exception e) {
            log.error("[!] 查询自定义菜单异常", e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 修改全局自定义菜单，覆盖原有完整菜单配置，成功返回新版本号，失败返回 null
     */
    public static Integer updateMenu(MenuData menu) {
        if (menu == null || menu.items() == null || menu.items().isEmpty()) {
            return null;
        }
        String url = baseUrl();
        String scene = "修改自定义菜单";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("menu", menuToMap(menu));
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.putJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "PUT", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "PUT", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 修改自定义菜单失败，HTTP 状态码 {}", result.status());
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "PUT", url, requestJson,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            return response == null ? null : response.path("version").asInt(0);
        } catch (Exception e) {
            log.error("[!] 修改自定义菜单异常", e);
            OfficialSendLogRepository.recordError(null, scene, "PUT", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    private static MenuData parseMenu(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        List<MenuItem> items = new ArrayList<>();
        for (JsonNode item : node.path("items")) {
            MenuItem parsed = parseMenuItem(item);
            if (parsed != null) {
                items.add(parsed);
            }
        }
        return new MenuData(items);
    }

    private static MenuItem parseMenuItem(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        List<SubMenuItem> subItems = new ArrayList<>();
        for (JsonNode sub : node.path("sub_menu_items")) {
            SubMenuItem parsed = parseSubMenuItem(sub);
            if (parsed != null) {
                subItems.add(parsed);
            }
        }
        Switch switchConfig = parseSwitch(node.path("switch"));
        return new MenuItem(
                node.path("name").asText(null),
                node.path("type").asText(null),
                subItems.isEmpty() ? null : subItems,
                node.path("send_message").asText(null),
                node.path("link").asText(null),
                switchConfig,
                node.path("align").asText(null));
    }

    private static SubMenuItem parseSubMenuItem(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return new SubMenuItem(
                node.path("name").asText(null),
                node.path("type").asText(null),
                node.path("send_message").asText(null),
                node.path("link").asText(null));
    }

    private static Switch parseSwitch(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return new Switch(
                node.path("switch_id").asText(null),
                node.path("default").asBoolean(false));
    }

    private static Map<String, Object> menuToMap(MenuData menu) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (menu.items() != null) {
            List<Object> items = new ArrayList<>();
            for (MenuItem item : menu.items()) {
                Map<String, Object> itemMap = itemToMap(item);
                if (itemMap != null) {
                    items.add(itemMap);
                }
            }
            map.put("items", items);
        }
        return map;
    }

    private static Map<String, Object> itemToMap(MenuItem item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "name", item.name());
        putIfPresent(map, "type", item.type());
        if (item.subMenuItems() != null && !item.subMenuItems().isEmpty()) {
            List<Object> subs = new ArrayList<>();
            for (SubMenuItem sub : item.subMenuItems()) {
                Map<String, Object> subMap = subItemToMap(sub);
                if (subMap != null) {
                    subs.add(subMap);
                }
            }
            map.put("sub_menu_items", subs);
        }
        putIfPresent(map, "send_message", item.sendMessage());
        putIfPresent(map, "link", item.link());
        if (item.switchConfig() != null) {
            map.put("switch", switchToMap(item.switchConfig()));
        }
        putIfPresent(map, "align", item.align());
        return map;
    }

    private static Map<String, Object> subItemToMap(SubMenuItem item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "name", item.name());
        putIfPresent(map, "type", item.type());
        putIfPresent(map, "send_message", item.sendMessage());
        putIfPresent(map, "link", item.link());
        return map;
    }

    private static Map<String, Object> switchToMap(Switch sw) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "switch_id", sw.switchId());
        map.put("default", sw.defaultOn());
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
