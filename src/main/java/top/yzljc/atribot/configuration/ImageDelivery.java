package top.yzljc.atribot.configuration;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.tools.Alert;

/**
 * @Author AndyOctopus
 * @ClassName ImageDelivery
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
@Slf4j
public final class ImageDelivery {

    private static final String WAY_OSS = "oss";

    /**
     * @param data 图片响应数据
     * @param renderUrl 生成图片的接口地址，用于解析同源 API 路径
     * @param platform 图片接收平台；为 null 时遵循生图服务返回的分发方式
     * @return 图片地址，缺少有效地址或图片标识时返回 null
     */
    public static String resolve(JsonNode data, String renderUrl, Platform platform) {
        if (platform != null && !platform.isOfficialQQPlatform()) {
            return resolveApi(data, renderUrl);
        }
        return resolve(data);
    }

    /**
     * 按生图服务返回的分发方式解析图片地址。
     *
     * @param data 图片响应数据
     * @return 图片地址；缺少有效地址或图片标识时返回 null
     */
    public static String resolve(JsonNode data) {
        if (data == null) {
            return null;
        }
        String url = absoluteUrl(data);
        if (url != null) {
            return url;
        }
        if ("cos".equalsIgnoreCase(data.path("way").asText())) {
            return null;
        }
        String uuid = data.path("uuid").asText(null);
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        String way = data.path("way").asText(null);
        return resolve(uuid, way);
    }

    private static String resolve(String uuid, String way) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        if (WAY_OSS.equalsIgnoreCase(way)) {
            String ossRoot = ossDumpRoot();
            if (ossRoot != null) {
                Alert.notify("触发OSS端流量转移：远程服务器要求调转至OSS端口，已回落到OSS根节点！");
                return join(ossRoot, uuid);
            }
            log.warn("生图端要求走 OSS，但本端未配置 delivery.oss-dump-base-url，本次回落到 API 根节点: uuid={}", uuid);
        }
        return imageUrl(OpenApi.get("bot.image.get"), "uuid", uuid);
    }

    /**
     * @param data 包含 api_url 的图片响应数据
     * @param renderUrl 生成图片的接口地址
     * @return 同源 API 取图地址；路径缺失或无效时返回 null，不回退到对象存储
     */
    public static String resolveApi(JsonNode data, String renderUrl) {
        if (data == null || !data.path("api_url").isTextual()) return null;
        String path = data.path("api_url").asText();
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\")) return null;
        try {
            URI base = URI.create(renderUrl);
            URI relative = URI.create(path);
            if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null
                    || relative.getRawAuthority() != null || relative.getRawFragment() != null) return null;
            return base.resolve(relative).toString();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return null;
        }
    }

    /**
     * 从服务端响应中获取完整的 COS 图片地址，保留签名参数。
     *
     * @param data 图片响应数据，way 必须为 {@code cos}
     * @return 有效的 HTTP(S) COS 地址；分发方式不匹配或地址缺失、无效时返回 null
     */
    public static String resolveCos(JsonNode data) {
        if (data == null || !"cos".equalsIgnoreCase(data.path("way").asText())) {
            return null;
        }
        return absoluteUrl(data);
    }

    public static String resolveDrawCard(JsonNode data) {
        return resolveDrawCard(data, () -> OpenApi.get("bot.loots.draw.image"));
    }

    static String resolveDrawCard(JsonNode data, Supplier<String> template) {
        if (data == null) return null;
        String url = absoluteUrl(data);
        if (url != null) return url;
        if ("cos".equalsIgnoreCase(data.path("way").asText())) return null;
        String itemId = data.path("item_id").asText(data.path("uuid").asText(null));
        if (itemId == null || itemId.isBlank()) return null;
        return imageUrl(template.get(), "itemId", itemId);
    }

    static String imageUrl(String template, String parameter, String value) {
        return template.replace("{" + parameter + "}", URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    private static String absoluteUrl(JsonNode data) {
        String value = data.path("url").asText("");
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null ? value : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String ossDumpRoot() {
        String root = Config.getInstance().getOssDumpBaseUrl();
        if (root == null || root.isBlank() || "null".equalsIgnoreCase(root.trim())) {
            return null;
        }
        return root.trim();
    }

    private static String join(String root, String uuid) {
        return root.endsWith("/") ? root + uuid : root + "/" + uuid;
    }
}
