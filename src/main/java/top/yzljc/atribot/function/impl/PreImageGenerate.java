package top.yzljc.atribot.function.impl;

import java.util.Map;
import java.util.Locale;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ImageDelivery;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atribot.service.request.BizResponse;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;

/**
 * @Author YZ_Ljc_
 * @ClassName PreImageGenerate
 * @Created_at 2026/06/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.impl
 */
public class PreImageGenerate {

    private static final String AUTH_HEADER = "Authorization";

    private static String bearer() {
        return "Bearer " + Config.getInstance().getAtribotKeySecret();
    }

    public static ImageDTO dump(String url) {
        return dump(Map.of("url", url));
    }

    public static ImageDTO dump(String url, Platform platform) {
        return dump(Map.of("url", url), platform);
    }

    public static ImageDTO dump(Map<String, ?> body) {
        return dump(body, null);
    }

    public static ImageDTO dump(Map<String, ?> body, Platform platform) {
        return dump(OpenApi.get("bot.image.dump"), body, platform);
    }

    public static ImageDTO dump(String url, Map<String, ?> body) {
        return dump(url, body, (Platform) null);
    }

    /**
     * @param url 图片生成接口地址
     * @param body 图片生成请求参数
     * @param platform 图片接收平台；官机 QQ 或 null 遵循服务端 HTTP 分发方式，其余平台使用 API
     * @return 包含所选取图地址的图片结果，失败时保留错误信息和请求标识
     */
    public static ImageDTO dump(String url, Map<String, ?> body, Platform platform) {
        return image(Requests.post(url, body, AUTH_HEADER, bearer()), url, platform);
    }

    /**
     * 生成用于 Markdown 的图片，QQ 群聊和私聊遵循服务端返回的素材、COS 或 API 分发方式
     *
     * @param url 图片生成接口地址
     * @param body 图片生成请求参数
     * @param platform 图片接收平台；频道及其他平台使用 HTTP 图片地址
     * @return 可嵌入 Markdown 的图片结果，失败时保留错误信息和请求标识
     */
    public static ImageDTO dumpMarkdown(String url, Map<String, ?> body, Platform platform) {
        return markdownImage(Requests.post(url, body, AUTH_HEADER, bearer()), url, platform);
    }

    /**
     * 生成图片并按指定方式选择响应中的取图地址，不根据接收平台推断分发方式。
     * 指定 COS 时要求服务端返回 COS 地址；指定 API 时使用同源 API 路径。
     * 指定 qqbot-media 时返回用于 Markdown 的素材引用，普通图片没法用
     * 目标地址不可用时返回图片错误，不回退到其他分发方式。
     *
     * @param url 图片生成接口地址
     * @param body 图片生成请求参数
     * @param way 取图方式，支持 {@code cos}、{@code api} 和用于 Markdown 的 {@code qqbot-media}，忽略大小写及首尾空白
     * @return 包含指定取图地址的图片结果；失败时包含错误信息和请求标识
     * @throws IllegalArgumentException 当 way 为 null、空白或不支持的取图方式时抛出，请求不会发送
     */
    public static ImageDTO dump(String url, Map<String, ?> body, String way) {
        if (way == null) {
            throw new IllegalArgumentException("图片取图方式不能为空");
        }
        Function<JsonNode, String> resolve = switch (way.trim().toLowerCase(Locale.ROOT)) {
            case "cos" -> ImageDelivery::resolveCos;
            case "api" -> data -> ImageDelivery.resolveApi(data, url);
            case "qqbot-media" -> ImageDelivery::resolveMaterial;
            default -> throw new IllegalArgumentException("不支持的图片取图方式: " + way);
        };
        return image(Requests.post(url, body, AUTH_HEADER, bearer()), resolve);
    }

    public static ImageDTO dumpViaApi(String url, Map<String, ?> body) {
        return image(Requests.post(url, body, AUTH_HEADER, bearer()), data -> ImageDelivery.resolveApi(data, url));
    }

    static ImageDTO image(BizResponse<JsonNode> response, String renderUrl, Platform platform) {
        // 普通图片组件不能使用 Markdown 素材引用，保留同源 API 取图
        return image(response, data -> "qqbot-media".equalsIgnoreCase(data.path("way").asText())
                ? ImageDelivery.resolveApi(data, renderUrl) : ImageDelivery.resolve(data, renderUrl, platform));
    }

    static ImageDTO markdownImage(BizResponse<JsonNode> response, String renderUrl, Platform platform) {
        return image(response, data -> "api".equalsIgnoreCase(data.path("way").asText())
                ? ImageDelivery.resolveApi(data, renderUrl) : ImageDelivery.resolve(data, renderUrl, platform));
    }

    private static ImageDTO image(BizResponse<JsonNode> response, Function<JsonNode, String> resolve) {
        if (!response.isSuccess()) {
            return new ImageDTO(null, 0, 0, response.message(), response.requestId());
        }
        JsonNode data = response.data();
        if (data == null || !data.isObject()) {
            return new ImageDTO(null, 0, 0, "图片服务响应数据无效，请稍后重试", response.requestId());
        }
        String imageUrl = resolve.apply(data);
        if (imageUrl == null || imageUrl.isBlank()) {
            return new ImageDTO(null, 0, 0, "图片地址无效，请稍后重试", response.requestId());
        }
        return new ImageDTO(imageUrl, data.path("width").asInt(), data.path("height").asInt());
    }

}
