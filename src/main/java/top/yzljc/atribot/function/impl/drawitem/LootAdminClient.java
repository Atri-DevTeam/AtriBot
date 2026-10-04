package top.yzljc.atribot.function.impl.drawitem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.BizResponse;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;

import static top.yzljc.atribot.utils.StringUtils.isBlankOrNullLiteral;

/**
 * @Author YZ_Ljc_
 * @ClassName LootAdminClient
 * @Created_at 2026/07/31
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.impl.drawitem
 */
public class LootAdminClient {


    public static BizResponse<JsonNode> listItems(int page, int pageSize) {
        String url = OpenApi.get("admin.loots.items.list") + "?page=" + page + "&pageSize=" + pageSize;
        return Requests.get(url, authHeaders());
    }

    public static BizResponse<JsonNode> createItem(String displayName, String description, byte[] imageBytes, String filename, String contentType,
                                      boolean special) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("displayName", displayName);
        fields.put("description", description == null ? "" : description);
        fields.put("special", String.valueOf(special));
        List<Requests.Upload> files = List.of(new Requests.Upload("image", filename, contentType, imageBytes));

        return Requests.multipart(OpenApi.get("admin.loots.items.create"), fields, files, authHeaders());
    }

    public static BizResponse<JsonNode> updateItem(String itemId, String displayName, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", displayName);
        body.put("description", description);
        return Requests.put(OpenApi.get("admin.loots.items.update", "itemId", itemId), body, authHeaders());
    }

    public static BizResponse<JsonNode> replaceItemImage(String itemId, byte[] imageBytes, String filename, String contentType) {
        List<Requests.Upload> files = List.of(new Requests.Upload("image", filename, contentType, imageBytes));
        return Requests.multipart(OpenApi.get("admin.loots.items.image", "itemId", itemId), Map.of(), files, authHeaders());
    }

    public static BizResponse<JsonNode> deleteItem(String itemId) {
        return Requests.delete(OpenApi.get("admin.loots.items.delete", "itemId", itemId), authHeaders());
    }

    private static String[] authHeaders() {
        String token = Config.getInstance().getLootsAdminToken();
        if (isBlankOrNullLiteral(token)) {
            return new String[0];
        }
        return new String[]{"Authorization", "Bearer " + token};
    }

    public static String imageBaseUrl() {
        return imageBaseUrl(OpenApi.get("bot.loots.item.image"));
    }

    static String imageBaseUrl(String template) {
        String suffix = "/{itemId}";
        if (!template.endsWith(suffix)) throw new IllegalStateException("物品图片接口格式无效");
        return template.substring(0, template.length() - suffix.length());
    }
}
