package top.yzljc.qqbot.service.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import top.yzljc.qqbot.utils.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpRequest {

    private static final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient redirectHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public static JsonNode sendGetRequest(String url) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                Logger.warn("GET Request failed, HTTP code: {}", response.getStatusCode().value());
            }
        } catch (Exception e) {
            Logger.warn("GET Request Error! URL: {}, Error: {}", url, e.getMessage());
        }
        return null;
    }

    public static JsonNode sendPostRequest(String url, Object body) {
        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                Logger.warn("POST Request failed, HTTP code: {}", response.getStatusCode().value());
            }
        } catch (Exception e) {
            Logger.warn("POST Request Error! URL: {}, Error: {}", url, e.getMessage());
        }
        return null;
    }

    public static JsonNode sendPostRequestFollowRedirect(String url) {
        try {
            Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody());

            HttpResponse<String> response = redirectHttpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                if (responseBody == null || responseBody.isBlank()) {
                    return null;
                }
                return mapper.readTree(responseBody);
            } else {
                Logger.warn("POST(redirect) Request failed, HTTP code: {}", statusCode);
            }
        } catch (Exception e) {
            Logger.warn("POST(redirect) Request Error! URL: {}, Error: {}", url, e.getMessage());
        }
        return null;
    }
}