package top.yzljc.atribot.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.utils.tools.Alert;

/**
 * @Author AndyOctopus
 * @ClassName ImageDelivery
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.configuration
 */
@Slf4j
public final class ImageDelivery {

    private static final String WAY_OSS = "oss";

    /**
     * 从生图端响应的 {@code data} 节点里取出 uuid 与 way，拼出可访问的图片地址
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
                Alert.notify("触发OSS端流量转移：远程服务器要求调转至OSS端口，已回落到OSS根节点！");
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