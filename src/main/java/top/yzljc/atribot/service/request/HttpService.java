package top.yzljc.atribot.service.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
public class HttpService {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_LOG_BODY_LENGTH = 4096;
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public static final HttpClient redirectHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.ALWAYS).build();

    private static void logHttpFailure(String method, String url, int statusCode, String responseBody) {
        log.warn("{} Request failed! URL: {}, HTTP code: {}, Response body: {}",
                method, url, statusCode, limitLogBody(responseBody));
    }

    private static void logRequestError(String method, String url, Exception e) {
        log.warn("{} Request Error! URL: {}, Error type: {}, Error message: {}",
                method, url, e.getClass().getName(), e.getMessage(), e);
    }

    private static String limitLogBody(String body) {
        if (body == null) {
            return "<null>";
        }
        if (body.isBlank()) {
            return "<blank>";
        }
        if (body.length() <= MAX_LOG_BODY_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_LOG_BODY_LENGTH) + "...(truncated, length=" + body.length() + ")";
    }

    private static void applyHeaders(Builder builder, String... headers) {
        if (headers.length % 2 != 0) {
            throw new IllegalArgumentException("Headers must be key/value pairs, length: " + headers.length);
        }
        for (int i = 0; i < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
    }

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
                logHttpFailure("GET", url, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logRequestError("GET", url, e);
        }
        return null;
    }

    public static JsonNode sendGetRequest(String url, String... headers) {
        try {
            Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET();
            applyHeaders(builder, headers);
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    return mapper.readTree(body);
                }
                return null;
            } else {
                logHttpFailure("GET", url, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logRequestError("GET", url, e);
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
                return response.body();
            } else {
                logHttpFailure("GET", url, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logRequestError("GET", url, e);
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
                logHttpFailure("POST(redirect)", url, statusCode, responseBody);
            }
        } catch (Exception e) {
            logRequestError("POST(redirect)", url, e);
        }
        return null;
    }

    public static JsonNode postJson(String url, Map<String, Object> bodyMap, String... headers) {
        try {
            String jsonBody = mapper.writeValueAsString(bodyMap);
            return postJson(url, jsonBody, headers);  // 调用原方法
        } catch (Exception e) {
            logRequestError("POST", url, e);
            return null;
        }
    }

    // 重载2：接收 Object
    public static JsonNode postJson(String url, Object bodyObj, String... headers) {
        try {
            String jsonBody = mapper.writeValueAsString(bodyObj);
            return postJson(url, jsonBody, headers);  // 调用原方法
        } catch (Exception e) {
            logRequestError("POST", url, e);
            return null;
        }
    }

    public static JsonNode postJson(String url, String jsonBody, String... headers) {
        try {
            Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (body != null && !body.isBlank()) {
                    return mapper.readTree(body);
                }
                log.warn("POST 返回空 body! URL: {}, status: {}", url, response.statusCode());
                return null;
            }
            logHttpFailure("POST", url, response.statusCode(), body);
            return null;
        } catch (Exception e) {
            logRequestError("POST", url, e);
            return null;
        }
    }

    public record PostResult(int status, String body) {
    }

    public static PostResult postJsonDetailed(String url, String jsonBody, String... headers) {
        try {
            Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logHttpFailure("POST(detailed)", url, response.statusCode(), response.body());
            }
            return new PostResult(response.statusCode(), response.body());
        } catch (Exception e) {
            logRequestError("POST(detailed)", url, e);
            return new PostResult(0, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static String postJsonForString(String url, String jsonBody, String... headers) {
        return postJsonForString(url, jsonBody, null, headers);
    }

    public static String postJsonForString(String url, String jsonBody, Duration duration, String... headers) {
        try {
            Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", DEFAULT_USER_AGENT);
            applyHeaders(builder, headers);
            if (duration != null) {
                builder.timeout(duration);
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            logHttpFailure("POST(string)", url, response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            logRequestError("POST(string)", url, e);
            return null;
        }
    }

    public static JsonNode putJson(String url, Map<String, Object> bodyMap, String... headers) {
        try {
            String jsonBody = mapper.writeValueAsString(bodyMap);
            return putJson(url, jsonBody, headers);
        } catch (Exception e) {
            logRequestError("PUT", url, e);
            return null;
        }
    }

    public static JsonNode putJson(String url, Object bodyObj, String... headers) {
        try {
            String jsonBody = mapper.writeValueAsString(bodyObj);
            return putJson(url, jsonBody, headers);
        } catch (Exception e) {
            logRequestError("PUT", url, e);
            return null;
        }
    }

    public static JsonNode putJson(String url, String jsonBody, String... headers) {
        try {
            Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (body != null && !body.isBlank()) {
                    return mapper.readTree(body);
                }
                return NullNode.getInstance();
            } else {
                logHttpFailure("PUT", url, response.statusCode(), body);
            }
            return null;
        } catch (Exception e) {
            logRequestError("PUT", url, e);
            return null;
        }
    }

    public static JsonNode sendDeleteRequest(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    return mapper.readTree(body);
                }
                return null;
            } else {
                logHttpFailure("DELETE", url, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logRequestError("DELETE", url, e);
        }
        return null;
    }

    public static String deleteRequestStr(String url, String... headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .DELETE();
            applyHeaders(builder, headers);
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                logHttpFailure("DELETE(string)", url, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logRequestError("DELETE(string)", url, e);
        }
        return null;
    }
}
