package top.yzljc.atricore.service.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import top.yzljc.atricore.utils.debug.MessageTrace;

/**
 * 共享 HTTP 客户端与请求辅助方法
 *
 * @Author YZ_Ljc_
 * @ClassName HttpService
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.service.request
 */
@Slf4j
public class HttpService {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_LOG_BODY_LENGTH = 4096;
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).build();

    public static final HttpClient redirectHttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).followRedirects(HttpClient.Redirect.ALWAYS).build();

    public static Builder newRequestBuilder() {
        return HttpRequest.newBuilder().timeout(DEFAULT_REQUEST_TIMEOUT);
    }

    private static HttpResponse<String> sendTraced(HttpRequest request) throws java.io.IOException, InterruptedException {
        if (!MessageTrace.isActive()) return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String path = request.uri().getPath();
        String name;
        if (path.matches("/v2/(groups|users)/[^/]+/messages/[^/]+") && "DELETE".equals(request.method()))
            name = "QQ 撤回消息";
        else if (path.matches("/v2/(groups|users)/[^/]+/messages")) name = "QQ 发送消息";
        else if (path.endsWith("/files")) name = "QQ 上传文件";
        else if (path.endsWith("/upload_prepare")) name = "QQ 准备分片上传";
        else if (path.endsWith("/upload_part_finish")) name = "QQ 确认上传分片";
        else name = "HTTP 请求";
        try (var span = MessageTrace.span(MessageTrace.operationName(name))) {
            try {
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
                String detail = "HTTP " + response.statusCode();
                String traceId = response.headers().firstValue("X-Tps-Trace-Id").orElse(null);
                try {
                    JsonNode body = response.body() == null ? null : mapper.readTree(response.body());
                    if (body != null && body.isObject()) {
                        JsonNode code = body.hasNonNull("err_code") ? body.get("err_code") : body.get("code");
                        if (name.startsWith("QQ ") && code != null && code.isIntegralNumber() && code.longValue() != 0) {
                            success = false;
                            detail += " · 业务码 " + code.longValue();
                        }
                        if (traceId == null) traceId = body.path("trace_id").asText(null);
                    }
                } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                    // 非 JSON 响应仅记录 HTTP 状态
                }
                if (traceId != null && traceId.matches("[A-Za-z0-9_-]{1,128}")) detail += " · trace_id=" + traceId;
                span.result(success, detail);
                return response;
            } catch (java.io.IOException | InterruptedException | RuntimeException failure) {
                span.result(false, failure.getClass().getSimpleName());
                throw failure;
            }
        }
    }

    private static void logHttpFailure(String method, String url, int statusCode, String responseBody) {
        log.warn("{} Request failed! URL: {}, HTTP code: {}, Response body: {}",
                method, url, statusCode, limitLogBody(responseBody));
    }

    private static void logRequestError(String method, String url, Exception e) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
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
            HttpRequest request = newRequestBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = sendTraced(request);
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

    /**
     * @param url     完整请求地址
     * @param headers 交替排列的请求头名称和值
     * @return 成功响应的 JSON，非 2xx 或解析失败时返回 null；需要错误响应时使用 {@link #sendGetRequestDetailed}
     */
    public static JsonNode sendGetRequest(String url, String... headers) {
        try {
            Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .GET();
            applyHeaders(builder, headers);
            HttpRequest request = builder.build();
            HttpResponse<String> response = sendTraced(request);
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
            java.net.http.HttpRequest request = newRequestBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> response = sendTraced(request);
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
            Builder requestBuilder = newRequestBuilder()
                    .uri(URI.create(url))
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

    /**
     * @param url      完整请求地址
     * @param jsonBody 已序列化的 JSON 请求体
     * @param headers  交替排列的请求头名称和值
     * @return 成功响应的 JSON，非 2xx 或解析失败时返回 null；需要错误响应时使用 {@link #postJsonDetailed(String, String, String...)}
     */
    public static JsonNode postJson(String url, String jsonBody, String... headers) {
        try {
            Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = sendTraced(builder.build());
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

    public record GetResult(int status, String body) {
    }

    /**
     * @param url     完整请求地址
     * @param bodyObj 待序列化的请求数据
     * @param headers 交替排列的请求头名称和值
     * @return 原始 HTTP 状态码与响应体，包含非 2xx 响应；请求未完成时状态码为 0
     */
    public static PostResult postJsonDetailed(String url, Object bodyObj, String... headers) {
        try {
            String jsonBody = mapper.writeValueAsString(bodyObj);
            return postJsonDetailed(url, jsonBody, headers);
        } catch (Exception e) {
            logRequestError("POST(detailed)", url, e);
            return new PostResult(0, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static PostResult postJsonDetailed(String url, String jsonBody, String... headers) {
        try {
            Builder builder = newRequestBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> response = sendTraced(builder.build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logHttpFailure("POST(detailed)", url, response.statusCode(), response.body());
            }
            return new PostResult(response.statusCode(), response.body());
        } catch (Exception e) {
            logRequestError("POST(detailed)", url, e);
            return new PostResult(0, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * PATCH 请求，返回状态码与响应体（用于需要判断成功与否的接口）
     */
    public static PostResult patchJsonDetailed(String url, String jsonBody, String... headers) {
        try {
            Builder builder = newRequestBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> response = sendTraced(builder.build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logHttpFailure("PATCH(detailed)", url, response.statusCode(), response.body());
            }
            return new PostResult(response.statusCode(), response.body());
        } catch (Exception e) {
            logRequestError("PATCH(detailed)", url, e);
            return new PostResult(0, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * PUT 请求，返回状态码与响应体（用于需要判断成功与否的接口）
     */
    public static PostResult putJsonDetailed(String url, String jsonBody, String... headers) {
        try {
            Builder builder = newRequestBuilder().uri(URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.method("PUT", HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> response = sendTraced(builder.build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logHttpFailure("PUT(detailed)", url, response.statusCode(), response.body());
            }
            return new PostResult(response.statusCode(), response.body());
        } catch (Exception e) {
            logRequestError("PUT(detailed)", url, e);
            return new PostResult(0, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static GetResult sendGetRequestDetailed(String url, String... headers) {
        try {
            Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .GET();
            applyHeaders(builder, headers);
            HttpRequest request = builder.build();
            HttpResponse<String> response = sendTraced(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logHttpFailure("GET(detailed)", url, response.statusCode(), response.body());
            }
            return new GetResult(response.statusCode(), response.body());
        } catch (Exception e) {
            logRequestError("GET(detailed)", url, e);
            return new GetResult(0, null);
        }
    }

    public static String postJsonForString(String url, String jsonBody, String... headers) {
        return postJsonForString(url, jsonBody, null, headers);
    }

    public static String postJsonForString(String url, String jsonBody, Duration duration, String... headers) {
        try {
            Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .timeout(duration != null ? duration : DEFAULT_REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", DEFAULT_USER_AGENT);
            applyHeaders(builder, headers);
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = sendTraced(builder.build());
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

    public record MultipartFile(String fieldName, String filename, String contentType, byte[] content) {
    }

    public static String postMultipartForString(String url, Map<String, String> fields, List<MultipartFile> files, String... headers) {
        try {
            String boundary = "----AtriMeowBoundary" + UUID.randomUUID();
            List<byte[]> bodyParts = new ArrayList<>();

            if (fields != null) {
                for (Map.Entry<String, String> entry : fields.entrySet()) {
                    bodyParts.add(("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"" + entry.getKey() + "\"\r\n\r\n"
                            + (entry.getValue() == null ? "" : entry.getValue()) + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
            }

            if (files != null) {
                for (MultipartFile file : files) {
                    String contentType = file.contentType() == null || file.contentType().isBlank()
                            ? "application/octet-stream"
                            : file.contentType();
                    bodyParts.add(("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"" + file.fieldName() + "\"; filename=\"" + file.filename() + "\"\r\n"
                            + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    bodyParts.add(file.content() == null ? new byte[0] : file.content());
                    bodyParts.add("\r\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            bodyParts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", DEFAULT_USER_AGENT);
            applyHeaders(builder, headers);
            builder.POST(HttpRequest.BodyPublishers.ofByteArrays(bodyParts));

            HttpResponse<String> response = sendTraced(builder.build());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            logHttpFailure("POST(multipart)", url, response.statusCode(), response.body());
            return null;
        } catch (Exception e) {
            logRequestError("POST(multipart)", url, e);
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
            Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");
            applyHeaders(builder, headers);
            builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody));

            HttpResponse<String> response = sendTraced(builder.build());
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
            HttpRequest request = newRequestBuilder()
                    .uri(URI.create(url))
                    .DELETE()
                    .build();
            HttpResponse<String> response = sendTraced(request);
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

    public static JsonNode sendDeleteRequest(String url, String... headers) {
        try {
            HttpRequest.Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .DELETE();
            applyHeaders(builder, headers);
            HttpRequest request = builder.build();
            HttpResponse<String> response = sendTraced(request);
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

    /**
     * DELETE 请求，返回状态码与响应体（用于需要判断成功与否的接口）
     */
    public static GetResult deleteRequestDetailed(String url, String... headers) {
        try {
            HttpRequest.Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .DELETE();
            applyHeaders(builder, headers);
            HttpRequest request = builder.build();
            HttpResponse<String> response = sendTraced(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logHttpFailure("DELETE(detailed)", url, response.statusCode(), response.body());
            }
            return new GetResult(response.statusCode(), response.body());
        } catch (Exception e) {
            logRequestError("DELETE(detailed)", url, e);
            return new GetResult(0, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public static String deleteRequestStr(String url, String... headers) {
        try {
            HttpRequest.Builder builder = newRequestBuilder()
                    .uri(URI.create(url))
                    .DELETE();
            applyHeaders(builder, headers);
            HttpRequest request = builder.build();
            HttpResponse<String> response = sendTraced(request);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                logHttpFailure("DELETE(string)", url, response.statusCode(), response.body());
                return "error";
            }
        } catch (Exception e) {
            logRequestError("DELETE(string)", url, e);
        }
        return "error";
    }
}
