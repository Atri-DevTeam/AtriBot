package top.yzljc.atribot.chat.official.panel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Panel
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.panel
 * @Description 指令面板管理
 * 面板支持 c2c / group / channel / dm 四种场景，c2c 和 group 可指定用户或群生效，一个机器人最多 20 个面板
 */
@Slf4j
public final class Panel {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    /** 关联对象操作类型：add 添加，del 移除 */
    public enum TargetOp {
        ADD("add"),
        DEL("del");

        private final String value;

        TargetOp(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** 面板元素，type 支持 command / link */
    public record PanelItem(String name, String desc, String type, boolean onlyAdmin, String link) {}

    /** 面板配置内容，items 最多 20 个 */
    public record PanelData(List<PanelItem> items, String remark) {}

    /** 面板列表项 */
    public record PanelRecord(String panelId, String scope, String targetType, PanelData panel,
                              String createdAt, String updatedAt, int version) {}

    /** 面板列表查询结果，nextCursor 为空串表示已到末页 */
    public record PanelListResult(List<PanelRecord> records, String nextCursor, boolean isEnd) {}

    /** 面板详情，userOpenIds / groupOpenIds 仅 targetType=specific 时返回 */
    public record PanelDetail(String panelId, String scope, String targetType, PanelData panel,
                              String createdAt, String updatedAt, int version,
                              List<String> userOpenIds, List<String> groupOpenIds) {}

    private static String baseUrl() {
        return Config.getInstance().getQqApiBaseUrl() + "/v2/panels";
    }

    private static String authHeader() {
        return "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
    }

    /**
     * 分页查询指定场景下的指令面板列表，失败返回 null
     *
     * @param scope  生效场景，c2c / group / channel / dm，必填
     * @param cursor 分页游标，首次查询可为空
     * @param limit  单页数量，默认 20，最大 50
     */
    public static PanelListResult listPanels(String scope, String cursor, int limit) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        String url = baseUrl() + "?scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8) + "&limit=" + limit;
        if (cursor != null && !cursor.isBlank()) {
            url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        String scene = "查询指令面板列表";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询指令面板列表失败，HTTP 状态码 {}，场景为 {}", result.status(), scope);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            if (response == null || response.isNull() || response.isMissingNode()) {
                log.error("[!] 查询指令面板列表失败，响应为空，场景为 {}", scope);
                return null;
            }
            List<PanelRecord> records = new ArrayList<>();
            for (JsonNode node : response.path("records")) {
                records.add(parsePanelRecord(node));
            }
            return new PanelListResult(records,
                    response.path("next_cursor").asText(""),
                    response.path("is_end").asBoolean(true));
        } catch (Exception e) {
            log.error("[!] 查询指令面板列表异常，场景为 {}", scope, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 创建指令面板，成功返回面板 ID，失败返回 null
     *
     * @param scope        生效场景，必填
     * @param targetType   作用范围，all 或 specific，仅 c2c/group 支持 specific
     * @param userOpenIds  用户 openid 列表，仅 c2c 且 specific 时有效
     * @param groupOpenIds 群 openid 列表，仅 group 且 specific 时有效
     * @param panel        面板配置内容，必填
     */
    public static String createPanel(String scope, String targetType, List<String> userOpenIds,
                                     List<String> groupOpenIds, PanelData panel) {
        if (scope == null || scope.isBlank() || panel == null) {
            return null;
        }
        String url = baseUrl();
        String scene = "创建指令面板";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("scope", scope);
            putIfPresent(body, "target_type", targetType);
            putStringList(body, "user_openids", userOpenIds);
            putStringList(body, "group_openids", groupOpenIds);
            body.put("panel", panelToMap(panel));
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 创建指令面板失败，HTTP 状态码 {}，场景为 {}", result.status(), scope);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            return response == null ? null : response.path("panel_id").asText(null);
        } catch (Exception e) {
            log.error("[!] 创建指令面板异常，场景为 {}", scope, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 查询指令面板详情，失败返回 null
     *
     * @param panelId 面板 ID
     */
    public static PanelDetail getPanelDetail(String panelId) {
        if (panelId == null || panelId.isBlank()) {
            return null;
        }
        String url = baseUrl() + "/" + panelId;
        String scene = "查询指令面板详情";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询指令面板详情失败，HTTP 状态码 {}，面板ID为 {}", result.status(), panelId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            if (response == null || response.isNull() || response.isMissingNode()) {
                log.error("[!] 查询指令面板详情失败，响应为空，面板ID为 {}", panelId);
                return null;
            }
            return new PanelDetail(
                    response.path("panel_id").asText(null),
                    response.path("scope").asText(null),
                    response.path("target_type").asText(null),
                    parsePanel(response.path("panel")),
                    response.path("created_at").asText(null),
                    response.path("updated_at").asText(null),
                    response.path("version").asInt(0),
                    toStringList(response.path("user_openids")),
                    toStringList(response.path("group_openids")));
        } catch (Exception e) {
            log.error("[!] 查询指令面板详情异常，面板ID为 {}", panelId, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 修改指令面板配置内容，不影响已关联对象，成功返回新版本号，失败返回 null
     *
     * @param panelId 面板 ID
     * @param panel   新的面板配置内容
     */
    public static Integer updatePanel(String panelId, PanelData panel) {
        if (panelId == null || panelId.isBlank() || panel == null) {
            return null;
        }
        String url = baseUrl() + "/" + panelId;
        String scene = "修改指令面板";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("panel", panelToMap(panel));
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.putJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "PUT", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "PUT", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 修改指令面板失败，HTTP 状态码 {}，面板ID为 {}", result.status(), panelId);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "PUT", url, requestJson,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            return response == null ? null : response.path("version").asInt(0);
        } catch (Exception e) {
            log.error("[!] 修改指令面板异常，面板ID为 {}", panelId, e);
            OfficialSendLogRepository.recordError(null, scene, "PUT", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 删除指令面板，删除后不再对任何用户或群生效
     *
     * @param panelId 面板 ID
     */
    public static boolean deletePanel(String panelId) {
        if (panelId == null || panelId.isBlank()) {
            return false;
        }
        String url = baseUrl() + "/" + panelId;
        String scene = "删除指令面板";
        try {
            HttpService.GetResult result = HttpService.deleteRequestDetailed(url, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "DELETE", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "DELETE", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 删除指令面板失败，HTTP 状态码 {}，面板ID为 {}", result.status(), panelId);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "DELETE", url, null,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 删除指令面板异常，面板ID为 {}", panelId, e);
            OfficialSendLogRepository.recordError(null, scene, "DELETE", url, null, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 修改指令面板关联对象（添加或删除用户/群 openid），channel / dm 场景不支持
     *
     * @param panelId      面板 ID
     * @param op           操作类型，add 或 del
     * @param userOpenIds  用户 openid 列表，仅 c2c 场景有效
     * @param groupOpenIds 群 openid 列表，仅 group 场景有效
     */
    public static boolean updatePanelTarget(String panelId, TargetOp op,
                                            List<String> userOpenIds, List<String> groupOpenIds) {
        if (panelId == null || panelId.isBlank() || op == null) {
            return false;
        }
        if ((userOpenIds == null || userOpenIds.isEmpty()) && (groupOpenIds == null || groupOpenIds.isEmpty())) {
            return false;
        }
        String url = baseUrl() + "/" + panelId + "/target";
        String scene = "修改指令面板关联对象";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("op", op.value());
            putStringList(body, "user_openids", userOpenIds);
            putStringList(body, "group_openids", groupOpenIds);
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.putJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "PUT", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "PUT", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 修改指令面板关联对象失败，HTTP 状态码 {}，面板ID为 {}，操作为 {}", result.status(), panelId, op);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "PUT", url, requestJson,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 修改指令面板关联对象异常，面板ID为 {}", panelId, e);
            OfficialSendLogRepository.recordError(null, scene, "PUT", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    private static PanelRecord parsePanelRecord(JsonNode node) {
        return new PanelRecord(
                node.path("panel_id").asText(null),
                node.path("scope").asText(null),
                node.path("target_type").asText(null),
                parsePanel(node.path("panel")),
                node.path("created_at").asText(null),
                node.path("updated_at").asText(null),
                node.path("version").asInt(0));
    }

    private static PanelData parsePanel(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        List<PanelItem> items = new ArrayList<>();
        for (JsonNode item : node.path("items")) {
            PanelItem parsed = parsePanelItem(item);
            if (parsed != null) {
                items.add(parsed);
            }
        }
        return new PanelData(items, node.path("remark").asText(null));
    }

    private static PanelItem parsePanelItem(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return new PanelItem(
                node.path("name").asText(null),
                node.path("desc").asText(null),
                node.path("type").asText(null),
                node.path("only_admin").asBoolean(false),
                node.path("link").asText(null));
    }

    private static Map<String, Object> panelToMap(PanelData panel) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (panel.items() != null) {
            List<Object> items = new ArrayList<>();
            for (PanelItem item : panel.items()) {
                Map<String, Object> itemMap = itemToMap(item);
                if (itemMap != null) {
                    items.add(itemMap);
                }
            }
            map.put("items", items);
        }
        putIfPresent(map, "remark", panel.remark());
        return map;
    }

    private static Map<String, Object> itemToMap(PanelItem item) {
        if (item == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "name", item.name());
        putIfPresent(map, "desc", item.desc());
        putIfPresent(map, "type", item.type());
        map.put("only_admin", item.onlyAdmin());
        putIfPresent(map, "link", item.link());
        return map;
    }

    private static List<String> toStringList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText(null);
                if (value != null && !value.isBlank()) {
                    list.add(value);
                }
            }
        }
        return list;
    }

    private static void putStringList(Map<String, Object> map, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            map.put(key, values);
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
