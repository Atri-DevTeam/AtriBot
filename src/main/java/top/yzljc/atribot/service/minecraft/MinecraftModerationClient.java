package top.yzljc.atribot.service.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** 远端 Minecraft 名字与皮肤审核 API 客户端。 */
public final class MinecraftModerationClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private MinecraftModerationClient() {}

    public static JsonNode filterName(String name) { return getJson("/minecraft/name/" + path(name)); }
    public static byte[] avatar(String skinId) { return getPng("/minecraft/avatar/" + path(skinId)); }
    public static byte[] skin3d(String skinId) { return getPng("/minecraft/skin3d/" + path(skinId)); }
    public static JsonNode submitName(String name) { return post("/minecraft-moderation/names", Map.of("name", name)); }
    public static JsonNode submitSkin(String player) { return post("/minecraft-moderation/skins", Map.of("player", player)); }
    public static JsonNode listNames(String status, int page, int size) { return list("names", status, page, size); }
    public static JsonNode listSkins(String status, int page, int size) { return list("skins", status, page, size); }
    public static JsonNode reviewName(long id, String status, String reason, String reviewer) {
        return put("/minecraft-moderation/names/" + id, reviewBody(status, reason, reviewer));
    }
    public static JsonNode reviewSkin(long id, String status, String reason, String reviewer) {
        return put("/minecraft-moderation/skins/" + id, reviewBody(status, reason, reviewer));
    }
    public static byte[] preview(String skinId, String type) {
        return getPng("/minecraft-moderation/skins/" + path(skinId) + "/preview/" + path(type.toUpperCase()));
    }

    private static JsonNode list(String type, String status, int page, int size) {
        String query = "?page=" + Math.max(1, page) + "&size=" + Math.max(1, Math.min(100, size));
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) query += "&status=" + path(status.toUpperCase());
        return getJson("/minecraft-moderation/" + type + query);
    }

    private static Map<String, String> reviewBody(String status, String reason, String reviewer) {
        return Map.of("status", status, "reason", reason == null ? "" : reason,
                "reviewer", reviewer == null || reviewer.isBlank() ? "webui" : reviewer);
    }

    private static JsonNode getJson(String endpoint) {
        requireKey();
        var result = HttpService.sendGetRequestDetailed(url(endpoint), "Authorization", authValue());
        return parse(result.status(), result.body());
    }

    private static JsonNode post(String endpoint, Object body) {
        try {
            requireKey();
            var result = HttpService.postJsonDetailed(url(endpoint), MAPPER.writeValueAsString(body), "Authorization", authValue());
            return parse(result.status(), result.body());
        } catch (Exception e) { throw new ModerationException(500, e.getMessage()); }
    }

    private static JsonNode put(String endpoint, Object body) {
        try {
            requireKey();
            var result = HttpService.putJsonDetailed(url(endpoint), MAPPER.writeValueAsString(body), "Authorization", authValue());
            return parse(result.status(), result.body());
        } catch (Exception e) { throw new ModerationException(500, e.getMessage()); }
    }

    private static byte[] getPng(String endpoint) {
        requireKey();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url(endpoint))).timeout(Duration.ofSeconds(30))
                    .header("Authorization", authValue()).header("Accept", "image/png").GET().build();
            HttpResponse<byte[]> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ModerationException(response.statusCode(), "远端图片请求失败");
            return response.body();
        } catch (ModerationException e) { throw e; }
        catch (Exception e) { throw new ModerationException(502, "无法连接 Minecraft 审核服务: " + e.getMessage()); }
    }

    private static JsonNode parse(int status, String body) {
        if (status == 0) throw new ModerationException(502, "无法连接 Minecraft 审核服务");
        try {
            JsonNode json = body == null || body.isBlank() ? MAPPER.nullNode() : MAPPER.readTree(body);
            if (status < 200 || status >= 300) throw new ModerationException(status, json.path("message").asText("远端审核请求失败"));
            return json.path("data");
        } catch (ModerationException e) { throw e; }
        catch (Exception e) { throw new ModerationException(502, "远端审核响应格式错误"); }
    }

    private static String authValue() { return "API " + Config.getInstance().getMinecraftModerationReviewKey().trim(); }
    private static void requireKey() {
        String key = Config.getInstance().getMinecraftModerationReviewKey();
        if (key == null || key.isBlank() || "null".equalsIgnoreCase(key.trim())) throw new ModerationException(503, "未配置 minecraft-moderation.review-key");
    }
    private static String url(String endpoint) { return ResourcesProperties.MINECRAFT_MODERATION_API.replaceAll("/+$", "") + endpoint; }
    private static String path(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20"); }

    public static final class ModerationException extends RuntimeException {
        private final int status;
        public ModerationException(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
