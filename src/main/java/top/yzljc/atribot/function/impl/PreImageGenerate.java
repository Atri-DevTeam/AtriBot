package top.yzljc.atribot.function.impl;

import top.yzljc.atribot.configuration.ResourcesProperties;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ImageDelivery;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.utils.ErrorReport;
import top.yzljc.atribot.utils.ServerNoResponseException;

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

    private static final String AUTH_HEADER = "Authorization";

    private static String bearer() {
        return "Bearer " + Config.getInstance().getAtribotKeySecret();
    }

    public static int create(String url) {
        HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        try {
            HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (Exception e) {
            return 500;
        }
    }

    public static ImageDTO dump(String url) {
        JsonNode resp = HttpService.postJson(
                ResourcesProperties.DUMP,
                Map.of("url", url), AUTH_HEADER, bearer());

        if (resp != null && resp.path("status").asInt() == 200) {
            String urlTmp = ImageDelivery.resolve(resp.path("data"));
            int width = resp.path("data").path("width").asInt();
            int height = resp.path("data").path("height").asInt();
            return new ImageDTO(urlTmp, width, height);
        }
        return null;
    }

    public static ImageDTO dump(Map<String, ?> body) {
        JsonNode resp = HttpService.postJson(
                ResourcesProperties.DUMP, body, AUTH_HEADER, bearer());

        if (resp != null && resp.path("status").asInt() == 200) {
            String urlTmp = ImageDelivery.resolve(resp.path("data"));
            int width = resp.path("data").path("width").asInt();
            int height = resp.path("data").path("height").asInt();
            return new ImageDTO(urlTmp, width, height);
        }
        return null;
    }

    public static ImageDTO dump(String url, Map<String, ?> body) {
        JsonNode resp = HttpService.postJson(url, body, AUTH_HEADER, bearer());

        if (resp == null || resp.path("status").asInt() != 200) {
            var err = ErrorReport.report(PreImageGenerate.class.getName(), new ServerNoResponseException());
            return new ImageDTO(null, 0, 0, "访问远程数据失败，如持续发生请向开发者报告此问题，traceId: " + err, err);
        }

        String urlTmp = ImageDelivery.resolve(resp.path("data"));
        int width = resp.path("data").path("width").asInt();
        int height = resp.path("data").path("height").asInt();

        return new ImageDTO(urlTmp, width, height);
    }
}