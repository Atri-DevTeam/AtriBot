package top.yzljc.atribot.function.general.impl;

import top.yzljc.atribot.configuration.ResourcesProperties;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName PreImageGenerate
 * @Created_at 2026/06/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general.impl
 */
public class PreImageGenerate {

    private static final String DUMP_URL = ResourcesProperties.IMAGE_DUMP_API;

    public static int create(String url) {
        HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        // 第一次生成访问，只后用缓存拿数据
        try {
            HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            return 500;
        }
    }

    public static ImageDTO dump(String url) {
        ImageDTO finalUrl = null;
        JsonNode resp = HttpService.postJson(DUMP_URL + "?key=" + Config.getInstance().getAtribotKeySecret(), Map.of("url", url));

        if (resp != null && resp.path("status").asInt() == 200) {
            String urlTmp = DUMP_URL + "/" + resp.path("data").path("uuid").asText();
            int width = resp.path("data").path("width").asInt();
            int height = resp.path("data").path("height").asInt();
            finalUrl = new ImageDTO(urlTmp, width, height);
        }

        return finalUrl;
    }
}