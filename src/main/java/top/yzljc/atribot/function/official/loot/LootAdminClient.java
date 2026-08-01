package top.yzljc.atribot.function.official.loot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName LootAdminClient
 * @Created_at 2026/07/31
 * @Project AtriBot
 * @Package top.yzljc.atribot.function.official.loot
 */
@Slf4j
public class LootAdminClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode listItems(int page, int pageSize) {
        String url = ResourcesProperties.LOOTS_ADMIN_ITEMS_API + "?page=" + page + "&pageSize=" + pageSize;
        return HttpService.sendGetRequest(url, authHeaders());
    }

    public static JsonNode createItem(String displayName, String description, byte[] imageBytes, String filename, String contentType) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("displayName", displayName);
        fields.put("description", description == null ? "" : description);
        List<HttpService.MultipartFile> files = List.of(new HttpService.MultipartFile("image", filename, contentType, imageBytes));

        String raw = HttpService.postMultipartForString(ResourcesProperties.LOOTS_ADMIN_ITEMS_API, fields, files, authHeaders());
        return parse(raw);
    }

    public static JsonNode updateItem(String itemId, String displayName, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", displayName);
        body.put("description", description);
        return HttpService.putJson(joinPath(ResourcesProperties.LOOTS_ADMIN_ITEMS_API, itemId), body, authHeaders());
    }

    public static JsonNode replaceItemImage(String itemId, byte[] imageBytes, String filename, String contentType) {
        List<HttpService.MultipartFile> files = List.of(new HttpService.MultipartFile("image", filename, contentType, imageBytes));
        String raw = HttpService.postMultipartForString(
                joinPath(ResourcesProperties.LOOTS_ADMIN_ITEMS_API, itemId) + "/image", Map.of(), files, authHeaders());
        return parse(raw);
    }

    public static JsonNode deleteItem(String itemId) {
        return HttpService.sendDeleteRequest(joinPath(ResourcesProperties.LOOTS_ADMIN_ITEMS_API, itemId), authHeaders());
    }

    private static String[] authHeaders() {
        String token = Config.getInstance().getLootsAdminToken();
        if (isBlank(token)) {
            return new String[0];
        }
        return new String[]{"Authorization", "Bearer " + token};
    }

    private static JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            log.warn("解析抽卡管理接口响应失败: {}", raw, e);
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim());
    }

    private static String joinPath(String base, String value) {
        String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return base.endsWith("/") ? base + encoded : base + "/" + encoded;
    }
}
