package top.yzljc.atricore.chat.official.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atricore.AtriProvider;
import top.yzljc.atricore.core.LogRecorder;
import top.yzljc.atricore.service.request.HttpService;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * QQ Markdown 图片素材的上传、查询与删除
 *
 * @Author YZ_Ljc_
 * @ClassName MaterialImages
 * @Created_at 2026/10/22
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.media
 */
public final class MaterialImages {
    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    public static final int MAX_IMAGES = 100;
    private static final ObjectMapper JSON = new ObjectMapper();

    private MaterialImages() {}

    /**
     * @param uploadId 上传任务 ID，有效期为 4 小时
     * @param presignedUrl 图片二进制的 PUT 上传地址
     * @param expiresIn 预签名上传地址的有效秒数
     */
    public record UploadTask(String uploadId, String presignedUrl, long expiresIn) {}

    /**
     * @param imageId 素材 ID，用于删除
     * @param url Markdown 使用的 qqbot-media 引用
     * @param ttl 自最后一次成功引用起计算的有效秒数
     * @param previewUrl 临时预览地址，不应作为长期引用保存
     */
    public record UploadedImage(String imageId, String url, long ttl, String previewUrl) {}

    /**
     * @param imageId 素材 ID
     * @param url Markdown 使用的 qqbot-media 引用
     * @param fileName 上传时的文件名
     * @param previewUrl 临时预览地址
     * @param createdAt 上传时间，Unix 秒
     * @param expiresAt 当前过期时间，Unix 秒；成功引用后由平台顺延
     */
    public record Image(String imageId, String url, String fileName, String previewUrl, long createdAt, long expiresAt) {}

    /**
     * @param images 当前页的有效素材
     * @param nextCursor 下一页游标，空字符串表示末页
     * @param total 当前机器人的有效素材总数
     */
    public record ImagePage(List<Image> images, String nextCursor, long total) {
        public ImagePage { images = List.copyOf(images); }
    }

    /**
     * 上传本地图片，不发送消息
     *
     * @param file PNG、JPEG、GIF 或 WebP 图片，最大 10 MiB
     * @return 已完成上传的素材及 Markdown 引用
     * @throws IOException 文件读取、平台请求或响应校验失败
     * @throws InterruptedException 上传被中断
     */
    public static UploadedImage upload(Path file) throws IOException, InterruptedException {
        long size = Files.size(file);
        if (size < 1 || size > MAX_IMAGE_BYTES) throw new IllegalArgumentException("图片大小必须在 1 字节至 10 MiB 之间");
        try (var input = Files.newInputStream(file)) {
            return upload(input.readNBytes(MAX_IMAGE_BYTES + 1), file.getFileName().toString());
        }
    }

    /**
     * 根据图片内容识别格式，计算摘要并完成申请、二进制上传和确认
     *
     * @param image 图片字节，调用结束前不得修改
     * @param fileName 文件名，扩展名必须与图片格式一致
     * @return 已完成上传的素材及 Markdown 引用
     * @throws IOException 平台请求或响应校验失败
     * @throws InterruptedException 上传被中断
     */
    public static UploadedImage upload(byte[] image, String fileName) throws IOException, InterruptedException {
        contentType(image, fileName);
        return client().upload(image, fileName);
    }

    /**
     * @param fileName 含扩展名的文件名
     * @param fileSize 图片大小，1 字节至 10 MiB
     * @param contentType 与扩展名一致的图片 MIME 类型
     * @param md5 文件 MD5，32 位十六进制
     * @param sha1 文件 SHA1，40 位十六进制
     * @return 上传任务及预签名 PUT 地址
     * @throws IOException 平台请求或响应校验失败
     * @throws InterruptedException 请求被中断
     */
    public static UploadTask prepare(String fileName, long fileSize, String contentType, String md5, String sha1)
            throws IOException, InterruptedException {
        return client().prepare(fileName, fileSize, contentType, md5, sha1);
    }

    /**
     * @param uploadId 申请上传时返回的任务 ID
     * @param md5 PUT 响应 ETag 去掉双引号后的 MD5
     * @return 素材 ID、Markdown 引用及临时预览地址
     * @throws IOException 平台请求或响应校验失败
     * @throws InterruptedException 请求被中断
     */
    public static UploadedImage complete(String uploadId, String md5) throws IOException, InterruptedException {
        return client().complete(uploadId, md5);
    }

    /**
     * @param cursor 上一页返回的游标，首页传入 null 或空字符串
     * @return 按上传时间倒序排列的当前页素材
     * @throws IOException 平台请求或响应校验失败
     * @throws InterruptedException 请求被中断
     */
    public static ImagePage list(String cursor) throws IOException, InterruptedException {
        return client().list(cursor);
    }

    /**
     * 删除素材并释放配额，已发送消息中的图片不受影响
     *
     * @param imageId 素材 ID
     * @return 平台确认删除时返回 true
     * @throws IOException 平台请求失败，包含素材不存在的情况
     * @throws InterruptedException 请求被中断
     */
    public static boolean delete(String imageId) throws IOException, InterruptedException {
        return client().delete(imageId);
    }

    private static Client client() {
        var provider = AtriProvider.getInstance();
        var chat = provider.getChatService();
        if (chat == null) throw new IllegalStateException("QQ 官方机器人服务未启用");
        return new Client(provider.getSettings().getQqApiBaseUrl(), chat.getTokenManager().getAccessToken(), provider.getLogRecorder());
    }

    /**
     * 保留平台 HTTP 状态和业务错误码的请求异常
     */
    public static final class ApiException extends IOException {
        private final int httpStatus;
        private final int code;

        ApiException(int httpStatus, int code, String message) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
        }

        /** @return 平台响应的 HTTP 状态码 */
        public int httpStatus() { return httpStatus; }

        /** @return 平台业务错误码，未提供时为 0 */
        public int code() { return code; }
    }

    static final class Client {
        private final String baseUrl;
        private final String accessToken;
        private final LogRecorder logs;

        Client(String baseUrl, String accessToken, LogRecorder logs) {
            this.baseUrl = baseUrl.replaceFirst("/+$", "") + "/v2/media-images";
            this.accessToken = accessToken;
            this.logs = logs;
        }

        UploadedImage upload(byte[] image, String fileName) throws IOException, InterruptedException {
            String type = contentType(image, fileName);
            String md5 = digest("MD5", image);
            UploadTask task = prepare(fileName, image.length, type, md5, digest("SHA-1", image));
            URI uri = URI.create(task.presignedUrl());
            if (uri.getHost() == null || !List.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null)
                throw new IOException("平台未返回有效的上传地址");
            var response = HttpService.httpClient.send(HttpService.newRequestBuilder().uri(uri)
                    .header("Content-Type", type).PUT(HttpRequest.BodyPublishers.ofByteArray(image)).build(),
                    HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("图片二进制上传失败，HTTP " + response.statusCode());
            String etag = response.headers().firstValue("ETag").orElse("").replace("\"", "").trim();
            if (!md5.equalsIgnoreCase(etag)) throw new IOException("上传响应 ETag 与图片 MD5 不一致");
            return complete(task.uploadId(), etag);
        }

        UploadTask prepare(String fileName, long fileSize, String type, String md5, String sha1)
                throws IOException, InterruptedException {
            if (fileSize < 1 || fileSize > MAX_IMAGE_BYTES) throw new IllegalArgumentException("图片不能超过 10 MiB");
            if (!filenameType(fileName).equals(type)) throw new IllegalArgumentException("文件扩展名与 Content-Type 不一致");
            requireDigest(md5, 32);
            requireDigest(sha1, 40);
            var data = request("POST", "/prepare", Map.of("file_name", fileName, "file_size", fileSize,
                    "content_type", type, "md5", md5, "sha1", sha1));
            return new UploadTask(required(data, "upload_id"), required(data, "presigned_url"), data.path("expires_in").asLong());
        }

        UploadedImage complete(String uploadId, String md5) throws IOException, InterruptedException {
            if (uploadId == null || uploadId.isBlank()) throw new IllegalArgumentException("upload_id 不能为空");
            requireDigest(md5, 32);
            var data = request("POST", "/complete", Map.of("upload_id", uploadId, "md5", md5));
            String url = required(data, "url");
            if (!url.startsWith("qqbot-media://image/v1/")) throw new IOException("平台返回的素材引用无效");
            return new UploadedImage(required(data, "image_id"), url, data.path("ttl").asLong(), data.path("preview_url").asText(""));
        }

        ImagePage list(String cursor) throws IOException, InterruptedException {
            if (cursor != null && cursor.length() > 4096) throw new IllegalArgumentException("分页游标过长");
            String query = cursor == null || cursor.isBlank() ? "" : "?cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
            var data = request("GET", query, null);
            if (!data.path("images").isArray()) throw new IOException("素材列表响应缺少 images 字段");
            List<Image> images = new ArrayList<>();
            for (var item : data.path("images")) images.add(new Image(required(item, "image_id"), required(item, "url"),
                    item.path("file_name").asText(""), item.path("preview_url").asText(""),
                    item.path("created_at").asLong(), item.path("expires_at").asLong()));
            return new ImagePage(images, data.path("next_cursor").asText(""), data.path("total").asLong());
        }

        boolean delete(String imageId) throws IOException, InterruptedException {
            if (imageId == null || !imageId.matches("[A-Za-z0-9_-]{1,256}")) throw new IllegalArgumentException("素材 ID 无效");
            request("DELETE", "/" + imageId + "/delete", null);
            return true;
        }

        private JsonNode request(String method, String suffix, Map<String, Object> body) throws IOException, InterruptedException {
            String url = baseUrl + suffix;
            String content = body == null ? null : JSON.writeValueAsString(body);
            String scene = "图片素材上传";
            String traceId = logs.recordSend(scene, method, url, content);
            int status = 0;
            String responseBody = null;
            try {
                var request = HttpService.newRequestBuilder().uri(URI.create(url)).header("Authorization", "QQBot " + accessToken)
                        .header("Content-Type", "application/json").method(method, content == null
                                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(content));
                var response = HttpService.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                status = response.statusCode();
                responseBody = response.body();
                JsonNode data = response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
                if (data == null || !data.isObject()) throw new IOException("QQ 素材接口响应无效");
                if (response.statusCode() < 200 || response.statusCode() >= 300 || data.path("code").asInt(0) != 0) {
                    int code = data.path("code").asInt(0);
                    throw new ApiException(response.statusCode(), code, data.path("message").asText("QQ 素材接口请求失败"));
                }
                logs.recordResponse(traceId, scene, method, url, content, response.statusCode(), response.body());
                return data;
            } catch (IOException | InterruptedException failure) {
                logs.recordError(traceId, scene, method, url, content,
                        status, responseBody, failure.getMessage());
                throw failure;
            }
        }
    }

    static String contentType(byte[] image, String fileName) {
        if (image == null || image.length < 1 || image.length > MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("图片大小必须在 1 字节至 10 MiB 之间");
        String type;
        if (startsWith(image, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) type = "image/png";
        else if (startsWith(image, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) type = "image/jpeg";
        else if (startsWith(image, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(image, "GIF89a".getBytes(StandardCharsets.US_ASCII))) type = "image/gif";
        else if (image.length >= 12 && startsWith(image, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && new String(image, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) type = "image/webp";
        else throw new IllegalArgumentException("仅支持 PNG、JPEG、GIF、WebP 图片");
        if (!type.equals(filenameType(fileName))) throw new IllegalArgumentException("文件扩展名与图片格式不一致");
        return type;
    }

    private static String filenameType(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.length() > 255 || fileName.contains("/") || fileName.contains("\\")
                || fileName.chars().anyMatch(c -> c < 32)) throw new IllegalArgumentException("图片文件名无效");
        return switch (fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> throw new IllegalArgumentException("文件扩展名必须为 png、jpg、jpeg、gif 或 webp");
        };
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && java.util.Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static String digest(String algorithm, byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data)); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(algorithm + " 不可用", failure); }
    }

    private static void requireDigest(String value, int length) {
        if (value == null || !value.matches("[a-fA-F0-9]{" + length + "}")) throw new IllegalArgumentException("文件摘要无效");
    }

    private static String required(JsonNode data, String key) throws IOException {
        if (!data.path(key).isTextual() || data.path(key).asText().isBlank()) throw new IOException("素材响应缺少 " + key);
        return data.path(key).asText();
    }
}
