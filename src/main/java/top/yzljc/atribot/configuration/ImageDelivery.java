package top.yzljc.atribot.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 按生图端给的 {@code way} 挑选图片根节点。
 *
 * <p>生图端在返回 {@code uuid} 的同时会带一个 {@code way}（{@code api} / {@code oss}）：
 * 平时是 {@code api}，走它自己的出口带宽，不产生 OSS 下行费用；
 * 只有它被限速或带宽打满时才给 {@code oss}，把这一段峰值甩到 OSS 上。
 *
 * <p>拿不到 way、way 不认识、或者本端根本没配 OSS 根节点，都统一回落到 api 根节点。
 * 这条回落永远是安全的：生图端本机也留着同一份图，只是少了这层流量卸载而已。
 *
 * @Author Claude Opus 5
 * @ClassName ImageDelivery
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
@Slf4j
public final class ImageDelivery {

    private static final String WAY_OSS = "oss";

    private ImageDelivery() {
    }

    /**
     * 从生图端响应的 {@code data} 节点里取出 uuid 与 way，拼出可访问的图片地址。
     *
     * @return 图片地址；data 里没有 uuid 时返回 null
     */
    public static String resolve(JsonNode data) {
        if (data == null) {
            return null;
        }
        String uuid = data.path("uuid").asText(null);
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        return resolve(uuid, data.path("way").asText(null));
    }

    public static String resolve(String uuid, String way) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        if (WAY_OSS.equalsIgnoreCase(way)) {
            String ossRoot = ossDumpRoot();
            if (ossRoot != null) {
                return join(ossRoot, uuid);
            }
            log.warn("生图端要求走 OSS，但本端未配置 delivery.oss-dump-base-url，本次回落到 API 根节点: uuid={}", uuid);
        }
        return join(ResourcesProperties.DUMP, uuid);
    }

    private static String ossDumpRoot() {
        String root = Config.getInstance().getOssDumpBaseUrl();
        if (root == null || root.isBlank() || "null".equalsIgnoreCase(root.trim())) {
            return null;
        }
        return root.trim();
    }

    private static String join(String root, String uuid) {
        return root.endsWith("/") ? root + uuid : root + "/" + uuid;
    }
}
