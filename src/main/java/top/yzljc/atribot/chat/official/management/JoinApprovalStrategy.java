package top.yzljc.atribot.chat.official.management;

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
 * @ClassName JoinApprovalStrategy
 * @Created_at 2026/08/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.management
 * @Description 入群自动审批策略管理
 *
 * 将关联群中命中白名单 QQ 号码的入群申请自动审批通过
 * 一个机器人最多创建 20 个策略，策略仅在机器人拥有对应群管理员身份时生效
 * 接口频率限制 60 QPM
 */
@Slf4j
public final class JoinApprovalStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 列表分页：默认单页条数与最大条数 */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    /** 白名单号码操作类型 */
    public enum WhitelistOp {
        ADD("add"),
        DEL("del");

        private final String value;

        WhitelistOp(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /** 关联群增删操作（修改策略时使用） */
    public enum GroupActionOp {
        ADD("add"),
        DEL("del");

        private final String value;

        GroupActionOp(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static String baseUrl() {
        return Config.getInstance().getQqApiBaseUrl() + "/v2/groups/join_approval_strategy";
    }

    private static String authHeader() {
        return "QQBot " + Atri.getInstance().getTokenManager().getAccessToken();
    }

    /**
     * 查询策略列表，next_cursor 为空字符串表示已到末页
     * 失败返回 null
     *
     * @param cursor 分页游标，首次查询可为空
     * @param limit  单页数量，默认 20，最大 100
     * @return 策略列表结果，失败为 null
     */
    public static StrategyListResult listStrategies(String cursor, int limit) {
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        }
        if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
        String url = baseUrl() + "?limit=" + limit;
        if (cursor != null && !cursor.isBlank()) {
            url += "&cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
        }
        String scene = "入群审批策略列表";
        try {
            HttpService.GetResult result = HttpService.sendGetRequestDetailed(url, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "GET", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "GET", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 查询入群审批策略列表失败，HTTP 状态码 {}，游标为 {}", result.status(), cursor);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "GET", url, null,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            if (response == null || response.isNull() || response.isMissingNode()) {
                log.error("[!] 查询入群审批策略列表失败，响应为空，游标为 {}", cursor);
                return null;
            }
            List<StrategyData> strategies = new ArrayList<>();
            for (JsonNode node : response.path("strategies")) {
                strategies.add(parseStrategy(node));
            }
            return new StrategyListResult(strategies, response.path("next_cursor").asText(""));
        } catch (Exception e) {
            log.error("[!] 查询入群审批策略列表异常，游标为 {}", cursor, e);
            OfficialSendLogRepository.recordError(null, scene, "GET", url, null, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 创建入群审批策略
     * groupOpenIds 与 groupIds 必须二选一，不能同时传入
     * 成功返回服务端生成的 strategy_id，失败返回 null
     *
     * @param groupOpenIds 关联群 openid 列表，最多 100 个
     * @param groupIds     关联 QQ 群号列表（字符串避免精度问题），最多 100 个
     * @param enable       是否启用策略
     * @param expireAt     过期时间，RFC3339 格式，为空则按接口默认一年后过期
     * @param remark       策略备注，最多 255 个汉字，可为空
     * @return 新策略 ID，失败为 null
     */
    public static String createStrategy(List<String> groupOpenIds, List<String> groupIds,
                                        boolean enable, String expireAt, String remark) {
        boolean hasOpenIds = groupOpenIds != null && !groupOpenIds.isEmpty();
        boolean hasGroupIds = groupIds != null && !groupIds.isEmpty();
        if (!hasOpenIds && !hasGroupIds) {
            return null;
        }
        if (hasOpenIds && hasGroupIds) {
            return null;
        }
        String url = baseUrl();
        String scene = "创建入群审批策略";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (hasOpenIds) {
                body.put("group_openids", groupOpenIds);
            } else {
                body.put("group_ids", groupIds);
            }
            body.put("is_enable", enable ? "on" : "off");
            if (expireAt != null && !expireAt.isBlank()) {
                body.put("expire_at", expireAt);
            }
            if (remark != null && !remark.isBlank()) {
                body.put("remark", remark);
            }
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 创建入群审批策略失败，HTTP 状态码 {}，关联群为 {}", result.status(), hasOpenIds ? groupOpenIds : groupIds);
                return null;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());

            JsonNode response = OBJECT_MAPPER.readTree(result.body());
            return response == null ? null : response.path("strategy_id").asText(null);
        } catch (Exception e) {
            log.error("[!] 创建入群审批策略异常，关联群为 {}", hasOpenIds ? groupOpenIds : groupIds, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 修改入群审批策略的基础属性（启用状态 / 过期时间 / 备注）
     * 只传需要改的字段，其余保持原样
     *
     * @param strategyId 策略 ID
     * @param enable     是否启用，为空则不改动
     * @param expireAt   过期时间，RFC3339 格式，为空则不改动
     * @param remark     策略备注，为空则不改动
     * @return 是否成功
     */
    public static boolean updateStrategy(String strategyId, Boolean enable, String expireAt, String remark) {
        if (strategyId == null || strategyId.isBlank()) {
            return false;
        }
        String url = baseUrl() + "/" + strategyId;
        String scene = "修改入群审批策略";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            if (enable != null) {
                body.put("is_enable", enable ? "on" : "off");
            }
            if (expireAt != null && !expireAt.isBlank()) {
                body.put("expire_at", expireAt);
            }
            if (remark != null && !remark.isBlank()) {
                body.put("remark", remark);
            }
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.patchJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "PATCH", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "PATCH", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 修改入群审批策略失败，HTTP 状态码 {}，策略ID为 {}", result.status(), strategyId);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "PATCH", url, requestJson,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 修改入群审批策略异常，策略ID为 {}", strategyId, e);
            OfficialSendLogRepository.recordError(null, scene, "PATCH", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 修改策略的关联群（新增或删除），群标识形式必须与创建时一致
     *
     * @param strategyId   策略 ID
     * @param op           关联群增删操作
     * @param groupOpenIds 待操作的群 openid 列表，与 groupIds 互斥
     * @param groupIds     待操作的 QQ 群号列表，与 groupOpenIds 互斥
     * @return 是否成功
     */
    public static boolean updateStrategyGroups(String strategyId, GroupActionOp op,
                                               List<String> groupOpenIds, List<String> groupIds) {
        if (strategyId == null || strategyId.isBlank() || op == null) {
            return false;
        }
        boolean hasOpenIds = groupOpenIds != null && !groupOpenIds.isEmpty();
        boolean hasGroupIds = groupIds != null && !groupIds.isEmpty();
        if (!hasOpenIds && !hasGroupIds) {
            return false;
        }
        String url = baseUrl() + "/" + strategyId;
        String scene = "修改入群审批策略";
        String requestJson = null;
        try {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("op", op.value());
            if (hasOpenIds) {
                action.put("group_openids", groupOpenIds);
            } else {
                action.put("group_ids", groupIds);
            }
            Map<String, Object> body = Map.of("group_action", action);
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.patchJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "PATCH", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "PATCH", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 修改入群审批策略关联群失败，HTTP 状态码 {}，策略ID为 {}，操作为 {}", result.status(), strategyId, op);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "PATCH", url, requestJson,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 修改入群审批策略关联群异常，策略ID为 {}", strategyId, e);
            OfficialSendLogRepository.recordError(null, scene, "PATCH", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 删除入群审批策略，接口响应体为空
     *
     * @param strategyId 策略 ID
     * @return 是否成功
     */
    public static boolean deleteStrategy(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) {
            return false;
        }
        String url = baseUrl() + "/" + strategyId;
        String scene = "删除入群审批策略";
        try {
            HttpService.GetResult result = HttpService.deleteRequestDetailed(url, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "DELETE", url, null);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "DELETE", url, null,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 删除入群审批策略失败，HTTP 状态码 {}，策略ID为 {}", result.status(), strategyId);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "DELETE", url, null,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 删除入群审批策略异常，策略ID为 {}", strategyId, e);
            OfficialSendLogRepository.recordError(null, scene, "DELETE", url, null, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 对策略关联的全部群发起全量扫描，将命中白名单号码的入群申请自动审批通过
     * 任务异步执行，约 10 分钟完成，接口响应体为空
     *
     * @param strategyId 策略 ID
     * @return 是否成功提交
     */
    public static boolean executeStrategy(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) {
            return false;
        }
        String url = baseUrl() + "/" + strategyId + "/execute";
        String scene = "执行入群审批策略";
        String requestJson = "{}";
        try {
            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 执行入群审批策略失败，HTTP 状态码 {}，策略ID为 {}", result.status(), strategyId);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 执行入群审批策略异常，策略ID为 {}", strategyId, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 修改策略白名单号码（新增或删除）
     *
     * @param strategyId    策略 ID
     * @param op            白名单操作
     * @param whitelistUsers QQ 号码列表（字符串避免精度问题），单次最多 10000 个
     * @return 是否成功
     */
    public static boolean updateWhitelist(String strategyId, WhitelistOp op, List<String> whitelistUsers) {
        if (strategyId == null || strategyId.isBlank() || op == null
                || whitelistUsers == null || whitelistUsers.isEmpty()) {
            return false;
        }
        String url = baseUrl() + "/" + strategyId + "/whitelist_users";
        String scene = "修改入群审批白名单";
        String requestJson = null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("op", op.value());
            body.put("whitelist_users", whitelistUsers);
            requestJson = OBJECT_MAPPER.writeValueAsString(body);

            HttpService.PostResult result = HttpService.postJsonDetailed(url, requestJson, "Authorization", authHeader());
            String traceId = OfficialSendLogRepository.recordSend(scene, "POST", url, requestJson);
            if (result.status() < 200 || result.status() >= 300) {
                OfficialSendLogRepository.recordError(traceId, scene, "POST", url, requestJson,
                        result.status(), result.body(), "HTTP 状态异常: " + result.status());
                log.error("[!] 修改入群审批白名单失败，HTTP 状态码 {}，策略ID为 {}，操作为 {}", result.status(), strategyId, op);
                return false;
            }
            OfficialSendLogRepository.recordResponse(traceId, scene, "POST", url, requestJson,
                    result.status(), result.body());
            return true;
        } catch (Exception e) {
            log.error("[!] 修改入群审批白名单异常，策略ID为 {}", strategyId, e);
            OfficialSendLogRepository.recordError(null, scene, "POST", url, requestJson, 0, null,
                    "异常: " + e.getMessage());
            return false;
        }
    }

    private static StrategyData parseStrategy(JsonNode node) {
        List<String> groupOpenIds = new ArrayList<>();
        for (JsonNode id : node.path("group_openids")) {
            groupOpenIds.add(id.asText());
        }
        List<String> groupIds = new ArrayList<>();
        for (JsonNode id : node.path("group_ids")) {
            groupIds.add(id.asText());
        }
        return new StrategyData(
                node.path("strategy_id").asText(null),
                groupOpenIds,
                groupIds,
                node.path("whitelist_user_count").asInt(0),
                "on".equals(node.path("is_enable").asText("")),
                node.path("expire_at").asText(null),
                node.path("created_at").asText(null),
                node.path("updated_at").asText(null),
                node.path("remark").asText(null));
    }

    /** 策略列表查询结果，nextCursor 为空字符串表示已到末页 */
    public record StrategyListResult(List<StrategyData> strategies, String nextCursor) {}

    /** 单条入群审批策略，关联群列表按创建时使用的标识形式返回 */
    public record StrategyData(String strategyId, List<String> groupOpenIds, List<String> groupIds,
                               int whitelistUserCount, boolean enable, String expireAt,
                               String createdAt, String updatedAt, String remark) {}
}
