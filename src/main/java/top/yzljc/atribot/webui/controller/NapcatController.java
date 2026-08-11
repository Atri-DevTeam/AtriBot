package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.napcat.GroupContentRecord;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.webui.Result;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.yzljc.atribot.webui.WebUiSupport.isBlank;

/** Napcat 平台 + 官方 API 调试 */
public class NapcatController {

    public static void listNapcatGroups(Context ctx) {
        List<NapcatGroupDTO> groups = new ArrayList<>();
        for (String groupId : GroupInformation.fetchAllGroupIds()) {
            groups.add(new NapcatGroupDTO(groupId, GroupInformation.getGroupName(groupId)));
        }
        groups.sort(Comparator.comparing(NapcatGroupDTO::name, Comparator.nullsLast(String::compareTo))
                .thenComparing(NapcatGroupDTO::groupId));
        ctx.json(Result.success(groups));
    }

    public static void getNapcatGroupFeatures(Context ctx) {
        String groupId = ctx.pathParam("groupId");
        if (!GroupInformation.fetchAllGroupIds().contains(groupId)) {
            ctx.json(Result.fail(404, "群聊不在服务范围内"));
            return;
        }

        Map<String, Boolean> features = new LinkedHashMap<>();
        for (String feature : GroupConfigManager.getFeatureList()) {
            features.put(feature, GroupConfigManager.isFeatureEnabled(groupId, feature));
        }
        ctx.json(Result.success(new NapcatFeatureConfigDTO(groupId, features)));
    }

    public static void setNapcatGroupFeature(Context ctx) {
        String groupId = ctx.pathParam("groupId");
        String feature = ctx.pathParam("feature");
        boolean enabled = Boolean.parseBoolean(ctx.queryParam("enabled"));

        if (!GroupConfigManager.getRegisteredFeatures().containsKey(feature)) {
            ctx.json(Result.fail(404, "未知的功能: " + feature));
            return;
        }

        GroupConfigManager.setFeature(groupId, feature, enabled);
        ctx.json(Result.success(new NapcatFeatureDTO(feature, GroupConfigManager.isFeatureEnabled(groupId, feature))));
    }

    public static void fetchNapcatMessages(Context ctx) {
        NapcatMessageRequestDTO dto = ctx.bodyAsClass(NapcatMessageRequestDTO.class);
        if (dto == null || isBlank(dto.getGroupId())) {
            ctx.json(Result.fail(400, "groupId 不能为空"));
            return;
        }
        long groupId;
        try {
            groupId = Long.parseLong(dto.getGroupId());
        } catch (NumberFormatException ignored) {
            ctx.json(Result.fail(400, "groupId 必须是数字"));
            return;
        }
        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(dto.getGroupId())) {
            ctx.json(Result.fail(404, "未开启该群的消息监听"));
            return;
        }
        int page = Math.max(dto.getPage(), 1);
        ctx.json(Result.success(GroupContentRecord.fetchMessages(groupId, page)));
    }

    public static void recallNapcatMessages(Context ctx) {
        NapcatRecallDTO dto = ctx.bodyAsClass(NapcatRecallDTO.class);
        if (dto == null || dto.getMessageIds() == null || dto.getMessageIds().isEmpty()) {
            ctx.json(Result.fail(400, "messageIds 不能为空"));
            return;
        }

        for (Long messageId : dto.getMessageIds()) {
            if (messageId != null) {
                GroupMessage.recallMessage(String.valueOf(messageId));
            }
        }
        ctx.json(Result.success("ok"));
    }

    public static void debugOfficialApi(Context ctx) {
        OfficialApiDebugRequestDTO dto = ctx.bodyAsClass(OfficialApiDebugRequestDTO.class);
        if (dto == null || isBlank(dto.getPath())) {
            ctx.json(Result.fail(400, "API 路径不能为空"));
            return;
        }

        String method = normalizeHttpMethod(dto.getMethod());
        if (method == null) {
            ctx.json(Result.fail(400, "不支持的请求方法"));
            return;
        }

        String targetUrl;
        try {
            targetUrl = buildOfficialApiUrl(dto.getPath());
        } catch (IllegalArgumentException e) {
            ctx.json(Result.fail(400, e.getMessage()));
            return;
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "QQBot " + Atri.getInstance().getTokenManager().getAccessToken());

            boolean hasBody = dto.getBody() != null && !dto.getBody().isBlank();
            if (hasBody && !hasDebugHeader(dto.getHeaders(), "content-type")) {
                builder.header("Content-Type", "application/json");
            }

            if (dto.getHeaders() != null) {
                dto.getHeaders().forEach((name, value) -> {
                    if (isAllowedDebugHeader(name) && value != null) {
                        builder.header(name.trim(), value);
                    }
                });
            }

            if ("GET".equals(method) || "HEAD".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else if (hasBody) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(dto.getBody()));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = HttpService.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            ctx.json(Result.success(new OfficialApiDebugResponseDTO(
                    method,
                    targetUrl,
                    response.statusCode(),
                    response.headers().map(),
                    response.body(),
                    System.currentTimeMillis() - start
            )));
        } catch (Exception e) {
            ctx.json(Result.fail(500, e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    private static String normalizeHttpMethod(String method) {
        String value = method == null ? "GET" : method.trim().toUpperCase();
        Set<String> allowed = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
        return allowed.contains(value) ? value : null;
    }

    private static String buildOfficialApiUrl(String path) {
        String value = path.trim();
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//")) {
            throw new IllegalArgumentException("只允许填写相对 API 路径");
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        String base = Config.getInstance().getQqApiBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + value;
    }

    private static boolean isAllowedDebugHeader(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String key = name.trim().toLowerCase();
        return !Set.of("authorization", "host", "content-length", "connection").contains(key);
    }

    private static boolean hasDebugHeader(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return false;
        }
        return headers.keySet().stream().anyMatch(key -> name.equalsIgnoreCase(key));
    }

    public record NapcatGroupDTO(String groupId, String name) {
    }

    public record NapcatFeatureConfigDTO(String groupId, Map<String, Boolean> features) {
    }

    public record NapcatFeatureDTO(String feature, boolean enabled) {
    }

    @Data
    public static class NapcatMessageRequestDTO {
        private String groupId;
        private int page;
    }

    @Data
    public static class NapcatRecallDTO {
        private List<Long> messageIds;
    }

    @Data
    public static class OfficialApiDebugRequestDTO {
        private String method;
        private String path;
        private Map<String, String> headers;
        private String body;
    }

    public record OfficialApiDebugResponseDTO(String method, String url, int statusCode,
                                              Map<String, List<String>> headers, String body,
                                              long durationMillis) {
    }
}
