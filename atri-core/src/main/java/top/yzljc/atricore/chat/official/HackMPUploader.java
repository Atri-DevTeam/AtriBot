package top.yzljc.atricore.chat.official;

import top.yzljc.atricore.AtriProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName HackMPUploader
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official
 * @Description 使用占位图片申请上传地址的备用图片上传器，这并不是一个正规的方案，只能作为一种备用存在
 */
@Slf4j
public final class HackMPUploader {
    private static final byte[] PLACEHOLDER = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA4AAAAvAAAAAAcQEf0PRET/Aw==");
    private static final int MAX_IMAGE_SIZE = 20 * 1024 * 1024;

    private HackMPUploader() {
    }

    /**
     * 使用 qq.debug-group-openId 申请上传地址，仅上传图片，不发送群消息
     *
     * @param image PNG、JPEG、GIF 或 WebP 图片字节，最大 20 MiB，调用结束前不得修改
     * @return 覆盖上传成功后的图片 URL，配置缺失、格式不支持或上传失败时返回 null；有效期由平台决定
     */
    public static String upload(byte[] image) {
        try {
            imageContentType(image);
            String groupId = AtriProvider.getInstance().getSettings().getDebugGroupOpenId();
            if (groupId == null || groupId.isBlank() || "null".equalsIgnoreCase(groupId.trim())) {
                throw new IllegalStateException("未配置 qq.debug-group-openId");
            }
            var service = AtriProvider.getInstance().getChatService();
            var uploader = service.getMediaUploader();
            return upload(image, service.groupFileUrl(groupId.trim()),
                    (url, data) -> uploader.postUpload(url, data, "备用图片", "raw_url"), uploader::putImage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("备用图片上传失败: {}", e.getMessage());
            return null;
        }
    }

    static String upload(byte[] image, String filesUrl, OfficialFileTransfer.JsonRequest request, ImagePut put)
            throws IOException, InterruptedException {
        String contentType = imageContentType(image);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (!filesUrl.endsWith("/files")) throw new IllegalArgumentException("文件上传接口地址无效");
        String baseUrl = filesUrl.substring(0, filesUrl.length() - "/files".length());
        var prepared = request.post(baseUrl + "/upload_prepare", Map.of(
                "file_type", 1, "file_size", PLACEHOLDER.length, "file_name", "image.webp",
                "md5", "a65c84bc93182dcacb41776f2bd764f5", "sha1", "268bf9ab04e18012afee8b16c23f548fd958a158"));
        String uploadId = prepared.path("upload_id").asText("");
        var parts = prepared.path("parts");
        if (uploadId.isBlank() || !parts.isArray() || parts.size() != 1) throw new IOException("占位预上传响应无效");
        URI uploadUrl = httpUri(parts.get(0).path("presigned_url").asText(""));
        requireSuccess(put.upload(uploadUrl, PLACEHOLDER, "备用图片占位"));
        request.post(baseUrl + "/upload_part_finish", Map.of("upload_id", uploadId, "part_index", 1,
                "block_size", PLACEHOLDER.length, "md5", "a65c84bc93182dcacb41776f2bd764f5"));
        var completed = request.post(filesUrl, Map.of("upload_id", uploadId, "srv_send_msg", false));
        String downloadUrl = downloadUrl(completed.path("raw_url").asText(""), contentType);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        requireSuccess(put.upload(uploadUrl, image, "备用图片覆盖"));
        return downloadUrl;
    }

    static String imageContentType(byte[] image) {
        if (image == null || image.length == 0 || image.length > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("图片大小必须在 1 字节至 20 MiB 之间");
        }
        if (startsWith(image, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
        if (startsWith(image, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
        if (startsWith(image, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                || startsWith(image, "GIF89a".getBytes(StandardCharsets.US_ASCII))) return "image/gif";
        if (image.length >= 12 && startsWith(image, "RIFF".getBytes(StandardCharsets.US_ASCII))
                && Arrays.equals(image, 8, 12, "WEBP".getBytes(StandardCharsets.US_ASCII), 0, 4)) return "image/webp";
        throw new IllegalArgumentException("不支持的图片格式，仅支持 PNG、JPEG、GIF、WebP");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    static String downloadUrl(String rawUrl, String contentType) throws IOException {
        URI uri = httpUri(rawUrl);
        List<String> query = new ArrayList<>();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            for (String parameter : uri.getRawQuery().split("&")) {
                String key = parameter.split("=", 2)[0];
                if (!URLDecoder.decode(key, StandardCharsets.UTF_8).equals("response-content-type"))
                    query.add(parameter);
            }
        }
        query.add("response-content-type=" + URLEncoder.encode(contentType, StandardCharsets.UTF_8));
        int end = rawUrl.length();
        int fragment = rawUrl.indexOf('#');
        if (fragment >= 0) end = fragment;
        int question = rawUrl.indexOf('?');
        if (question >= 0 && question < end) end = question;
        return rawUrl.substring(0, end) + "?" + String.join("&", query)
                + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    }

    private static URI httpUri(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() != null && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())))
                return uri;
        } catch (IllegalArgumentException ignored) {
        }
        throw new IOException("上传响应缺少有效的 HTTP 地址");
    }

    private static void requireSuccess(int status) throws IOException {
        if (status < 200 || status >= 300) throw new IOException("图片上传失败，HTTP " + status);
    }

    @FunctionalInterface
    interface ImagePut {
        int upload(URI url, byte[] image, String scene) throws IOException, InterruptedException;
    }
}
