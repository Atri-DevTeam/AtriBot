package top.yzljc.atribot.function.general.impl;

import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @Author YZ_Ljc_
 * @ClassName PreImageGenerate
 * @Created_at 2026/06/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general.impl
 */
public class PreImageGenerate {

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
}