package top.yzljc.atribot.function.official.imagesource;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 图源远端交互。
 *
 * <p>图片本体不落本地：这里只把附件字节流临时读进内存算 hash，随后把元数据 POST 给远端图床，
 * 由远端按 uuid 去拉取并保管图片。
 *
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

    /**
     * 拉取图片字节并计算 SHA-256。
     *
     * @return 十六进制 hash，失败返回 null
     */
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
     * 库里的 review_status 只服务于前端展示，审核结论由远端事后回调 {@code /api/public/imagesource/review}。
     *
     * <p>响应体与本项目的 {@code Result} 同构，以 {@code status} 判定：200 成功，400 上传失败。
     *
     * @return 是否上报成功；远端未启用时视为成功，便于本地独立调试
     */
    public static boolean upload(ImageSourceDTO dto) {
        Config config = Config.getInstance();
        String url = config.getImageSourceUploadUrl();
        if (!config.isImageSourceEnabled() || isBlank(url)) {
            log.info("图源远端未启用，跳过上报: id={}", dto.getId());
            return true;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", dto.getImageUuid());
        body.put("uploader", dto.getUploaderId());
        body.put("time", dto.getCreateTime() != null ? dto.getCreateTime().getTime() : System.currentTimeMillis());
        body.put("hash", dto.getHash());
        // 以下为扩展字段，远端可按需忽略
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
                return false;
            }
            int status = response.path("status").asInt(-1);
            if (status == 200) {
                log.info("图源上报成功: id={}, uuid={}", dto.getId(), dto.getImageUuid());
                return true;
            }
            log.warn("图源上报被远端拒绝: id={}, uuid={}, status={}, message={}",
                    dto.getId(), dto.getImageUuid(), status, response.path("message").asText(""));
            return false;
        } catch (Exception e) {
            log.warn("图源上报异常: id={}", dto.getId(), e);
            return false;
        }
    }

    /**
     * 把审核结果回传给远端。远端未配置回传地址时静默跳过，不影响本地审核落库。
     */
    public static void reportReview(ImageSourceDTO dto) {
        Config config = Config.getInstance();
        String url = config.getImageSourceReviewUrl();
        if (!config.isImageSourceEnabled() || isBlank(url) || isBlank(dto.getImageUuid())) {
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uuid", dto.getImageUuid());
        body.put("uploader", dto.getUploaderId());
        body.put("time", dto.getReviewTime() != null ? dto.getReviewTime().getTime() : System.currentTimeMillis());
        body.put("hash", dto.getHash());
        body.put("review", dto.getReviewStatus());
        body.put("reviewer", dto.getReviewer());
        body.put("remark", dto.getReviewRemark());

        try {
            HttpService.postJson(url, body, authHeaders(config));
            log.info("图源审核结果已回传: uuid={}, review={}", dto.getImageUuid(), dto.getReviewStatus());
        } catch (Exception e) {
            log.warn("回传图源审核结果失败: uuid={}", dto.getImageUuid(), e);
        }
    }

    /**
     * WebUI 展示用地址：优先走远端图床，未配置时回退到平台原始 CDN 链接。
     *
     * <p>原始链接带会过期的 rkey，只能作为兜底。
     */
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
}
