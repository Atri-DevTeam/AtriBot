package top.yzljc.atribot.function.impl.pic;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.request.HttpService;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.service.request.BizResponse;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;
import top.yzljc.atribot.utils.HashUtils;

import static top.yzljc.atricore.utils.StringUtils.isBlankOrNullLiteral;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageSourceClient
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.impl.pic
 */
@Slf4j
public class ImageSourceClient {
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public static String fetchAndHash(String url) {
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            var response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("拉取投稿图片失败: status={}, url={}", response.statusCode(), url);
                return null;
            }
            byte[] body = response.body();
            if (body == null || body.length == 0 || body.length > MAX_BYTES) {
                log.warn("投稿图片为空或超过大小上限: url={}", url);
                return null;
            }
            return HashUtils.sha256Hex(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("拉取投稿图片异常: url={}", url, e);
            return null;
        }
    }

    /**
     * 将投稿交给 UGC pic 服务下载并保存，上传成功后仍处于待审核状态
     *
     * @param dto 已生成图片 UUID 的投稿记录
     * @return UGC 处理后的尺寸和文件大小，服务未启用或请求失败时返回失败结果
     */
    public static UploadResult upload(ImageSourceDTO dto) {
        if (!Config.getInstance().isImageSourceEnabled()) return UploadResult.fail("图源投稿未启用");
        try {
            return upload(dto, OpenApi.get("pic.upload"), authHeaders());
        } catch (Exception e) {
            log.warn("图源上传失败: id={}", dto.getId(), e);
            return UploadResult.fail("图源服务暂不可用");
        }
    }

    static UploadResult upload(ImageSourceDTO dto, String endpoint, String... headers) {
        var response = Requests.post(endpoint, Map.of("uuid", dto.getImageUuid(), "sourceUrl", dto.getSourceUrl()), headers);
        if (!response.isSuccess()) {
            log.warn("图源上传失败: id={}, code={}", dto.getId(), response.bizCode());
            return UploadResult.fail(response.message());
        }
        JsonNode data = response.data();
        if (data == null || !dto.getImageUuid().equals(data.path("uuid").asText())
                || data.path("width").asInt() <= 0 || data.path("height").asInt() <= 0 || data.path("file_size").asLong() <= 0) {
            return UploadResult.fail("图源服务返回的图片信息无效");
        }
        return new UploadResult(true, "", data.path("width").asInt(), data.path("height").asInt(), data.path("file_size").asLong());
    }

    public record UploadResult(boolean ok, String message, int width, int height, long fileSize) {
        public static UploadResult fail(String message) { return new UploadResult(false, message, 0, 0, 0L); }
    }

    public static RemoteResult delete(ImageSourceDTO dto) {
        if (isBlankOrNullLiteral(dto.getImageUuid())) return RemoteResult.fail("图片 UUID 缺失");
        try {
            return remoteResult(Requests.delete(OpenApi.get("pic.delete", "uuid", dto.getImageUuid()), authHeaders()));
        } catch (Exception e) {
            log.warn("远端图源删除失败: id={}", dto.getId(), e);
            return RemoteResult.fail("图源服务暂不可用");
        }
    }

    /**
     * 同步 UGC 审核状态，由远端控制图片是否进入随机图池
     *
     * @param dto 待变更的投稿记录
     * @param status 目标审核状态
     * @return 远端确认结果，失败时调用方不得更新本地审核状态
     */
    public static RemoteResult setStatus(ImageSourceDTO dto, ImageReviewStatus status) {
        if (isBlankOrNullLiteral(dto.getImageUuid())) return RemoteResult.fail("图片 UUID 缺失");
        try {
            return remoteResult(Requests.post(OpenApi.get("pic.status", "uuid", dto.getImageUuid()),
                    Map.of("status", status.name()), authHeaders()));
        } catch (Exception e) {
            log.warn("远端图源审核状态变更失败: id={}", dto.getId(), e);
            return RemoteResult.fail("图源服务暂不可用");
        }
    }

    static RemoteResult remoteResult(BizResponse<JsonNode> response) {
        return response.isSuccess() ? new RemoteResult(true, "") : RemoteResult.fail(response.message());
    }

    public record RemoteResult(boolean ok, String message) {
        public static RemoteResult fail(String message) { return new RemoteResult(false, message); }
    }

    public static ImageDAO randomImage() { return randomImage(null); }

    public static ImageDAO randomImage(Platform platform) {
        ImageSourceDTO dto = ImageSourceRepository.findRandomReviewed();
        if (dto == null) return null;
        int width = dto.getProcessedWidth() > 0 ? dto.getProcessedWidth() : dto.getWidth();
        int height = dto.getProcessedHeight() > 0 ? dto.getProcessedHeight() : dto.getHeight();
        boolean apiOnly = platform != null && !platform.isOfficialQQPlatform();
        return new ImageDAO(apiOnly ? viewUrl(dto) : deliveryUrl(dto), width, height);
    }

    public static ImageDAO getRandomImage() { return randomImage(); }

    public static ImageDAO getRandomImage(Platform platform) { return randomImage(platform); }

    /**
     * 查询当前可用的分发地址，不缓存可能过期的 COS 签名或远端副本状态
     *
     * @param dto 已审核的投稿记录
     * @return UGC 选定的分发地址，查询失败时使用图片 API 地址
     */
    public static String deliveryUrl(ImageSourceDTO dto) {
        String fallback = viewUrl(dto);
        if (isBlankOrNullLiteral(dto.getImageUuid())) return fallback;
        try {
            var response = Requests.get(OpenApi.get("pic.delivery", "uuid", dto.getImageUuid()), authHeaders());
            if (response.isSuccess() && response.data() != null) {
                return deliveryUrl(response.data(), Config.getInstance().getUgcApiUrl(), fallback);
            }
            log.warn("查询图源分发失败，使用 API 地址: uuid={}, code={}", dto.getImageUuid(), response.bizCode());
        } catch (Exception e) {
            log.warn("查询图源分发失败，使用 API 地址: uuid={}", dto.getImageUuid(), e);
        }
        return fallback;
    }

    static String deliveryUrl(JsonNode data, String baseUrl, String fallback) {
        for (String field : new String[]{"url", "api_url"}) {
            String value = data.path(field).asText("");
            if (isBlankOrNullLiteral(value)) continue;
            try {
                URI uri = URI.create(value.startsWith("/") && !value.startsWith("//")
                        ? baseUrl.replaceAll("/+$", "") + value : value);
                if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null && uri.getUserInfo() == null) return uri.toString();
            } catch (IllegalArgumentException ignored) {
            }
        }
        return fallback;
    }

    /**
     * 获取与 UGC api_url 对应的图片本体接口，不调用分发查询
     *
     * @param dto 投稿记录
     * @return WebUI 使用的 API 地址，无图片 UUID 或目录不可用时返回 null
     */
    public static String viewUrl(ImageSourceDTO dto) {
        if (isBlankOrNullLiteral(dto.getImageUuid())) return null;
        try {
            return OpenApi.get("pic.image", "uuid", dto.getImageUuid());
        } catch (Exception e) {
            log.warn("图源图片接口不可用: uuid={}", dto.getImageUuid(), e);
            return null;
        }
    }

    private static String[] authHeaders() {
        String token = Config.getInstance().getAtribotKeySecret();
        return isBlankOrNullLiteral(token) ? new String[0] : new String[]{"Authorization", "Bearer " + token};
    }
}
