package top.yzljc.atribot.function.general.impl;

import top.yzljc.atribot.configuration.ResourcesProperties;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.utils.ErrorReport;
import top.yzljc.atribot.utils.HashAuthFailedException;
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

    private static volatile ImageHashCache hashCache;

    private static ImageHashCache getHashCache() {
        if (hashCache == null) {
            synchronized (PreImageGenerate.class) {
                if (hashCache == null) {
                    hashCache = ImageHashCache.load();
                }
            }
        }
        return hashCache;
    }

    private static String extractEndpoint(String url, Map<String, ?> body) {
        String path = url;
        int queryIdx = path.indexOf('?');
        if (queryIdx >= 0) {
            path = path.substring(0, queryIdx);
        }

        int lastSlash = path.lastIndexOf('/');
        String endpoint = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;

        if ("anan-emoji-text".equals(endpoint) && body != null) {
            String mode = (String) body.get("mode");
            if (mode != null && !mode.isBlank()) {
                endpoint = endpoint + ":" + mode;
            }
        }
        return endpoint;
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
            String urlTmp = ResourcesProperties.DUMP + "/" + resp.path("data").path("uuid").asText();
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
            String urlTmp = ResourcesProperties.DUMP + "/" + resp.path("data").path("uuid").asText();
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

        String urlTmp = ResourcesProperties.DUMP + "/" + resp.path("data").path("uuid").asText();
        int width = resp.path("data").path("width").asInt();
        int height = resp.path("data").path("height").asInt();
        String actualHash = resp.path("data").path("hash").asText(null);

        // 哈希校验
        String endpoint = extractEndpoint(url, body);
        ImageHashCache cache = getHashCache();
        if (!cache.validate(endpoint, actualHash)) {
            String traceId = ErrorReport.report("PreImageGenerate",
                    new HashAuthFailedException("背景图哈希校验失败: endpoint=" + endpoint
                            + ", expected=" + cache.getExpectedHash(endpoint)
                            + ", actual=" + actualHash));
            return ImageDTO.hashMismatch(traceId);
        }

        return new ImageDTO(urlTmp, width, height);
    }
}