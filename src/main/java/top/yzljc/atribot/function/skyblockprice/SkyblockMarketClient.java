package top.yzljc.atribot.function.skyblockprice;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.request.Requests;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SkyblockMarketClient
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.skyblockprice
 */
public class SkyblockMarketClient {
    public record Item(String id, String key) {}

    public JsonNode search(String query, String cursor) throws IOException {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("q", query);
        body.put("limit", 20);
        body.put("only_priced", true);
        if (cursor != null && !cursor.isBlank()) body.put("cursor", cursor);
        return request("search", body);
    }

    public JsonNode quote(Item item) throws IOException {
        JsonNode result = quotes(List.of(item));
        if (result.isEmpty() || result.get(0).path("unavailable").asBoolean(false))
            throw new IOException("物品价格暂不可用");
        return result.get(0);
    }

    public JsonNode quotes(List<Item> items) throws IOException {
        JsonNode result = request("quotes", Map.of("items", items)).path("quotes");
        if (!result.isArray() || result.size() > 20) throw new IOException("行情响应格式无效");
        return result;
    }

    public JsonNode icon(Item item) throws IOException {
        return request("icon", item);
    }

    private JsonNode request(String operation, Object body) throws IOException {
        var result = Requests.post(OpenApi.get("bot.hypixel.skyblock.market." + operation), body,
                "Authorization", "Bearer " + Config.getInstance().getAtribotKeySecret());
        if (!result.isSuccess() || result.data() == null || !result.data().isObject())
            throw new IOException("物品行情服务暂不可用");
        return result.data();
    }
}
