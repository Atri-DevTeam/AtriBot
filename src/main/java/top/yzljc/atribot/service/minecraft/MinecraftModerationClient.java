package top.yzljc.atribot.service.minecraft;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.BizResponse;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftModerationClient
 * @Created_at 2026/10/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.minecraft
 */
public final class MinecraftModerationClient {
    private static final Client CLIENT = new Client(OpenApi::get,
            () -> Config.getInstance().getMinecraftModerationReviewKey());

    private MinecraftModerationClient() {}

    public static JsonNode filterName(String name) { return CLIENT.filterName(name); }
    public static byte[] avatar(String skinId) { return CLIENT.avatar(skinId); }
    public static byte[] skin3d(String skinId) { return CLIENT.skin3d(skinId); }
    public static JsonNode submitName(String name) { return CLIENT.submitName(name); }
    public static JsonNode submitSkin(String player) { return CLIENT.submitSkin(player); }
    public static JsonNode listNames(String status, int page, int size) { return CLIENT.list("names", status, page, size); }
    public static JsonNode listSkins(String status, int page, int size) { return CLIENT.list("skins", status, page, size); }
    public static JsonNode reviewName(long id, String status, String reason, String reviewer) {
        return CLIENT.review("names", id, status, reason, reviewer);
    }
    public static JsonNode reviewSkin(long id, String status, String reason, String reviewer) {
        return CLIENT.review("skins", id, status, reason, reviewer);
    }
    public static byte[] preview(String skinId, String type) { return CLIENT.preview(skinId, type); }

    static final class Client {
        private final Function<String, String> routes;
        private final Supplier<String> key;

        Client(Function<String, String> routes, Supplier<String> key) {
            this.routes = routes;
            this.key = key;
        }

        JsonNode filterName(String name) {
            String auth = authValue();
            return data(Requests.get(route("minecraft.name", "name", name), "Authorization", auth));
        }

        byte[] avatar(String skinId) { return image("minecraft.avatar", "skinId", skinId); }
        byte[] skin3d(String skinId) { return image("minecraft.skin3d", "skinId", skinId); }

        JsonNode submitName(String name) { return post("admin.minecraft.names.submit", Map.of("name", name)); }
        JsonNode submitSkin(String player) { return post("admin.minecraft.skins.submit", Map.of("player", player)); }

        JsonNode list(String type, String status, int page, int size) {
            String auth = authValue();
            String query = "?page=" + Math.max(1, page) + "&size=" + Math.max(1, Math.min(100, size));
            if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) query += "&status=" + path(status.toUpperCase());
            return data(Requests.get(routes.apply("admin.minecraft." + type + ".list") + query, "Authorization", auth));
        }

        JsonNode review(String type, long id, String status, String reason, String reviewer) {
            String auth = authValue();
            Map<String, String> body = Map.of("status", status, "reason", reason == null ? "" : reason,
                    "reviewer", reviewer == null || reviewer.isBlank() ? "webui" : reviewer);
            return data(Requests.put(route("admin.minecraft." + type + ".review", "id", String.valueOf(id)), body, "Authorization", auth));
        }

        byte[] preview(String skinId, String type) {
            return image("admin.minecraft.skins.preview", "skinId", skinId, "asset", type.toUpperCase());
        }

        private JsonNode post(String route, Object body) {
            String auth = authValue();
            return data(Requests.post(routes.apply(route), body, "Authorization", auth));
        }

        private byte[] image(String key, String... parameters) {
            String auth = authValue();
            return data(Requests.getBytes(route(key, parameters), Duration.ofSeconds(30), "Authorization", auth));
        }

        private String route(String key, String... parameters) {
            String url = routes.apply(key);
            for (int i = 0; i < parameters.length; i += 2) {
                url = url.replace("{" + parameters[i] + "}", path(parameters[i + 1]));
            }
            return url;
        }

        private String authValue() {
            String value = key.get();
            if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
                throw new ModerationException(503, "未配置 minecraft-moderation.review-key");
            }
            return "API " + value.trim();
        }
    }

    private static <T> T data(BizResponse<T> response) {
        if (!response.isSuccess()) {
            throw new ModerationException(response.httpCode() == 0 ? 502 : response.httpCode(), response.message());
        }
        return response.data();
    }

    private static String path(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static final class ModerationException extends RuntimeException {
        private final int status;
        public ModerationException(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
}
