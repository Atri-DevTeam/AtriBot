package top.yzljc.atribot.service.request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Author YZ_Ljc_
 * @ClassName Requests
 * @Created_at 2026/10/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.request
 */
public final class Requests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private Requests() {}

    /**
     * @param url 完整请求地址
     * @param headers 交替排列的请求头名称和值
     * @return 保留非 2xx 响应消息的业务结果，未收到响应时 httpCode 为 0，缺少有效业务码时 bizCode 为 -1
     */
    public static BizResponse<JsonNode> get(String url, String... headers) {
        return send(CLIENT, "GET", url, HttpRequest.BodyPublishers.noBody(), null, TIMEOUT, headers);
    }

    static BizResponse<JsonNode> get(HttpClient client, String url, Duration timeout) {
        return send(client, "GET", url, HttpRequest.BodyPublishers.noBody(), null, timeout);
    }

    public static BizResponse<JsonNode> post(String url, Object body, String... headers) {
        return json("POST", url, body, headers);
    }

    public static BizResponse<JsonNode> put(String url, Object body, String... headers) {
        return json("PUT", url, body, headers);
    }

    public static BizResponse<JsonNode> delete(String url, String... headers) {
        return send(CLIENT, "DELETE", url, HttpRequest.BodyPublishers.noBody(), null, TIMEOUT, headers);
    }

    /**
     * @param url 图片 API 的完整地址
     * @param timeout 请求时限
     * @param headers 交替排列的请求头名称和值
     * @return 成功时 data 为原始字节，失败时保留业务错误，不跟随重定向
     */
    public static BizResponse<byte[]> getBytes(String url, Duration timeout, String... headers) {
        if (headers.length % 2 != 0) throw new IllegalArgumentException("请求头必须成对提供");
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Accept", "image/png").GET();
            for (int i = 0; i < headers.length; i += 2) builder.header(headers[i], headers[i + 1]);
            var response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            boolean json = response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT).contains("json");
            if (status >= 200 && status < 300 && !json) {
                return new BizResponse<>(status, 0, "请求成功", null, null, response.body());
            }
            var error = decode(status, new String(response.body(), StandardCharsets.UTF_8));
            return new BizResponse<>(status, error.isSuccess() ? -1 : error.bizCode(),
                    error.isSuccess() ? "图片响应格式无效" : error.message(), error.requestId(), error.timestamp(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BizResponse<>(0, -1, "请求已中断", null, null, null);
        } catch (Exception e) {
            return new BizResponse<>(0, -1, "无法连接服务，请稍后重试", null, null, null);
        }
    }

    private static BizResponse<JsonNode> json(String method, String url, Object body, String... headers) {
        try {
            return send(CLIENT, method, url, HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(body)),
                    "application/json", TIMEOUT, headers);
        } catch (Exception e) {
            return new BizResponse<>(0, -1, "请求数据格式无效", null, null, null);
        }
    }

    public record Upload(String fieldName, String filename, String contentType, byte[] content) {}

    /**
     * @param url 完整上传地址
     * @param fields 表单字段
     * @param files 上传文件，调用完成前不得修改文件字节
     * @param headers 交替排列的请求头名称和值
     * @return 保留上传失败消息的业务结果，不重试上传请求
     */
    public static BizResponse<JsonNode> multipart(String url, Map<String, String> fields, List<Upload> files, String... headers) {
        String boundary = "AtriMeow-" + UUID.randomUUID();
        List<byte[]> parts = new ArrayList<>();
        for (var field : fields.entrySet()) {
            parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + quoted(field.getKey())
                    + "\"\r\n\r\n" + (field.getValue() == null ? "" : field.getValue()) + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        for (Upload file : files) {
            String type = file.contentType() == null || file.contentType().isBlank() ? "application/octet-stream" : file.contentType();
            if (type.contains("\r") || type.contains("\n")) throw new IllegalArgumentException("文件内容类型无效");
            parts.add(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + quoted(file.fieldName())
                    + "\"; filename=\"" + quoted(file.filename()) + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            parts.add(file.content());
            parts.add("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        parts.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return send(CLIENT, "POST", url, HttpRequest.BodyPublishers.ofByteArrays(parts),
                "multipart/form-data; boundary=" + boundary, TIMEOUT, headers);
    }

    private static String quoted(String value) {
        return value == null ? "" : value.replace("\r", "").replace("\n", "").replace("\\", "_").replace("\"", "_");
    }

    private static BizResponse<JsonNode> send(HttpClient client, String method, String url,
            HttpRequest.BodyPublisher body, String contentType, Duration timeout, String... headers) {
        if (headers.length % 2 != 0) throw new IllegalArgumentException("请求头必须成对提供");
        try {
            var builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                    .header("Accept", "application/json").method(method, body);
            if (contentType != null) builder.header("Content-Type", contentType);
            for (int i = 0; i < headers.length; i += 2) builder.header(headers[i], headers[i + 1]);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return decode(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new BizResponse<>(0, -1, "请求已中断", null, null, null);
        } catch (Exception e) {
            return new BizResponse<>(0, -1, "无法连接服务，请稍后重试", null, null, null);
        }
    }

    static BizResponse<JsonNode> decode(int httpCode, String body) {
        if (httpCode == 0) {
            return new BizResponse<>(0, -1, "无法连接服务，请稍后重试", null, null, null);
        }
        JsonNode root;
        try {
            root = body == null || body.isBlank() ? null : MAPPER.readTree(body);
        } catch (Exception e) {
            root = null;
        }
        if (root == null || !root.isObject()) {
            return new BizResponse<>(httpCode, -1, "服务响应格式异常，请稍后重试", null, null, null);
        }
        JsonNode code = root.path("code");
        long bizCode = code.isIntegralNumber() && code.canConvertToLong() ? code.longValue() : -1;
        String message = text(root, "message");
        if (message == null) {
            message = httpCode >= 200 && httpCode < 300 && bizCode == 0 ? "请求成功" : "请求失败，请稍后重试";
        }
        return new BizResponse<>(httpCode, bizCode, message, text(root, "request_id"),
                root.path("timestamp").asText(null), root.get("data"));
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }
}
