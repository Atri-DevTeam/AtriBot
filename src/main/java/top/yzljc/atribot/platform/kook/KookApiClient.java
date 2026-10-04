package top.yzljc.atribot.platform.kook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.platform.Platform;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName KookApiClient
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.kook
 */
@Slf4j
public final class KookApiClient {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final HttpClient MEDIA_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final long MAX_REMOTE_ASSET_BYTES = 32L * 1024 * 1024;
    private final String baseUrl;
    private final String authorization;
    private final Duration timeout;

    public KookApiClient(String baseUrl, String botToken, Duration timeout) {
        URI uri = URI.create(baseUrl);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("KOOK API 地址无效");
        }
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalArgumentException("KOOK Bot Token 不能为空");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("KOOK 请求超时必须大于零");
        }
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.authorization = "Bot " + botToken.trim();
        this.timeout = timeout;
    }

    /**
     * 调用 KOOK GET 接口，仅返回业务状态成功时的数据部分。
     *
     * @param path 相对于 API 根地址的接口路径，例如 {@code /user/me}
     * @param query 查询参数
     * @return 响应中的 {@code data}，请求失败返回 {@code null}
     */
    public JsonNode get(String path, Map<String, String> query) {
        String parameters = query.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        return execute(request(path, parameters).GET().build());
    }

    /**
     * 调用 KOOK POST 接口，不自动重试可能产生副作用的请求。
     *
     * @param path 相对于 API 根地址的接口路径
     * @param body JSON 请求体
     * @return 响应中的 {@code data}，请求失败返回 {@code null}
     */
    public JsonNode post(String path, JsonNode body) {
        return execute(request(path, "").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build());
    }

    public String sendMessage(Platform platform, String targetId, int type, String content, String quote) {
        if (targetId == null || targetId.isBlank() || content == null || content.isBlank()) return null;
        ObjectNode body = JSON.createObjectNode().put("target_id", targetId).put("type", type).put("content", content);
        if (quote != null && !quote.isBlank()) body.put("quote", quote).put("reply_msg_id", quote);
        JsonNode data = post(messagePath(platform) + "/create", body);
        return data == null ? null : data.path("msg_id").asText(null);
    }

    public boolean updateMessage(Platform platform, String messageId, String content) {
        if (messageId == null || messageId.isBlank() || content == null || content.isBlank()) return false;
        return post(messagePath(platform) + "/update",
                JSON.createObjectNode().put("msg_id", messageId).put("content", content)) != null;
    }

    public boolean recallMessage(Platform platform, String messageId) {
        if (messageId == null || messageId.isBlank()) return false;
        return post(messagePath(platform) + "/delete", JSON.createObjectNode().put("msg_id", messageId)) != null;
    }

    /**
     * 上传媒体资源，供当前机器人发送图片、文件等消息使用。
     *
     * @param file 本地文件路径
     * @return 当前机器人可用的资源 URL，上传失败返回 {@code null}
     */
    public String uploadAsset(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try {
            return uploadAsset(file.getFileName().toString(), HttpRequest.BodyPublishers.ofFile(file));
        } catch (IOException e) {
            log.warn("KOOK 资源上传失败: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 下载远程媒体并上传为当前机器人的资源，下载请求不携带机器人凭据。
     *
     * @param url HTTP 或 HTTPS 资源地址，下载内容最多 32 MiB
     * @return 当前机器人可用的资源 URL，地址无效、下载或上传失败返回 {@code null}
     */
    public String uploadAsset(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null) return null;
            HttpRequest download = HttpRequest.newBuilder(uri).timeout(timeout)
                    .header("User-Agent", "AtriMeow").GET().build();
            HttpResponse<byte[]> response = MEDIA_HTTP.send(download,
                    HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), MAX_REMOTE_ASSET_BYTES));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("KOOK 资源下载失败: status={}", response.statusCode());
                return null;
            }
            return uploadAsset(response.body(), "image");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("KOOK 资源下载失败: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 上传内存中的媒体数据。
     *
     * @param data 媒体文件字节
     * @param filename 上传文件名
     * @return 当前机器人可用的资源 URL，参数无效或上传失败返回 {@code null}
     */
    public String uploadAsset(byte[] data, String filename) {
        if (data == null || data.length == 0 || filename == null || filename.isBlank()) return null;
        return uploadAsset(filename, HttpRequest.BodyPublishers.ofByteArray(data));
    }

    private String uploadAsset(String filename, HttpRequest.BodyPublisher content) {
        String boundary = "AtriKook" + UUID.randomUUID().toString().replace("-", "");
        filename = filename.replaceAll("[\\r\\n\"\\\\]", "_");
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: application/octet-stream\r\n\r\n";
        var body = HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofString(head, StandardCharsets.UTF_8),
                content,
                HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        JsonNode data = execute(request("/asset/create", "")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(body).build());
        return data == null ? null : data.path("url").asText(null);
    }

    private HttpRequest.Builder request(String path, String query) {
        if (path == null || !path.matches("/[a-z0-9_-]+/[a-z0-9_-]+")) {
            throw new IllegalArgumentException("KOOK API 路径无效");
        }
        return HttpRequest.newBuilder(URI.create(baseUrl + path + (query.isEmpty() ? "" : "?" + query)))
                .timeout(timeout).header("Authorization", authorization)
                .header("Accept", "application/json").header("User-Agent", "AtriMeow");
    }

    private JsonNode execute(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("KOOK API 请求失败: path={}, status={}", request.uri().getPath(), response.statusCode());
                return null;
            }
            JsonNode body = JSON.readTree(response.body());
            if (body == null || !body.path("code").isIntegralNumber() || !body.path("code").canConvertToInt()
                    || body.path("code").intValue() != 0
                    || !body.hasNonNull("data")) {
                log.warn("KOOK API 响应失败: path={}, code={}", request.uri().getPath(),
                        body == null ? "missing" : body.path("code"));
                return null;
            }
            return body.get("data");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            log.warn("KOOK API 请求异常: path={}, error={}", request.uri().getPath(), e.getClass().getSimpleName());
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String messagePath(Platform platform) {
        return switch (platform) {
            case KOOK_CHANNEL -> "/message";
            case KOOK_DM -> "/direct-message";
            default -> throw new IllegalArgumentException("不支持的 KOOK 消息场景");
        };
    }
}
