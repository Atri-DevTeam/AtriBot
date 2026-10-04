package top.yzljc.atribot.function.impl;

import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ImageDelivery;
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

    public static ImageDTO dump(Map<String, ?> body) {
        return dump(OpenApi.get("bot.image.dump"), body);
    }

    public static ImageDTO dump(String url, Map<String, ?> body) {
        return image(Requests.post(url, body, AUTH_HEADER, bearer()));
    }

    public static ImageDTO dumpViaApi(String url, Map<String, ?> body) {
        return image(Requests.post(url, body, AUTH_HEADER, bearer()), data -> ImageDelivery.resolveApi(data, url));
    }

    static ImageDTO image(BizResponse<JsonNode> response) {
        return image(response, ImageDelivery::resolve);
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
