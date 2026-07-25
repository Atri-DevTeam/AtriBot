package top.yzljc.atribot.function.official.imagesource;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageSourceClient
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.imagesource
 */
@Slf4j
public class ImageSourceClient {

    /** 单张投稿允许读取的最大字节数，超过直接判失败，避免撑爆内存 */
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public static String fetchAndHash(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("拉取投稿图片失败: status={}, url={}", response.statusCode(), url);
                return null;
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                log.warn("拉取投稿图片得到空响应: url={}", url);
                return null;
            }
            if (body.length > MAX_BYTES) {
                log.warn("投稿图片超过大小上限: size={}, url={}", body.length, url);
                return null;
            }
            return sha256Hex(body);
        } catch (Exception e) {
            log.warn("拉取投稿图片异常: url={}", url, e);
            return null;
        }
    }

    /**
     * 把投稿上报给远端图床。
     *
     * <p>uuid 由上传端（也就是这里）生成并先行落库，远端只做接收，不回传 uuid。
     * body 约定：{@code {uuid, uploader, time, hash, ...}} —— <b>不带审核状态</b>，
     * 库里的 review_status 只服务于 Bot 端 WebUI 展示与审核。
     *
     * <p>响应体与本项目的 {@code Result} 同构，以 {@code status} 判定：200 成功，400 上传失败。
     *
     * @return 上报结果；远端未启用时视为成功，便于本地独立调试
     */
    public static UploadResult upload(ImageSourceDTO dto) {
        Config config = Config.getInstance();
        String url = config.getImageSourceUploadUrl();
        if (!config.isImageSourceEnabled() || isBlank(url)) {
            log.info("图源远端未启用，跳过上报: id={}", dto.getId());
            return UploadResult.success();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", dto.getImageUuid());
        body.put("uploader", dto.getUploaderId());
        body.put("time", dto.getCreateTime() != null ? dto.getCreateTime().getTime() : System.currentTimeMillis());
        body.put("hash", dto.getHash());
        body.put("uploaderName", dto.getUploaderName());
        body.put("platform", dto.getPlatform());
        body.put("groupId", dto.getGroupId());
        body.put("sourceUrl", dto.getSourceUrl());
        body.put("fileName", dto.getFileName());
        body.put("contentType", dto.getContentType());
        body.put("width", dto.getWidth());
        body.put("height", dto.getHeight());
        body.put("size", dto.getFileSize());

        try {
            JsonNode response = HttpService.postJson(url, body, authHeaders(config));
            if (response == null) {
                log.warn("图源上报无响应: id={}, uuid={}", dto.getId(), dto.getImageUuid());
                return UploadResult.fail("远端无响应");
            }
            int status = response.path("status").asInt(-1);
            if (status == 200) {
                log.info("图源上报成功: id={}, uuid={}", dto.getId(), dto.getImageUuid());
                JsonNode data = response.path("data");
                return UploadResult.success(
                        firstPositiveInt(data, "weight", "width"),
                        firstPositiveInt(data, "height"),
                        firstPositiveLong(data, "filesize", "fileSize", "size")
                );
            }
            String message = response.path("message").asText("");
            log.warn("图源上报被远端拒绝: id={}, uuid={}, status={}, message={}",
                    dto.getId(), dto.getImageUuid(), status, message);
            return UploadResult.fail(isBlank(message) ? "远端拒绝接收图片" : message);
        } catch (Exception e) {
            log.warn("图源上报异常: id={}", dto.getId(), e);
            return UploadResult.fail("远端连接失败");
        }
    }

    public record UploadResult(boolean ok, String message, int width, int height, long fileSize) {
        public static UploadResult success() {
            return success(0, 0, 0L);
        }

        public static UploadResult success(int width, int height, long fileSize) {
            return new UploadResult(true, "", width, height, fileSize);
        }

        public static UploadResult fail(String message) {
            return new UploadResult(false, message, 0, 0, 0L);
        }
    }

    public static RemoteResult delete(ImageSourceDTO dto) {
        Config config = Config.getInstance();
        String url = config.getImageSourceDeleteUrl();
        if (!config.isImageSourceEnabled() || isBlank(url) || isBlank(dto.getImageUuid())) {
            return RemoteResult.success();
        }

        try {
            JsonNode response = HttpService.sendDeleteRequest(joinPath(url, dto.getImageUuid()), authHeaders(config));
            if (response == null) {
                log.warn("远端图源删除无响应: id={}, uuid={}", dto.getId(), dto.getImageUuid());
                return RemoteResult.fail("远端无响应");
            }
            int status = response.path("status").asInt(-1);
            if (status == 200) {
                log.info("远端图源删除成功: id={}, uuid={}", dto.getId(), dto.getImageUuid());
                return RemoteResult.success();
            }
            String message = response.path("message").asText("");
            log.warn("远端图源删除失败: id={}, uuid={}, status={}, message={}",
                    dto.getId(), dto.getImageUuid(), status, message);
            return RemoteResult.fail(isBlank(message) ? "远端拒绝删除图片" : message);
        } catch (Exception e) {
            log.warn("远端图源删除异常: id={}, uuid={}", dto.getId(), dto.getImageUuid(), e);
            return RemoteResult.fail("远端连接失败");
        }
    }

    public record RemoteResult(boolean ok, String message) {
        public static RemoteResult success() {
            return new RemoteResult(true, "");
        }

        public static RemoteResult fail(String message) {
            return new RemoteResult(false, message);
        }
    }

    public static ImageDAO randomImage() {
        ImageSourceDTO dto = ImageSourceRepository.findRandomReviewed();
        if (dto == null) {
            return null;
        }
        int width = dto.getProcessedWidth() > 0 ? dto.getProcessedWidth() : dto.getWidth();
        int height = dto.getProcessedHeight() > 0 ? dto.getProcessedHeight() : dto.getHeight();
        return new ImageDAO(viewUrl(dto), width, height);
    }

    public static ImageDAO getRandomImage() {
        return randomImage();
    }

    public static String viewUrl(ImageSourceDTO dto) {
        String base = Config.getInstance().getImageSourceViewBaseUrl();
        if (!isBlank(base) && !isBlank(dto.getImageUuid())) {
            return base.endsWith("/") ? base + dto.getImageUuid() : base + "/" + dto.getImageUuid();
        }
        return dto.getSourceUrl();
    }

    private static String[] authHeaders(Config config) {
        String token = config.getImageSourceToken();
        if (isBlank(token)) {
            return new String[0];
        }
        return new String[]{"Authorization", "Bearer " + token};
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim());
    }

    private static String joinPath(String base, String value) {
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return base.endsWith("/") ? base + encoded : base + "/" + encoded;
    }

    private static int firstPositiveInt(JsonNode node, String... fields) {
        for (String field : fields) {
            int value = node.path(field).asInt(0);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private static long firstPositiveLong(JsonNode node, String... fields) {
        for (String field : fields) {
            long value = node.path(field).asLong(0L);
            if (value > 0) {
                return value;
            }
        }
        return 0L;
    }
}
