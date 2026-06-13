package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QQ 官方机器人 ARK 消息模板。
 * <p>
 * 使用 {@link #of(int, List)} 工厂方法 + 预定义 key 常量快速构造：
 * <pre>{@code
 * Ark ark = Ark.of(Ark.TEMPLATE_LINK_TEXT_LIST, List.of(
 *     Map.of("key", Ark.KEY_DESC, "value", "机器人消息"),
 *     Map.of("key", Ark.KEY_TITLE, "value", "新闻标题"),
 *     Map.of("key", Ark.KEY_META_URL, "value", "https://example.com"),
 *     Map.of("key", Ark.KEY_META_LIST, "obj", List.of(
 *         Map.of("obj_kv", List.of(
 *             Ark.pair("name", "aaa"),
 *             Ark.pair("age", "3")
 *         ))
 *     ))
 * ));
 * }</pre>
 *
 * @Author YZ_Ljc_
 * @ClassName Ark
 * @Created_at 2026/06/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 * @deprecated 沟槽的 ARK 不让用啊而且难用的一批
 */
@Getter
@AllArgsConstructor
public class Ark {

    /** 链接+文本列表模板 */
    public static final int TEMPLATE_LINK_TEXT_LIST = 23;
    /** 文本+缩略图模板 */
    public static final int TEMPLATE_TEXT_THUMBNAIL = 24;
    /** 大图模板 */
    public static final int TEMPLATE_BIG_IMAGE = 37;

    /** 描述文本（模板 23, 24） */
    public static final String KEY_DESC = "#DESC#";
    /** 提示文本（模板 23） */
    public static final String KEY_PROMPT = "#PROMPT#";
    /** 标题（模板 23, 24, 37） */
    public static final String KEY_TITLE = "#TITLE#";
    /** Meta 描述（模板 23） */
    public static final String KEY_META_DESC = "#META_DESC#";
    /** Meta 链接（模板 23） */
    public static final String KEY_META_URL = "#META_URL#";
    /** Meta 列表，数组类型（模板 23） */
    public static final String KEY_META_LIST = "#META_LIST#";
    /** 图片 URL（模板 24, 37） */
    public static final String KEY_IMG = "#IMG#";
    /** 跳转链接（模板 24, 37） */
    public static final String KEY_LINK = "#LINK#";

    @JsonProperty("template_id")
    private final int templateId;

    private final List<KvItem> kv;

    /**
     * 快速构建一个 {@code obj_kv} 内的简单键值对 Map。
     * <pre>{@code
     * Ark.pair("name", "aaa")  // => {"key": "name", "value": "aaa"}
     * }</pre>
     */
    public static Map<String, String> pair(String key, String value) {
        return Map.of("key", key, "value", value);
    }

    /**
     * 使用 Map 列表构建 Ark 实例。
     * <p>
     * 每个 Map 元素有两种形式：
     * <ul>
     *   <li>纯文本变量：{@code Map.of("key", "xxx", "value", "xxx")}</li>
     *   <li>数组变量：{@code Map.of("key", "xxx", "obj", List.of(...))}<br>
     *       其中 {@code obj} 的每个元素为 {@code Map.of("obj_kv", List.of(pair(...), ...))}</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public static Ark of(int templateId, List<Map<String, Object>> kvMaps) {
        List<KvItem> items = new ArrayList<>();
        for (Map<String, Object> kvMap : kvMaps) {
            String key = (String) kvMap.get("key");
            if (kvMap.containsKey("obj")) {
                List<Map<String, Object>> objRaw = (List<Map<String, Object>>) kvMap.get("obj");
                List<ObjItem> objItems = new ArrayList<>();
                for (Map<String, Object> objMap : objRaw) {
                    List<Map<String, String>> objKvRaw = (List<Map<String, String>>) objMap.get("obj_kv");
                    List<Pair> pairs = new ArrayList<>();
                    for (Map<String, String> pairMap : objKvRaw) {
                        pairs.add(new Pair(pairMap.get("key"), pairMap.get("value")));
                    }
                    objItems.add(new ObjItem(pairs));
                }
                items.add(new KvItem(key, null, objItems));
            } else {
                String value = (String) kvMap.get("value");
                items.add(new KvItem(key, value, null));
            }
        }
        return new Ark(templateId, items);
    }

    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class KvItem {
        private final String key;
        private final String value;
        private final List<ObjItem> obj;
    }

    @Getter
    @AllArgsConstructor
    public static class ObjItem {
        @JsonProperty("obj_kv")
        private final List<Pair> objKv;
    }

    @Getter
    @AllArgsConstructor
    public static class Pair {
        private final String key;
        private final String value;
    }
}
