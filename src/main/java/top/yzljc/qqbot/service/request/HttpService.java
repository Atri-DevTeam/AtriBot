package top.yzljc.qqbot.service.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
public class HttpService {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public static final HttpClient redirectHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.ALWAYS).build();

    public static JsonNode sendGetRequest(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    return mapper.readTree(body);
                }
                return null;
            } else {
                log.warn("GET Request failed, HTTP code: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("GET Request Error! URL: {}, Error: {}", url, e.getMessage());
        }
        return null;
    }

    public static String getRequestStr(String url) {
        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String result = response.body();
                log.info("Request success, response: {}", result);
                return result;
            } else {
                log.warn("GET Request failed, HTTP code: {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("GET Request Error! URL: {}, Error: {}", url, e.getMessage());
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

            HttpResponse<String> response = redirectHttpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                if (responseBody == null || responseBody.isBlank()) {
                    return null;
                }
                return mapper.readTree(responseBody);
            } else {
                log.warn("POST(redirect) Request failed, HTTP code: {}", statusCode);
            }
        } catch (Exception e) {
            log.warn("POST(redirect) Request Error! URL: {}, Error: {}", url, e.getMessage());
        }
        return null;
    }

    public static JsonNode postJson(String url, String jsonBody, String... headers) {
        try {
            Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");
            for (int i = 0; i < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
            builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    return mapper.readTree(body);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("POST Request Error! URL: {}, Error: {}", url, e.getMessage());
            return null;
        }
    }

    public static String postJsonForString(String url, String jsonBody, String... headers) {
        try {
            Builder builder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");
            for (int i = 0; i < headers.length; i += 2) {
                builder.header(headers[i], headers[i + 1]);
            }
            builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            log.warn("POST Request non-2xx, HTTP code: {}", response.statusCode());
            log.debug("POST Request response body: {}", response.body());
            return null;
        } catch (Exception e) {
            log.warn("POST Request Error! URL: {}, Error: {}", url, e.getMessage());
            return null;
        }
    }
}